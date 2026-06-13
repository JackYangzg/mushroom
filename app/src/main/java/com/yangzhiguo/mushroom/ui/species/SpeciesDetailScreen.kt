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
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
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
                        CompleteSpeciesInformation(
                            species = current,
                            identificationPoints = points,
                        )
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

@Composable
private fun CompleteSpeciesInformation(
    species: SpeciesEntity,
    identificationPoints: List<String>,
) {
    InformationSection(
        "命名与分类",
        listOf(
            "中文名" to species.chineseName,
            "拉丁名" to species.scientificName,
            "命名人" to species.authority,
            "俗名" to species.speciesCommon,
            "野外鉴定名" to species.fieldIdentification,
            "界" to bilingual(species.communityZh, species.communityLa),
            "门" to bilingual(species.phylumZh, species.phylumLa),
            "纲" to bilingual(species.classZh, species.classLa),
            "目" to bilingual(species.orderZh, species.orderLa),
            "亚目" to bilingual(species.suborderZh, species.suborderLa),
            "科" to bilingual(species.familyZh, species.familyLa),
            "亚科" to bilingual(species.subfamilyZh, species.subfamilyLa),
            "属" to bilingual(species.genusZh, species.genusLa),
            "亚属" to bilingual(species.subgenusZh, species.subgenusLa),
            "组/节" to bilingual(species.sectionZh, species.sectionLa),
            "拉丁属名" to species.speciesLatinGenus,
            "种加词" to species.specificEpithet,
        ),
    )

    InformationSection(
        "形态描述",
        listOf(
            "综合描述" to species.speciesDescription,
            "菌盖" to species.capDescription,
            "菌盖菌肉" to species.capContext,
            "菌褶（管）" to species.lamellaDescription,
            "菌柄" to species.stipeDescription,
            "菌柄菌肉" to species.stipeContext,
            "菌环" to species.ringDescription,
            "菌托" to species.volvaDescription,
            "气味" to species.odor,
            "孢子" to species.sporeDescription,
            "描述参考资料" to species.descriptionReference,
        ),
    )

    if (identificationPoints.isNotEmpty()) {
        DetailSection("辨识要点") {
            identificationPoints.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
        }
    }

    InformationSection(
        "用途与营养类型",
        listOf(
            "食用真菌" to species.edibleFungus,
            "药用真菌" to species.medicinalFungus,
            "有毒真菌" to species.toxicFungus,
            "条件性食用/有毒" to species.conditionallyFungus,
            "共生真菌" to species.mycorrhizalFungus,
            "腐生真菌" to species.saprophyticFungus,
            "寄生真菌" to species.parasiticFungus,
            "经济用途" to species.economicUse,
            "用途参考资料" to species.purposeReferences,
            "毒性与症状" to species.toxicitySymptoms,
        ),
    )

    InformationSection(
        "生态与分布",
        listOf(
            "营养习性和生境" to species.habitat,
            "基质" to species.substrate,
            "伴生树种" to species.treeSpecies,
            "气候带" to species.climateZone,
            "热带物种" to species.tropicalSpecies,
            "亚热带物种" to species.subtropicalSpecies,
            "温带/亚高山物种" to species.temperateSpecies,
            "西南特有" to species.southwestSpecific,
            "云南特有" to species.yunnanSpecific,
            "西南分布" to species.isSouthwest,
            "西藏分布" to species.isXizang,
            "四川/重庆分布" to species.isSichuan,
            "贵州分布" to species.isGuizhou,
            "高黎贡山名录" to species.isGaoligong,
            "云南分布" to species.isYunnan,
            "分布地点" to species.distributionLocation,
            "海拔范围" to species.altitudeRange,
            "季节" to species.season,
            "生境参考资料" to species.habitReferences,
        ),
    )

    InformationSection(
        "名录与审核",
        listOf(
            "红色名录等级" to species.directoryGrade,
            "名录参考资料" to species.directoryReferences,
        ),
    )

    InformationSection(
        "数据来源",
        listOf(
            "来源页面" to species.sourceUrl,
            "来源分类" to species.sourceTypes,
            "抓取来源" to species.scrawSource,
            "蘑菇业务 ID" to species.mushroomId.takeIf { it > 0 },
            "最后更新" to species.lastUpdated,
            "三维模型" to species.model3dUrl,
        ),
    )
}

@Composable
private fun InformationSection(title: String, rows: List<Pair<String, Any?>>) {
    val visibleRows = rows.filter { displayValue(it.second) != null }
    if (visibleRows.isEmpty()) return
    DetailSection(title) { InformationRows(visibleRows) }
}

@Composable
private fun InformationRows(rows: List<Pair<String, Any?>>) {
    rows.forEach { (label, rawValue) ->
        displayValue(rawValue)?.let { FeatureLine(label, it) }
    }
}

private fun displayValue(value: Any?): String? = when (value) {
    null -> null
    is String -> value.trim().takeIf { it.isNotEmpty() && it != "[]" }
    else -> value.toString()
}

private fun bilingual(chinese: String?, latin: String?): String? =
    listOfNotNull(
        chinese?.trim()?.takeIf { it.isNotEmpty() },
        latin?.trim()?.takeIf { it.isNotEmpty() },
    ).distinct().joinToString("（", postfix = if (!chinese.isNullOrBlank() && !latin.isNullOrBlank()) "）" else "")
        .takeIf { it.isNotBlank() }

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
