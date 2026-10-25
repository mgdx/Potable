# Publier une version

Ce document dit comment construire, signer, vérifier et publier l'APK de
Potable. Les choix qu'il applique sont consignés dans `docs/decisions.md`.

## La clé de signature, et pourquoi elle ne change jamais

Android identifie une application par son `applicationId` **et par le
certificat qui signe son APK**. Une mise à jour n'est acceptée que si elle
porte la même signature que la version installée. Perdre la clé, c'est priver
toutes les installations de la version suivante : il faudrait désinstaller,
donc perdre ses communes suivies, et réinstaller.

La clé n'est donc pas un mot de passe qu'on renouvelle : **c'est l'identité de
Potable**. Elle n'est jamais dans le dépôt (`.gitignore` refuse `*.jks` et
`keystore.properties`) et vit en deux exemplaires au moins : le gestionnaire de
mots de passe de la personne qui publie, et une copie hors ligne.

### Créer la clé (une seule fois)

```bash
keytool -genkeypair \
  -keystore potable-release.jks \
  -storetype PKCS12 \
  -alias <alias> \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -dname "CN=Potable, C=FR"
```

Un magasin PKCS12 ne peut pas avoir un mot de passe de clé différent de celui
du magasin : les deux valeurs sont identiques. Une fois la clé créée, consigner
l'empreinte SHA-256 de son certificat (public) dans ce document, pour que
chacun puisse vérifier un APK téléchargé :

```bash
keytool -list -v -keystore potable-release.jks -alias <alias> | grep SHA256
```

La clé de Potable existe (créée le 2026-10-25, RSA 4096, `CN=Potable, C=FR`,
alias `potable`, valable 10 000 jours). Empreinte SHA-256 de son certificat :

```
1a60e23938a43fccda3c3046b09a940d114da8a986ddeeda501ad0d303e3c82d
```

Pour vérifier un APK téléchargé :
`apksigner verify --print-certs potable.apk | grep "SHA-256 digest"` doit
afficher cette empreinte sur la ligne `certificate`.

### Brancher la clé sur la build

`app/build.gradle.kts` lit `keystore.properties` à la racine du dépôt :

```properties
storeFile=/chemin/absolu/vers/potable-release.jks
storePassword=<mot de passe>
keyAlias=<alias>
keyPassword=<mot de passe>
```

`chmod 600 keystore.properties potable-release.jks`. Un chemin relatif dans
`storeFile` est résolu depuis la racine du dépôt.

**Sans ce fichier, l'APK release n'est pas signé** : la build réussit et
produit `app/build/outputs/apk/release/app-release-unsigned.apk`. C'est le cas
de toute machine autre que celle de la personne qui publie, et de F-Droid.
Aucune clé n'est inventée en repli, pas même celle de debug : un APK non signé
ne s'installe pas, il ne peut donc pas être publié par erreur.

La signature utilise les schémas v2 et v3. Le v3 porte la lignée du
certificat : c'est lui qui permettrait un jour de changer de clé sans casser
les mises à jour.

## Construire

Prérequis : un **JDK 21** (celui de l'image de build F-Droid ; la build ne
télécharge aucune toolchain), le SDK Android (plateforme 37) et le **NDK
28.2.13676358** (`sdkmanager "ndk;28.2.13676358"`). L'application ne compile
aucun code natif, mais AGP élague les bibliothèques natives de DataStore et
d'`androidx.graphics.path` avec le NDK : c'est pourquoi sa version est fixée
dans `app/build.gradle.kts` (voir « Reproductibilité »).

```bash
./gradlew check                       # tests, lint, ktlint, checkRtl, checkTranslations
./gradlew clean :app:assembleRelease  # R8 + réduction des ressources
./gradlew :app:lintRelease
```

Un seul APK sort, sans découpage par ABI : `app-release.apk` (signé) ou
`app-release-unsigned.apk`. Cible du SPEC §2 : moins de 6 Mo. Mesuré à la
version 0.1.0 : **2 942 194 octets** (11 210 440 sans R8).

Les seuls avertissements tolérés sont les deux du manifest merger sur
`processDebugUnitTestManifest` (`FOREGROUND_SERVICE` et
`SystemForegroundService`, voir `docs/decisions.md`).

### Ce que pèse l'APK

`~/Android/Sdk/cmdline-tools/latest/bin/apkanalyzer` décompose l'APK ; avec
`dex packages --defined-only --proguard-mappings
app/build/outputs/mapping/release/mapping.txt`, il attribue le code minifié à
ses paquets d'origine. À la version 0.1.0 (octets compressés dans l'APK) :

