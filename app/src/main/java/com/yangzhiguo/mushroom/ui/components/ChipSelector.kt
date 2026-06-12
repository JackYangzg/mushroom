package com.yangzhiguo.mushroom.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipSelector(
    items: List<String>,
    selected: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    multiSelect: Boolean = false,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val isSelected = item in selected
            FilterChip(
                selected = isSelected,
                onClick = {
                    onSelectionChange(
                        if (multiSelect) {
                            if (isSelected) selected - item else selected + item
                        } else {
                            if (isSelected) emptySet() else setOf(item)
                        },
                    )
                },
                label = { Text(item, style = MaterialTheme.typography.labelLarge) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}
