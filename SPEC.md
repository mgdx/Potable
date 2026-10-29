# SPEC — Potable

> Application Android « qualité de l'eau du robinet ».
>
> Ce document est la **source de vérité** du projet. Toute décision technique ou
> fonctionnelle non couverte ici doit être soumise avant implémentation.
>
> **Nom retenu : Potable.** Le mot s'écrit et se comprend à l'identique en
> français et en anglais, et ne promet aucun verdict sanitaire (voir §14).
> L'`applicationId` reste en ASCII pur, de la forme `fr.<domaine>.potable`, et
> devient définitif dès la première publication sur le Play Store.

---

## 1. Objet

Application Android libre permettant à toute personne résidant en France de
consulter les résultats du contrôle sanitaire de l'eau distribuée à son
domicile, d'en suivre l'évolution dans le temps, et d'être notifiée à chaque
nouvelle analyse ou en cas de conclusion sanitaire défavorable.

L'application **restitue** les conclusions officielles des ARS. Elle
**n'établit aucun score, note ou verdict propre**. Ce point est une exigence
non négociable : voir §14.

## 2. Principes directeurs

| Principe | Conséquence |
|---|---|
| Logiciel libre | GPLv3, dépendances libres uniquement |
| Vie privée | Aucune télémétrie, aucun compte, aucun identifiant persistant |
| Légèreté | APK cible < 6 Mo, pas de Google Play Services |
| Sobriété réseau | Aucun appel réseau non provoqué par l'utilisateur, hors tâche d'alerte |
| Honnêteté des données | Aucune interprétation ajoutée aux conclusions sanitaires |

## 3. Utilisateurs et cas d'usage

- **Consultation ponctuelle** — « l'eau de ma commune est-elle conforme ? »
- **Suivi** — surveiller une ou plusieurs communes (domicile, résidence
  secondaire, famille) et être prévenu des nouvelles analyses.
- **Curiosité paramétrique** — connaître la dureté de son eau pour régler un
  lave-vaisselle, suivre les nitrates, chercher un pesticide donné.

## 4. Sources de données

### 4.1 API Hub'Eau — Qualité de l'eau potable (v1)

Base : `https://hubeau.eaufrance.fr/api/v1/qualite_eau_potable/`

Deux opérations :

**`communes_udi`** — lien entre communes et unités de distribution (UDI).
En entrant un code ou un nom de commune, renvoie toutes les UDI qui la
desservent, avec le nom de quartier le cas échéant.

- Paramètres utilisés : `code_commune`, `code_reseau`, `annee`
- Exemple : `communes_udi?code_commune=45234`

**`resultats_dis`** — prélèvements, résultats d'analyses et conclusions
sanitaires. La granularité de la réponse est le **résultat d'analyse**.

- Paramètres utilisés : `code_commune`, `code_reseau`, `code_parametre`,
  `date_min_prelevement`, `date_max_prelevement`, `fields`, `sort`, `size`,
  `page`
- Exemple : `resultats_dis?code_commune=45234&code_parametre=1340`

**Point structurant à respecter impérativement.** Les conclusions sanitaires
(`conclusion_conformite_prelevement`, `conformite_limites_bact_prelevement`,
`conformite_limites_pc_prelevement`, `conformite_references_bact_prelevement`,
`conformite_references_pc_prelevement`) sont portées **au niveau du
prélèvement**, donc répétées sur chaque ligne de résultat. Le regroupement se
fait par `code_prelevement`. Toute agrégation naïve ligne à ligne produirait
des comptages faux.

Les champs `code_commune` et `code_reseau` font la jointure entre les deux
opérations.

**Limites de l'API à intégrer dans le client :**

- Pagination non séquentielle (`page`, `size`) ; la réponse expose `count`,
  `first`, `last`, `prev`, `next`, `data`.
- Taille de page maximale variable selon l'opération ; profondeur d'accès
  plafonnée à 20 000 enregistrements (`page * size`).
- Longueur d'URL maximale : 2 083 caractères — bloquant si dépassée.
- Formats : JSON et CSV. On utilise JSON.
- Mise à jour de la source : **mensuelle**. Inutile d'interroger plus souvent.
- Aucune clé d'API, aucune authentification.

Conditions générales d'utilisation de Hub'Eau à citer dans l'écran « À propos ».

### 4.2 Référentiel des communes (embarqué)

Un fichier embarqué dans l'APK : code INSEE, nom, code postal principal,
centroïde (lat/lon), nom normalisé pour la recherche insensible aux accents.

