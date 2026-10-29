package io.github.mgdx.potable.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.ui.common.SectionCard

/**
 * A radio group: each whole row is the touch target and is announced as a
 * radio button, selected or not; the button itself takes no click of its own.
 */
@Composable
internal fun <T> ChoiceGroup(
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        choices.forEach { choice ->
            val isSelected = choice == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(choice) },
                        role = Role.RadioButton,
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Text(
                    text = label(choice),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

/** A whole row that toggles a switch, announced as one switch with its title. */
@Composable
internal fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
                },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Material's opacity for disabled content. */
private const val DISABLED_ALPHA = 0.38f

/**
 * One section of the settings in a [SectionCard]. Its rows bring their own
 * 16dp horizontal padding (SegmentedChoices among them), so the card pads
 * vertically only and the title, padded the same, is drawn here rather than
 * by the card, which would set it against the card's edge. The rows keep
 * their own vertical rhythm, without the card's spacing between them.
 */
@Composable
internal fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionCard(contentPadding = PaddingValues(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .semantics { heading() },
        )
        Column(content = content)
    }
}

@Composable
internal fun Description(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
