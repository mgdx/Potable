package io.github.mgdx.potable.ui.about

import android.content.ActivityNotFoundException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.BuildConfig
import io.github.mgdx.potable.R
import io.github.mgdx.potable.ui.common.BackTopBar
import io.github.mgdx.potable.ui.common.SectionCard
import io.github.mgdx.potable.ui.disclaimer.DisclaimerPoints
import kotlinx.coroutines.launch

/** About (SPEC §5 F8): version, notice, sources, licenses. Static, no network. */
@Composable
fun AboutScreen(onBack: () -> Unit, onSettings: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val linkFailed = stringResource(R.string.about_open_link_failed)
    // Links open in the user's browser through ACTION_VIEW; nothing else.
    val openLink: (String) -> Unit = { url ->
        try {
            uriHandler.openUri(url)
        } catch (_: IllegalArgumentException) {
            scope.launch { snackbarHostState.showSnackbar(linkFailed) }
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar(linkFailed) }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { BackTopBar(title = stringResource(R.string.about_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header()
            Body(stringResource(R.string.about_presentation))
            ListItem(
                modifier = Modifier.clickable(onClick = onSettings),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_title)) },
            )

            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = stringResource(R.string.about_notice_title),
            ) {
                DisclaimerPoints(bulletColor = MaterialTheme.colorScheme.secondary)
            }

            // Vertical padding only: the links are ListItems, inset by themselves.
            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                CardHeading(stringResource(R.string.about_sources_title))
                Body(stringResource(R.string.about_source_hubeau))
                Link(
                    stringResource(R.string.about_source_hubeau_terms),
                    stringResource(R.string.url_hubeau_terms),
                    openLink,
                )
                Body(stringResource(R.string.about_source_communes))
                Link(
                    stringResource(R.string.about_source_communes_link),
                    stringResource(R.string.url_geo_api),
                    openLink,
                )
                Body(stringResource(R.string.about_source_cog))
            }

            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = stringResource(R.string.about_privacy_title),
            ) {
                Text(
                    text = stringResource(R.string.about_privacy),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            SectionTitle(stringResource(R.string.about_license_title))
            Body(stringResource(R.string.about_license))
            Link(
                stringResource(R.string.about_license_link),
                stringResource(R.string.url_gpl),
                openLink,
            )
            Link(
                stringResource(R.string.about_source_code_link),
                stringResource(R.string.url_repository),
                openLink,
            )

            SectionTitle(stringResource(R.string.about_donate_title))
            Body(stringResource(R.string.about_donate))
            Link(
                stringResource(R.string.about_donate_link),
                stringResource(R.string.url_donate),
                openLink,
            )

            SectionTitle(stringResource(R.string.about_libraries_title))
            stringArrayResource(R.array.about_libraries).forEach { library ->
                Text(
                    text = library,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/** The logo mark beside the name and the version. */
@Composable
private fun Header() {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Decorative: the name beside it says it all.
        Icon(
            painter = painterResource(R.drawable.ic_logo_mark),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
        )
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .semantics { heading() },
    )
}

/**
 * The title of a card whose content has no horizontal padding, inset like the
 * title [SectionCard] draws itself.
 */
@Composable
private fun CardHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .semantics { heading() },
    )
}

/**
 * A link, opened by the browser: the label says where it goes, the trailing
 * icon that it leaves the app.
 */
@Composable
private fun Link(label: String, url: String, open: (String) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { open(url) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(label, color = MaterialTheme.colorScheme.primary) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