- Généré à partir du découpage administratif communal officiel.
- Format : SQLite pré-construit en `assets/`, ou fichier compact chargé au
  premier lancement dans la base locale. L'agent tranche et documente.
- Permet la recherche par nom **et** le rattachement d'une position GPS à une
  commune, **sans aucun appel réseau**.
- Un script `tools/generate_communes.sh` (ou `.py`) régénère ce fichier ; il
  est versionné et documenté.

### 4.3 Référentiel des paramètres analysés

Les libellés de paramètres viennent de l'API (`libelle_parametre`,
`code_parametre`, `libelle_unite`). L'application maintient en plus une petite
table éditoriale des paramètres les plus consultés, pour les mettre en avant et
les expliquer en une phrase :

- `1340` — Nitrates (confirmé par la documentation Hub'Eau)
- Dureté / titre hydrotimétrique, pH, chlore résiduel libre : les codes
  doivent être **vérifiés contre l'API** avant d'être figés dans le code.
  Aucun code paramètre ne doit être écrit en dur sans vérification.

Une seconde table éditoriale, les **substances surveillées**, réunit les
substances que les usagers cherchent d'abord mais qui sont rarement analysées
à chaque prélèvement : somme des 20 PFAS, total des pesticides, TFA, chlorure
de vinyle monomère, plomb, arsenic, perchlorate (codes vérifiés le 2026-10-28,
`docs/hubeau-api.md` ; `WatchedSubstance`), et, outre-mer seulement, le
chlordécone (vérifié le 2026-10-29). Les nitrates restent dans la première
table.

## 5. Fonctionnalités de la v1

### F1 — Choix de la commune

- Recherche par nom ou code postal, hors ligne, sur le référentiel embarqué.
- Tolérance aux accents et à la casse.
- Géolocalisation **optionnelle** : un bouton « autour de moi » demande la
  permission au moment de l'usage, jamais au lancement. Refus de permission =
  l'application reste pleinement utilisable par la saisie.
- L'application fonctionne sans jamais accorder la permission de localisation.
- Champ vide : l'écran de recherche liste, sous « autour de moi », les
  dernières communes consultées (« Consultées récemment ») : les cinq
  dernières unités dont la fiche a été ouverte, la plus récente d'abord, sans
  doublon (commune et UDI), nom de la commune puis nom de l'UDI. Toucher une
  ligne rouvre la fiche ; un bouton par ligne la retire de la liste. Liste
  vide : l'explication de la recherche s'affiche à la place.

### F2 — Choix de l'UDI

Une commune peut être desservie par plusieurs UDI (quartiers différents).

- Une seule UDI : sélection automatique, transparente pour l'utilisateur.
- Plusieurs UDI : écran de choix listant les libellés d'UDI et les noms de
  quartier renvoyés par `communes_udi`, avec une explication en une ligne de ce
  qu'est une unité de distribution.
- Le choix est mémorisé.

### F3 — Fiche « mon eau »

Écran principal pour une commune/UDI suivie :

- Conclusion sanitaire du **dernier prélèvement**, en clair et en évidence.
- Date du prélèvement et âge relatif (« il y a 3 semaines »).
- Nom du distributeur et du maître d'ouvrage si disponibles.
- Conformités bactériologique et physico-chimique, limites et références
  distinguées.
- Carte « Substances surveillées » (§4.3), sous les paramètres clés : pour
  chaque substance, sa **dernière mesure connue sur l'UDI** dans la fenêtre
  d'historique réglée, même plus ancienne que le dernier prélèvement (valeur
  et limite telles que publiées, côte à côte, sans comparaison ; date et âge ;
  une phrase d'explication). Les substances lues sans aucune mesure sur la
  période sont nommées dans une note « non analysées depuis le … », qui
  rappelle que l'absence d'analyse ne dit rien de leur présence (le
  chlordécone ne l'est qu'en Guadeloupe et en Martinique) ; aucune mesurée :
  une ligne le dit, avec le début de la période. Sous le total des
  pesticides, le nombre de pesticides recherchés et quantifiés dans le même
  prélèvement (`PesticideCounts`). Chargée
  après le reste de la fiche, depuis le cache des séries (F5) ; un échec ne
  touche que la carte. Toucher une ligne ouvre l'historique de la substance.
- Accès au détail complet du prélèvement.
- Encart permanent : source des données, date de dernière mise à jour.

### F4 — Détail d'un prélèvement

Liste des paramètres analysés : libellé, valeur, unité, limite de qualité
quand elle est fournie. Regroupement par famille (bactériologie,
physico-chimie, pesticides, métaux…). Recherche textuelle dans la liste.
Sous le titre des pesticides, le nombre de pesticides et de métabolites
recherchés et quantifiés dans tout le prélèvement, sommes exclues.

Ouvert depuis l'historique d'un paramètre (F5, appui long sur une mesure),
un prélèvement, même ancien, s'affiche d'abord comme la fiche : conclusion
ARS, paramètres clés, carte « prélèvement », conformités, sans rien de
propre à l'unité ; un bouton mène à la liste ci-dessus, sans nouvelle
requête. Sur la fiche comme sur ce rapport, un code « D » ou une conclusion
qui mentionne une dérogation ajoute sous les conformités une note
factuelle sur ce qu'est une dérogation, sans verdict (§14).

### F5 — Historique et graphiques

- Sélection d'un paramètre → série temporelle sur la profondeur disponible.
- Graphique simple (ligne ou points), avec la limite réglementaire tracée
  quand elle existe.
- Fenêtre par défaut : 3 ans. Réglable.
- Bibliothèque de graphiques libre et légère, ou tracé maison en Compose
  Canvas. Pas de dépendance lourde ni propriétaire.
- Historique aussi des conclusions sanitaires : frise des prélèvements avec
  leur verdict.

### F6 — Communes suivies

- Plusieurs communes/UDI suivies simultanément.
- Aucune n'est « principale » : l'application s'ouvre toujours sur la
  recherche (voir `docs/decisions.md`, 2026-10-27).
- Chaque ligne affiche le nom de la commune, son code postal (le premier,
  suivi de « +n » s'il y en a d'autres, comme dans les résultats de la
  recherche ; tiré du référentiel embarqué, absent si la commune n'y figure
  pas), puis le nom de l'UDI.
- Recherche dans la liste par nom de commune ou d'UDI (sans casse ni
  accents) ou par début de code postal (« 450 » → Orléans), non mémorisée.
