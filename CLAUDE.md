# CLAUDE.md — Potable

**Attention : le `CLAUDE.md` du dossier parent (`AndroidStudioProjects/`) décrit
un autre projet (Sceau, NFC). Il ne s'applique pas ici. Seul ce fichier vaut
pour Potable.**

Potable : application Android libre (GPLv3) qui affiche les résultats du
contrôle sanitaire de l'eau du robinet en France (API Hub'Eau
`qualite_eau_potable`). `SPEC.md` est la source de vérité ; lis-le avant toute
implémentation. Tout écart est consigné dans `docs/decisions.md`.

## Commandes

```bash
./gradlew :core:test                 # tests JVM du cœur
./gradlew :data:testDebugUnitTest    # tests du module data
./gradlew :app:assembleDebug         # APK de debug
./gradlew lint ktlintCheck           # zéro avertissement exigé
./gradlew check                      # tout ce qui bloque la fusion
```

## Architecture

- `:core` — Kotlin JVM pur, **aucun import `android.*`**. Modèles, parsing
  JSON Hub'Eau (kotlinx.serialization), regroupement par `code_prelevement`,
  sélection d'UDI, recherche de commune, détection de nouvelle analyse.
- `:data` — bibliothèque Android : client Hub'Eau (OkHttp), Room, DataStore,
  WorkManager, chargement du référentiel des communes (asset).
- `:app` — UI Compose / Material 3, Navigation Compose, notifications.
  Injection manuelle (`AppContainer`).
- Package racine : `io.github.mgdx.potable` (`.core`, `.data`, `.app`… voir
  le code).

## Règles non négociables

- Aucun score, note ou verdict propre : l'appli restitue les conclusions ARS
  (SPEC §14). Pas de coche/bouclier/pouce dans l'iconographie.
- Un seul domaine réseau : `hubeau.eaufrance.fr`, HTTPS, cleartext interdit.
- Aucune télémétrie, aucun service Google, dépendances libres justifiées dans
  `docs/dependencies.md`.
- Anglais par défaut dans `res/values/`, aucune chaîne codée en dur ; les
  toute chaîne nouvelle va dans
  `strings.xml`.
- RTL : uniquement `start`/`end` (jamais `left`/`right`, `absolutePadding`,
  `AbsoluteAlignment`). La tâche `checkRtl` l'impose.
- Libellés métier de l'API (paramètres, unités, conclusions) affichés tels
  quels, jamais traduits.
- Commits en anglais, style Conventional Commits.
