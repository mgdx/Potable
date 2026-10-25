# Traductions

Potable est en anglais par défaut (`app/src/main/res/values/`) et traduit en
français (`values-fr/`), et dans aucune autre langue (SPEC §11.1,
`docs/decisions.md` du 2026-10-27). Ce fichier tient l'état de relecture de
la traduction. Le vocabulaire est fixé par `docs/glossary.md`, qui s'impose à
toute la traduction.

## État de relecture

| Langue | Dossier | Fastlane | État | Relu par | Date |
|---|---|---|---|---|---|
| Anglais (référence) | `values/` | `en-US` | texte source | — | — |
| Français | `values-fr/` | `fr-FR` | traduction IA, à relire par un locuteur natif | — | — |

États possibles : *traduction IA, à relire par un locuteur natif* ;
*relue* (une relecture native complète, chaînes et métadonnées Fastlane) ;
*à revoir* (des chaînes ont été ajoutées ou modifiées depuis la relecture :
les lister dans la dernière section ci-dessous).

## Contribuer une relecture

Une relecture vaut pour toute la traduction : `strings.xml` et les
métadonnées Fastlane (`fastlane/metadata/android/fr-FR/`).

1. Lire `docs/glossary.md`. Si un terme du glossaire
   vous semble mal choisi, le dire d'abord (issue ou merge request sur le
   glossaire seul) : un terme se change partout à la fois, jamais chaîne par
   chaîne.
2. Relire `app/src/main/res/values-fr/strings.xml` en regard de
   `values/strings.xml`. Les commentaires du fichier anglais expliquent
   chaque paramètre (`%1$s`, `%1$d`). Corriger le sens, le naturel et la
   typographie ; ne pas toucher aux noms des clés ni aux paramètres.
3. Si possible, vérifier à l'écran : Android 13+, Paramètres > Système >
   Langues > Langues des applications > Potable.
4. Lancer `python3 tools/check_translations` : il doit afficher
   `check_translations: OK`.
5. Dans la même merge request, passer le français à *relue* dans le tableau
   ci-dessus, avec votre nom ou pseudonyme et la date.

Ce qui ne se traduit pas (libellés de l'API en français, Hub'Eau, ARS, pH,
noms des sources) est listé dans le glossaire.

## Règle de fusion

**Une chaîne ajoutée en anglais sans sa traduction française bloque la
fusion.** Toute merge request qui ajoute ou modifie une chaîne de
`values/strings.xml` fournit la chaîne en français, en respectant le
glossaire ; si la traduction est produite par IA, le français repasse à
*à revoir* avec la liste des chaînes concernées. `tools/check_translations`
le vérifie (chaîne manquante, paramètre de format différent, apostrophe non
échappée…),
via la tâche Gradle `./gradlew checkTranslations`, qui rejoint
`./gradlew check`. `lint`
bloque aussi une chaîne manquante ou en trop (`MissingTranslation`,
`ExtraTranslation`), une quantité de pluriel manquante ou inutile
(`MissingQuantity`, `UnusedQuantity`) et un dossier `values-xx` absent de
`res/xml/locales_config.xml` (`UnusedTranslation`).

## Pour les traducteurs

### Où sont les chaînes

- Source anglaise, seule référence : `app/src/main/res/values/strings.xml`.
  Les chaînes y sont regroupées par écran, dans l'ordre où l'utilisateur
  les rencontre ; garder le même ordre et les mêmes commentaires de section
  dans la traduction facilite la relecture.
- Traduction : `app/src/main/res/values-fr/strings.xml`.
- Métadonnées de la fiche : `fastlane/metadata/android/fr-FR/`.
- Ne jamais recopier une ressource `translatable="false"` (`app_name`, URL,
  liste des bibliothèques, citation INSEE, `geo.api.gouv.fr`) : le script la
  refuse dans une traduction.

### Vocabulaire et contexte

- `docs/glossary.md` fixe les termes récurrents et la typographie
  française ; il s'impose.
- Le commentaire au-dessus d'une chaîne anglaise donne son contexte : ce
  que vaut chaque paramètre, l'endroit de l'écran, la place disponible pour
  les libellés courts (puces, boutons, titres d'une ligne). Le lire avant de
  traduire ; ne pas le traduire.
- Quelques chaînes en citent une autre : `follows_empty` cite le bouton
  `sheet_follow` (« Follow »), `about_privacy` le bouton `nearby_action`
  (« Near me »), `settings_alert_unfavourable_included` l'interrupteur
  `alert_new_analyses`. La citation reprend mot pour mot la traduction de la
  chaîne citée.
- Les libellés publiés par Hub'Eau (paramètres, unités, conclusions,
  limites `<=50 mg/L`) n'apparaissent jamais dans `strings.xml` : ce sont des
  données, insérées par les paramètres, affichées en français partout.