- Ajout et retrait. Pas de réordonnancement manuel : la liste est triée par
  ordre alphabétique du nom de commune, puis du nom d'UDI (collation
  française, accents et casse pris en compte comme dans un dictionnaire),
  et cet ordre vaut partout où les suivis sont listés (voir
  `docs/decisions.md`, 2026-10-27).

### F7 — Alertes

- Tâche périodique via **WorkManager**, contrainte réseau requise, périodicité
  par défaut **hebdomadaire** (les données ne bougent qu'une fois par mois).
- Pour chaque commune suivie : requête `resultats_dis` triée
  décroissante, limitée aux champs nécessaires, comparaison de la date du
  prélèvement le plus récent avec la valeur mémorisée.
- Deux types de notification, activables séparément :
  - **Nouvelle analyse disponible** (toutes conclusions confondues)
  - **Conclusion défavorable uniquement** (non-conformité, consommation
    déconseillée ou interdite)
- Notifications désactivées par défaut, activées explicitement par
  l'utilisateur. Permission `POST_NOTIFICATIONS` demandée à ce moment-là.
- Aucun service en avant-plan, aucun réveil exact, aucune exemption de
  batterie.

### F8 — Réglages et « À propos »

- Périodicité des alertes, types de notification, fenêtre d'historique.
- Thème clair / sombre / système.
- Purge du cache (en entier ou par commune ; la purge des unités ni suivies
  ni récentes est automatique, §7).
- Sources, licences, crédits Hub'Eau et ministère chargé de la santé,
  avertissement du §14, lien vers le dépôt.

## 6. Hors périmètre de la v1

À ne pas implémenter, même partiellement : carte nationale, comparaison entre
communes, classement, score de synthèse, partage social, export PDF, widget
d'écran d'accueil, données de baignade ou de cours d'eau.

## 7. Stockage local

Base **Room**. Cache des communes suivies **et consultées récemment** (écarts
consignés dans `docs/decisions.md`) — jamais de jeu national.

Tables minimales :

- `commune` (référentiel embarqué, lecture seule)
- `suivi` (code_commune, code_reseau, libellé ; colonnes `principale` et
  `position` conservées mais inutilisées depuis 2026-10-27, l'ordre étant
  alphabétique)
- `prelevement` (code_prelevement, code_reseau, date, conclusions, distributeur)
- `resultat` (code_prelevement, code_parametre, libellé, valeur, unité, limite)
- `synchro` (par unité en cache : horodatage de la dernière requête réussie ;
  pour un suivi seulement, date du dernier prélèvement vu)

Règles :

- Rétention configurable, 3 ans par défaut ; purge automatique au-delà.
- Le cache ne garde que les unités (code commune publié par Hub'Eau, code
  UDI) **suivies ou présentes dans « Consultées récemment »** (F1, cinq au
  plus). Toute autre unité perd tout son cache (derniers prélèvements,
  rapports, séries, frise, synchro) dès qu'elle sort de ces deux listes :
  retirée à la main des récentes, poussée hors des récentes par une sixième,
  ou retirée des suivis ; et au démarrage, pour les restes d'une version
  précédente. Purge par unité : une autre UDI de la même commune encore
  référencée garde le sien. Une unité qui quitte une liste en restant dans
  l'autre garde son cache.
- Purge manuelle de tout le cache, ou du cache d'une seule commune ; aucune
  ne touche aux suivis.
- Le cache sert de source d'affichage : l'application est **consultable hors
  ligne** pour toute commune suivie ou consultée récemment, avec un bandeau
  indiquant la fraîcheur.
- Rafraîchissement au premier affichage du jour, sur pull-to-refresh, et par la
  tâche d'alerte. Jamais en boucle.

## 8. Architecture

- Kotlin, **Jetpack Compose**, Material 3.
- `minSdk 26` (Android 8.0), `compileSdk 37`, JDK 17.
- Module `:core` — modèles, logique métier, parsing, regroupement par
  prélèvement, calcul des états. **Aucun import Android**, testable en JVM pure.
- Module `:data` — client Hub'Eau, Room, WorkManager.
- Module `:app` — UI Compose, navigation, notifications.
- Client HTTP libre et léger, sérialisation `kotlinx.serialization`.
- Injection de dépendances manuelle ou minimaliste ; pas de framework lourd.
- Aucune dépendance propriétaire, aucun SDK d'analyse, aucun tracker.

## 9. Interface

- **Anglais par défaut**, français fourni en traduction complète (voir §11).
  Vocabulaire courant : la notion d'unité de distribution est expliquée, jamais
  supposée connue, dans les deux langues.
- Chaque écran gère explicitement : chargement, vide, erreur réseau, données
  périmées, absence de données pour la commune.
- Mode sombre complet.
- Accessibilité : contrastes AA, libellés de contenu sur tous les éléments
  interactifs, taille de police respectée, navigation au clavier possible.
- Les couleurs ne portent jamais seules l'information : une conclusion
  défavorable est aussi signalée par un texte et une icône.

## 10. Vie privée

- Permissions : `INTERNET`, `POST_NOTIFICATIONS` (à la demande),
  `ACCESS_COARSE_LOCATION` (à la demande, optionnelle).
- La position n'est **jamais** transmise, ni stockée : elle sert uniquement à
  résoudre une commune en local, puis est écartée.
- Un seul domaine contacté : `hubeau.eaufrance.fr`. HTTPS obligatoire, trafic
  en clair interdit dans la configuration réseau.
- Aucune donnée personnelle. Les communes suivies restent sur l'appareil.

## 11. Internationalisation

- Aucune chaîne codée en dur, y compris dans les logs destinés à l'utilisateur.
- **Locale par défaut : anglais.** `res/values/strings.xml` est en anglais.
  Aucune chaîne d'une autre langue ne doit subsister dans `res/values/`.

### 11.1 Langues

| Langue | Dossier | Fastlane | Sens |
|---|---|---|---|
| Anglais (défaut) | `values/` | `en-US` | LTR |
| Français | `values-fr/` | `fr-FR` | LTR |

Deux langues seulement, par choix de l'auteur (`docs/decisions.md`,
2026-10-27).

