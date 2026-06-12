package com.yangzhiguo.mushroom.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.domain.model.ToxicityLevel
import com.yangzhiguo.mushroom.domain.model.UseType
import com.yangzhiguo.mushroom.ui.theme.ScientificNameStyle
import com.yangzhiguo.mushroom.ui.theme.extended

/**
 * One row in the S11 species list and the S3 推荐区. Shows:
 *  - MushroomIcon (image placeholder — local assets not present in prototype)
 *  - Chinese name (bold), scientific name (italic gray)
 *  - Toxicity icon + season + habitat compact one-liner
 */
@Composable
fun SpeciesRow(
    species: SpeciesEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (tint, icon) = toxicityVisuals(species.toxicityLevel)
    val useLabel = when (species.useType) {
        UseType.EDIBLE -> "可食"
        UseType.MEDICINAL -> "可药用"
        UseType.POISONOUS -> "有毒"
        UseType.CAUTION -> "需谨慎"
        UseType.UNREPORTED -> "无报道"
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                MushroomIcon(size = 48.dp)
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = species.chineseName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(text = icon, style = MaterialTheme.typography.titleMedium, color = tint)
                }
                Text(text = species.scientificName, style = ScientificNameStyle)
                Text(
                    text = "$useLabel · ${species.season} · ${species.habitat}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun toxicityVisuals(level: ToxicityLevel): Pair<Color, String> = when (level) {
    ToxicityLevel.DEADLY -> MaterialTheme.colorScheme.error to "🚨"
    ToxicityLevel.TOXIC -> MaterialTheme.extended.warning to "⚠️"
    ToxicityLevel.MILD -> MaterialTheme.extended.neutral to "❌"
    ToxicityLevel.NONE -> MaterialTheme.extended.success to "✅"
}
