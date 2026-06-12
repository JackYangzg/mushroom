package com.yangzhiguo.mushroom.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.domain.model.ToxicityLevel
import com.yangzhiguo.mushroom.domain.model.UseType

@Composable
fun SpeciesRow(
    species: SpeciesEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { MushroomIcon(size = 46.dp) }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(species.chineseName, style = MaterialTheme.typography.titleMedium)
                Text(
                    species.scientificName,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    species.habitat.substringBefore(",").ifBlank { "生境未记录" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RiskLabel(species)
        }
    }
}

@Composable
private fun RiskLabel(species: SpeciesEntity) {
    val isRisk = species.toxicityLevel == ToxicityLevel.TOXIC ||
        species.toxicityLevel == ToxicityLevel.DEADLY ||
        species.useType == UseType.CAUTION
    val label = when {
        species.toxicityLevel == ToxicityLevel.DEADLY -> "高风险"
        species.toxicityLevel == ToxicityLevel.TOXIC -> "有毒"
        species.useType == UseType.CAUTION -> "需谨慎"
        species.useType == UseType.EDIBLE -> "有食用记录"
        species.useType == UseType.MEDICINAL -> "药用记录"
        else -> "资料记录"
    }
    Surface(
        color = if (isRisk) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isRisk) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isRisk) {
                Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