- La traduction française couvre **100 %** des chaînes. Une chaîne ajoutée en
  anglais sans sa traduction française bloque la fusion.
- `android:localeConfig` déclare ces deux langues, pour que l'utilisateur
  puisse choisir la langue de l'appli indépendamment de celle du système
  (Android 13+). En dessous, l'appli suit la langue du système, et retombe
  sur l'anglais pour toute autre langue. `androidResources.localeFilters`
  écarte de l'APK les traductions des bibliothèques dans d'autres langues.
- Un glossaire de projet (`docs/glossary.md`) fixe la traduction des termes
  récurrents : unité de distribution, prélèvement, conformité, limite de
  qualité, référence de qualité, distributeur.
- L'état de relecture de la traduction est tenu dans `docs/translations.md`.

### 11.2 Mise en page RTL

Aucune langue livrée ne s'écrit de droite à gauche, mais la mise en page
reste prête pour le RTL : c'est la bonne pratique Android, et elle ne coûte
rien tant qu'elle est imposée dès l'écriture.

- `android:supportsRtl="true"` dans le manifeste.
- Exclusivement `start`/`end`, jamais `left`/`right`, dans les modificateurs
  Compose, les alignements et les marges. Une vérification lint dédiée
  l'impose.
- Les icônes directionnelles (retour, chevrons, flèches de navigation) sont
  mirroirées en RTL. Les icônes non directionnelles (goutte, alerte,
  réglages) ne le sont pas.
