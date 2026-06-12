package com.yangzhiguo.mushroom.ui.species

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangzhiguo.mushroom.R
import com.yangzhiguo.mushroom.ui.components.MushroomIcon
import com.yangzhiguo.mushroom.ui.components.ToxicityBanner
import com.yangzhiguo.mushroom.ui.theme.ScientificNameStyle
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                Text(text = species?.chineseName ?: stringResource(R.string.species_list_title), style = MaterialTheme.typography.titleLarge)
            }
            species?.let { sp ->
                val idPoints = remember(sp.identificationPoints) {
                    runCatching {
                        Json.decodeFromString(ListSerializer(String.serializer()), sp.identificationPoints)
                    }.getOrDefault(emptyList())
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val imageFile by viewModel.imageFile.collectAsStateWithLifecycle()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (imageFile != null && imageFile!!.exists()) {
                            coil.compose.AsyncImage(
                                model = imageFile,
                                contentDescription = sp.chineseName,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            MushroomIcon(size = 120.dp, capColor = Color.White, stemColor = Color.LightGray)
                        }
                    }
                    Text(text = sp.chineseName, style = MaterialTheme.typography.titleLarge)
                    Text(text = sp.scientificName, style = ScientificNameStyle)
                    ToxicityBanner(level = sp.toxicityLevel, chineseName = sp.chineseName, scientificName = sp.scientificName)
                    InfoRow(stringResource(R.string.species_season), sp.season)
                    InfoRow(stringResource(R.string.species_habitat), sp.habitat)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.species_id_points), style = MaterialTheme.typography.titleMedium)
                    idPoints.forEach { line ->
                        Text("• $line", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (sp.toxicitySymptoms.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.species_symptoms), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Text(sp.toxicitySymptoms, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.species_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(stringResource(R.string.species_source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "$label:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
