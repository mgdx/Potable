# Hub'Eau `qualite_eau_potable` — comportement observé

Sondage du 2026-10-25, sur
`https://hubeau.eaufrance.fr/api/v1/qualite_eau_potable/`, commune témoin
Orléans (45234 : 11 lignes `communes_udi`, 79 087 lignes `resultats_dis`).
Ce fichier décrit ce que l'API **fait**, pas ce que sa documentation annonce ;
à revérifier si un comportement change.

## Statuts HTTP

| Cas | Statut |
|---|---|
| Toutes les lignes tiennent dans la page | `200` |
| Il reste des lignes au-delà de la page (y compris sur la dernière page) | `206 Partial Content`, corps JSON normal |
| Page au-delà de la dernière | `200`, `data` vide, **`next` toujours renseigné** |
| Paramètre invalide (`size=0`, `sort=xx`, date illisible) | `400`, corps `{"code":"InvalidRequest","field_errors":[…]}` |
| Requête trop lente côté serveur | `503`, page HTML d'Apache, après **≈ 120 s** |

Conséquences pour le client : `206` est un succès ; on arrête de paginer sur
`next == null` **ou** sur une page vide ; un délai de lecture de 60 s coupe
avant le `503`.

## Taille de page

| Opération | Défaut | Maximum honoré | Au-delà |
|---|---|---|---|
| `communes_udi` | 5 000 | 20 000 (`size=20000` → 20 000 lignes) | `size=20001` est **silencieusement ramené à 5 000** (pas d'erreur) |
| `resultats_dis` | 5 000 | 20 000 | idem, ramené à 5 000 |

`size=0` → `400`.

## Profondeur `page * size`

Le plafond de 20 000 annoncé par le SPEC **n'est pas appliqué** :
`communes_udi?annee=2026&size=20000&page=3` (lignes 40 001 à 49 475) et
`resultats_dis?code_commune=45234&size=20000&page=4` (lignes 60 001 à 79 087)
répondent normalement. Le client ne s'appuie pas pour autant sur cette
tolérance : il ne lit que quelques pages.

## Tri

`sort` n'accepte que `asc` ou `desc` (`sort=date_prelevement` → `400`). Il trie
sur `date_prelevement` complet (date et heure). Sans `sort`, l'ordre observé
est décroissant. Les lignes d'un même prélèvement sont contiguës. Deux
prélèvements peuvent tomber le même jour (Orléans, 2026-08-26 : 12:50 et
10:04, 27 lignes chacun).

## Filtre `code_reseau` de `resultats_dis`

**Il fonctionne**, bien que les lignes ne portent plus `code_reseau` mais une
liste `reseaux` : une ligne est retenue si **l'un** des réseaux de sa liste
correspond. Orléans : 79 087 lignes sans filtre, 78 218 avec `045000474`,
35 052 avec `045001825` (réseau voisin présent dans les mêmes listes), 0 avec un
code inconnu. Il fonctionne aussi sans `code_commune`.

**Piège :** si `fields` contient `code_reseau`, l'API le renvoie comme un
**tableau** de chaînes (`["045000474","045001825",…]`), pas comme une chaîne.
`ResultRow.codeReseau` (`String?`) ne sait pas le lire : le client ne demande
donc jamais `code_reseau` dans `fields`.

## `fields`

Liste séparée par des virgules (`%2C` accepté). Un nom inconnu ne provoque pas
d'erreur : la réponse contient alors **tous** les champs.

## Filtres de date `date_min_prelevement` / `date_max_prelevement`

Ils fonctionnent, mais avec des pièges qui expliquent les « 0 résultat » :

| Valeur envoyée (Orléans) | Lignes | Lecture |
|---|---|---|
| `date_min=2026-08-26` | 54 | les deux prélèvements du 26 |
| `date_min=2026-08-26&date_max=2026-08-26` | **0** | `date_max` est exclusif, à minuit |
| `date_max=2026-08-26` | 79 033 | exclut les 54 lignes du 26 |
| `date_min=2026-08-26 12:50:00` (espace) | 54 | l'heure est **ignorée** (lu comme `2026-08-26`) |
| `date_max=2026-08-26 12:50:00` (espace) | 79 033 | idem, heure ignorée |
| `date_min=2026-08-26T00:00:00` | 54 | |
| `date_min=2026-08-26T12:50:00Z` | **0** | alors que `date_prelevement` vaut `2026-08-26T12:50:00Z` |
| `date_max=2026-08-26T12:49:00Z` | 79 087 | inclut le prélèvement « 12:50Z » |
| `date_min=…T10:50:00Z&date_max=…T12:50:00Z` | 27 | seul le prélèvement « 12:50Z » |
| `date_min=26/08/2026` | `400` | format refusé |

Toutes ces observations s'expliquent si les heures de `date_prelevement` sont
en réalité l'heure **légale française** suffixée à tort de `Z`, alors que les
bornes suffixées `Z` sont, elles, lues en UTC (décalage de 2 h en été, 1 h en
hiver). C'est une hypothèse cohérente avec tous les essais, pas une certitude.

Règles retenues :

- borner **au jour** (`yyyy-MM-dd`) : `date_min` inclusif, `date_max`
  exclusif, donc `date_max` = lendemain du dernier jour voulu ;
- ne jamais filtrer à l'heure près ;
- `latestSample` n'utilise **aucun** filtre de date : tri décroissant et
  pagination suffisent.
- à signaler à `:core` : le `Z` de `date_prelevement` n'est probablement pas
  de l'UTC ; un `Instant.parse` brut décale l'heure affichée d'1 à 2 h.

## Lenteur

Requêtes ciblées (une commune, `size` ≤ 500) : 0,2 à 1 s. `size=20000` avec
`fields` réduit : 5 à 8 s. Un filtre de date sur une commune entière
(`date_min=2026-01-01`, sans `date_max`) a donné un `503` au bout de 120 s, alors
que la même fenêtre bornée des deux côtés répondait en 0,2 s. Le client fixe
connexion 15 s, lecture 60 s, et ne réessaie jamais.

## Longueur d'URL

Le client refuse lui-même (`IllegalArgumentException`) toute URL de plus de
2 083 caractères (SPEC §4.1), avant tout envoi ; non testé côté serveur.

## Requêtes du client (`:data`)

- `communes_udi?code_commune=…&page=N&size=5000`, pages suivies jusqu'à
  `next == null` ou page vide (borne de sécurité : 20 pages).
- Derniers prélèvements complets (tous ceux du jour du plus récent) :
  `resultats_dis?code_commune=…[&code_reseau=…]&fields=…&sort=desc&page=N&size=500`,
  pages suivies tant que la dernière ligne lue a été prélevée le même jour
  publié (date lue en UTC) que la toute première (borne : 10 pages, soit
  5 000 lignes).

- Série d'un paramètre (`RemoteHistoryRepository`, détail dans « Historique ») :
  `resultats_dis?code_commune=…&code_reseau=…&code_parametre=<codes>&date_min_prelevement=<début>&date_max_prelevement=<lendemain à Paris>&fields=…&sort=desc&page=N&size=5000`,
  pages suivies tant qu'elles sont pleines (borne : 20 pages, 100 000 lignes).
- Frise des conclusions : même requête sur `code_parametre=1302,1382,1449` et
  les seuls champs d'en-tête du prélèvement.

## Paramètres mis en avant

Vérification du 2026-10-25 (SPEC §4.3 : aucun code écrit en dur sans
vérification). Requête, pour Orléans (45234), Paris (75056), Marseille
(13055), Bordeaux (33063), Lyon (69123) et Toulouse (31555) :

```
resultats_dis?code_commune=…&size=20000&sort=desc
  &fields=code_parametre,libelle_parametre,code_parametre_se,code_type_parametre,libelle_unite
```

soit les 20 000 lignes les plus récentes de chaque commune (836 paramètres
distincts au total).

| Paramètre | `code_parametre` | `code_parametre_se` | `libelle_parametre` | Unité | Présent à |
|---|---|---|---|---|---|
| Nitrates | `1340` | NO3 | Nitrates (en NO3) | mg/L | les six |
| Dureté | `7972` | TH | Titre hydrotimétrique | °f | les six |
| Dureté | `1345` | TH | Titre hydrotimétrique | °f | toutes sauf Paris |
| pH | `1302` | PH | pH | unité pH | les six |
| Chlore libre | `1398` | CL2LIB | Chlore libre | mg(Cl2)/L | les six |

Un même paramètre peut donc être publié sous deux codes (dureté : `7972` à
Orléans, `1345` ailleurs, parfois les deux dans une même commune) ; le code
les accepte tous (`FeaturedParameter.codes`). Voisins volontairement exclus :
« Chlore total » (`1399`), « pH d'équilibre à la t° échantillon » (`6488`),
« Titre alcalimétrique complet » (`1347`), « Nitrates/50 + Nitrites/3 »
(`6374`).

## Familles de paramètres

L'API ne publie **aucune** famille (bactériologie, pesticides…) :
`code_type_parametre` ne distingue que les résultats numériques (`N`) des
qualitatifs (`O`, unité `SANS OBJET` : aspect, couleur, odeur, saveur), et
`code_parametre_se` n'est qu'une abréviation du libellé. Le regroupement de
l'écran Détail (SPEC F4) se fonde donc sur l'unité et le libellé ; la règle et
ses limites sont dans le KDoc de `ParameterFamilies`. Sur les 836 paramètres
du sondage : 627 pesticides et métabolites, 110 composés organiques, 37
physico-chimie, 17 désinfection, 16 métaux, 13 microbiologie, 13
radioactivité, 3 autres.

## Autres observations

- Toulouse (31555) publie, dans **chaque** prélèvement de la première page
  (`resultats_dis?code_commune=31555&sort=desc&size=500`, 2026-10-25), une
  ligne « Prélèvement sous acréditation » (`code_parametre_se` `PLVACR`, type
  `O`, valeur `OUI`) dont `code_parametre` est **`null`**. Avec un
  `code_parametre` obligatoire, toute la page échouait à la lecture et la
  fiche de Toulouse affichait une erreur. `ResultRow.codeParametre` est
  désormais nullable : une telle ligne compte pour le prélèvement (date,
  conclusion, distributeur) mais n'est pas un résultat analysé
  (`Samples.group`). Fixture : `resultats_dis_31555_sans_code_parametre.json`.
- `resultat_alphanumerique` peut valoir `N.M.` (non mesuré, avec
  `resultat_numerique` nul) ; un résultat qualitatif (« Aspect normal ») porte
  `resultat_numerique` à `0.0`. L'appli affiche donc le texte publié, et le
  nombre seulement quand il n'y a aucun texte.

## Seuils : `limite_qualite_parametre` et `reference_qualite_parametre`

Relevé du 2026-10-25 : 20 000 lignes les plus récentes (ou toutes, pour une
petite commune) de Orléans (45234), Paris (75056), Marseille (13055),
Toulouse (31555), Saint-Denis de La Réunion (97411), Strasbourg (67482),
L'Abergement-Clémenciat (01001) et Ajaccio (2A004), champs
`limite_qualite_parametre,reference_qualite_parametre,libelle_parametre`.
Tous les textes renseignés ont la même forme : une ou deux comparaisons
reliées par `et`, puis l'unité. Aucun texte sans nombre n'a été rencontré
(les paramètres sans seuil portent `null`). Textes distincts relevés, tous
repris dans `ThresholdsTest` :

| Forme | Textes |
|---|---|
| Borne haute, virgule décimale | `<=0,03 µg/L`, `<=0,1 mg/L`, `<=0,1 µg/L`, `<=0,1 mSv/a`, `<=0,25 mg/L`, `<=0,5 mg/L`, `<=0,5 µg/L`, `<=0,5 NFU`, `<=0,7 mg/L`, `<=1,5 mg/L`, `<=2,5 µg/L` |
| Borne haute, **point** décimal (même paramètre, autre commune) | `<=0.01 µg/L`, `<=0.1 µg/L`, `<=0.5 µg/L`, `<=0.1 mSv/a` |
| Borne haute, entier | `<=0 n/(100mL)`, `<=0 NPP/100mL`, `<=1 NFU`, `<=2 NFU`, `<=1 mg/L`, `<=2 mg/L`, `<=50 mg/L`, `<=200 mg/L`, `<=250 mg/L`, `<=1 µg/L`, `<=3 µg/L`, `<=5 µg/L`, `<=6 µg/L`, `<=10 µg/L`, `<=20 µg/L`, `<=30 µg/L`, `<=50 µg/L`, `<=60 µg/L`, `<=100 µg/L`, `<=200 µg/L`, `<=250 µg/L`, `<=25 °C`, `<=100 Bq/L` |
| Unité avec espèce chimique | `<=1 mg(Cu)/L`, `<=2 mg(Cu)/L`, `<=2 mg(C)/L`, `<=15 mg(Pt)/L`, `<=20 µg(Se)/L`, `<=50 µg(CN)/L` |
| Intervalle | `>=6,5 et <=9 unité pH` (pH, en **référence**, pas en limite), `>=200 et <=1100 µS/cm` (conductivité), `>=1 et <=2 SANS OBJET` (« Equilibre calcocarbonique 0/1/2/3/4 ») |

`Thresholds.parse` lit `<=`, `<`, `≤` comme borne haute, `>=`, `>`, `≥` comme
borne basse, `=` comme les deux, la virgule ou le point décimal, garde l'unité
telle que publiée sauf `SANS OBJET` (pas d'unité), et rend `null` pour tout le
reste (texte sans comparaison, deux bornes du même côté, bornes croisées,
comparaison après l'unité).

Pour le pH, la limite est nulle et l'intervalle est dans la **référence** :
le graphique doit tracer `reference` quand `limit` est absente.

## Historique

Sondage du 2026-10-25 entre 01 h et 02 h UTC. L'API a répondu `500` (corps de
65 octets) à toutes les requêtes pendant quelques minutes vers 01 h 20 UTC,
puis `502 Proxy Error` sur une requête lourde (Bordeaux, 10 ans, toutes
lignes), avant de répondre de nouveau normalement : aucune conclusion n'a été
tirée d'un échec isolé.

### Liste de codes dans `code_parametre`

`code_parametre=7972,1345` est accepté et vaut « l'un ou l'autre » :
Orléans, 3 ans, 104 lignes (58 × `1345`, 46 × `7972`). Encodée (`%2C`), la
virgule donne les mêmes 104 lignes.

### Doublons dans une série

À Paris, la plupart des prélèvements publient **deux** pH, l'un mesuré sur
place (`code_lieu_analyse` = `T`, terrain), l'autre au laboratoire (`L`), avec
des valeurs différentes (`07500217014` : 7,3 et 7,58). Paris CENTRE, pH sur
10 ans : 11 525 lignes pour 5 820 prélèvements. La série garde un point par
`code_prelevement`, celui du laboratoire s'il existe (`Series.parameterSeries`,
fixture `resultats_dis_75056_ph.json`).

Un même prélèvement peut aussi publier **deux lignes du même code, au même
lieu**, dont une seule porte le seuil. Paris, UDI `075000227`, anthraquinone
(`2013`), relevé du 2026-10-25 : `Anthraquinone (HAP)` sans limite et
`Anthraquinone (pesticide)` avec `<=0,1 µg/L`, dans un ordre variable. Garder la
première ligne lue faisait perdre la limite (visible surtout quand la fenêtre ne
contient qu'une mesure). La série préfère donc, à lieu égal, la ligne qui publie
une limite ou une référence, et complète les seuils du dernier point par les
autres lignes du même prélèvement, jamais par un prélèvement plus ancien
(fixture `resultats_dis_75056_anthraquinone.json`).

### Série : mesures

`fields=code_prelevement,code_commune,date_prelevement,code_parametre,libelle_parametre,code_lieu_analyse,resultat_alphanumerique,resultat_numerique,libelle_unite,limite_qualite_parametre,reference_qualite_parametre`
(`code_commune` est obligatoire pour `ResultRow`).

| Série | Lignes | Taille | Durée |
|---|---|---|---|
| Orléans, nitrates (`1340`), 3 ans | 104 | 31 Ko | 1,3 s |
| Paris CENTRE, pH (`1302`), 10 ans, une page de 20 000 | 11 525 | 3,4 Mo | 28,8 s |

### Frise des conclusions : stratégie

Les lignes sont par résultat : rapatrier toutes les lignes d'une fenêtre est
trop lourd (Paris CENTRE, 6 mois : 1 658 lignes pour 44 prélèvements ; la
commune entière compte ~228 000 lignes). On ne demande donc que les lignes de
quelques paramètres mesurés dans presque tous les prélèvements, avec les seuls
champs d'en-tête, puis on dédoublonne par `code_prelevement`
(`Series.sampleSummaries`).

Codes retenus : **pH `1302`, Escherichia coli `1449`, plomb `1382`**. Le pH et
E. coli sont dans presque tous les contrôles ; les prélèvements qui manquaient
aux autres candidats (température `1301`, conductivité `1303`) étaient des
campagnes **plomb** seules (au robinet), d'où `1382`.

Couverture : prélèvements distincts obtenus par la stratégie / par toutes les
lignes de la même fenêtre (`code_reseau` filtré).

| Commune, UDI, fenêtre | Toutes lignes | 1301+1302+1303+1449 | **1302+1382+1449** |
|---|---|---|---|
| Orléans `045000474`, 6 mois | 22 | 22 | **22** |
| Orléans `045000474`, 12 mois | 75 | 75 | **75** |
| Paris CENTRE `075000221`, 6 mois | 44 | 43 (manque : plomb seul) | **44** |
| Toulouse (31555, sans `code_reseau`), 6 mois | 118 | 109 (8 plomb seul, 1 autre) | **117 (99,2 %)** |
| L'Abergement-Clémenciat (01001) `001000556`, tout l'historique | 29 | 29 | **29** |

Limite : un prélèvement qui ne mesure aucun des trois paramètres manque à la
frise (Toulouse : un prélèvement ne portant que « 17b-estradiol » et
« 4-nonylphenol ramifié », campagne de micropolluants). Sur Paris CENTRE,
3 ans, les deux jeux de codes donnent les mêmes 1 346 prélèvements.

Requête : `code_parametre=1302,1382,1449`,
`fields=code_prelevement,code_commune,date_prelevement,conclusion_conformite_prelevement,conformite_limites_bact_prelevement,conformite_limites_pc_prelevement,conformite_references_bact_prelevement,conformite_references_pc_prelevement`,
`date_min` et `date_max` (lendemain), `sort=desc`. Environ 3 lignes et
~400 octets par prélèvement (la conclusion est un texte long).

| Frise | Prélèvements | Lignes | Taille | Durée |
|---|---|---|---|---|
| Orléans, 3 ans | 449 | 970 | 429 Ko | 4,0 s |
| Paris CENTRE, 3 ans | 1 346 | 3 990 | 1,6 Mo | 8,2 à 15,9 s |
| L'Abergement-Clémenciat, 3 ans | 8 | 17 | 7,6 Ko | 0,4 s |
| Paris CENTRE, 10 ans, une page de 20 000 | 5 821 | 17 336 | 6,8 Mo | 32 à **60 s** |
| Paris CENTRE, 10 ans, `size=5000`, page 1 / page 4 | — | 5 000 / 2 336 | 2,0 / 0,9 Mo | 9,5 / 10,2 s |

Une page de 20 000 lignes peut atteindre le délai de lecture du client
(60 s) ; des pages de **5 000** lignes répondent en ~10 s chacune. Le client
pagine donc par 5 000 et suit les pages tant qu'elles sont pleines (la
dernière page répond encore `206` avec `next` renseigné).

### Lenteur d'une série et taille de page (mesures du 2026-10-25)

Le 2026-10-25, `resultats_dis?code_commune=75056&code_reseau=075000221&code_parametre=1302&date_min_prelevement=2023-10-25&size=5000&sort=desc`
a répondu `500` au bout de 55,9 s, pendant une panne générale de l'API (des
`500` en 0,2 à 17 s sur toutes les requêtes à la même heure, voir plus haut).
Mesures une fois l'API revenue, sous la forme qu'envoie le client
(`fields` réduits, `date_max` = lendemain) :

