package com.yangzhiguo.mushroom.ui.species

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.data.repository.SpeciesRepository
import com.yangzhiguo.mushroom.domain.model.UseType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SpeciesListViewModel @Inject constructor(
    private val repo: SpeciesRepository,
) : ViewModel() {

    private val filter = MutableStateFlow<UseType?>(null)
    private val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SpeciesListUiState> = combine(filter, query) { f, q -> f to q }
        .flatMapLatest { (f, q) ->
            when {
                q.isNotBlank() -> repo.search(q)
                f != null -> repo.filterByUseType(f)
                else -> repo.observeAll()
            }
        }
        .combine(filter) { list, currentFilter ->
            SpeciesListUiState(
                filter = currentFilter,
                query = query.value,
                species = list,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SpeciesListUiState.Empty,
        )

    fun setFilter(f: UseType?) { filter.value = f }
    fun setQuery(q: String) { query.value = q }
}

data class SpeciesListUiState(
    val filter: UseType?,
    val query: String,
    val species: List<SpeciesEntity>,
) {
    companion object {
        val Empty = SpeciesListUiState(null, "", emptyList())
    }
}
