# Potable — dépendances

Toutes les dépendances sont libres, publiées sur Google Maven ou Maven
Central, sans service Google ni bibliothèque propriétaire (SPEC §2, §8).
Versions : `gradle/libs.versions.toml`. Toute nouvelle dépendance est ajoutée
ici avant d'être utilisée. La liste « Open-source libraries » de l'écran
À propos (`about_libraries` dans `strings.xml`) reprend les dépendances
embarquées de ce fichier et se tient à jour avec lui.

La localisation (« Autour de moi ») passe par le `LocationManager` du
framework via `LocationManagerCompat` (`androidx.core`), sans Google Play
Services ni bibliothèque supplémentaire.

## Outillage de build

| Dépendance | Version | Rôle | Licence | Pourquoi |
|---|---|---|---|---|
| Android Gradle Plugin (`com.android.application`, `com.android.library`) | 9.3.3 | Build Android, lint ; embarque le support Kotlin | Apache 2.0 | Obligatoire |
| Kotlin (`org.jetbrains.kotlin.jvm`, `plugin.compose`, `plugin.serialization`) | 2.3.21 | Compilateur du module JVM `:core`, plugins Compose et sérialisation | Apache 2.0 | Langage du projet |
| KSP (`com.google.devtools.ksp`) | 2.3.11 | Processeur d'annotations de Room | Apache 2.0 | Room ; appliqué à `:data` (pas de kapt) |
| ktlint Gradle (`org.jlleitschuh.gradle.ktlint`) | 14.2.0 | Format du code Kotlin, `ktlintCheck` | MIT | SPEC §12 : ktlint sans avertissement |

## `:core`

| Dépendance | Version | Rôle | Licence | Pourquoi |
|---|---|---|---|---|
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 | Lecture du JSON Hub'Eau | Apache 2.0 | Sérialisation imposée par le SPEC §8 ; pas de réflexion |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.11.0 | Fonctions suspendues, `Dispatchers` | Apache 2.0 | Appels réseau et lecture d'asset hors du thread principal |
| `junit:junit` (test) | 4.13.2 | Tests unitaires JVM | EPL 1.0 | Standard, compris par Gradle et AGP |

## `:data`

| Dépendance | Version | Rôle | Licence | Pourquoi |
|---|---|---|---|---|
| `com.squareup.okhttp3:okhttp` | 5.4.0 | Client HTTPS vers `hubeau.eaufrance.fr` | Apache 2.0 | Léger, sans Retrofit (voir `decisions.md`) |
| `androidx.datastore:datastore-preferences` | 1.2.1 | Réglages de l'utilisateur (avertissement, thème, fenêtres en années, UDI mémorisée) | Apache 2.0 | Remplaçant officiel des `SharedPreferences`, asynchrone ; aucun service Google |
| `androidx.room:room-runtime` (`api`), `room-compiler` (KSP) | 2.8.4 | Base `potable.db` : communes suivies et cache de leurs prélèvements (SPEC §7) | Apache 2.0 | Imposé par le SPEC §7 ; `api` car `PotableDatabase` est construite par `AppContainer`. `room-ktx` inutile : depuis Room 2.7 ses extensions (`withTransaction`, `Flow`) sont dans `room-runtime` |
| `com.squareup.okhttp3:mockwebserver3-junit4` (test) | 5.4.0 | Serveur HTTP local pour tester le client | Apache 2.0 | Tests du client sans réseau réel |
| `org.robolectric:robolectric` (test) | 4.16.1 | Fournit SQLite et un `Context` aux tests JVM de Room (base en mémoire), image Android 34 (`robolectric.properties`) | MIT | Room ne tourne pas sans SQLite Android ; sans Robolectric, la base ne se teste que sur appareil (test instrumenté, hors CI). Jamais embarqué dans l'APK. Robolectric télécharge son image `android-all-instrumented` depuis Maven Central au premier test |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` (test) | 1.11.0 | `runTest` | Apache 2.0 | Tests de code suspendu |
| `androidx.room:room-testing` (test) | 2.8.4 | `MigrationTestHelper` : crée chaque ancienne version de `potable.db` depuis `data/schemas/` et valide les migrations | Apache 2.0 | Seul outil officiel de vérification des migrations Room ; jamais embarqué dans l'APK |
| `kotlinx-coroutines-core`, `junit` | voir `:core` | | | |

## `:app`

