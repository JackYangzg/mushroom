package com.yangzhiguo.mushroom.ui.species

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yangzhiguo.mushroom.cache.ImageCacheRepository
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.data.repository.SpeciesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SpeciesDetailViewModel @Inject constructor(
    private val repo: SpeciesRepository,
    private val imageCache: ImageCacheRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SpeciesEntity?>(null)
    val state: StateFlow<SpeciesEntity?> = _state.asStateFlow()

    private val _imageFile = MutableStateFlow<File?>(null)
    val imageFile: StateFlow<File?> = _imageFile.asStateFlow()

    fun load(id: Int) {
        viewModelScope.launch {
            val sp = repo.findById(id)
            _state.value = sp
            // 触发图片懒加载：先看 DB imageLocalPath，命中则直接返回；否则远端拉
            _imageFile.value = imageCache.getOrFetch(id, sp?.imageUrl)
        }
    }

    fun toggleFavorite() {
        val current = _state.value ?: return
        viewModelScope.launch {
            repo.toggleFavorite(current.id)
            // 重新拉取,让 isFavorite 通过 state 流回 UI
            _state.value = repo.findById(current.id)
        }
    }
}
