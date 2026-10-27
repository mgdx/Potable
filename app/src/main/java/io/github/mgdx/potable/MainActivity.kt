package io.github.mgdx.potable

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.alert.AlertTarget
import io.github.mgdx.potable.alert.alertTarget
import io.github.mgdx.potable.alert.sanitized
import io.github.mgdx.potable.data.preferences.ThemeMode
import io.github.mgdx.potable.ui.PotableNavHost
import io.github.mgdx.potable.ui.disclaimer.DisclaimerGate
import io.github.mgdx.potable.ui.theme.PotableTheme
import io.github.mgdx.potable.ui.theme.isDark
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /**
     * Where an alert notification asked to go (a sheet, or "My communes"),
     * until the navigation opens it. Read once from the launching intent: a
     * recreated activity (rotation) keeps its back stack instead.
     */
    private val alertTarget = MutableStateFlow<AlertTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before the navigation graph exists: Navigation reads the activity's
        // intent, which any application can fill (audit V2). Also when
        // recreated, since the system hands the original intent back.
        intent?.let { setIntent(it.sanitized()) }
        if (savedInstanceState == null) alertTarget.value = intent?.alertTarget()
        // Both themes paint a dark canvas (the logo blue, or the night navy):
        // the system bar icons are light in both, whatever the device's theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(DARK_SCRIM),
        )
        val container = (application as PotableApplication).container
        setContent {
            // SYSTEM until the preference is read: the first frame matches the
            // window, which follows the device too.
            val themeMode by container.preferences.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val darkTheme = themeMode.isDark()
            PotableTheme(darkTheme = darkTheme) {
                DisclaimerGate(container) {
                    val target by alertTarget.collectAsStateWithLifecycle()
                    PotableNavHost(
                        container = container,
                        alertTarget = target,
                        onAlertTargetOpened = { alertTarget.value = null },
                    )
                }
            }
        }
    }

    /** A notification touched while the application is open (`FLAG_ACTIVITY_SINGLE_TOP`). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val clean = intent.sanitized()
        setIntent(clean)
        clean.alertTarget()?.let { alertTarget.value = it }
    }

    private companion object {
        // The dark scrim enableEdgeToEdge() uses by default behind a
        // three-button navigation bar (androidx.activity).
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
