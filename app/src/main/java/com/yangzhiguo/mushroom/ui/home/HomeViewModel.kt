package com.yangzhiguo.mushroom.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.data.repository.SpeciesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    speciesRepo: SpeciesRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = speciesRepo.observeAll()
        .map { all -> HomeUiState(recommendedSpecies = all.take(5)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Empty,
        )
}

data class HomeUiState(
    val recommendedSpecies: List<SpeciesEntity>,
) {
    companion object {
        val Empty = HomeUiState(emptyList())
    }
}