| Entrée | Compressé | Décompressé |
| --- | ---: | ---: |
| `classes.dex` | 1 680 730 | 3 290 080 |
| `assets/communes.tsv` (référentiel) | 693 531 | 1 687 545 |
| `resources.arsc` (non compressé) | 292 604 | 292 604 |
| `assets/PublicSuffixDatabase.list` (OkHttp) | 42 484 | 132 737 |
| `META-INF/androidx/**` | 29 244 | 82 834 |
| `lib/*` (4 ABI) | 64 552 | 64 552 |

Dans le dex (octets définis, décompressés) : Compose 1,43 Mo (dont `ui`
590 Ko, `foundation` 400 Ko, `material3` 135 Ko, icônes 14 Ko), autres AndroidX
560 Ko (DataStore 102 Ko, WorkManager 93 Ko, Room 60 Ko, Navigation 55 Ko),
Potable 262 Ko, kotlinx 175 Ko, Kotlin 151 Ko, OkHttp + Okio 142 Ko.

`localeFilters` sur les deux langues de `locales_config.xml` est appliqué
depuis le 2026-10-27 (anglais et français seulement, `docs/decisions.md`) : il
retire les traductions des bibliothèques dans les autres langues. Gains
mesurés et **non appliqués** (la cible est tenue) : `ignoreAssetsPatterns += "PublicSuffixDatabase.list"` (−42 Ko,
OkHttp ne le lit que pour les cookies), exclusions de `META-INF/androidx/**`,
`/META-INF/*.version` et `/kotlin/**` (−55 Ko) : −97 Ko en tout.

## Vérifier

### La signature

```bash
~/Android/Sdk/build-tools/37.0.0/apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

Attendu : `v2 scheme: true`, `v3 scheme: true`, et l'empreinte SHA-256 du
certificat de Potable. `CN=Android Debug` signifie que ce n'est pas la bonne
clé.

### Le wrapper Gradle

Le wrapper est celui de Gradle 9.5.0 ; `gradle-wrapper.properties` épingle la
distribution par `distributionSha256Sum`. Sommes officielles
(`https://services.gradle.org/distributions/gradle-9.5.0-wrapper.jar.sha256`
et `…-bin.zip.sha256`) :

| Fichier | SHA-256 |
| --- | --- |
| `gradle/wrapper/gradle-wrapper.jar` | `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7` |
| `gradle-9.5.0-bin.zip` | `553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746` |

```bash
sha256sum gradle/wrapper/gradle-wrapper.jar
```

Après une montée de version de Gradle, lancer **deux fois**
`./gradlew wrapper --gradle-version <v> --distribution-type bin
--gradle-distribution-sha256-sum <somme du zip>` : la première exécution
tourne sous l'ancien Gradle et réécrit son propre jar, la seconde écrit celui
de la nouvelle version. Puis comparer le jar à la somme officielle et mettre
ce tableau à jour.

### La reproductibilité

F-Droid reconstruit l'APK à partir des sources et le compare au fichier
publié : il ne publie rien si les deux diffèrent. Ce qui a été fait pour
qu'ils coïncident :

- `dependenciesInfo { includeInApk = false; includeInBundle = false }` : pas
  de bloc de dépendances chiffré par Google dans l'APK ;
- `vcsInfo { include = false }` en release : AGP n'écrit ni le commit ni, hors
  dépôt Git, le message `NO_VALID_GIT_FOUND`
  (`META-INF/version-control-info.textproto`) ;
- `ndkVersion` fixé (voir plus haut) ;
- `BuildConfig` ne contient ni date ni chemin (`VERSION_NAME`, `VERSION_CODE`,
  `BUILD_TYPE`, `APPLICATION_ID`, `DEBUG`) ; les entrées du zip sont datées
  du 1er janvier 1981 par AGP ;
- dépôts publics seulement (`google()`, `mavenCentral()`,
  `gradlePluginPortal()` pour les plugins), aucun téléchargement de JDK.

Vérifier sur deux builds propres (de préférence deux clones à deux chemins
différents) :

```bash
./gradlew clean :app:assembleRelease
cp app/build/outputs/apk/release/app-release-unsigned.apk /tmp/build1.apk
./gradlew clean :app:assembleRelease --no-build-cache --no-configuration-cache
cmp app/build/outputs/apk/release/app-release-unsigned.apk /tmp/build1.apk
```

`cmp` ne doit rien afficher. En cas de différence, `diffoscope` (ou
`unzip -lv` des deux fichiers, colonne CRC) désigne l'entrée fautive.

Pour comparer un APK **signé** à une reconstruction non signée, comme le fait
F-Droid :

```bash
apksigcopier compare app-release.apk --unsigned app-release-unsigned.apk
```

(`apksigcopier` est dans Debian et dans `fdroidserver`.) Le code de sortie 0
signifie que seule la signature les sépare.

