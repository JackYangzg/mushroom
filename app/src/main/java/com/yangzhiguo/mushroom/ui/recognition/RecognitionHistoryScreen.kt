package com.yangzhiguo.mushroom.ui.recognition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.yangzhiguo.mushroom.data.local.SpeciesDao
import com.yangzhiguo.mushroom.recognition.Candidate
import com.yangzhiguo.mushroom.recognition.RecognitionHistoryRecord
import com.yangzhiguo.mushroom.recognition.RecognitionHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class RecognitionHistoryViewModel @Inject constructor(
    private val repository: RecognitionHistoryRepository,
    private val speciesDao: SpeciesDao,
) : ViewModel() {
    val records: StateFlow<List<RecognitionHistoryRecord>> = repository.records

    private val _candidateSpeciesIds = MutableStateFlow<Map<String, Int>>(emptyMap())
    val candidateSpeciesIds: StateFlow<Map<String, Int>> = _candidateSpeciesIds.asStateFlow()

    fun find(id: String): RecognitionHistoryRecord? = repository.find(id)

    fun resolveCandidateIds(record: RecognitionHistoryRecord) {
        viewModelScope.launch {
            _candidateSpeciesIds.value = record.result.candidates.mapNotNull { candidate ->
                speciesDao.findBestNameMatch(
                    scientificName = candidate.scientificName.trim(),
                    commonName = candidate.commonName.orEmpty().trim(),
                )?.let {
                    candidate.scientificName to it.id
                }
            }.toMap()
        }
    }
}

@Composable
fun RecognitionHistoryScreen(
    onBack: () -> Unit,
    onRecordClick: (String) -> Unit,
    viewModel: RecognitionHistoryViewModel = hiltViewModel(),
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { HistoryTopBar(title = "识别历史", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (records.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(16.dp))
                Text("还没有识别记录", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "完成一次拍照识别后，结果会自动保存在这里。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {
                items(records, key = { it.id }) { record ->
                    HistoryRow(record = record, onClick = { onRecordClick(record.id) })
                    Divider()
                }
            }
        }
    }
}

@Composable
fun RecognitionHistoryDetailScreen(
    historyId: String,
    onBack: () -> Unit,
    onOpenSpecies: (Int) -> Unit,
    viewModel: RecognitionHistoryViewModel = hiltViewModel(),
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val candidateSpeciesIds by viewModel.candidateSpeciesIds.collectAsStateWithLifecycle()
    val record = remember(records, historyId) { viewModel.find(historyId) }

    LaunchedEffect(record?.id) {
        record?.let(viewModel::resolveCandidateIds)
    }

    Scaffold(
        topBar = { HistoryTopBar(title = "识别记录", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (record == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("这条识别记录不存在或已损坏")
            }
        } else {
            HistoryDetailContent(
                record = record,
                candidateSpeciesIds = candidateSpeciesIds,
                onOpenSpecies = onOpenSpecies,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun HistoryRow(
    record: RecognitionHistoryRecord,
    onClick: () -> Unit,
) {
    val top = record.result.candidates.firstOrNull()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.size(68.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            record.photoPaths.firstOrNull()?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                top?.commonName ?: top?.scientificName ?: "未命名识别",
                style = MaterialTheme.typography.titleMedium,
            )
            if (!top?.commonName.isNullOrBlank()) {
                Text(
                    top?.scientificName.orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                formatHistoryTime(record.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun HistoryDetailContent(
    record: RecognitionHistoryRecord,
    candidateSpeciesIds: Map<String, Int>,
    onOpenSpecies: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var thinkingExpanded by remember(record.id) { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            formatHistoryTime(record.createdAt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(record.photoPaths, key = { it }) { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = "识别照片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
            }
        }
        Text("识别候选", style = MaterialTheme.typography.titleMedium)
        record.result.candidates.forEachIndexed { index, candidate ->
            HistoryCandidateRow(
                number = index + 1,
                candidate = candidate,
                speciesId = candidateSpeciesIds[candidate.scientificName],
                onOpenSpecies = onOpenSpecies,
            )
        }
        Text("判断依据", style = MaterialTheme.typography.titleMedium)
        val reasons = record.result.candidates.mapNotNull { it.reason }.filter { it.isNotBlank() }
        if (reasons.isEmpty()) {
            Text("模型未返回详细依据。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            reasons.forEach { Text("• $it") }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { thinkingExpanded = !thinkingExpanded }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("大模型分析过程", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(
                        if (thinkingExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                    )
                }
                AnimatedVisibility(thinkingExpanded) {
                    Text(
                        record.result.thinking.ifBlank { "没有保存到分析过程。" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                }
            }
        }
        Text(
            "历史记录仅供回看，不代表识别结果已被专业人员确认。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryCandidateRow(
    number: Int,
    candidate: Candidate,
    speciesId: Int?,
    onOpenSpecies: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = speciesId != null) { speciesId?.let(onOpenSpecies) }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("$number", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(
                candidate.commonName ?: candidate.scientificName,
                color = if (speciesId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (!candidate.commonName.isNullOrBlank()) {
                Text(
                    candidate.scientificName,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistoryTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

private fun formatHistoryTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
