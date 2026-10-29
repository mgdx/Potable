package io.github.mgdx.potable.ui.settings

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.mgdx.potable.R
import io.github.mgdx.potable.alert.NotificationAccess
import io.github.mgdx.potable.core.model.Commune
import io.github.mgdx.potable.data.CommuneRepository
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.ui.ComposeHostRegistration
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.FakeWaterRepository
import io.github.mgdx.potable.ui.follow
import io.github.mgdx.potable.ui.string
import io.github.mgdx.potable.ui.theme.PotableTheme
import io.github.mgdx.potable.ui.waitForText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** The settings (SPEC §5 F8): backup actions, and when the alerts last checked. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {
    @get:Rule(order = 0)
    val host = ComposeHostRegistration()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val checkedAt = Instant.parse("2026-10-24T06:30:00Z")

    /** The stored settings: the new analyses alert as given, one past check. */
    private class Preferences(alertOn: Boolean, checkedAt: Instant) : AppPreferences {
        override val disclaimerAccepted: Flow<Boolean> = flowOf(true)

        override suspend fun acceptDisclaimer() = Unit

        override val alertNewAnalysis = MutableStateFlow(alertOn)
        override val lastAlertCheck = MutableStateFlow<Instant?>(checkedAt)
    }

    private val communes = object : CommuneRepository {
        override suspend fun search(query: String, limit: Int): List<Commune> = emptyList()

        override suspend fun nearest(
            latitude: Double,
            longitude: Double,
            maxDistanceKm: Double?,
        ): Commune? = null
    }

    private val notifications = object : NotificationAccess {
        override fun canPost(): Boolean = true

        override val canRequest: Boolean = true
    }

    private fun show(alertOn: Boolean, followed: Boolean) {
        val viewModel = SettingsViewModel(
            Preferences(alertOn, checkedAt),
            FakeWaterRepository(),
            notifications,
            communes,
            FakeFollowRepository(if (followed) listOf(follow(1)) else emptyList()),
        )
        compose.setContent {
            PotableTheme {
                SettingsScreen(viewModel = viewModel, onBack = {}, onSearch = {})
            }
        }
        compose.waitForText(string(R.string.settings_backup_title))
    }

    private val lastCheckText: String
        get() = string(
            R.string.settings_alert_last_check,
            formatCheckTime(checkedAt, Locale.getDefault(), ZoneId.systemDefault()),
        )

    @Test
    fun `the backup section offers its export and its import`() {
        show(alertOn = false, followed = false)

        compose.onNodeWithText(string(R.string.settings_backup_export))
            .assertHasClickAction()
            .assertIsEnabled()
        compose.onNodeWithText(string(R.string.settings_backup_import))
            .assertHasClickAction()
            .assertIsEnabled()
    }

    @Test
    fun `the last check shows while an alert is on and a commune is followed`() {
        show(alertOn = true, followed = true)

        compose.waitForText(lastCheckText)
    }

    @Test
    fun `no last check without a followed commune`() {
        show(alertOn = true, followed = false)

        compose.onNodeWithText(string(R.string.settings_alerts_no_follows)).assertExists()
        compose.onNodeWithText(lastCheckText).assertDoesNotExist()
    }
}
