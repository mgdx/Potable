package io.github.mgdx.potable.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.R
import io.github.mgdx.potable.ui.common.SegmentedChoices
import io.github.mgdx.potable.ui.common.asApiText
import io.github.mgdx.potable.ui.common.bidiWrap
import io.github.mgdx.potable.ui.sample.currentLocale
import io.github.mgdx.potable.ui.sheet.formatFetchedDate
import java.time.ZoneId

/** Cache retention and the purge of the whole cache (SPEC §7). */
@Composable
internal fun StorageSection(
    retentionYears: Int,
    yearChoices: List<Int>,
    purge: PurgeState,
    onRetention: (Int) -> Unit,
    onPurge: () -> Unit,
) = SettingsCard(stringResource(R.string.settings_storage_title)) {
    Description(stringResource(R.string.settings_retention_description))
    SegmentedChoices(
        choices = yearChoices,
        selected = retentionYears,
        label = { pluralStringResource(R.plurals.settings_years, it, it) },
        onSelect = onRetention,
    )
    Description(stringResource(R.string.settings_clear_cache_description))
    OutlinedButton(
        onClick = onPurge,
        enabled = purge != PurgeState.RUNNING,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(stringResource(R.string.settings_clear_cache))
    }
}

/** The communes in the cache, each of which can be cleared alone (SPEC §7). */
@Composable
internal fun CachedCommunesSection(
    communes: List<CachedCommuneItem>,
    onClear: (CachedCommuneItem) -> Unit,
) = SettingsCard(stringResource(R.string.settings_cached_communes_title)) {
    CachedCommunes(communes, onClear)
}

/**
 * The communes in the cache (SPEC §7), each with a button that clears its
 * cache alone; a sentence when it is empty. The caller shows nothing until
 * the cache is read.
 */
@Composable
private fun CachedCommunes(
    communes: List<CachedCommuneItem>,
    onClear: (CachedCommuneItem) -> Unit,
) {
    if (communes.isEmpty()) {
        Description(stringResource(R.string.settings_cached_communes_empty))
        return
    }
    val locale = currentLocale()
    val context = LocalContext.current
    communes.forEach { commune ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = commune.label,
                    style = MaterialTheme.typography.bodyLarge.asApiText(),
                )
                commune.lastFetched?.let { fetched ->
                    val date = remember(fetched, locale) {
                        formatFetchedDate(fetched, locale, ZoneId.systemDefault())
                    }
                    Text(
                        text = stringResource(R.string.sheet_data_from, date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Localised by the system ("42 kB", "42 ko").
            Text(
                text = Formatter.formatShortFileSize(context, commune.sizeBytes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
            IconButton(onClick = { onClear(commune) }) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(
                        R.string.settings_clear_commune_description,
                        bidiWrap(commune.label),
                    ),
                )
            }
        }
    }
}