- Apostrophe : ’ (U+2019) dans le texte ; `Hub\'Eau` garde son apostrophe
  droite, échappée. Une apostrophe droite non échappée est refusée par
  aapt2 et par le script.
- Aucune marque de direction (RLM, LRM, isolants Unicode) dans les
  chaînes : le code isole lui-même les données (`bidiWrap`, SPEC §11.2).
- Les nombres et les dates arrivent déjà formatés pour la langue : ne jamais
  écrire un chiffre en dur, toujours passer par le paramètre.

### Pluriels

- Quantités du français, pas copie de l'anglais : `one`, `many`, `other`.
- **`many` est exigé par `lint` (`MissingQuantity`)** : le fournir,
  identique à `other` (il ne sert qu'aux très grands nombres,
  « 1 000 000 de jours »).
- `settings_alert_weeks` ne sert qu'à partir de 2 semaines (« Every week »
  est `settings_alert_weekly`) ; son article `one` se traduit quand même.

### Vérifier

```bash
python3 tools/check_translations      # doit afficher « check_translations: OK »
./gradlew checkTranslations           # le même contrôle, depuis Gradle
./gradlew lint                        # pluriels, chaînes manquantes ou en trop
python3 tools/check_fastlane.py       # métadonnées Fastlane : fichiers et longueurs
```

### Chaînes à paramètres

Mêmes paramètres qu'en anglais, même numéro, place libre dans la phrase.
Les paramètres marqués *donnée* reçoivent un texte de Hub'Eau ou du
référentiel des communes, en français, isolé par le code.

| Chaîne | Paramètres |
|---|---|
| `commune_and_unit` | `%1$s` commune, `%2$s` unité de distribution (*données*) ; titre des notifications et libellé lu par les lecteurs d'écran |
| `quality_limit`, `quality_reference` | `%1$s` limite ou référence publiée, `<=50 mg/L` (*donnée*) |
| `search_no_results`, `sample_no_match` | `%1$s` texte saisi par l'utilisateur |
| `search_postal_codes_more` | `%1$s` premier code postal (*donnée*), `%2$d` nombre de codes en plus |
| `search_published_under` | `%1$s` commune de rattachement ou son code INSEE (*donnée*) |
| `sheet_data_from`, `sheet_refresh_failed` | `%1$s` date de la dernière mise à jour |
| `sheet_parameter_history_action` | `%1$s` nom traduit du paramètre (`featured_*`) ; action lue par TalkBack, minuscule initiale |
| `sheet_sampled_on` | `%1$s` date du prélèvement |
| `sheet_distributor`, `sheet_owner` | `%1$s` distributeur, collectivité responsable (*données*) |
| `age_days`, `age_weeks`, `age_months`, `age_years` (pluriels) | `%d` de 1 à 6 jours, 1 à 4 semaines, 1 à 11 mois, 1 an ou plus |
| `timeline_empty` | `%1$s` début de la période (date) |
| `timeline_summary` (pluriel) | `%1$d` nombre de prélèvements, `%2$s` début de la période (date) |
| `timeline_non_compliance` | `%1$s` type de conformité (`sheet_limits_*`, `sheet_references_*`), `%2$s` « Not compliant » |
| `timeline_refresh_failed` | `%1$s` un message `error_*` (phrase complète) |
| `history_selected_point` | `%1$s` date, `%2$s` valeur publiée avec son unité (*donnée*) |
| `history_chart_description` (pluriel) | `%1$d` nombre de mesures, `%2$s` et `%3$s` première et dernière dates, `%4$s` et `%5$s` plus basse et plus haute valeurs (*données*) |
| `history_legend_limit_*`, `history_legend_reference*` | `%1$s` limite ou référence publiée (*donnée*) |
| `follows_move_up`, `follows_move_down`, `follows_set_primary_description`, `follows_remove` | `%1$s` le libellé `commune_and_unit` |
| `follows_remove_title` | `%1$s` commune (*donnée*) |
| `follows_remove_text` | `%1$s` unité de distribution (*donnée*) |
| `settings_years` (pluriel) | `%d` 1, 3, 5 ou 10 ; puce et bouton radio, une dizaine de caractères |
| `settings_alert_weeks` (pluriel) | `%d` 2 ou 4 |
| `alert_notification_new`, `alert_notification_date` | `%1$s` date du prélèvement |
| `about_version` | `%1$s` numéro de version, `1.0` |

## Chaînes à revoir

Aucune pour l'instant.
