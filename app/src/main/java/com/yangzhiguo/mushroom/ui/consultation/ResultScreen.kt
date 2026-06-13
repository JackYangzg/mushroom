package com.yangzhiguo.mushroom.ui.consultation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yangzhiguo.mushroom.R
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.data.repository.SpeciesRepository
import com.yangzhiguo.mushroom.domain.model.FeatureTraits
import com.yangzhiguo.mushroom.ui.components.MushroomIcon
import com.yangzhiguo.mushroom.ui.components.PrimaryButton
import com.yangzhiguo.mushroom.ui.components.ToxicityBanner
import com.yangzhiguo.mushroom.ui.theme.extended
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val repo: SpeciesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<List<SpeciesEntity>>(emptyList())
    val state: StateFlow<List<SpeciesEntity>> = _state.asStateFlow()

    fun load(mushroomIds: List<Int>) {
        viewModelScope.launch { _state.value = repo.findByMushroomIds(mushroomIds) }
    }
}

@Composable
fun ResultScreen(
    photoUri: Uri?,
    traits: FeatureTraits,
    candidateIds: List<Int>,
    onDone: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    LaunchedEffect(candidateIds) { viewModel.load(candidateIds) }
    val candidates by viewModel.state.collectAsStateWithLifecycle()
    val top = candidates.firstOrNull()
    val context = LocalContext.current
    val noRingNoVolva = traits.ring == "无" && traits.volva == "无"

    val shareText = buildString {
        append("我用「蘑菇鉴别」鉴定了这株蘑菇:")
        appendLine()
        if (top != null) {
            append("${top.chineseName} (${top.scientificName}) — ${top.useType.name} · ${top.toxicityLevel.label}")
        } else {
            append("暂未匹配到合适条目,建议补充菌褶特写后重试")
        }
        appendLine()
        append("免费,离线可查 100 种常见蘑菇。")
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDone) { Icon(Icons.Rounded.ArrowBack, contentDescription = null) }
                Text(stringResource(R.string.result_title), style = MaterialTheme.typography.titleMedium)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (top != null) {
                    ToxicityBanner(
                        level = top.toxicityLevel,
                        edibility = top.edibility,
                        useType = top.useType,
                        chineseName = top.chineseName,
                        scientificName = top.scientificName,
                    )
                    Text(stringResource(R.string.result_section_conclusion), style = MaterialTheme.typography.titleMedium)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(top.toxicitySymptoms.ifBlank {
                                "${top.chineseName} (${top.scientificName}),${top.useType.name}。"
                            }, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "辨识要点:${top.identificationPoints}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                    if (noRingNoVolva) {
                        Surface(
                            color = MaterialTheme.extended.warningContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                text = stringResource(R.string.result_note_no_ring_no_volva),
                                color = MaterialTheme.extended.onWarningContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    Text(stringResource(R.string.result_section_compare), style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (photoUri != null) {
                                coil.compose.AsyncImage(
                                    model = photoUri,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                )
                            } else {
                                MushroomIcon(size = 80.dp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.result_your_photo), style = MaterialTheme.typography.bodySmall)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            MushroomIcon(size = 80.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.result_field_photo), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    Text(
                        text = "未匹配到条目,建议补充菌褶特写后重试。",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (candidates.size > 1) {
                    Text("其他候选", style = MaterialTheme.typography.titleMedium)
                    candidates.drop(1).forEach { sp ->
                        Text("• ${sp.chineseName} (${sp.scientificName}) · ${sp.toxicityLevel.label}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(stringResource(R.string.result_section_risk), style = MaterialTheme.typography.titleMedium)
                Text("⚠ ${stringResource(R.string.result_risk_consult)}", style = MaterialTheme.typography.bodyMedium)
                Text("⚠ ${stringResource(R.string.result_risk_no_wild)}", style = MaterialTheme.typography.bodyMedium)
            }
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.result_action_share),
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(send, "分享给朋友"))
                    },
                )
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.result_action_done))
                }
            }
        }
    }
}
