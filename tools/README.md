# tools/

Scripts de génération et de contrôle, Python 3, bibliothèque standard
uniquement (aucune dépendance à installer).

## `generate_communes.py` — référentiel des communes

Produit `data/src/main/assets/communes.tsv`, le référentiel embarqué des
communes (SPEC §4.2) : recherche par nom et rattachement d'une position GPS à
une commune, sans appel réseau.

### Source et licence

API Découpage administratif, <https://geo.api.gouv.fr> (données du Code
officiel géographique de l'INSEE, populations municipales de l'INSEE et codes
postaux de La Poste), et fichier INSEE des mouvements de communes (voir plus
bas). Licence Ouverte Etalab 2.0 : réutilisation libre, y compris commerciale,
sous réserve de mentionner la source. La mention doit figurer dans l'écran
« À propos » (« Découpage administratif — geo.api.gouv.fr ; Code officiel
géographique — INSEE, Licence Ouverte 2.0 »).

Trois requêtes à l'API et un fichier INSEE :

| Fichier brut | Requête |
|---|---|
| `communes.json` (communes actuelles) | `/communes?fields=code,nom,codesPostaux,centre,population&format=json` |
| `deleguees.json` (déléguées et associées) | `/communes_associees_deleguees?fields=code,nom,type,chefLieu,centre&format=json` |
| `arrondissements.json` (arrondissements municipaux) | `/communes?type=arrondissement-municipal&fields=code,nom,codesPostaux,centre,population&format=json` |
| `mvt_commune.csv` (mouvements, INSEE) | `https://www.insee.fr/fr/statistiques/fichier/8740222/v_mvt_commune_2026.csv` |

L'API ne donne pas de population pour les déléguées et associées (le champ
`population` n'existe pas sur `/communes_associees_deleguees`).

Note : `/communes?type=commune-deleguee` renvoie une liste vide ; les
communes déléguées et associées ne sont servies que par
`/communes_associees_deleguees`.

### Régénérer

```bash
python3 tools/generate_communes.py                      # date du jour dans l'en-tête
python3 tools/generate_communes.py --date 2026-10-25    # date imposée
python3 tools/generate_communes.py --save-dir /tmp/geo  # garde aussi les réponses brutes (4 fichiers)
python3 tools/generate_communes.py --from-dir /tmp/geo  # rejoue sans réseau
python3 tools/check_communes.py                         # contrôle du résultat
```

La sortie est déterministe : mêmes réponses de l'API et même `--date` donnent
le même fichier octet pour octet (vérifié par `--save-dir` puis `--from-dir`
et `cmp`). Le script échoue bruyamment (code de sortie non nul) sur une
réponse vide, un centroïde absent, un arrondissement inconnu ou une commune
déléguée rattachée à une commune inexistante.

À relancer après chaque mise à jour annuelle du COG (1er janvier) : les
fusions de communes prennent effet à cette date.

### Format de sortie

UTF-8, fin de ligne `\n`. Ligne 1 (format v2) :
`# potable-communes v2 source=https://geo.api.gouv.fr events=v_mvt_commune_2026 generated=<YYYY-MM-DD>`.
Puis une ligne par entrée, triée par code (ordre lexicographique, donc `2A`/`2B`
entre `29` et `30`), 7 colonnes séparées par une tabulation :

```
code  name  postal_codes  latitude  longitude  hubeau_code  population
```

