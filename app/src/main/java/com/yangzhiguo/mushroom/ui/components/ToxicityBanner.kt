package com.yangzhiguo.mushroom.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yangzhiguo.mushroom.domain.model.ToxicityLevel
import com.yangzhiguo.mushroom.ui.theme.ScientificNameStyle
import com.yangzhiguo.mushroom.ui.theme.extended

/**
 * The result header banner. 4 visual variants matching `design_doc §3 S9`:
 *  - DEADLY    (toxicity_level 3) → error container (deep red) + 🚨
 *  - TOXIC     (2)                 → warning container (amber) + ⚠
 *  - INEDIBLE  / MILD (1)         → neutral container (gray) + ❌
 *  - EDIBLE / NONE (0)            → success container (green) + ✅
 *
 * Color is dual-channel (icon + text) so it's color-blind safe per design §0.
 */
@Composable
fun ToxicityBanner(
    level: ToxicityLevel,
    chineseName: String,
    scientificName: String,
    modifier: Modifier = Modifier,
) {
    val label: String
    val advice: String
    val icon = if (level == ToxicityLevel.NONE) Icons.Rounded.Info else Icons.Rounded.Warning
    val container: androidx.compose.ui.graphics.Color
    val onContainer: androidx.compose.ui.graphics.Color
    when (level) {
        ToxicityLevel.DEADLY -> {
            label = "高风险：可能致命"
            advice = "严禁采食，接触或误食后请立即寻求专业帮助。"
            container = MaterialTheme.colorScheme.errorContainer
            onContainer = MaterialTheme.colorScheme.onErrorContainer
        }
        ToxicityLevel.TOXIC -> {
            label = "毒性风险"
            advice = "不要采食，仅凭照片不能排除更危险的近似种。"
            container = MaterialTheme.extended.warningContainer
            onContainer = MaterialTheme.extended.onWarningContainer
        }
        ToxicityLevel.MILD -> {
            label = "需谨慎"
            advice = "缺少可靠食用结论，不建议采食。"
            container = MaterialTheme.extended.neutralContainer
            onContainer = MaterialTheme.extended.onNeutralContainer
        }
        ToxicityLevel.NONE -> {
            label = "有食用记录"
            advice = "不代表照片中的个体可安全食用，请勿据此采食野生蘑菇。"
            container = MaterialTheme.extended.successContainer
            onContainer = MaterialTheme.extended.onSuccessContainer
        }
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = onContainer)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = label, style = MaterialTheme.typography.titleLarge, color = onContainer)
                Text(
                    text = advice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer,
                )
                Text(
                    text = "$chineseName · $scientificName",
                    style = ScientificNameStyle.copy(color = onContainer.copy(alpha = 0.72f)),
                )
            }
        }
    }
}
