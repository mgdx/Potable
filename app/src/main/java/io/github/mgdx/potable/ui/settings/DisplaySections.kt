package io.github.mgdx.potable.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.mgdx.potable.R
import io.github.mgdx.potable.data.preferences.ThemeMode
import io.github.mgdx.potable.ui.common.SegmentedChoices

/** Light, dark or system theme (SPEC §5 F8). */
@Composable
internal fun AppearanceSection(themeMode: ThemeMode, onSelect: (ThemeMode) -> Unit) =
    SettingsCard(stringResource(R.string.settings_appearance_title)) {
        SegmentedChoices(
            choices = ThemeMode.entries,
            selected = themeMode,
            label = { stringResource(it.labelRes()) },
            onSelect = onSelect,
        )
    }

/** The history window shown by default (SPEC §5 F5). */
@Composable
internal fun HistorySection(years: Int, yearChoices: List<Int>, onSelect: (Int) -> Unit) =
    SettingsCard(stringResource(R.string.settings_history_title)) {
        Description(stringResource(R.string.settings_history_description))
        SegmentedChoices(
            choices = yearChoices,
            selected = years,
            label = { pluralStringResource(R.plurals.settings_years, it, it) },
            onSelect = onSelect,
        )
    }

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}
