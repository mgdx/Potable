# Publier sur Google Play

Un canal de plus, à côté de la page des versions et de F-Droid, et le seul qui
impose ses propres règles. Ce fichier dit ce que Play demande à Potable, ce qui
y a été répondu, et ce qui se refait à chaque version. Le modèle est
`docs/play-store.md` de Roue Libre (même auteur, même compte Play).

`docs/release.md` reste la référence pour la version elle-même : numéro,
notes, contrôles, signature, tag. Ce fichier ne couvre que ce que Play ajoute.

## Un compte Google, sans service Google

Le SPEC interdit les services Google **dans l'application** : Play Services,
Firebase, Play Integrity, Play In-App Review ou Update. Rien de cela n'est
ajouté pour Play : ce qui y est envoyé est le même code que celui que F-Droid
reconstruit, et il tourne toujours sur un appareil sans services Google. Le
magasin est une porte d'entrée pour qui n'installe que depuis lui, pas une
dépendance.

## Création de l'application dans la Play Console

| Champ | Réponse |
| --- | --- |
| Nom de l'application | `Potable` |
| Nom du package | `io.github.mgdx.potable` |
| Langue par défaut | Anglais (États-Unis), `en-US`, comme l'interface et la fiche Fastlane |
| Application ou jeu | Application |
| Gratuite ou payante | Sans frais (définitif une fois publiée) |

## La signature : clé générée par Google

Play refuse un APK pour une nouvelle application : il prend un **app bundle**
(`.aab`) et signe lui-même ce qu'il distribue. Le choix a été fait le
30 octobre 2026, comme pour Roue Libre : **Google génère et détient la clé de
signature de l'application**, plutôt que de recevoir celle du projet.

La conséquence est écrite ici plutôt que découverte plus tard :

- La page des versions et F-Droid sont **une seule installation** : la recette
  F-Droid nomme le certificat dans `AllowedAPKSigningKeys` et vérifie sa
  reconstruction contre le fichier publié au lieu de signer elle-même, donc une
  mise à jour passe de l'un à l'autre.
- Play est une **seconde installation, séparée**. Qui a installé Potable depuis
  GitHub ou F-Droid ne peut pas passer à la version Play, ni l'inverse, sans
  désinstaller d'abord, ce qui perd ses communes suivies et ses réglages ;
  l'export de Paramètres → Sauvegarde permet de les retrouver. La clé de
  publication du projet ne quitte jamais le poste.

La **clé d'envoi** est une troisième clé, qui n'est pas une identité : elle
prouve seulement qui envoie le bundle, et Google la remplace sur demande si
elle est perdue. Elle va dans `~/.android-keys/potable-play-upload.jks`, alias
`potable-upload`, RSA 4096, et ses quatre lignes s'ajoutent à
`keystore.properties`, à côté de celles de la clé de publication :

```properties
uploadStoreFile=/home/leo/.android-keys/potable-play-upload.jks
uploadStorePassword=…
uploadKeyAlias=potable-upload
uploadKeyPassword=…
```

`app/build.gradle.kts` signe alors `bundleRelease` avec elle, et seulement
`bundleRelease` : `assembleRelease` garde la clé de publication, sur laquelle
repose la vérification de F-Droid. Tant que ces lignes manquent, le bundle est
signé par la clé de publication, et Play enregistrerait ce certificat comme
celui des envois : cela marche, mais la clé sert à un endroit de plus que
nécessaire. **Créer la clé d'envoi avant le premier envoi.**

## Construire le bundle

```bash
./gradlew bundleRelease     # app/build/outputs/bundle/release/app-release.aab
```

Potable n'a pas de découpage par ABI : `assembleRelease` et `bundleRelease`
peuvent tourner dans la même invocation sans se gêner.

Vérifier le certificat avant l'envoi ; attendu `CN=Potable Upload`, et
`CN=Potable` signifie que les lignes `upload` manquent :

```bash
unzip -p app/build/outputs/bundle/release/app-release.aab 'META-INF/*.RSA' \
  | openssl pkcs7 -inform DER -print_certs -noout | head -2
```

## La fiche du Play Store

| Champ | Contenu |
| --- | --- |
| Titre | `Potable` |
| Description courte | `fastlane/metadata/android/<langue>/short_description.txt`, sous les 80 caractères de Play |
| Description complète | `fastlane/metadata/android/<langue>/full_description.txt`, sous les 4000 caractères |
| Langues | `en-US` et `fr-FR`, celles de l'interface |
| Icône | `fastlane/metadata/android/en-US/images/icon.png`, 512×512 |
| Image de présentation | 1024×500, **obligatoire sur Play et encore absente** |
| Captures d'écran | `fastlane/metadata/android/<langue>/images/phoneScreenshots/`, 1080×2160 |
| Catégorie | Maison et intérieur (voir plus bas) |
| Politique de confidentialité | `https://github.com/mgdx/Potable/blob/master/docs/privacy-policy.en.md` |
| Adresse de contact | `leo@magadoux.fr` |
| Site web | `https://github.com/mgdx/Potable` |

L'adresse de contact est **affichée publiquement** sur la fiche, Play
l'exige. C'est celle des commits, déjà publique dans l'historique du dépôt.

La politique de confidentialité doit être **en ligne avant l'envoi** du
formulaire : la version anglaise n'est servie par GitHub qu'une fois `master`
poussé. La version française, `docs/privacy-policy.md`, fait foi.