Pour reproduire les conditions de F-Droid sur un poste : un clone neuf, un
seul JDK 21, aucun provisionnement de toolchain.

```bash
JDK21=<un JDK 21 complet>   # ex. ~/.gradle/jdks/jetbrains_s_r_o_-21-amd64-linux.2
JAVA_HOME=$JDK21 ./gradlew --no-daemon --no-configuration-cache \
  -Porg.gradle.java.installations.auto-download=false \
  -Porg.gradle.java.installations.auto-detect=false \
  -Porg.gradle.java.installations.paths=$JDK21 \
  clean :app:assembleRelease
```

Deux pièges, qui donnaient « Cannot find a Java installation on your machine
… matching: {languageVersion=21 …}. Toolchain auto-provisioning is not
enabled » avec l'ancienne version de cette commande :

- **le JDK doit être complet**. Sous Debian, `/usr/lib/jvm/java-21-openjdk-amd64`
  n'est souvent que le JRE (`openjdk-21-jre-headless`, pas de `bin/javac`) :
  Gradle ne le retient ni comme JVM du démon (`gradle-daemon-jvm.properties`
  demande 21) ni comme toolchain. Installer `openjdk-21-jdk-headless` ou
  pointer un JDK complet ;
- **`--no-daemon`** : sans lui, un démon JDK 21 déjà lancé par une autre build
  est réutilisé et la commande réussit même quand le JDK désigné ne convient
  pas ; le test ne prouve alors rien.

`installations.paths` rend le choix explicite : la build réussit même si
`JAVA_HOME` désigne un JRE ou une autre version, tant que `paths` nomme un JDK
21 complet. Vérifié le 2026-10-25 sur un clone neuf avec le JDK 21 JetBrains
de `~/.gradle/jdks/` : `BUILD SUCCESSFUL`, APK non signé de SHA-256
`0aac8097417c385e1582d53979f3613b3224e73155bba53eed83203b91726311`, identique
à celui de la revue F-Droid.

### Le graphe de dépendances

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath \
  | grep -iE 'gms|firebase|play-services|com\.google\.android'
```

Ne doit rien afficher. Le seul artefact `com.google` attendu est
`com.google.guava:listenablefuture` (Apache 2.0, classe vide tirée par
WorkManager).

## Publier, étape par étape

1. **Version** dans `app/build.gradle.kts` : `versionCode` augmente d'un et ne
   redescend jamais (c'est lui qu'Android compare), `versionName` suit le
   versionnage sémantique. Les deux sont écrits en littéraux : l'outil de mise
   à jour de F-Droid lit ces lignes.
2. **Notes de version** : `fastlane/metadata/android/<langue>/changelogs/<versionCode>.txt`,
   l'anglais (`en-US`) d'abord.
3. **Construire et vérifier** : `./gradlew check`, puis
   `./gradlew clean :app:assembleRelease :app:lintRelease`, puis la signature
   (`apksigner`) et la reproductibilité (ci-dessus).
4. **Tester l'APK release sur un appareil** : c'est le seul à passer par R8.
   Parcours minimal : recherche d'une commune, fiche, historique, suivi,
   activation d'une alerte (WorkManager), changement de thème, relance de
   l'application (Room, DataStore).
5. **Étiqueter** le commit de la version `vX.Y.Z` et publier l'APK renommé
   `potable-<version>.apk` dans la release, avec l'empreinte du certificat dans
   le texte de la release.

## F-Droid

La recette vit dans [fdroiddata](https://gitlab.com/fdroid/fdroiddata),
`metadata/io.github.mgdx.potable.yml`. L'entrée de build doit nommer le même
NDK que `app/build.gradle.kts` (`r28c` = 28.2.13676358) :

```yaml
Builds:
  - versionName: 0.1.0
    versionCode: 1
    commit: v0.1.0
    subdir: app
    gradle:
      - yes
    ndk: r28c
```

Pour que F-Droid serve l'APK signé par le projet plutôt qu'un APK signé de sa
propre clé, la recette ajoute `binary:` (URL de l'APK publié) et
`AllowedAPKSigningKeys:` (empreinte SHA-256 du certificat) : F-Droid vérifie
alors que sa reconstruction coïncide avec le fichier publié. Ce choix se fait
**à la première soumission** : une version déjà signée par F-Droid ne peut plus
passer sous la clé du projet.

Le format exact de la recette est celui que produit `fdroid rewritemeta` ; la
référence d'un dossier accepté est `docs/release.md` de Roue Libre (même
auteur, même toolchain : AGP 9.3, Gradle 9.5.0, JDK 21).

F-Droid ne lance pas les tests : le téléchargement de l'image Android de
Robolectric à leur première exécution ne concerne pas sa build.