| Dépendance | Version | Rôle | Licence | Pourquoi |
|---|---|---|---|---|
| `androidx.compose:compose-bom` (ui, ui-graphics, ui-tooling-preview, material3 ; ui-tooling en debug) | 2026.02.01 | Interface Jetpack Compose, Material 3 | Apache 2.0 | Imposé par le SPEC §8 |
| `androidx.activity:activity-compose` | 1.13.0 | `setContent`, edge-to-edge | Apache 2.0 | Point d'entrée Compose |
| `androidx.core:core-ktx` | 1.19.1 | Extensions AndroidX | Apache 2.0 | Compatibilité API 26+ |
| `androidx.lifecycle:lifecycle-runtime-ktx`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` | 2.11.0 | ViewModel, collecte d'états liée au cycle de vie | Apache 2.0 | État des écrans, survie à la rotation |
| `androidx.navigation:navigation-compose` | 2.10.2 | Navigation entre écrans | Apache 2.0 | Imposé par le SPEC (Navigation Compose) |
| `androidx.work:work-runtime-ktx` | 2.11.2 | Tâche périodique d'alerte (`AlertWorker`, `CoroutineWorker`), contrainte réseau, planification unique | Apache 2.0 | Imposé par le SPEC §5 F7 ; s'appuie sur `JobScheduler`, sans service Google. Son `SystemForegroundService` et la permission `FOREGROUND_SERVICE` sont retirés du manifeste (`tools:node="remove"`) : aucun worker ne passe au premier plan. Il ajoute `WAKE_LOCK`, `ACCESS_NETWORK_STATE` et `RECEIVE_BOOT_COMPLETED`, nécessaires à la tâche. Apporte `androidx.startup` et `room-runtime`, déjà présents |
| `androidx.compose.material:material-icons-core` | via BOM 2026.02.01 | Icônes Material de base (retour, recherche, alerte, info, position) | Apache 2.0 | `material3` 1.4 ne l'apporte plus ; le jeu « extended » n'est pas nécessaire |
| `junit` (test) | 4.13.2 | Tests unitaires | EPL 1.0 | |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` (test) | 1.11.0 | Tests des ViewModels (temps virtuel, `Dispatchers.Main`) | Apache 2.0 | |
| `org.robolectric:robolectric` (test) | 4.16.1 | `Context` Android pour les tests JVM des intents de notification et des requêtes WorkManager (image Android 34, `robolectric.properties`) | MIT | Déjà utilisé par `:data` ; sans lui ces tests exigeraient un appareil. Jamais embarqué dans l'APK |
| `androidx.work:work-testing` (test) | 2.11.2 | `WorkManagerTestInitHelper` : WorkManager en mémoire et synchrone, pour vérifier la requête réellement planifiée (période, contrainte réseau, mise à jour, annulation) | Apache 2.0 | Outil officiel de test de WorkManager ; vérifie ce que WorkManager reçoit et non une copie de la configuration. Jamais embarqué dans l'APK |
| `androidx.compose.ui:ui-test-junit4` (test) | via BOM 2026.02.01 | Tests d'interface Compose des écrans (`*ScreenTest`), exécutés sur la JVM par Robolectric dans `./gradlew check`, sans émulateur : `createComposeRule`, recherche de nœuds par leur sémantique, saisie et clics | Apache 2.0 | Outil officiel de test de Compose. Jamais embarqué dans l'APK. `ui-test-manifest` n'est volontairement pas utilisé : il ajouterait à l'APK de debug une `ComponentActivity` exportée ; les tests déclarent cette activité au gestionnaire de paquets de Robolectric (`ComposeHostRegistration`) |

## Déclarées au catalogue, pas encore utilisées

| Dépendance | Version | Rôle prévu | Licence |
|---|---|---|---|
| `androidx.room:room-ktx` | 2.8.4 | Inutile depuis Room 2.7 (voir `:data`) ; à retirer du catalogue | Apache 2.0 |

## Données embarquées

| Données | Fichier | Source | Version | Licence | Attribution |
|---|---|---|---|---|---|
| Pertinence des métabolites de pesticides pour les EDCH (23 métabolites) | `core/src/main/resources/metabolites-pertinence.csv` | Anses, « Tableau relatif aux métabolites pertinents pour les EDCH » : [tableur](https://www.anses.fr/system/files/tableur-pertinence-metabolites-juillet-2025.xls), publié sur [anses.fr](https://www.anses.fr/fr/content/tableau-metabolites-pesticides-edch) | Dernière mise à jour : juillet 2025 (téléchargé le 2026-10-29, SHA-256 `4a0a164b…80e23031`) | Informations publiques librement et gratuitement réutilisables (code des relations entre le public et l'administration, art. L321-1 et suivants), selon les [mentions légales](https://www.anses.fr/fr/content/mentions-l%C3%A9gales) d'anses.fr : réutilisation permise à condition de citer l'Anses, de ne pas altérer les informations ni en dénaturer le sens, et d'indiquer la date de dernière mise à jour. Le classement est en outre une donnée réglementaire factuelle (instruction DGS, arrêté du 11 janvier 2007 modifié) | **Exigée** : « Anses », date « juillet 2025 ». Citées dans l'écran (chaîne `metabolite_relevance_explanation`) ; à ajouter aux sources de l'écran À propos |

Les 23 lignes du tableau sont reprises en entier et sans modification
(colonnes 1 à 5) ; Potable ajoute dans des colonnes séparées le code Hub'Eau
apparié et la méthode d'appariement (`docs/hubeau-api.md`, « Pertinence des
métabolites »), ce qui n'altère pas l'information publiée. Le
CC BY-NC-ND des mentions légales vise les « autres contenus » du site
(textes éditoriaux, images), pas les informations publiques. Aucun appel
réseau vers anses.fr : le fichier est embarqué et mis à jour à la main.