- **Graphiques (F5)** : l'axe du temps reste orienté de gauche à droite dans
  toutes les langues, convention usuelle des graphiques scientifiques,
  y compris dans les langues RTL. Les libellés, la légende et les infobulles
  suivent en revanche la mise en page RTL.
- Chiffres : formatés via la locale (`NumberFormat`, `DateTimeFormatter`), sans
  forcer de système de chiffres.
- Les données françaises de l'API (noms de communes, libellés de paramètres)
  insérées dans une phrase RTL sont isolées avec `BidiFormatter` pour
  éviter les inversions de ponctuation et d'unités.
- Les valeurs avec unité (`12,5 mg/L`) sont toujours rendues comme un bloc
  LTR isolé.
- Les libellés métier renvoyés par l'API (`libelle_parametre`,
  `libelle_unite`, conclusions sanitaires) sont en français et ne sont **pas**
  traduits : ce sont des données, pas de l'interface. En locale anglaise, ils
  sont affichés tels quels, accompagnés d'une mention indiquant que les
  libellés officiels sont en français.
- La table éditoriale des paramètres mis en avant (§4.3) est, elle, traduite.
- Pluriels et paramètres de format corrects, pas de concaténation de phrases.
- Dates et nombres formatés selon la locale.
- Les traductions ultérieures passent par de simples `values-xx/`, sans
  modification de code.

## 12. Qualité

- Tests unitaires sur `:core` : regroupement par `code_prelevement`, détection
  d'une nouvelle analyse, sélection d'UDI, recherche de commune.
- Tests sur des réponses API figées en `resources/`, dont un cas à plusieurs
  UDI et un cas sans donnée.
- `lint`, `ktlintCheck` et les tests passent **sans aucun avertissement**.
- Build reproductible, pas de dépendance à un dépôt non public.

## 13. Distribution

- Licence **GPLv3**.
- Dépôt public avec `README.md`, ce `SPEC.md`, `CONTRIBUTING.md`, `docs/`.
- Métadonnées **Fastlane** dans les deux langues du §11.1 (`en-US` par
  défaut), prêtes pour F-Droid. Captures d'écran au minimum en anglais et
  en français.
- APK signé, versions publiées en release ; conformité aux exigences
  d'inclusion de F-Droid vérifiée avant soumission.

## 14. Avertissements et responsabilité

À afficher dans « À propos » et au premier lancement :

- Les résultats proviennent du contrôle sanitaire réglementaire des ARS,
  diffusés par le ministère chargé de la santé via Hub'Eau.
- Les données sont mises à jour mensuellement : un résultat affiché peut avoir
  plusieurs semaines.
- Les analyses portent sur l'unité de distribution, pas sur le robinet de
  l'utilisateur : l'état des canalisations privées n'est pas couvert.
- L'application ne délivre **aucun avis sanitaire**. En cas de doute, se
  référer à l'ARS ou au distributeur d'eau.
- Aucun score, aucune note, aucune classification inventée par l'application.

## 15. Cas limites à traiter explicitement

- Commune sans UDI référencée, ou sans prélèvement récent.
- Commune desservie par plusieurs UDI, y compris avec changement d'UDI d'une
  année sur l'autre.
- Prélèvement sans conclusion sanitaire renseignée.
- Résultat non numérique (mention textuelle, seuil de détection).
- API indisponible ou lente : affichage du cache, message explicite, aucun
  plantage, aucune boucle de retry agressive.
- Réponse dépassant la profondeur de pagination : requête bornée en dates
  plutôt que paginée à l'infini.
- Communes fusionnées ou codes INSEE obsolètes.

## 16. Critères d'acceptation de la v1

1. Depuis une installation neuve et sans accorder la moindre permission, on
   peut rechercher une commune et lire la conclusion sanitaire du dernier
   prélèvement.