Les descriptions ne renvoient à aucun autre magasin ni à aucun APK
téléchargeable, ce que Play interdit dans une fiche. Elles disent déjà que
Potable est indépendant de Hub'Eau, du ministère et des ARS, et nomment leurs
sources : c'est ce qui le distingue d'une application qui se ferait passer
pour officielle. À garder ainsi aux prochaines modifications.

**Les captures sont à la limite du format.** Play refuse une image dont le
grand côté dépasse le double du petit ; celles du Fairphone 3 font exactement
le double. Si la Console les refuse, les poser entières sur un fond uni de
1080×1920, comme le fait `tools/make_play_assets.py` de Roue Libre, plutôt que
de les rogner. L'image de présentation se compose de la même façon, aux
couleurs du logo (`docs/brand/`).

**La catégorie.** Potable ne mesure rien sur l'utilisateur et ne donne aucun
avis sanitaire (SPEC §14) : « Santé et remise en forme » et « Médecine »
appelleraient des déclarations santé qui ne le concernent pas. « Maison et
intérieur » décrit ce qu'il montre, l'eau qui arrive au robinet ; « Éducation »
serait l'alternative.

**Les notes de version** : les fichiers `changelogs/<versionCode>.txt` tiennent
déjà sous les 500 caractères de Play (limite F-Droid identique). Ils se
collent tels quels, en anglais et en français.

## Ce qui a été déclaré, et pourquoi

Répondre de la même façon à chaque fois : une réponse qui s'écarte des autres
est ce qui fait retirer une fiche.

- **Sécurité des données : aucune donnée collectée, aucune donnée partagée.**
  Play compte une donnée comme collectée quand elle quitte l'appareil, y
  compris vers un serveur tiers. Rien de ce qui concerne l'utilisateur ne part :
  la recherche se fait dans le référentiel embarqué, et la position, lue
  seulement sur « Autour de moi », sert une fois sur le téléphone puis est écartée,
  sans être enregistrée ni envoyée. Les requêtes à Hub'Eau demandent des
  données publiques par des codes publics de commune et d'unité de
  distribution, sur un geste explicite de l'utilisateur, sans identifiant ;
  elles ne portent rien de l'utilisateur au-delà de l'adresse IP que toute
  requête montre à tout serveur. Le point discutable est connu : la commune
  trouvée par « Autour de moi » part vers Hub'Eau si l'utilisateur l'ouvre. Ce n'est
  pas sa position mais une commune publique qu'il a choisi de consulter,
  comme il l'aurait tapée. Si Google conteste, la réponse prudente est
  « Position approximative » collectée, pour le fonctionnement de
  l'application, facultative, non partagée.
- **Données chiffrées en transit** : oui (HTTPS seul, trafic en clair
  interdit). **Suppression des données** : sans objet, rien n'est collecté.
- **Pas d'identifiant publicitaire.** Le manifeste fusionné ne déclare pas
  `AD_ID` ; à la question sur la publicité, la réponse est non.
- **Accès à l'application** : tous les écrans sont accessibles sans compte ni
  code.
- **Suppression de compte** : sans objet, il n'y a pas de compte.
- **Classification du contenu** : questionnaire « Utilitaires, productivité,
  communication ou autre » ; ni violence, ni sexualité, ni grossièreté, ni
  jeux d'argent, ni achats, ni contenu créé par les utilisateurs, ni partage
  de la position avec qui que ce soit.
- **Public cible** : 16 ans et plus, comme Roue Libre. Inclure les moins de
  13 ans ferait entrer la règle Familles, qui demande ce dont l'application n'a
  pas l'usage. Cela ne contredit pas la politique de confidentialité, qui dit
  que l'application convient à tous et ne collecte rien quel que soit l'âge.
- **Applications de santé** : aucune fonction de santé. Potable n'enregistre,
  ne suit, ne mesure ni ne conseille rien sur la santé de l'utilisateur ; il
  restitue des résultats publics de contrôle de l'eau.
- **Ni application d'actualités, ni financière, ni gouvernementale** :
  Potable n'est développé ni par ni pour une administration, ce que sa
  description dit. Pas concerné par la règle de sécurité des enfants, qui vise
  les réseaux sociaux et les sites de rencontre.

## La première publication, une seule fois

Un compte développeur personnel ouvert après le 13 novembre 2023 ne peut
publier en production qu'après un **test fermé d'au moins douze testeurs,
inscrits sans interruption pendant quatorze jours**, et Google vérifie qu'ils
ont réellement ouvert l'application. L'obligation vaut **pour chaque
application** : l'avoir remplie pour Roue Libre n'en dispense pas Potable.
Douze comptes Google distincts, sur de vrais appareils ; ni émulateurs, ni
seconds comptes de la même personne.

L'ordre est donc : créer la piste de test fermé, réunir les douze, laisser
passer la quinzaine, puis demander l'accès à la production. C'est la quinzaine
qui fixe la date de publication, pas le code.

## À chaque version ensuite

1. Tout `docs/release.md`, qui ne change pas.
2. `./gradlew bundleRelease`, et vérifier le certificat comme plus haut.
3. Envoyer le bundle, coller `changelogs/<versionCode>.txt` comme notes, en
   anglais et en français.
4. Refaire les captures et l'image de présentation seulement si l'interface a
   changé.
5. Déployer. Play échelonne lui-même la diffusion ; la page des versions et
   F-Droid ne l'attendent pas.
