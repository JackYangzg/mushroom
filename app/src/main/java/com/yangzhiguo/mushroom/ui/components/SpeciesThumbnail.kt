package com.yangzhiguo.mushroom.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.yangzhiguo.mushroom.cache.ImageCacheRepository
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SpeciesThumbnailViewModel @Inject constructor(
    private val imageCache: ImageCacheRepository,
) : ViewModel() {
    var imageFile by mutableStateOf<File?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    fun load(species: SpeciesEntity) {
        if (loading || imageFile != null) return
        loading = true
        viewModelScope.launch {
            try {
                imageFile = imageCache.getOrFetchThumbnail(
                    mushroomId = species.mushroomId,
                    remoteUrl = species.imageUrl,
                    scientificName = species.scientificName,
                    sourceUrl = species.sourceUrl,
                )
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun SpeciesThumbnail(
    species: SpeciesEntity,
    modifier: Modifier = Modifier,
    viewModel: SpeciesThumbnailViewModel = hiltViewModel(key = "species-thumbnail-${species.mushroomId}"),
) {
    LaunchedEffect(species.mushroomId) { viewModel.load(species) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val file = viewModel.imageFile
        when {
            file != null && file.exists() -> AsyncImage(
                model = file,
                contentDescription = species.chineseName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            viewModel.loading -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            else -> MushroomIcon(size = 46.dp)
        }
    }
}
