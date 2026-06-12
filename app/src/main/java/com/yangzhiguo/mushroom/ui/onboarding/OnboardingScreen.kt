package com.yangzhiguo.mushroom.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yangzhiguo.mushroom.R
import com.yangzhiguo.mushroom.ui.components.MushroomIcon
import com.yangzhiguo.mushroom.ui.components.PrimaryButton

/**
 * S2 Onboarding: 3-step pager + final agreement checkbox.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    var agreed by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDone) {
                    Text(stringResource(R.string.onboarding_skip), color = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                OnboardingPage(page = page)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == pagerState.currentPage) 10.dp else 8.dp)
                            .padding(horizontal = 4.dp)
                            .background(
                                color = if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            ),
                    )
                }
            }
            if (pagerState.currentPage == 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                    Text(
                        text = stringResource(R.string.onboarding_agreement),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                PrimaryButton(
                    text = if (pagerState.currentPage == 2) stringResource(R.string.onboarding_enter)
                    else stringResource(R.string.onboarding_next),
                    onClick = {
                        if (pagerState.currentPage == 2 && agreed) onDone()
                    },
                    enabled = pagerState.currentPage < 2 || agreed,
                )
            }
        }
    }
}

@Composable
private fun OnboardingPage(page: Int) {
    val (title, body) = when (page) {
        0 -> stringResource(R.string.onboarding_title_1) to stringResource(R.string.onboarding_body_1)
        1 -> stringResource(R.string.onboarding_title_2) to stringResource(R.string.onboarding_body_2)
        else -> stringResource(R.string.onboarding_title_3) to stringResource(R.string.onboarding_body_3)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MushroomIcon(size = 120.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}