| Paris CENTRE, pH | `size` | Page | Statut | Taille | 1er octet | Total |
|---|---|---|---|---|---|---|
| 3 ans | 5 000 | 1 (dernière) | 200 | 0,9 Mo | 2,6 à 4,7 s | 4,5 à 6,5 s |
| 3 ans | 1 000 | 1 | 206 | 0,35 Mo | 1,6 s | 2,3 s |
| 3 ans, sans `fields` ni `date_max` | 5 000 | 1 | 200 | 2,9 Mo | 3,3 s | 5,4 s |
| 10 ans | 5 000 | 1 / 3 | 206 | 1,7 / 0,5 Mo | 7,3 / 3,1 s | 10,8 / 4,1 s |
| 10 ans | 2 000 | 1 / 6 | 206 | 0,7 / 0,5 Mo | 4,3 / 2,8 s | 5,6 / 4,1 s |

Une page de 5 000 lignes reste à moins de 11 s, loin du délai de lecture de
60 s ; des pages plus petites allongent le total (10 ans : ~23 s en 3 pages de
5 000, ~30 s en 6 pages de 2 000) sans rien gagner contre une panne, où même
une petite requête échoue. La taille de 5 000 est donc gardée. Le délai de
lecture dépassé sur une connexion établie n'est plus présenté comme un
problème de connexion : il donne `DataError.Timeout` (« Hub'Eau took too long
to answer »), distinct de `DataError.Network`.