2. Une commune à plusieurs UDI propose un choix clair et le mémorise.
3. L'historique d'un paramètre s'affiche sous forme de graphique lisible avec
   la limite réglementaire.
4. Après activation, une notification est émise lorsqu'un prélèvement plus
   récent que celui déjà vu apparaît.
5. En mode avion, l'application affiche les données en cache et signale leur
   fraîcheur.
6. Aucune requête réseau vers un autre domaine que `hubeau.eaufrance.fr`,
   vérifiable par capture.
7. `lint`, `ktlintCheck` et les tests passent sans avertissement.
8. Pour chacune des deux langues du §11.1, l'interface est intégralement
   traduite, sans aucune chaîne d'une autre langue hors données de l'API.
9. En mise en page RTL (option développeur « Forcer la mise en page de
   droite à gauche »), tous les écrans sont mirroirés correctement, aucun
   texte n'est tronqué ou chevauché, et les valeurs avec unité restent
   lisibles.
10. Un script de vérification (`tools/check_translations`) échoue si une
    chaîne manque dans une langue ou si un paramètre de format diffère entre
    l'anglais et une traduction.

## 17. Jalons

1. Socle : modules, référentiel des communes, client Hub'Eau, tests `:core`.
2. Consultation : recherche, choix d'UDI, fiche, détail du prélèvement.
3. Persistance : Room, suivis, cache, hors ligne.
4. Historique et graphiques.
5. Alertes WorkManager.
6. Traductions : glossaire, français, passe RTL complète, script de
   vérification.
7. Finitions : accessibilité, mode sombre, Fastlane multilingue, release.

Le support RTL n'attend pas le jalon 6 : `start`/`end` et `supportsRtl` sont
exigés dès le jalon 1, sous peine de devoir reprendre tous les écrans.

## 18. Identité visuelle

### 18.1 Logo

Logo retenu : **le robinet et la goutte-courbe**. Un robinet stylisé dont
tombe une goutte, et dans la goutte une courbe brisée qui évoque le suivi des
analyses dans le temps.

- Fichier source : `docs/brand/potable_logo.svg`, grille de 108 × 108 unités
  (celle des icônes adaptatives Android). C'est la référence pour toutes les
  déclinaisons.
- Tout le motif tient dans la zone de sécurité de 66 unités centrée, pour
  survivre à tous les masques des lanceurs (rond, carré, goutte).
- Aucun texte dans l'icône.
- Interdits : coche, bouclier, pouce levé ou tout symbole qui affirmerait que
  l'eau est saine (voir §14).

### 18.2 Couleurs

| Rôle | Valeur |
|---|---|
| Fond, couleur principale (bleu, approximation en aplat) | `#2350B5` |
| Motif | `#FFFFFF` |
| Accent clair (états, graphiques) | `#9CC8FF` |

Le contraste blanc sur `#2350B5` est d'environ 7:1. La couleur principale sert
de fond (`background`, `surface`) au thème clair, dont le texte est blanc ; le
thème sombre est un bleu nuit de la même teinte (`#0B1838`). L'accent clair est
le `secondary` des deux thèmes (courbes, indicateurs). Les contrastes sont
vérifiés par `ThemeContrastTest`.

### 18.3 Déclinaisons à produire

- **Icône adaptative** (`mipmap-anydpi-v26/ic_launcher.xml`) :
  - calque de fond : aplat `#2350B5`
  - calque de premier plan : robinet et goutte en blanc, courbe en `#2350B5`,
    en `VectorDrawable` issu du SVG source.
- **Icône monochrome** (icônes thématiques, Android 13+) : silhouette du
  robinet et de la goutte, la courbe étant **évidée** dans la goutte (contour
  du trait converti en forme puis soustrait, règle `evenOdd`), puisque ce
  calque n'admet qu'une seule couleur.
- **Icône ronde** et icônes `mipmap` matricielles pour Android 8.0 à 7.1 sur
  les appareils qui ne gèrent pas les icônes adaptatives.
- **Fastlane** : `icon.png` 512 × 512 dans chaque langue, ou un seul fichier
  partagé si l'outil le permet.
- **Version large** pour le README : logo et nom « Potable » côte à côte.

### 18.4 Lisibilité

La courbe est le détail le plus fin du logo. À 24 px, elle ne doit pas se
fondre dans la goutte : l'épaisseur du trait (3,5 unités) ne descend pas en
dessous de ce seuil, et un contrôle visuel à 48 et 24 px est fait avant toute
release.
