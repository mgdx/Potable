# Glossaire de traduction

Fixe la traduction française des termes récurrents de Potable (SPEC §11.1 :
anglais et français seulement). Il s'impose à toute la traduction : un terme
du glossaire ne se change plus sans repasser sur **toutes** ses occurrences,
dans `values-fr/` et dans les métadonnées Fastlane `fr-FR`.

## Méthode

1. **Vocabulaire du système Android** d'abord (Paramètres, Historique, vider
   le cache, par défaut…), puis **vocabulaire des services publics de l'eau**
   français (ARS, ministère chargé de la santé, directive (UE) 2020/2184 sur
   les eaux destinées à la consommation humaine et sa transposition).
2. **Langage courant** ensuite : SPEC §9 impose d'expliquer la notion d'unité
   de distribution, jamais de la supposer connue. Un terme réglementaire
   opaque cède la place à un terme courant, avec une note.
3. **Aucun verdict ajouté** (SPEC §14) : on évite tout terme qui sonne comme
   une note, un score ou une évaluation propre à l'appli (*note*,
   *évaluation* pour la conclusion sanitaire…).

## Ce qui ne se traduit jamais

- **Potable**, **Hub'Eau** (avec son apostrophe droite, écrite `Hub\'Eau`
  dans le XML), **ARS**, **INSEE**, **pH**, `geo.api.gouv.fr`, *Licence
  Ouverte Etalab 2.0*, *Code officiel géographique*, *Découpage administratif
  communal*, le titre de l'API « Qualité de l'eau potable ».
- Les libellés métier renvoyés par l'API (paramètres, unités, conclusions
  sanitaires, limites et références publiées comme `<=50 mg/L`) : ce sont des
  données, affichées en français dans les deux langues (SPEC §11.2). Les
  chaînes qui les introduisent le disent (« official wording, in French »).
- Les URL et les chaînes `translatable="false"`.

## Concepts (référence anglaise)

| Clé | Anglais (`values/`) | Concept, terme officiel français |
|---|---|---|
| udi | distribution unit | unité de distribution (UDI) : partie du réseau qui distribue une eau de qualité homogène, gérée par un même exploitant et un même maître d'ouvrage |
| sample | sample, sampled on | prélèvement (et ses analyses), prélevé le |
| sampling | sampling | prélèvement (l'action) |
| analysis | analysis, analysed parameters | analyse, paramètres analysés |
| compliance | compliance | conformité (aux limites et aux références de qualité) |
| compliant | compliant | conforme (code `C`) |
| not_compliant | not compliant | non conforme (code `N`) |
| derogation | compliant under a derogation | conforme dans le cadre d'une dérogation (code `D`) |
| not_assessed | not assessed | sans objet (code `S`) |
| limit | quality limit | limite de qualité : seuil sanitaire impératif |
| reference | quality reference | référence de qualité : valeur indicative de suivi du traitement |
| distributor | distributor | distributeur, exploitant du service |
| authority | responsible authority | maître d'ouvrage : collectivité responsable du service (commune, syndicat) |
| conclusion | sanitary conclusion | conclusion sanitaire de l'ARS |
| ars | regional health agency (ARS) | agence régionale de santé (ARS) |
| ministry | French Ministry of Health | ministère chargé de la santé |
| control | sanitary control | contrôle sanitaire |
| commune | commune | commune (collectivité territoriale française) |
| postal | postal code | code postal |
| tap | tap water / drinking water | eau du robinet / eau destinée à la consommation humaine |
| follow | Follow / Following | suivre une commune, état « suivie » |
| primary | primary commune, Primary, Set as primary | commune qui s'ouvre au démarrage |
| key | key parameters | paramètres mis en avant (SPEC §4.3) |
| nitrates | Nitrates | nitrates |
| hardness | Hardness | dureté (titre hydrotimétrique, TH) |
| ph | pH | pH |
| chlorine | Free chlorine | chlore libre |
| history | History, sample history | historique, historique des prélèvements |
| cache | cached data, Clear cached data | données en cache, vider le cache |
| official | Official wording, in French | libellé officiel en français |

---

## Français (`values-fr`)

| Clé | Terme | Note |
|---|---|---|
| udi | unité de distribution | terme des bulletins ARS et de Hub'Eau ; sigle UDI seulement en complément |
| udi (phrase) | « Une unité de distribution est la partie du réseau d’eau qui distribue une eau de même qualité. » | |
| sample | prélèvement ; « Prélevé le %1$s » | jamais « échantillon » |
| sampling | prélèvement | |
| analysis | analyse ; paramètres analysés | |
| compliance | Conformité | |
| compliant | Conforme | |
| not_compliant | Non conforme | |
| derogation | Conforme dans le cadre d’une dérogation | libellé du swagger Hub'Eau ; « Conforme (dérogation) » toléré si la place manque |
| not_assessed | Sans objet | libellé officiel du code `S`, plus juste que « Non évalué » |
| limit | limite de qualité | Code de la santé publique, arrêté du 11 janvier 2007 |
| reference | référence de qualité | idem |
| distributor | distributeur | champ Hub'Eau `nom_distributeur` |
| authority | collectivité responsable | le terme officiel « maître d’ouvrage » est du jargon ; à citer entre parenthèses si besoin |
| conclusion | conclusion sanitaire | |
| ars | agence régionale de santé (ARS) | |
| ministry | ministère de la Santé | |
| control | contrôle sanitaire | |
| commune | commune | |
| postal | code postal | |
| tap | eau du robinet ; eau potable | |
| follow | Suivre / Suivie | accord avec « commune » ou « unité », toutes deux féminines |
| primary | commune principale ; « Principale » ; « Définir comme principale » | |
| key | paramètres clés | |
| nitrates / hardness / ph / chlorine | Nitrates / Dureté / pH / Chlore libre | |
| history | Historique ; Historique des prélèvements | terme Android |
| cache | données en cache ; Vider le cache | terme Android (Stockage) |
| official | Libellé officiel | « en français » est redondant en français : on l’omet |

**Typographie.** Espace insécable (U+00A0) avant « : » et à l’intérieur des
guillemets « … » ; espace fine insécable (U+202F, ou U+00A0 à défaut) avant
« ; ! ? ». Apostrophe typographique ’ (U+2019), sauf dans `Hub\'Eau`. Points de
suspension en un caractère « … ». Majuscule au premier mot seulement, pas de
point final sur un libellé court. Formulation impersonnelle ou vouvoiement,
jamais de tutoiement. Les caractères spéciaux s’écrivent directement en UTF-8
dans le XML.

## Règles communes

- **Paramètres de format** : mêmes paramètres qu'en anglais, même numéro
  (`%1$s` reste `%1$s`), place libre dans la phrase. Dans un `<plurals>`, les
  articles `zero`, `one` et `two` peuvent omettre le nombre (`%d`) ; tous les
  autres paramètres restent obligatoires.
- **Pluriels** : quantités du français, pas copie de l'anglais : `one`,
  `many`, `other` (`many` couvre les très grands nombres, « 1 000 000 de
  prélèvements » ; `lint` l'exige, le fournir identique à `other`).
- **Échappement XML et Android** : `'` s'écrit `\'` (ou ’ typographique),
  `"` s'écrit `\"`, `&` s'écrit `&amp;`, `<` s'écrit `&lt;`.
- **Commentaires du fichier anglais** : ils décrivent chaque paramètre ; les
  lire avant de traduire, ne pas les recopier dans les traductions (ou les
  recopier en anglais, sans les traduire).
- **Longueur** : un libellé qui déborde se raccourcit en restant clair ; en
  dernier recours, le signaler dans `docs/translations.md`.
