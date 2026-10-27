package io.github.mgdx.potable.ui.disclaimer

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Surface {
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.disclaimer_accept))
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = stringResource(R.string.disclaimer_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            DisclaimerPoints()
        }
    }
}

/** The five points of SPEC §14, shared by the first-launch notice and About. */
@Composable
fun DisclaimerPoints(
    modifier: Modifier = Modifier,
    bulletColor: Color = MaterialTheme.colorScheme.primary,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(
            R.string.disclaimer_point_source,
            R.string.disclaimer_point_freshness,
            R.string.disclaimer_point_scope,
            R.string.disclaimer_point_no_advice,
            R.string.disclaimer_point_no_score,
        ).forEach { point ->
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
