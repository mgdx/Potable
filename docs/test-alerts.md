# Potable — tester les alertes sur appareil

Outils et commandes pour vérifier les alertes (SPEC §5 F7) sur un appareil.
Les outils n'existent que dans le **build debug** :
`DebugAlertsActivity` (`app/src/debug/`), déclarée dans
`app/src/debug/AndroidManifest.xml`, absente du manifeste et du code de la
release. Elle exige `android.permission.DUMP`, que détient `adb shell` et
qu'aucune application tierce ne peut obtenir : seules les commandes ci-dessous
l'atteignent.

```bash
PKG=io.github.mgdx.potable
./gradlew :app:installDebug
```

## Outils de debug

| Commande | Effet |
|---|---|
| `adb shell am start -n $PKG/.debug.DebugAlertsActivity --es action age` | Recule d'un jour la date « dernier prélèvement vu » de chaque suivi (1970 s'il n'y en a pas encore) : à la vérification suivante, le dernier prélèvement est « nouveau ». |
| `adb shell am start -n $PKG/.debug.DebugAlertsActivity --es action run` | Exécute la vérification tout de suite, une fois (`OneTimeWorkRequest` de `AlertWorker`, sans contrainte réseau ; une unité injoignable est sautée). |
| `adb logcat -s PotableDebug` | Résultat des deux commandes ci-dessus. |

L'activité n'affiche rien et se termine aussitôt. La vérification ne fait
rien si aucune alerte n'est activée dans les Réglages, ni si les
notifications de l'appli ne peuvent pas être affichées.

## Scénarios

**Installation neuve.**
```bash
adb uninstall $PKG; ./gradlew :app:installDebug
adb shell am start -n $PKG/.MainActivity
adb shell dumpsys jobscheduler | grep -A3 "$PKG"     # aucune tâche alert-check
adb shell dumpsys package $PKG | grep POST_NOTIFICATIONS   # granted=false
```
Aucune demande de permission au lancement ; Réglages → Alerts : les deux
interrupteurs sont désactivés.

**Permission à l'activation (Android 13+).** Réglages → Alerts →
« New analyses » : la demande système apparaît à ce moment. Refuser :
l'interrupteur reste désactivé, une explication et « Open notification
settings » s'affichent. Pour rejouer la demande :
```bash
adb shell pm revoke $PKG android.permission.POST_NOTIFICATIONS
adb shell pm clear-permission-flags $PKG android.permission.POST_NOTIFICATIONS user-set user-fixed
```
Permission révoquée alors qu'une alerte est active (`pm revoke` puis retour
sur les Réglages) : le message « Notifications are turned off… » s'affiche.

**Tâche planifiée.** Une alerte active, puis :
```bash
adb shell dumpsys jobscheduler | grep -B2 -A25 "$PKG/androidx.work.impl.background.systemjob.SystemJobService"
```
On y lit la contrainte réseau (`CONNECTIVITY`) et la période (1, 2 ou
4 semaines selon le réglage ; WorkManager confie à JobScheduler une
échéance par exécution). Désactiver les deux alertes : la tâche disparaît.

**Notification « nouvelle analyse ».** Suivre au moins une commune,
activer « New analyses », attendre quelques secondes (ligne de base), puis :
```bash
adb shell am start -n $PKG/.debug.DebugAlertsActivity --es action age
adb shell am start -n $PKG/.debug.DebugAlertsActivity --es action run
adb logcat -s PotableDebug WM-WorkerWrapper
```
Une notification par commune suivie : titre « commune », texte « New
analysis published: sample taken on <date> ». La toucher ouvre la fiche de
l'unité (appli fermée comme ouverte) ; le retour revient à l'écran de départ.

**« Clear cache » garde la ligne de base.** Une commune suivie, « New
analyses » activé, ligne de base posée ; `age`, puis Réglages → « Clear
cache » (confirmer), puis `run` : la notification arrive quand même. La
date « dernier prélèvement vu » des unités suivies survit à la purge
(seule la date de dernière requête est effacée) ; sans elle, la vérification
reprendrait une ligne de base et n'annoncerait rien.
Couvert par `CachingWaterRepositoryTest`.

**« Unfavourable only ».** Désactiver « New analyses », activer
« Unfavourable conclusions only », refaire `age` puis `run` : aucune
notification pour une commune dont le dernier prélèvement est conforme ; une
notification (conclusion publiée, en français) pour un prélèvement non
conforme. La règle est couverte par `AlertRulesTest` et `AlertRunTest`.

**Manifeste.**
```bash
./gradlew :app:processReleaseManifest
grep -n 'permission\|service' app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml
```
Permissions attendues : `INTERNET`, `ACCESS_COARSE_LOCATION`,
`POST_NOTIFICATIONS`, et celles de WorkManager (`WAKE_LOCK`,
`ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`) ; aucune
`FOREGROUND_SERVICE`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` ni
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` ; pas de `SystemForegroundService`, pas
de `DebugAlertsActivity`.
