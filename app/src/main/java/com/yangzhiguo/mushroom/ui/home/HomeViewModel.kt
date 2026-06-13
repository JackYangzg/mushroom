package com.yangzhiguo.mushroom.ui.home

import androidx.lifecycle.ViewModel
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.data.repository.SpeciesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @Suppress("unused") speciesRepo: SpeciesRepository,
) : ViewModel() {

    /**
     * 之前订阅 `speciesRepo.observeAll()` 来提供"最常见的蘑菇"列表;
     * 但 `HomeScreen` 从未渲染 `recommendedSpecies`,该订阅在 app 启动 + 任何
     * 表写入时都会触发 `mushroom_species` 上的 Room InvalidationTracker + 重跑
     * 整个去重 SQL。直接保持 empty,等"最常见蘑菇"真要在 Home 落地时再单独接
     * 一个轻量查询。
     */
    val state: StateFlow<HomeUiState> = MutableStateFlow(HomeUiState.Empty).asStateFlow()
}

data class HomeUiState(
    val recommendedSpecies: List<SpeciesEntity>,
) {
    companion object {
        val Empty = HomeUiState(emptyList())
    }
}
