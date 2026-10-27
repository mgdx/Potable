# Audit des requêtes Hub'Eau et du cache — 2026-10-27

Question de l'auteur : « Pour le cache, a-t-on bien vérifié qu'on ne refait pas
de requêtes inutiles pour récupérer les données ? »

Périmètre : tout ce qui appelle `HubEauClient` (`communes_udi`,
`resultats_dis`) : `Caching*Repository` et `Remote*Repository`,
`CachingSampleRepository`, `HubEauAlertChecker` et `AlertWorker`, et les
ViewModels de `ui/sheet`, `ui/timeline`, `ui/history`, `ui/sample`, `ui/udi`.
La recherche de commune et « Mes communes » ne font aucune requête (référentiel
embarqué, table `suivi`).

Politique de fraîcheur inchangée (SPEC §7) : réseau au plus une fois par jour
civil à Paris par cache, sauf tirer-pour-actualiser ou « Réessayer » ; en cas
d'échec, le cache s'il existe. Seule extension : la liste des UDI d'une
commune suit désormais cette règle (elle était redemandée à chaque affichage).

## Méthode

Chaque chemin est mesuré par un test JVM qui compte les appels au réseau :
dépôt factice qui compte (`FakeNetwork.calls`, `udiCalls`, `seriesCalls`,
`timelineCalls`, `seriesUntil`/`timelineUntil` pour la fenêtre demandée),
`MockWebServer` pour les URL réellement émises (`RemoteHistoryRepositoryTest`),
ou dépôt factice côté ViewModel (`refreshCalls`, `calls`). Une « requête »
ci-dessous est un appel logique ; il peut faire plusieurs pages HTTP quand la
réponse dépasse la taille de page (500 lignes pour le dernier prélèvement,
1 000 pour un rapport, 5 000 pour un historique).

Rotation et ré-abonnement : aucun chemin réseau ne dépend d'un abonnement
(`stateIn(WhileSubscribed)` n'est employé que par les Réglages, sur Room et
DataStore). Les requêtes partent de l'`init` des ViewModels, qui survivent à
la rotation ; un retour sur l'écran recrée le ViewModel seulement si l'entrée
de navigation a été quittée, et le cache du jour répond alors.

## Bilan par chemin

| Chemin | 1er affichage | Réouverture, cache du jour | Réouverture, cache périmé | Rotation / ré-abonnement | Verdict |
|---|---|---|---|---|---|
| Fiche : dernier(s) prélèvement(s) (`SheetViewModel` → `CachingWaterRepository.refreshCache` → `latestSamples`) | 1 | 0 | 1 | 0 | OK (`the network is called once a day unless forced`, `a unit already looked at is shown from the cache, offline too`) |
| Fiche : tirer-pour-actualiser, deux fois de suite | 1 forcée | — | — | — | OK, garde `refreshJob` (`a second pull while a refresh runs does not ask again`, ajouté) |
| Fiche : encart 12 mois (`loadRecentUnfavourable` → `sampleTimeline` 12 mois) | 1 | 0 | 1 | 0 ; pas de relecture quand le cache réémet le même prélèvement (`recentFor`) | OK (`the unfavourable samples of the last 12 months are read once the sample is shown`) |
| Frise (`TimelineViewModel`, 3 ans par défaut) après la fiche le même jour | **avant : 1 sur 3 ans** (les 12 mois déjà en cache redemandés) ; **après : 1 sur les 2 ans manquants** | 0 | 1 (fenêtre entière) | 0 | **Corrigé** (`a longer window asks only for the days a cache of today lacks`) |
| Frise : passage à 1 an / 10 ans | 0 / **avant : 10 ans ; après : les 7 ans manquants** | — | — | — | **Corrigé**, même test |
| Frise : tirer pendant le premier chargement | **avant : 2** (chargement annulé à l'écran mais téléchargé jusqu'au bout, puis relancé) ; **après : 1** | — | — | — | **Corrigé** (`a pull during the first load does not ask again`) |
| Série d'un paramètre (`ParameterHistoryViewModel` → `parameterSeries`) | 1 | 0 | 1 | 0 | OK (`a followed unit is fetched once a day, then read from the cache`) |
| Série : fenêtre allongée | **avant : fenêtre entière ; après : jours manquants seuls** | — | — | — | **Corrigé** (même mécanisme que la frise) |
| Série : actualiser pendant une requête (double tirage, bouton puis tirage, tirage pendant le 1er chargement) | **avant : 2 ou plus ; après : 1** | — | — | — | **Corrigé** (`a refresh while a request runs does not ask again`) |
| Détail d'un prélèvement du jour (`SampleDetailViewModel`) | 0 (lit l'état de la fiche) | 0 | 0 | 0 | OK |
| Rapport d'un ancien prélèvement (`SampleReportViewModel` → `CachingSampleRepository`) | 1 (0 s'il est l'un des derniers en cache) | 0 (gardé jusqu'à purge ou rétention) | 0 (un prélèvement publié ne change pas) | 0 | OK (`a sample is fetched once, then read from the cache with its results`, `a latest sample already cached by a refresh needs no request`) ; « Réessayer » seulement après échec |
| Liste des UDI (`UdiViewModel` → `udis` → `communes_udi`) : recherche puis « Changer d'UDI » depuis la fiche | **avant : 2** (une à chaque affichage) ; **après : 1** | **avant : 1 ; après : 0** | 1 | 0 | **Corrigé** (`the units of a commune are asked for once a day`) |
| Alertes : contrôle périodique (`AlertWorker` → `HubEauAlertChecker.check`) | 1 requête légère (1 ligne) par unité suivie | — | — | — | OK, minimum pour détecter un nouveau prélèvement ; échec d'une unité : réessayée à la période suivante, jamais `Result.retry()` |
| Alertes : nouveau prélèvement déjà en cache (fiche ouverte après sa publication) | **avant : +1 rafraîchissement forcé ; après : +0** | — | — | — | **Corrigé** (`a new sample the app already cached is announced without fetching it again`) |
| Alertes : nouveau prélèvement absent du cache | +1 rafraîchissement forcé | — | — | — | OK, la notification ouvre la fiche qui doit l'avoir |
| Alertes : activation (`AlertScheduler.setBaseline`) | 1 requête légère par unité suivie, seulement au passage de « aucune alerte » à « au moins une » | — | — | — | OK (`AlertSchedulerTest`) |

