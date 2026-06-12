package com.yangzhiguo.mushroom.ui.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangzhiguo.mushroom.R
import com.yangzhiguo.mushroom.ui.components.MushroomIcon

/**
 * S1 Splash. Loads the 100-species seed once on first launch (1-2s), then
 * routes to Onboarding (first launch) or MainShell (returning). On failure
 * shows a Retry button per `design_doc §6`.
 */
@Composable
fun SplashScreen(
    onReady: (firstLaunch: Boolean) -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        (state as? SplashViewModel.State.Ready)?.let { onReady(it.firstLaunch) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val s = state) {
                SplashViewModel.State.Loading -> Loading()
                is SplashViewModel.State.Error -> ErrorState(message = s.message, onRetry = viewModel::retry)
                is SplashViewModel.State.Ready -> Loading() // transition handled by LaunchedEffect
            }
        }
    }
}

@Composable
private fun Loading() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MushroomIcon(size = 96.dp)
        Text(
            text = stringResource(R.string.splash_brand_zh),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.splash_brand_en),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(8.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
        )
        Text(
            text = stringResource(R.string.splash_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(24.dp),
    ) {
        MushroomIcon(size = 72.dp)
        Text(
            text = stringResource(R.string.splash_error),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Button(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_retry))
        }
    }
}
