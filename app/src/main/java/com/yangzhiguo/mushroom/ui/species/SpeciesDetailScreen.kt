package com.yangzhiguo.mushroom.ui.species

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Divider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yangzhiguo.mushroom.R
import com.yangzhiguo.mushroom.ui.components.MushroomIcon
import com.yangzhiguo.mushroom.ui.components.ToxicityBanner
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeciesDetailScreen(
    speciesId: Int,
    onBack: () -> Unit,
    viewModel: SpeciesDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(speciesId) { viewModel.load(speciesId) }
    val species by viewModel.state.collectAsStateWithLifecycle()
    val imageFiles by viewModel.imageFiles.collectAsStateWithLifecycle()
    val imageLoading by viewModel.imageLoading.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
                Text("物种详情", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                species?.let { current ->
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            if (current.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = stringResource(R.string.profile_my_favorites),
                            tint = if (current.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            species?.let { current ->
                val points = remember(current.identificationPoints) {
                    runCatching {
                        Json.decodeFromString(ListSerializer(String.serializer()), current.identificationPoints)
                    }.getOrDefault(emptyList())
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SpeciesGallery(
                        speciesName = current.chineseName,
                        imageFiles = imageFiles,
                        loading = imageLoading,
                    )
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(current.chineseName, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                current.scientificName,
                                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ToxicityBanner(
                            level = current.toxicityLevel,
                            edibility = current.edibility,
                            useType = current.useType,
                            chineseName = current.chineseName,
                            scientificName = current.scientificName,
                        )
                        DetailSection("如何辨认") {
                            points.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
                            if (points.isEmpty()) Text("暂无辨识要点", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DetailSection("形态特征") {
                            FeatureLine("菌盖", current.capDescription.orEmpty())
                            FeatureLine("菌褶", current.lamellaDescription.orEmpty())
                            FeatureLine("菌柄", current.stipeDescription.orEmpty())
                            FeatureLine("菌环", current.ringDescription.orEmpty())
                            FeatureLine("菌托", current.volvaDescription.orEmpty())
                        }
                        DetailSection("生境与季节") {
                            FeatureLine(stringResource(R.string.species_habitat), current.habitat)
                            FeatureLine(stringResource(R.string.species_season), current.season)
                        }
                        if (current.toxicitySymptoms.isNotBlank()) {
                            DetailSection("毒性与症状") {
                                Text(current.toxicitySymptoms, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        Divider()
                        Text(
                            "仅供科普，不能作为采食建议。物种资料来自 iFlora，图片优先来自 iFlora，缺失时来自 iNaturalist。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeciesGallery(
    speciesName: String,
    imageFiles: List<File>,
    loading: Boolean,
) {
    val pagerState = rememberPagerState(pageCount = { imageFiles.size.coerceAtLeast(1) })
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
        ) { page ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    imageFiles.isNotEmpty() -> AsyncImage(
                        model = imageFiles[page],
                        contentDescription = "$speciesName 图片 ${page + 1}/${imageFiles.size}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    loading -> CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                    )
                    else -> MushroomIcon(
                        size = 120.dp,
                        capColor = Color.White,
                        stemColor = Color.LightGray,
                    )
                }
            }
        }
        if (imageFiles.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                imageFiles.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun FeatureLine(label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