## Corrections

1. **Liste des UDI redemandée à chaque affichage.** `CachingWaterRepository.udis`
   garde en mémoire la réponse de `communes_udi` par commune pour le jour
   (Paris), 16 communes au plus (LRU) ; appels concurrents sérialisés, un
   échec n'est pas gardé, `purgeCommune` et `purgeCache` l'oublient, une purge
   pendant la requête n'est pas réécrite (`cacheGeneration`). Pas de table
   Room : aucun changement de schéma, au prix d'une requête après la mort du
   processus.
2. **Historique : une fenêtre plus longue que le cache du jour retéléchargeait
   tout.** `CachingHistoryRepository` ne demande plus que les jours qui
   précèdent la couverture (`HistoryRepository.parameterSeriesBefore` /
   `sampleTimelineBefore`, `date_max_prelevement` = premier jour couvert,
   exclusif) et les joint au cache. Le cas quotidien : l'encart de la fiche lit
   12 mois, puis la frise 3 ans ; avant, la frise redemandait les 3 ans. Les
   libellés d'une série restent les plus récents publiés, sauf si seule la
   période ancienne a des mesures. Un échec rend le cache seul, comme ailleurs.
   L'implémentation par défaut de l'interface (fenêtre entière, puis coupe)
   garde les dépôts factices valides.
3. **Actualiser pendant une requête relançait la même requête.** Le client
   HTTP n'annule pas l'appel OkHttp quand la coroutine est annulée (voir
   ci-dessous) : annuler puis relancer téléchargeait deux fois.
   `ParameterHistoryViewModel.refresh` et `TimelineViewModel.refresh`
   ignorent désormais un tirage quand une requête court déjà (la fiche le
   faisait déjà).
4. **Alerte : rafraîchissement forcé d'un prélèvement déjà en cache.**
   `HubEauAlertChecker` ne force le rafraîchissement que si le cache ne
   contient pas déjà un prélèvement au moins aussi récent que celui annoncé.

## Non corrigé, à arbitrer

- **Annulation non propagée au réseau.** `HubEauClient.get` exécute
  `call.execute()` (bloquant) dans `withContext(Dispatchers.IO)` : quitter un
  écran pendant un chargement, ou changer de fenêtre en cours de requête,
  laisse la requête aller au bout pour rien (5 000 lignes d'historique, jusqu'à
  une dizaine de secondes). Correction possible : `enqueue` +
  `suspendCancellableCoroutine` avec `call.cancel()` à l'annulation ; non faite
  ici car elle touche le client central (délais, distinction
  `Timeout`/`Network`, lecture bornée du corps) et mérite son propre lot.
- **Taille de page du dernier prélèvement (500 lignes).** Un gros prélèvement
  (Paris : 643 lignes) coûte deux pages ; 1 000 lignes l'éviteraient mais
  téléchargeraient plus de lignes de prélèvements plus anciens pour les petites
  communes. Compromis octets / requêtes, laissé tel quel.
- **Liste des UDI hors ligne.** Gardée en mémoire seulement : après la mort du
  processus, une commune déjà consultée ne se rouvre par la recherche qu'avec
  le réseau (la fiche, elle, reste lisible depuis « Mes communes »). Une table
  Room demanderait un changement de schéma.

## À vérifier par capture réseau sur appareil (PCAPdroid)

1. Rechercher une commune à plusieurs UDI, en choisir une, puis « Changer
   d'UDI » depuis la fiche : **un seul** `communes_udi`.
2. Ouvrir une fiche (un `resultats_dis` sans `date_min`, puis un sur
   `code_parametre=1302,1382,1449` avec `date_min` = aujourd'hui − 12 mois),
   puis la frise : la seconde requête de frise doit porter
   `date_max_prelevement` = aujourd'hui − 12 mois et `date_min` =
   aujourd'hui − 3 ans. Rouvrir la frise : aucune requête.
3. Graphique d'un paramètre : tirer deux fois vite, ou tirer puis toucher
   « Actualiser » : **une seule** requête.
4. Pivoter l'écran sur la fiche, la frise et le graphique : aucune requête.
5. Fermer et rouvrir la fiche le même jour : aucune requête ; le lendemain :
   une.
6. Alertes (`docs/test-alerts.md`) : une requête `size=1` par unité suivie ;
   pas de requête complète si la fiche a déjà montré le nouveau prélèvement.
