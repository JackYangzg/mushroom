package com.yangzhiguo.mushroom.ui.species

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangzhiguo.mushroom.R
import com.yangzhiguo.mushroom.domain.model.UseType
import com.yangzhiguo.mushroom.ui.components.SpeciesRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesListScreen(
    onSpeciesClick: (Int) -> Unit,
    viewModel: SpeciesListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabs = listOf(
        null to R.string.species_filter_all,
        UseType.EDIBLE to R.string.species_filter_edible,
        UseType.MEDICINAL to R.string.species_filter_medicinal,
        UseType.POISONOUS to R.string.species_filter_poisonous,
        UseType.CAUTION to R.string.species_filter_caution,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.species_list_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text(stringResource(R.string.species_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
            )
            TabRow(selectedTabIndex = tabs.indexOfFirst { it.first == state.filter }.coerceAtLeast(0)) {
                tabs.forEach { (filterValue, labelRes) ->
                    Tab(
                        selected = state.filter == filterValue,
                        onClick = { viewModel.setFilter(filterValue) },
                        text = { Text(stringResource(labelRes)) },
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.species, key = { it.id }) { sp ->
                    SpeciesRow(species = sp, onClick = { onSpeciesClick(sp.id) })
                }
            }
        }
    }
}