- `postal_codes` : codes triés, séparés par `,`, éventuellement vide ;
- `latitude`, `longitude` : centroïde fourni par l'API, point décimal,
  5 décimales au plus (l'API en donne 4), zéros finaux supprimés ;
- `hubeau_code` : vide pour une commune actuelle, sinon le code à interroger
  sur Hub'Eau ;
- `population` : population municipale (entier) des communes actuelles et des
  arrondissements, telle que la sert l'API ; vide pour les déléguées,
  associées et anciens codes. Sert uniquement au classement de la recherche
  (à rang textuel égal, la plus peuplée d'abord).

Le format v1 (sans la 7e colonne) reste lu par `CommuneFile.parse`.

Taille (génération du 2026-10-25) : 37 384 entrées (34 879 actuelles,
1 992 déléguées ou associées, 45 arrondissements, 468 anciens codes),
1,69 Mo brut, 0,69 Mo compressé (`gzip -9`). En v1 : 36 916 entrées, 1,52 Mo,
0,60 Mo.

### Choix faits

**Périmètre.** Métropole, Corse et tous les codes `97x` (Guadeloupe,
Martinique, Guyane, La Réunion, Mayotte, Saint-Pierre-et-Miquelon,
Saint-Barthélemy, Saint-Martin). Les codes `98x` (TAAF, Wallis-et-Futuna,
Polynésie, Nouvelle-Calédonie, Clipperton) sont exclus : hors du contrôle
sanitaire des ARS, donc absents de Hub'Eau (vérifié : 98411, 98735 → 0
résultat). Saint-Pierre-et-Miquelon est conservé malgré l'absence de données
(97502 → 0 résultat) : l'appli affiche alors le cas « commune sans UDI »
(SPEC §15).

**Communes actuelles.** `hubeau_code` vide : Hub'Eau répond sous le code
INSEE lui-même.

**Arrondissements municipaux** (Paris 751xx, Lyon 6938x, Marseille 132xx) :
conservés pour la recherche et la géolocalisation, `hubeau_code` = code de la
commune (75056, 69123, 13055). Correspondance codée en dur dans le script
(fixée par la loi PLM) ; un arrondissement hors de ces trois préfixes fait
échouer la génération.

**Communes déléguées et associées** : conservées sous leur nom officiel (ex.
`49071 Chanzeaux`), ce qui permet de les retrouver par leur ancien nom ;
`hubeau_code` = code de la commune de rattachement (`chefLieu` de l'API).
Lorsqu'une déléguée a le même code que sa commune nouvelle (cas du chef-lieu,
584 cas), elle n'est pas dupliquée : seule l'entrée actuelle est gardée, sous
le nom de la commune nouvelle (`49092 Chemillé-en-Anjou` ; « Chemillé » reste
trouvable par préfixe dans la plupart des cas). L'API ne fournit pas de code
postal pour les déléguées : la colonne est laissée vide plutôt que d'y recopier
tous les codes de la commune nouvelle.

### Vérifications Hub'Eau (2026-10-25)

Requête : `https://hubeau.eaufrance.fr/api/v1/qualite_eau_potable/communes_udi?code_commune=<code>&size=1`
(`count` = nombre de couples commune/UDI/année).

| Code interrogé | Résultat |
|---|---|
| 75056 Paris | 44 |
| 75115 Paris 15e | 0 |
| 69123 Lyon | 11 |
| 69381 Lyon 1er | 0 |
| 13055 Marseille | 176 |
| 13201 Marseille 1er | 0 |
| 01330 Ruffieu (déléguée de 01187 Haut Valromey au 2025-01-01) | 9, années ≤ 2024 seulement |
| 01187 Haut Valromey | 13, dont 2025 et 2026 |
| 28276 Neuvy-en-Beauce → 28319 Neuville Saint Denis (2025) | 9 (≤ 2024) → 15 (dont 2026) |
| 70557 Villefrancon → 70152 Colombine (2025) | 9 (≤ 2024) → 11 (dont 2026) |
| 86231 Saint-Macoux → 86247 Val-de-Comporté (2024) | 8 (≤ 2023) → 14 (dont 2026) |
| 49071 Chanzeaux → 49092 Chemillé-en-Anjou (2015) | 0 → 22 |
| 61496 Urou-et-Crennes → 61474 Gouffern en Auge | 0 → 113 |

Conclusions :

- Paris, Lyon et Marseille ne sont publiés que sous le code de la commune ;
  les codes d'arrondissement ne répondent pas.
- Pour une commune fusionnée, c'est le **nouveau code** qui porte les données
  courantes. L'ancien code ne garde que les rattachements UDI des années
  antérieures à la fusion, et `resultats_dis?code_commune=01330` renvoie
  0 résultat contre 2 926 pour 01187 : les résultats d'analyse sont tous
  publiés sous le nouveau code. D'où `hubeau_code` = commune de rattachement.

### Anciens codes INSEE (SPEC §15)

Les communes absorbées sans garder de statut de déléguée, ou dont le statut de
déléguée a été supprimé depuis, n'apparaissent dans aucune des trois listes de
l'API. Elles viennent du fichier INSEE des mouvements de communes
(`v_mvt_commune_<millésime>.csv`, Code officiel géographique, « Liste des
événements survenus aux communes […] depuis 1943 »), publié sur
<https://www.insee.fr> et référencé par le jeu de données
<https://www.data.gouv.fr/datasets/code-officiel-geographique-cog/>.

**Stabilité de la source.** Chaque millésime a sa propre URL (2025 :
`/fichier/8377162/v_mvt_commune_2025.csv`, 2026 :
`/fichier/8740222/v_mvt_commune_2026.csv`) ; une URL de millésime ne change
pas, mais la suivante n'est pas prévisible. Le millésime et son URL sont deux
constantes du script (`MVT_VINTAGE`, `MVT_URL`), à mettre à jour avec le COG
annuel, en même temps que l'API passe au nouveau COG. Le millésime figure dans
l'en-tête du fichier (`events=`).

**Sélection.** Événements datés du 1er janvier 2015 ou après, de type
absorption (`MOD` 31 fusion simple, 32 commune nouvelle, 33 fusion-association,
34 fusion-association transformée en fusion simple, 35 suppression de commune
déléguée ; depuis 2015 il n'y a en pratique que 32, 34 et 35), dont la commune
d'arrivée est une commune (`TYPECOM_AP` = `COM`) de code différent. Pour un
même ancien code, le dernier événement l'emporte. La commune d'arrivée peut
avoir été absorbée à son tour ou avoir changé de code (`MOD` 41, 50) : la
chaîne est suivie jusqu'à une commune actuelle. Un code déjà présent (commune
actuelle, rétablie, déléguée ou arrondissement) est ignoré.

**Contenu des lignes.** Nom = libellé de la commune au moment de l'absorption
(`LIBELLE_AV`), `hubeau_code` = commune actuelle, codes postaux et population
vides. Le fichier INSEE ne donne pas de centroïde : la ligne reprend celui de
la commune actuelle (sans effet sur la géolocalisation, qui ignore les entrées
munies d'un `hubeau_code` hors arrondissements).

Génération du 2026-10-25 : 468 anciens codes ajoutés (272 issus d'une
suppression de déléguée, 135 de communes nouvelles sans déléguée, 61 de
fusions-associations transformées), 0 non résolu. Exemples : `49074
La Chapelle-Rousselin` → 49092, `27058 Bernières-sur-Seine` → 27676.

Hub'Eau : `communes_udi?code_commune=49074` → 0 résultat, comme les autres
anciens codes vérifiés ci-dessus. Cas particulier, 27058 a été le code de la
commune nouvelle Les Trois Lacs de 2021 à 2023 (transferts de chef-lieu) et
garde 7 rattachements historiques ; les données courantes sont sous 27676.

## `check_communes.py` — autotest du référentiel

```bash
python3 tools/check_communes.py [fichier]   # défaut : data/src/main/assets/communes.tsv
```

Vérifie : en-tête v2, fins de ligne `\n`, 7 colonnes partout, population
entière ou vide (obligatoire pour une commune actuelle), codes INSEE bien
formés, uniques et triés, codes postaux sur 5 chiffres, coordonnées
numériques (5 décimales au plus) dans les bornes Métropole+Corse, Antilles,
Guyane, La Réunion, Mayotte et Saint-Pierre-et-Miquelon, présence de
`45234 Orléans` (45000), `75056 Paris`, d'un arrondissement de chacune des
villes PLM, de `97411 Saint-Denis`, de la déléguée `01330 Ruffieu`, de
l'ancien code `49074 La Chapelle-Rousselin`, une population plausible pour
Strasbourg, et que chaque `hubeau_code` non vide
désigne une commune actuelle du fichier. Code de sortie 1 si un contrôle échoue (tous les écarts sont listés, 50 au plus).

## `check_translations` — contrôle des traductions

Vérifie la traduction française (`values-fr/strings.xml`), seule langue
traduite (SPEC §11.1), contre l'anglais de référence (`app/src/main/res/values/strings.xml`), SPEC
§11 et §16 critère 10. Python 3, bibliothèque standard.

```bash
python3 tools/check_translations                 # dépôt courant
python3 tools/check_translations --root /chemin  # autre racine de dépôt
python3 -m unittest discover -s tools/tests      # tests du script
```

Une ligne par problème, `langue: nom: problème`, puis un bilan
(`check_translations: OK, …` ou `check_translations: FAILED, N error(s), …`).
Code de sortie 1 s'il reste une erreur, 0 sinon (les avertissements ne font
pas échouer).

Erreurs :

- fichier de langue absent ou XML mal formé (balise non fermée, `&` nu…),
  racine autre que `<resources>`, nom en double ;
- `<string>`, `<plurals>` ou `<string-array>` traduisible absent d'une langue ;
  clé présente dans une traduction mais pas en anglais ; type différent ;
- ressource `translatable="false"` en anglais présente dans une traduction ;
- paramètres de format différents de l'anglais : on compare les `%1$s`,
  `%2$d`… (un positionnel compte une fois, il peut changer de place ou être
  répété) et les `%s`, `%d` non positionnels (comptés). `%%` et `%n` sont
  ignorés ; le drapeau espace n'est pas reconnu, pour que « 50 % de » ne soit
  pas lu comme `% d` ;
- `string-array` : même nombre d'articles, mêmes paramètres article par
  article ;
- `plurals` : chaque article est comparé à l'article `other` anglais. Les
  articles `zero`, `one` et `two` peuvent omettre un paramètre entier (`%d`,
  `%1$d`…) — « Hier » — mais aucun autre, et aucun
  article ne peut en ajouter. Quantité inconnue, quantité en double ou
  `other` absent : erreur ;
- apostrophe `'` non échappée hors d'une section entre guillemets droits
  (aapt2 la refuserait) ; `\'` et ’ sont acceptés. Vaut aussi pour l'anglais.

Avertissement seulement : texte resté identique à l'anglais, sauf s'il ne
contient, hors paramètres et ponctuation, que des termes invariables. Deux
listes :

- toutes langues (`IDENTICAL_ALLOWED_TERMS`) : Potable, Hub'Eau, pH, ARS,
  INSEE, geo.api.gouv.fr, Etalab, GNU, GPL, API, Code officiel géographique,
  Licence Ouverte ;
- par langue (`IDENTICAL_ALLOWED_TERMS_BY_LANGUAGE`), pour les mots qui
  s'écrivent comme en anglais et sont la bonne traduction dans cette langue
  seulement : fr Nitrates, Pesticides, Version, Sources.

Les espaces insécables (U+00A0, U+202F, U+2009, tapées telles quelles ou
sous la forme d'échappement Android ` `) comptent comme des espaces
ordinaires dans cette comparaison : « Version %1$s » est identique à
« Version %1$s ». Plutôt que de contourner l'avertissement, ajouter le terme
à la liste de la langue.

Gradle : la tâche `checkTranslations` (fichier `build.gradle.kts` racine)
lance ce script ; `./gradlew check` en dépend, comme de `checkRtl`. Une
erreur fait échouer la build ; un avertissement est affiché sans la faire
échouer, mais la règle du projet (zéro avertissement) vaut aussi ici.
