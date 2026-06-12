package com.yangzhiguo.mushroom.ui.species

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Divider
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

@Composable
fun SpeciesDetailScreen(
    speciesId: Int,
    onBack: () -> Unit,
    viewModel: SpeciesDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(speciesId) { viewModel.load(speciesId) }
    val species by viewModel.state.collectAsStateWithLifecycle()
    val imageFile by viewModel.imageFile.collectAsStateWithLifecycle()

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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (imageFile != null && imageFile!!.exists()) {
                            AsyncImage(
                                model = imageFile,
                                contentDescription = current.chineseName,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            MushroomIcon(size = 120.dp, capColor = Color.White, stemColor = Color.LightGray)
                        }
                    }
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
                            chineseName = current.chineseName,
                            scientificName = current.scientificName,
                        )
                        DetailSection("如何辨认") {
                            points.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
                            if (points.isEmpty()) Text("暂无辨识要点", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DetailSection("形态特征") {
                            FeatureLine("菌盖", current.capDescription)
                            FeatureLine("菌褶", current.gillDescription)
                            FeatureLine("菌柄", current.stipeDescription)
                            FeatureLine("菌环", current.ringDescription)
                            FeatureLine("菌托", current.volvaDescription)
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
                            "仅供科普，不能作为采食建议。数据来源：${current.sourceUrl.ifBlank { "iflora.cn" }}",
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
