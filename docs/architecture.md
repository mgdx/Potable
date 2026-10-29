# Architecture

Trois modules Gradle, dépendants dans un seul sens. La frontière qui compte —
aucune API Android dans la logique métier — est tenue par le compilateur, pas
par la discipline : `:core` est un module Kotlin JVM, qui ne voit tout
simplement pas le SDK Android.

```
┌──────────────────────────────────────────────────────────────────┐
│  :app                                               Android      │
│                                                                  │
│  ui/            une activité, Compose + Material 3,              │
│                 Navigation Compose, ViewModels                   │
│  alert/         AlertWorker (WorkManager), AlertScheduler,       │
│                 AlertNotifier, intents des notifications         │
│  location/      Locator : LocationManager du système             │
│  AppContainer   injection manuelle, ni Hilt ni Koin              │
└───────────────────────────────┬──────────────────────────────────┘
                                │ dépend de
┌───────────────────────────────▼──────────────────────────────────┐
│  :data                                    bibliothèque Android   │
│                                                                  │
│  HubEauClient          OkHttp ───────────► hubeau.eaufrance.fr   │
│  RemoteWaterRepository / CachingWaterRepository                  │
│  RemoteHistoryRepository / CachingHistoryRepository              │
│  alert/                AlertChecker : requêtes légères, codes vus│
│  follow/               communes suivies                          │
│  local/                Room, base potable.db                     │
│  preferences/          DataStore                                 │
│  AssetCommuneRepository  assets/communes.tsv                     │
└───────────────────────────────┬──────────────────────────────────┘
                                │ dépend de (en api)
┌───────────────────────────────▼──────────────────────────────────┐
│  :core                                         Kotlin pur        │
│                                                                  │
│  hubeau/        lecture du JSON (kotlinx.serialization)          │
│  model/         Commune, Udi, Sample, Compliance, séries         │
│  Samples        regroupement des lignes par code_prelevement     │
│  UdiSelection   une UDI, plusieurs, ou aucune                    │
│  CommuneIndex   recherche hors ligne, commune la plus proche     │
│  Series, Thresholds, ParameterFamilies, FeaturedParameters       │
│  NewAnalysis, Unfavourable                                       │
└──────────────────────────────────────────────────────────────────┘
```

Package racine : `io.github.mgdx.potable` (`.core`, `.data`, et l’application
directement sous la racine).

## `:core` — ce que Potable sait de l’eau

Tout ce qui se raisonne sans appareil : lire une réponse de Hub’Eau, en faire
des prélèvements, choisir une unité de distribution, trouver une commune,
décider qu’une analyse est nouvelle ou qu’une conclusion est défavorable. Tout
y est testé sur la JVM, en grande partie sur des réponses réelles de l’API
figées dans `core/src/test/resources/hubeau/` — dont une commune à plusieurs
unités de distribution et une commune sans donnée (SPEC §12).

- **Le regroupement par prélèvement** (`Samples.group`) est le point
  structurant du SPEC §4.1. L’API renvoie une ligne par *résultat d’analyse*,
  et répète sur chacune les conclusions du *prélèvement* : toute agrégation
  ligne à ligne compterait faux. `:core` regroupe par `code_prelevement` avant
  toute autre chose.
- **Les conformités** (`Compliance`) ne connaissent que les codes documentés
  par Hub’Eau — `C`, `D` (dérogation), `N`, `S` ; tout autre code est restitué
  comme inconnu, jamais interprété.
- **Les seuils** (`Thresholds`) lisent les limites et références publiées
  (`<=50 mg/L`, `>=6,5 et <=9 unité pH`) ; ce qu’ils ne savent pas lire n’est
  pas tracé, plutôt que deviné.
- **Les familles de paramètres** (`ParameterFamilies`) sont déduites de
  l’unité et du libellé : l’API n’en publie aucune. C’est un classement de
  présentation, sans jugement.