## Alertes

### Requête légère du dernier prélèvement

`resultats_dis?code_commune=…&code_reseau=…&sort=desc&page=1&size=1` avec les
champs d'en-tête de la frise
(`fields=code_prelevement,code_commune,date_prelevement,conclusion_conformite_prelevement,conformite_limites_bact_prelevement,conformite_limites_pc_prelevement,conformite_references_bact_prelevement,conformite_references_pc_prelevement`),
sans filtre de paramètre ni de date. Le tri décroissant porte sur la date et
l'heure, et toutes les lignes d'un prélèvement portent son en-tête : la
première ligne suffit. Mesures du 2026-10-25 :

| Unité | `size` | Statut | Taille | Durée |
|---|---|---|---|---|
| Orléans `045000474` | 1 / 5 / 20 | 206 | 1,6 / 3,3 / 9,5 Ko | 0,24 / 0,24 / 0,26 s |
| Paris CENTRE `075000221` | 1 / 5 / 20 | 206 | 1,6 / 3,3 / 9,5 Ko | 0,24 / 0,23 / 0,26 s |
| L'Abergement-Clémenciat `001000556` | 1 / 5 / 20 | 206 | 1,6 / 3,3 / 9,5 Ko | 0,25 / 0,23 / 0,27 s |
| Toulouse (31555, sans `code_reseau`) | 1 / 5 / 20 | 206 | 1,5 / 3,2 / 9,2 Ko | 3,3 (1er appel) / 0,26 / 0,25 s |

