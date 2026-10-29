package io.github.mgdx.potable.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.R
import io.github.mgdx.potable.data.backup.BackupCodec
import io.github.mgdx.potable.data.backup.BackupError
import io.github.mgdx.potable.data.backup.BackupRead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/**
 * Export and import of the followed communes (the application keeps no
 * system backup): through the system file picker, without any permission.
 */
@Composable
internal fun BackupSection(running: Boolean, onExport: (Uri) -> Unit, onImport: (Uri) -> Unit) {
    val exporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON)) { uri ->
            uri?.let(onExport)
        }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(onImport)
        }
    SettingsCard(stringResource(R.string.settings_backup_title)) {
        Description(stringResource(R.string.settings_backup_description))
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { exporter.launch("potable-${LocalDate.now()}.json") },
                enabled = !running,
            ) {
                Text(stringResource(R.string.settings_backup_export))
            }
            OutlinedButton(
                // "*/*" too: some file managers type a JSON file as anything.
                onClick = { importer.launch(arrayOf(JSON, "*/*")) },
                enabled = !running,
            ) {
                Text(stringResource(R.string.settings_backup_import))
            }
        }
    }
}

/** The snackbar text of [message]. */
@Composable
internal fun backupMessageText(message: BackupMessage): String = when (message) {
    BackupMessage.Exported -> stringResource(R.string.settings_backup_exported)
    BackupMessage.ExportFailed -> stringResource(R.string.settings_backup_export_failed)
    is BackupMessage.ImportFailed -> stringResource(
        when (message.error) {
            BackupError.UNREADABLE -> R.string.settings_backup_unreadable
            BackupError.NOT_A_BACKUP -> R.string.settings_backup_not_backup
            BackupError.UNSUPPORTED_VERSION -> R.string.settings_backup_unsupported
            BackupError.TOO_LARGE -> R.string.settings_backup_too_large
        },
    )
    is BackupMessage.Imported -> {
        val summary = message.summary
        // One sentence per line, the zero counts after the first left out.
        listOfNotNull(
            pluralStringResource(
                R.plurals.settings_backup_imported,
                summary.imported,
                summary.imported,
            ),
            summary.alreadyFollowed.takeIf { it > 0 }?.let {
                pluralStringResource(R.plurals.settings_backup_already, it, it)
            },
            summary.invalid.takeIf { it > 0 }?.let {
                pluralStringResource(R.plurals.settings_backup_invalid, it, it)
            },
        ).joinToString("\n")
    }
}

/** Writes [text] to [uri], off the main thread. */
internal suspend fun ContentResolver.writeBackup(uri: Uri, text: String) =
    withContext(Dispatchers.IO) {
        val output = openOutputStream(uri, "wt") ?: throw IOException("no output")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

/** Reads the backup at [uri], bounded, off the main thread. */
internal suspend fun ContentResolver.readBackup(uri: Uri): BackupRead =
    withContext(Dispatchers.IO) {
        val input = openInputStream(uri) ?: throw IOException("no input")
        input.use(BackupCodec::read)
    }

private const val JSON = "application/json"
