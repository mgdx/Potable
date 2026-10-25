package io.github.mgdx.potable

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PotableApplication : Application() {
    /** The application's object graph, built once per process. */
    lateinit var container: AppContainer

    /** Work that outlives any screen; it ends with the process. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // The channels exist before the first alert, so that the system's
        // notification settings, which the Settings screen links to, list them;
        // their names follow the language at each start.
        container.alertNotifier.createChannels()
        // Keeps the periodic alert check in line with the settings (SPEC §5 F7).
        container.alertScheduler.start(scope)
    }
}