- **Une conclusion défavorable** (`Unfavourable`) se lit dans les seuls codes
  et textes publiés par l’ARS, jamais dans les mesures : appliquer soi-même un
  seuil aux valeurs serait un verdict propre (SPEC §14). La règle et le corpus
  qui l’a fixée sont dans [`decisions.md`](decisions.md) et
  [`hubeau-api.md`](hubeau-api.md#alertes).

## Les flux de données

### Le référentiel des communes, embarqué

`data/src/main/assets/communes.tsv` contient toutes les communes, déléguées,
arrondissements et anciens codes, avec leur centroïde et le code sous lequel
Hub’Eau les publie. Il est produit par
[`tools/generate_communes.py`](../tools/README.md) à partir de geo.api.gouv.fr
et du COG de l’INSEE, et versionné tel quel.

`AssetCommuneRepository` (`:data`) le lit une fois, à la demande ;
`CommuneIndex` (`:core`) l’indexe en mémoire. La recherche par nom ou code
postal et « Autour de moi » (commune dont le centroïde est le plus proche, à
moins de 20 km) ne quittent jamais le téléphone. Le choix d’un fichier texte
plutôt que d’une table Room pré-remplie est consigné dans
[`decisions.md`](decisions.md).

### Le client Hub’Eau

`HubEauClient` est le seul objet qui parle au réseau, et il ne connaît qu’une
adresse : `https://hubeau.eaufrance.fr/api/v1/qualite_eau_potable/`. Deux
opérations, `communes_udi` et `resultats_dis`, en GET, lues par
kotlinx.serialization ; pas de Retrofit (`decisions.md`). Le client limite les
champs demandés (`fields`), borne les requêtes en dates plutôt que de paginer
à l’infini (SPEC §15), et suit les pages avec une borne de sécurité. Les échecs
deviennent des `DataError` explicites — réseau, délai dépassé alors que Hub’Eau
était joint, réponse illisible, erreur du serveur — que l’interface traduit
en messages. Les comportements observés de l’API (lenteurs, tris, filtres de
date, pannes) sont consignés dans [`hubeau-api.md`](hubeau-api.md).

### Le cache Room, des communes consultées

La base `potable.db` ne contient jamais de jeu national : seulement les
**communes suivies** et les données publiques des unités de distribution
**consultées**, suivies ou non (SPEC §7). Ses tables :

| Table | Contenu |
|---|---|
| `suivi` | commune, unité de distribution, libellés ; triés par nom à la lecture (colonnes `position` et `principale` inutilisées, gardées pour éviter une migration) |
| `prelevement`, `resultat` | les derniers prélèvements de chaque unité consultée (tous ceux du dernier jour, `dernier = 1`) et les prélèvements plus anciens ouverts depuis l’historique (`dernier = 0`), avec leurs résultats |
| `synchro` | par unité : dernière requête réussie ; pour un suivi, dernier prélèvement vu par les alertes et date depuis laquelle ses codes sont gardés (`codes_vus_depuis`, schéma v5) |
| `prelevement_vu` | pour un suivi : codes des prélèvements vus par les alertes sur la fenêtre de 92 jours (schéma v5) ; gardés par les purges comme la date vue, effacés au retrait du suivi |
| `serie`, `serie_point` | séries de paramètres consultées (historique) |
| `historique_prelevement`, `historique_synchro` | frise des conclusions |

Le schéma est exporté (`data/schemas/`) et ses migrations sont testées
(`MigrationTest`).

`CachingWaterRepository` enveloppe `RemoteWaterRepository` : pour toute
unité consultée, suivie ou non, le cache est la source d’affichage, rafraîchie
au premier affichage du jour, sur un tirer-pour-actualiser, et par la tâche
d’alerte pour les suivis — jamais en boucle. Un échec réseau laisse le cache
intact, et l’écran l’affiche avec la date de ses données. `CachingHistoryRepository`
fait de même pour les séries et la frise. Le cache ne garde que les unités
suivies ou récentes : `CacheJanitor`, lancé par `PotableApplication`, observe
les deux listes et appelle `pruneUnreferenced` au démarrage puis à chaque
changement de leur union ; une unité qui en sort perd tout son cache. Retirer
un suivi oublie la date et les codes vus par les alertes, et le cache de l’unité si elle
n’est pas récente. La rétention (3 ans par défaut) est
appliquée après chaque rafraîchissement réussi, en gardant toujours le dernier
prélèvement de chaque unité. Les réglages listent les communes en cache
(`cachedCommunes`) et vident tout le cache (`purgeCache`) ou celui d’une
commune (`purgeCommune`).

Les réglages — thème, fenêtres en années, rétention, alertes, unité de
distribution choisie pour chaque commune, acceptation de l’avertissement —
sont dans DataStore (`AppPreferences`).

### L’historique

`CachingHistoryRepository` sert deux choses : la **série d’un paramètre** sur
la fenêtre choisie (1, 3, 5 ou 10 ans) et la **frise des conclusions**.
`Series` (`:core`) en tire un point par prélèvement, la mesure de laboratoire
étant préférée à la mesure de terrain, et garde la limite et la référence
publiées pour le tracé. La frise ne demande que les lignes de trois paramètres
présents dans presque tous les prélèvements (pH, E. coli, plomb), et leurs
seuls champs d’en-tête : quelques lignes par prélèvement au lieu de plusieurs
dizaines. Ces deux choix, et la taille de page qui garde les réponses sous le
délai de lecture, sont justifiés dans [`decisions.md`](decisions.md) et
[`hubeau-api.md`](hubeau-api.md#historique).

Le graphique est tracé à la main en Compose `Canvas`, sans bibliothèque de
graphiques ; l’axe du temps reste orienté de gauche à droite, même en mise en page RTL
(SPEC §11.2).

### Les alertes

Désactivées par défaut. Quand l’utilisateur en active une :

1. `AlertScheduler` (`:app`), qui observe les préférences, pose une **ligne de
   base** — la date du dernier prélèvement de chaque unité suivie et les
   codes des prélèvements de sa fenêtre, pour ne pas annoncer comme nouvelle
   une analyse déjà connue — puis planifie une tâche
   périodique unique (`enqueueUniquePeriodicWork`), à la période choisie (1, 2
   ou 4 semaines), avec une contrainte réseau.
2. À chaque exécution, `AlertWorker` délègue à `AlertRun`, qui ne contacte
   Hub’Eau que si une alerte est active et que les notifications peuvent
   s’afficher.
3. `AlertChecker` (`:data`) lit, par unité suivie, les **en-têtes des
   prélèvements de la fenêtre** (des 92 jours précédant le dernier vu à
   aujourd’hui ; lignes pH, plomb, E. coli, champs d’en-tête) et la **ligne la
   plus récente** (`size=1`). Est nouveau tout prélèvement dont le code n’est
   pas parmi ceux déjà vus (`NewAnalysis.newSamples`, `:core`) ; une unité
   suivie avec la 0.3.1 ou avant, sans codes, compare encore les dates une fois. Il
   enregistre tous les codes de la fenêtre, rafraîchit le cache de l’unité
   s’il y a du nouveau, et émet un événement par unité : le plus récent des
   nouveaux prélèvements défavorables, sinon le plus récent des nouveaux.
4. `AlertNotifier` affiche une notification par commune, sur l’un des deux
   canaux (`new_analysis`, `unfavourable`) ; le texte d’une conclusion
   défavorable est la conclusion publiée, telle quelle. La toucher ouvre la
   fiche de l’unité.

Ni service au premier plan, ni alarme exacte, ni exemption d’optimisation de
batterie (SPEC §5 F7) : le service au premier plan que WorkManager déclare par
défaut est retiré du manifeste fusionné.

## L’interface

Une seule activité, `MainActivity`, qui héberge `PotableNavHost`, dont les
destinations sont des routes typées (`ui/navigation/Destinations.kt`). Écrans :
recherche, choix de l’unité de distribution, fiche « mon eau », détail d’un
prélèvement, historique d’un paramètre, frise des conclusions, communes
suivies, réglages, à propos. L’avertissement du premier lancement (SPEC §14)
n’est pas une destination de navigation mais un composable qui enveloppe le
graphe : le retour arrière depuis l’avertissement quitte l’application au lieu
d’atteindre un écran non accepté.

Chaque écran qui charge des données a son ViewModel, qui expose un état observé et distingue
explicitement chargement, vide, erreur réseau, données périmées et absence de
données (SPEC §9). Au démarrage, l’application s’ouvre toujours sur la recherche.

Règles tenues partout, et vérifiées par la build :

- **Aucune chaîne codée en dur** : tout est dans `res/values/strings.xml`, en
  anglais par défaut, et sa traduction française (seule autre langue).
- **`start`/`end` uniquement** : la tâche `checkRtl` (racine du projet,
  branchée sur `check`) refuse `left`, `right`, `absolutePadding`,
  `AbsoluteAlignment` dans le Kotlin et le XML.
- **Les données françaises dans une phrase RTL** sont isolées
  (`BidiFormatter`), et une valeur avec son unité est toujours un bloc LTR.
- **Les libellés de l’API ne sont jamais traduits** : ce sont des données.

## Choix structurants

Chacun est argumenté, avec ce qu’il écarte, dans
[`decisions.md`](decisions.md). Les principaux :

| Choix | Ce qu’il permet |
|---|---|
| `:core` en Kotlin JVM pur | Toute la logique testée sans émulateur |
| Injection manuelle (`AppContainer`) | Un seul lieu d’assemblage, aucun framework |
| Référentiel en TSV embarqué, indexé en mémoire | Recherche et géolocalisation hors ligne, fichier lisible et diffable |
| OkHttp seul, sans Retrofit | Deux GET, une dépendance de moins |
| Cache Room des communes consultées | Consultation hors ligne sans stocker de jeu national |
| Dates de prélèvement lues en UTC | La date affichée est la date publiée, sans décalage d’un jour |
| Requête d’alerte d’une ligne | Quelques kilo-octets par commune et par semaine |
| Graphique en `Canvas` | Aucune bibliothèque de graphiques |
| Tests Room et WorkManager sous Robolectric | `./gradlew check` couvre la base et la planification sans appareil |
| Tests d'écran Compose sous Robolectric | Les écrans principaux testés sans émulateur |
| Export JSON des suivis (`data/backup/`) | Changer de téléphone sans sauvegarde système |
| Toolchain JDK 21 | Celle de l’image de build de F-Droid, qui refuse de télécharger un JDK |
| Avertissements traités comme erreurs | Le zéro avertissement du SPEC §12 tenu par la build |
