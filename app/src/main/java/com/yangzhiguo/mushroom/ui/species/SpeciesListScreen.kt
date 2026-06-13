package com.yangzhiguo.mushroom.ui.species

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangzhiguo.mushroom.R
import com.yangzhiguo.mushroom.domain.model.UseType
import com.yangzhiguo.mushroom.ui.components.SpeciesRow

@Composable
fun SpeciesListScreen(
    onSpeciesClick: (Int) -> Unit,
    viewModel: SpeciesListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filters = listOf(
        null to R.string.species_filter_all,
        UseType.EDIBLE to R.string.species_filter_edible,
        UseType.MEDICINAL to R.string.species_filter_medicinal,
        UseType.POISONOUS to R.string.species_filter_poisonous,
        UseType.CAUTION to R.string.species_filter_caution,
    )

    // 用 TextFieldValue 携带 selection 信息，避免 VM 回推 state.query 时
    // 触发重组导致光标被重置到开头。IME 输入时直接保留 IME 给的 selection。
    var queryField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue("", TextRange(0)))
    }
    LaunchedEffect(state.query) {
        if (queryField.text != state.query) {
            // 仅在 VM 端外部更新 query 时同步（光标放到末尾）
            queryField = TextFieldValue(state.query, TextRange(state.query.length))
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = stringResource(R.string.species_list_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            )
            OutlinedTextField(
                value = queryField,
                onValueChange = { newValue ->
                    queryField = newValue  // 保留 IME 推过来的 selection（光标跟随输入）
                    viewModel.setQuery(newValue.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text(stringResource(R.string.species_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filters) { (value, label) ->
                    FilterChip(
                        selected = state.filter == value,
                        onClick = { viewModel.setFilter(value) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            Text(
                text = "${state.species.size} 条记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            ) {
                items(state.species, key = { it.mushroomId }) { species ->
                    SpeciesRow(species = species, onClick = { onSpeciesClick(species.mushroomId) })
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
