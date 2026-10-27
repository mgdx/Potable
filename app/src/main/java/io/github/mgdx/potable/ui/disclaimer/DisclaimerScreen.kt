package io.github.mgdx.potable.ui.disclaimer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.R

/**
 * Shows the first-launch notice (SPEC §14) until it is acknowledged, then
 * [content]. While the preference is being read, only the background shows.
 */
@Composable
fun DisclaimerGate(container: AppContainer, content: @Composable () -> Unit) {
    val viewModel: DisclaimerViewModel = viewModel(factory = DisclaimerViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state) {
        DisclaimerState.UNKNOWN -> Surface(modifier = Modifier.fillMaxSize()) {}
        DisclaimerState.REQUIRED -> DisclaimerScreen(onAccept = viewModel::accept)
        DisclaimerState.ACCEPTED -> content()
    }
}

@Composable
private fun DisclaimerScreen(onAccept: () -> Unit) {
    // Two pages: the SPEC §14 notice, then what the alerts do (SPEC §5 F7).
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = page > 0) { page-- }
    val isLast = page == PAGE_COUNT - 1
    val button = if (isLast) R.string.disclaimer_accept else R.string.disclaimer_next
    val title = if (page == 0) R.string.disclaimer_title else R.string.intro_alerts_title
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Surface {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PageIndicator(page = page, count = PAGE_COUNT)
                    Button(
                        onClick = { if (isLast) onAccept() else page++ },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(button))
                    }
                }
            }
        },
    ) { innerPadding ->
        // Keyed on the page so that the second one starts scrolled to the top.
        key(page) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                if (page == 0) DisclaimerPoints() else AlertPoints()
            }
        }
    }
}

private const val PAGE_COUNT = 2

/** One dot per page, the current one filled; read as "Page 1 of 2". */
@Composable
private fun PageIndicator(page: Int, count: Int) {
    val description = stringResource(R.string.intro_page_indicator, page + 1, count)
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (index == page) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        CircleShape,
                    ),
            )
        }
    }
}

/** What the alerts do, and that they stay off until turned on in the settings. */
@Composable
private fun AlertPoints() {
    BulletPoints(
        points = listOf(
            R.string.intro_alerts_point_follow,
            R.string.intro_alerts_point_notify,
            R.string.intro_alerts_point_off,
        ),
        bulletColor = MaterialTheme.colorScheme.primary,
    )
}

/** The five points of SPEC §14, shared by the first-launch notice and About. */
@Composable
fun DisclaimerPoints(
    modifier: Modifier = Modifier,
    bulletColor: Color = MaterialTheme.colorScheme.primary,
) {
    BulletPoints(
        points = listOf(
            R.string.disclaimer_point_source,
            R.string.disclaimer_point_freshness,
            R.string.disclaimer_point_scope,
            R.string.disclaimer_point_no_advice,
            R.string.disclaimer_point_no_score,
        ),
        bulletColor = bulletColor,
        modifier = modifier,
    )
}

@Composable
private fun BulletPoints(points: List<Int>, bulletColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        points.forEach { point ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // A plain dot: no tick nor shield (SPEC §14, no verdict iconography).
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(6.dp)
                        .background(bulletColor, CircleShape),
                )
                Text(text = stringResource(point), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