`size=1` : ~1,6 Ko par unité suivie (dont ~1,2 Ko pour `first`/`last`/`next`),
un quart de seconde. `HubEauAlertChecker` en fait une par suivi et par
passage.

### Conclusions défavorables

Corpus : `resultats_dis` des départements 01, 02, 13, 22, 29, 33, 45, 59, 62,
67, 69, 75, 76, 2A, 2B, 971, 972, 973, 974, 976 (lignes pH, E. coli, plomb,
juin 2025 à octobre 2026), plus les prélèvements à code `N` de 01, 13, 29,
45, 59, 2A, 971, 974 depuis 2024 : **45 685 prélèvements**, 4 037 conclusions
distinctes. Mots cherchés : « déconseill », « interdi », « restriction »,
« non conforme », « ne satisfaisant pas », « ne pas consommer ».

Constats :

- **Les codes et le texte divergent.** 17 134 prélèvements ont au moins un code
  `N` ; 5 871 d'entre eux ont une conclusion qui ne dit rien de défavorable,
  parfois même « conforme » (`97100139684`, Le Gosier, 2026-05-05, `C,C,C,N` :
  « Eau d'alimentation faiblement minéralisée conforme aux exigences de qu
  alité en vigueur pour l'ensemble des paramètres mesurés »). À l'inverse,
  661 prélèvements aux quatre codes sans `N` ont une conclusion défavorable :
  - `06900172826`, Lyon, 2025-10-22, `C,C,C,C` : « Eau d'alimentation conforme
    aux limites de qualité et non conforme aux références de qualité. » ;
  - `02A00122406`, Figari, 2026-08-25, `C,C,C,C` : « … conforme aux limites de
    qualité et ne satisfaisant pas aux références de qualité. » ;
  - `05900342568`, Saint-Benin, 2026-06-11, `C,C,C,C` : « Eau d'alimentation
    non conforme aux limites de qualité en vigueur pour le paramètre nitrates.
    Cette eau est déconseillée aux populations sensibles … » ;
  - `05900343647`, Boussières-en-Cambrésis, 2026-07-31 : « … Sa consommation
    est donc déconseillée pour les nourrissons de moins de 6 mois. »
    (perchlorates, Nord, Aisne, Pas-de-Calais : très fréquent) ;
  - `97100139216`, Anse-Bertrand, 2026-01-23 : « Une interdiction de consommer
    l'eau est en cours. » ;
  - `05900343236`, Locquignol, 2026-07-24 : « Cette eau est soumise à une
    restriction de consommation pour tous les usages alimentaires … » ;
  - `97100140548`, Saint-Barthélemy, 2026-06-30 : « L'interdiction de
    consommation prise par la collectivité le 19 juin 2026 est maintenue. »
  - `06700272440`, Brumath, 2026-07-20, `C,D,C,C` (dérogation) : « Eau
    d'alimentation non conforme aux limites de qualité réglementaires en
    vigueur. … L'eau peut être consommée sans restriction. »
- **Les mots seuls trompent.** Mentions qui ne disent rien de défavorable sur
  le prélèvement :
  - niées : « ne nécessitant pas de restriction de consommation »
    (`04500168826`, Beaulieu-sur-Loire, 2026-01-19), « cette non conformité ne
    nécessite cependant pas de restriction » (plusieurs centaines de prélèvements), « sans
    restriction d'usage », « aucune restriction des usages de l'eau n'est
    préconisée » ;
  - levées : « ce qui a permis de lever l'interdiction de consommation »
    (`04500167918`, Douchy-Montcorbon, 2025-11-05), « une demande de levée de
    l'interdiction … a été émise » (`97100140700`, Saint-Barthélemy,
    2026-07-17), « l'interdiction prise le 05 août 2025 a été levée » ;
  - passées : « Ce prélèvement fait suite à une non conformité constatée le
    30/01/2026 » (`04500169121`, Mareau-aux-Bois), « nouveau contrôle effectué
    après une non conformité bactériologique » (`07600308543`, Fontenay),
    « recontrôle … ayant entrainé une demande d'interdiction » (`97100139384`,
    Gourbeyre) ;
  - conditionnelles : « Au delà de cette valeur, il est recommandé de ne pas
    consommer cette eau, une restriction d'usage est nécessaire. » (texte type
    des Côtes-d'Armor sur les chlorates, `02200246415`, Plélo, 2026-08-19).
- **Des espaces parasites coupent les mots** (texte recoupé à largeur fixe) :
  « déc onseillée » (`00200210099`, Retheuil), « non-conformi té »,
  « consommati on ». Les mots sont donc cherchés sur le texte normalisé
  **sans espaces**.

Règle retenue (`core.Unfavourable`) : un des quatre codes à `N`, **ou** une
mention de non-conformité, de consommation déconseillée, interdite ou
restreinte qui n'est, dans sa proposition, ni niée (« pas », « sans »,
« aucun »), ni levée (« lever », « levée »), ni passée (« suite à »,
« après une », « fin à », « ayant entraîné »), ni, dans sa phrase,
conditionnelle (« au-delà », « en cas de », « si »). Sur le corpus, le texte
seul signale 661 prélèvements sans code `N` ; les 61 conclusions distinctes relues correspondent toutes à
une non-conformité ou une restriction publiée. Les cas ci-dessus sont les
tests de `UnfavourableTest`.
