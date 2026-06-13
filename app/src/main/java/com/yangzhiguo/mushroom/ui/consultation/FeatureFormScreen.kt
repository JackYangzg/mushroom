package com.yangzhiguo.mushroom.ui.consultation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.yangzhiguo.mushroom.R
import com.yangzhiguo.mushroom.domain.model.FeatureTraits
import com.yangzhiguo.mushroom.domain.usecase.MatchLocalSpeciesUseCase
import com.yangzhiguo.mushroom.ui.components.ChipSelector
import com.yangzhiguo.mushroom.ui.components.PrimaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeatureFormViewModel @Inject constructor(
    private val matchLocal: MatchLocalSpeciesUseCase,
) : ViewModel() {
    suspend fun matchCandidates(traits: FeatureTraits): List<Int> =
        matchLocal(traits).map { it.mushroomId }
}

@Composable
fun FeatureFormScreen(
    traits: FeatureTraits,
    onTraitsChange: (FeatureTraits) -> Unit,
    onSubmit: (FeatureTraits, candidateIds: List<Int>) -> Unit,
    onBack: () -> Unit,
    viewModel: FeatureFormViewModel = hiltViewModel(),
) {
    var capColor by remember { mutableStateOf(traits.capColor) }
    var ring by remember { mutableStateOf(traits.ring) }
    var volva by remember { mutableStateOf(traits.volva) }
    var habitat by remember { mutableStateOf(traits.habitat) }
    var season by remember { mutableStateOf(traits.season) }
    var note by remember { mutableStateOf(traits.note) }

    val requiredFilled = listOf(capColor, ring, volva, habitat).all { !it.isNullOrBlank() }
    val missing = 4 - listOf(capColor, ring, volva, habitat).count { !it.isNullOrBlank() }

    var noMatchWarning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = null) }
                    Text(text = stringResource(R.string.form_title), style = MaterialTheme.typography.titleLarge)
                }
                if (noMatchWarning) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            text = stringResource(R.string.form_no_match_warning),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                FormField(label = stringResource(R.string.form_field_cap_color)) {
                    ChipSelector(
                        items = listOf("白", "黄", "红", "褐", "黑", "紫", "其他"),
                        selected = setOfNotNull(capColor),
                        onSelectionChange = { capColor = it.firstOrNull() },
                    )
                }
                FormField(label = stringResource(R.string.form_field_ring)) {
                    ChipSelector(
                        items = listOf("有", "无", "看不清"),
                        selected = setOfNotNull(ring),
                        onSelectionChange = { ring = it.firstOrNull() },
                    )
                }
                FormField(label = stringResource(R.string.form_field_volva)) {
                    ChipSelector(
                        items = listOf("有", "无", "看不清"),
                        selected = setOfNotNull(volva),
                        onSelectionChange = { volva = it.firstOrNull() },
                    )
                }
                FormField(label = stringResource(R.string.form_field_habitat)) {
                    ChipSelector(
                        items = listOf("针叶林", "阔叶林", "草地", "腐木", "田边"),
                        selected = setOfNotNull(habitat),
                        onSelectionChange = { habitat = it.firstOrNull() },
                    )
                }
                FormField(label = stringResource(R.string.form_field_season)) {
                    ChipSelector(
                        items = listOf("春", "夏", "秋", "冬"),
                        selected = setOfNotNull(season),
                        onSelectionChange = { season = it.firstOrNull() },
                        multiSelect = true,
                    )
                }
                FormField(label = stringResource(R.string.form_field_note)) {
                    Text(
                        text = note.ifBlank { stringResource(R.string.form_note_hint) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (note.isBlank()) 0.4f else 0.8f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            PrimaryButton(
                text = if (requiredFilled) stringResource(R.string.form_submit)
                else "还需填 $missing 项",
                onClick = {
                    val t = FeatureTraits(capColor, ring, volva, habitat, season, note)
                    onTraitsChange(t)
                    scope.launch {
                        val candidates = viewModel.matchCandidates(t)
                        if (candidates.isEmpty()) {
                            noMatchWarning = true
                        } else {
                            onSubmit(t, candidates)
                        }
                    }
                },
                enabled = requiredFilled,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun FormField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
