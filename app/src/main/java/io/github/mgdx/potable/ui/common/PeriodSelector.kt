package io.github.mgdx.potable.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.R
import io.github.mgdx.potable.data.preferences.AppPreferences

/**
 * One choice among a few, as a Material 3 segmented button row spanning the
 * content width (16dp from the edges). Each segment is announced as a radio
 * button, selected or not. The selected segment carries no check mark: the
 * application shows no tick of its own (SPEC §14), the fill says the choice.
 */
@Composable
fun <T> SegmentedChoices(
    choices: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        choices.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = choice == selected,
                onClick = { onSelect(choice) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
                icon = {},
                label = { Text(label(choice)) },
            )
        }
    }
}

/** The window of a history, 1 to 10 years: one choice among [AppPreferences.YEAR_CHOICES]. */
@Composable
fun PeriodSelector(years: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SegmentedChoices(
        choices = AppPreferences.YEAR_CHOICES,
        selected = years,
        label = { pluralStringResource(R.plurals.settings_years, it, it) },
        onSelect = onSelect,
        modifier = modifier,
    )
}
