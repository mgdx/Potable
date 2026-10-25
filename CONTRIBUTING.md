# Contribuer à Potable

Merci de l’intérêt que vous portez au projet. Toute contribution est la
bienvenue : relecture d’une traduction, correction, revue de code, signalement
d’une donnée mal restituée.

La documentation du projet est en français ; le code, ses commentaires et les
messages de commit sont en anglais, et l’interface est en anglais par défaut,
les autres langues arrivant comme traductions.

## Avant de commencer

Lisez [`SPEC.md`](SPEC.md). C’est la spécification complète et la source de
vérité du projet. Plusieurs choix qui peuvent sembler arbitraires y sont
justifiés — l’absence de tout score, l’anglais comme langue par défaut, le
référentiel des communes embarqué plutôt qu’interrogé. Les écarts au SPEC et
les choix qu’il laisse ouverts sont consignés, avec leurs raisons, dans
[`docs/decisions.md`](docs/decisions.md).

Si une proposition contredit l’un ou l’autre, **ouvrez d’abord une issue**
pour en discuter. Il en va de même pour tout changement important : mieux vaut
un échange de cinq minutes qu’une pull request à réécrire.

Une règle passe avant toutes les autres : **Potable n’établit aucun verdict
propre** (SPEC §14). Pas de score, pas de note, pas de classement, pas de
coche ni de bouclier dans l’iconographie, et les conclusions de l’ARS sont
affichées avec leurs mots. Une contribution qui ajouterait une interprétation,
si bien intentionnée soit-elle, ne sera pas fusionnée.

## Traduire

C’est la contribution la plus utile si vous n’écrivez pas de code.

