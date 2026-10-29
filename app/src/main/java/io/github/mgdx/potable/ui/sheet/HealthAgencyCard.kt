package io.github.mgdx.potable.ui.sheet

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.RegionalHealthAgencies
import io.github.mgdx.potable.ui.common.SectionCard
import io.github.mgdx.potable.ui.common.asApiText

/**
 * Whom to ask about this water (SPEC §5 F3, §14): the regional health agency
 * of the commune ([RegionalHealthAgencies]) for a question about health, with
 * a link to its website opened in the browser, and the distributor published
 * by Hub'Eau for a question about the service. Facts only: no advice, no
 * verdict. Not shown when neither is known.
 */
@Composable
internal fun HealthAgencyCard(communeCode: String, distributor: String?) {
    val agency = RegionalHealthAgencies.forCommune(communeCode)
    val service = distributor?.takeIf { it.isNotBlank() }
    if (agency == null && service == null) return
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val linkFailed = stringResource(R.string.about_open_link_failed)
    SectionCard(title = stringResource(R.string.sheet_contacts_title)) {
        agency?.let {
            Column {
                Text(
                    text = stringResource(R.string.sheet_contacts_health),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = it.name, style = MaterialTheme.typography.bodyLarge.asApiText())
                val description =
                    stringResource(R.string.sheet_contacts_website_description, it.name)
                // Opens in the user's browser through ACTION_VIEW; the app itself loads nothing.
                TextButton(
                    onClick = {
                        try {
                            uriHandler.openUri(it.website)
                        } catch (_: IllegalArgumentException) {
                            Toast.makeText(context, linkFailed, Toast.LENGTH_SHORT).show()
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, linkFailed, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = description },
                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                ) {
                    Text(stringResource(R.string.sheet_contacts_website))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                }
            }
        }
        service?.let {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.sheet_contacts_service),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = it, style = MaterialTheme.typography.bodyLarge.asApiText())
            }
        }
    }
}
