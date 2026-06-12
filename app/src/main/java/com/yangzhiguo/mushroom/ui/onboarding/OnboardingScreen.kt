package com.yangzhiguo.mushroom.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var agreed by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            MushroomIcon(size = 104.dp)
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.onboarding_title_1),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_body_1),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("拍摄建议", style = MaterialTheme.typography.titleMedium)
                    Text("保持画面清晰，并拍到菌盖、菌褶和菌柄。", style = MaterialTheme.typography.bodyMedium)
                    Text("照片会用于本次识别；识别结果不能替代专业鉴定。", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                Text(
                    text = stringResource(R.string.onboarding_agreement),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(R.string.onboarding_enter),
                onClick = onDone,
                enabled = agreed,
            )
        }
    }
}