L’application est écrite en anglais, dans
[`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml),
qui ne porte aucun qualificatif de langue : c’est ce qu’Android sert quand
rien d’autre ne correspond, il est donc toujours complet. La seule traduction
est le français, dans `values-fr/`, et les textes des magasins sont dans
`fastlane/metadata/android/en-US/` et `fr-FR/`. Potable n’est proposé qu’en
anglais et en français (SPEC §11.1) : une traduction dans une autre langue ne
sera pas fusionnée.

La traduction française a été **produite par IA** et attend la relecture d’un
locuteur natif. [`docs/translations.md`](docs/translations.md) tient l’état
de relecture et décrit la marche à suivre ; en résumé :

1. **Lisez le [glossaire](docs/glossary.md).** Il fixe la traduction des termes
   récurrents — unité de distribution, prélèvement, conformité, limite et
   référence de qualité, distributeur — et s’impose à toutes les chaînes. Si
   un terme vous semble mal choisi, dites-le d’abord dans une issue ou une
   pull request sur le glossaire seul : un terme se change partout à la fois,
   jamais chaîne par chaîne.
2. **Relisez `values-fr/strings.xml` en regard de l’anglais.** Les
   commentaires du fichier anglais expliquent chaque paramètre et le contexte
   de chaque phrase. Corrigez le sens, le naturel et la typographie ; ne
   touchez ni aux noms des clés, ni aux paramètres.
3. **Vérifiez à l’écran si vous le pouvez** : sous Android 13 et plus,
   Paramètres > Système > Langues > Langues des applications > Potable.
4. **Lancez le contrôle** :

   ```bash
   python3 tools/check_translations
   ```

   Il doit afficher `check_translations: OK`. Il signale une chaîne manquante,
   un paramètre de format différent de l’anglais, une apostrophe non
   échappée.
5. **Dans la même pull request**, passez le français à *relue* dans le tableau
   de `docs/translations.md`, avec votre nom ou pseudonyme et la date.

### Règles à respecter

**Les libellés de l’API ne se traduisent pas.** Noms des paramètres, unités,
conclusions sanitaires, limites publiées (`<=50 mg/L`) : ce sont des données,
affichées en français dans toutes les langues. Hub’Eau, ARS, INSEE et pH ne se
traduisent pas non plus ; la liste complète est en tête du glossaire.

**Les paramètres sont positionnels et gardent leur numéro.** `%1$s` reste
`%1$s`, mais sa place dans la phrase peut changer. On ne concatène jamais deux
chaînes dans le code : l’ordre des mots change d’une langue à l’autre.

**Les pluriels passent par `<plurals>`, et les catégories varient.** L’anglais
en utilise deux (`one`, `other`), le français trois (`one`, `many`, `other`),
selon les [règles de pluriel du CLDR](https://cldr.unicode.org/index/cldr-spec/plural-rules) ;
`many` se fournit identique à `other`.

**La typographie est celle de la langue cible.** En français : apostrophe
typographique ’ (sauf dans `Hub\'Eau`), espace insécable avant « : » et à
l’intérieur des guillemets « … », vouvoiement ou tournure impersonnelle,
jamais de tutoiement. Le détail est dans le glossaire.

**Une chaîne ajoutée en anglais sans sa traduction française bloque la
fusion.** Si la traduction est produite par IA, le français repasse à *à revoir*
dans `docs/translations.md`, avec la liste des chaînes concernées.

## Signaler un bug

Indiquez le modèle de l’appareil et la version d’Android, la version de
Potable et la façon dont il a été installé, ce que vous attendiez, ce qui s’est
passé, et comment le reproduire.

Si le problème concerne une commune, nommez-la avec son code INSEE ou son code
postal : la donnée publiée par Hub’Eau est souvent en cause, et la distinction
est rapide à faire. Une commune n’est pas une donnée personnelle ; votre
adresse, si. **N’incluez ni adresse, ni position, ni capture d’écran qui en
montre une.**

L’application n’envoie aucun journal nulle part. Si vous en joignez un capturé
avec `adb logcat`, relisez-le d’abord : c’est votre décision, et il peut
contenir plus que vous ne le pensez.

Une faille de sécurité ne va pas dans une issue publique : voir
[`SECURITY.md`](SECURITY.md).

## Proposer du code

### Mise en place

```bash
git clone https://github.com/mgdx/Potable.git
cd Potable
./gradlew check
```

Il faut un JDK 21 et le SDK Android (compileSdk 37). Aucune clé, aucun compte.

### Avant d’ouvrir une pull request

```bash
./gradlew ktlintFormat   # met en forme
./gradlew check          # tests, lint, ktlintCheck, checkRtl : sans un seul avertissement
```

L’analyse statique ne tolère aucun avertissement : les avertissements du
compilateur Kotlin sont des erreurs, et lint aussi. Ce n’est pas de la
coquetterie : le projet est fait pour être audité par les relecteurs de
F-Droid, et un avertissement qui traîne cache le suivant.

### Ce que la revue regardera

- **La séparation des couches.** La logique métier va dans `:core`, qui
  n’admet **aucun import Android** : c’est ce qui la rend testable sur la JVM,
  sans émulateur. `:data` porte le réseau, Room, DataStore et WorkManager ;
  `:app` l’interface. Voir [`docs/architecture.md`](docs/architecture.md).
- **Aucune chaîne codée en dur**, ni dans le Kotlin, ni dans un fichier XML :
  tout passe par `strings.xml`, avec sa traduction française.
- **`start` et `end`, jamais `left` et `right`.** Ni `absolutePadding`, ni
  `AbsoluteAlignment`, ni `paddingLeft` : l’interface doit pouvoir se
  mettre en miroir dans une langue de droite à gauche. La tâche `checkRtl`, branchée sur `./gradlew check`, l’impose
  et indique le fichier et la ligne fautifs.
- **Un seul domaine réseau**, `hubeau.eaufrance.fr`, en HTTPS. Une
  fonctionnalité qui demanderait un autre serveur relève d’une issue, pas
  d’une pull request.
- **Des tests.** Toute logique de `:core` est testée, si possible sur des
  réponses figées de l’API (`core/src/test/resources/`), et toute correction
  de bug arrive avec le test qui l’aurait détectée.
- **Des commentaires qui expliquent le *pourquoi*.** Un commentaire qui
  paraphrase le code est du bruit. Les comportements surprenants de l’API ont
  leur place dans [`docs/hubeau-api.md`](docs/hubeau-api.md), les choix dans
  [`docs/decisions.md`](docs/decisions.md).

### Commits

En anglais, au format [Conventional Commits](https://www.conventionalcommits.org/) :
`feat(app): …`, `fix(core): …`, `docs: …`. Atomiques, avec un message qui
décrit l’**intention** plutôt que la manipulation : « fix(core): read both
bounds of a pH range » vaut mieux que « change Thresholds.kt ».

### Ajouter une dépendance

Elle doit être :

1. **justifiée dans [`docs/dependencies.md`](docs/dependencies.md)**, avant
   d’être utilisée : son rôle, sa licence, pourquoi elle plutôt qu’une autre ;
2. **libre et compatible avec la GPLv3** ;
3. **sans service Google** : ni Play Services, ni Firebase, ni ML Kit.
   L’application doit fonctionner sur un Android sans services Google ;
4. **sans télémétrie** : l’analyse Exodus Privacy ne doit détecter aucun
   traqueur.

## Les données

**Le référentiel des communes** (`data/src/main/assets/communes.tsv`) est
généré, jamais modifié à la main. Pour le régénérer — après chaque révision
annuelle du Code officiel géographique, au 1er janvier, quand des communes
fusionnent :

```bash
python3 tools/generate_communes.py
python3 tools/check_communes.py
```

Si le fichier INSEE des mouvements de communes change de millésime, mettez à
jour les constantes `MVT_VINTAGE` et `MVT_URL` du script. Le détail est dans
[`tools/README.md`](tools/README.md).

**Les données de Hub’Eau** ne sont pas les nôtres. Une valeur fausse, une
conclusion absente ou un réseau mal rattaché se signalent à Hub’Eau ou à
l’ARS concernée ; une valeur juste mais mal affichée par Potable, ici.

## Licence

En contribuant, vous acceptez que votre travail soit publié sous
[GPLv3](LICENSE), comme le reste du projet.
