package com.yangzhiguo.mushroom.ui.profile

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
class MyFavoritesViewModel @Inject constructor(
    repo: SpeciesRepository,
) : ViewModel() {

    val state: StateFlow<MyFavoritesUiState> = repo.observeFavorites()
        .map { list -> MyFavoritesUiState(species = list) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MyFavoritesUiState.Empty,
        )
}

data class MyFavoritesUiState(
    val species: List<SpeciesEntity>,
) {
    companion object {
        val Empty = MyFavoritesUiState(emptyList())
    }
}
