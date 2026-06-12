package com.yangzhiguo.mushroom.ui.consultation.controller

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.yangzhiguo.mushroom.domain.model.FeatureTraits

@Stable
class ConsultationFlowState {
    private val _photoUris = mutableStateOf<List<Uri>>(emptyList())
    val photoUris: List<Uri> get() = _photoUris.value
    val photoUri: Uri? get() = _photoUris.value.firstOrNull()

    private val _traits = mutableStateOf(FeatureTraits())
    val traits: FeatureTraits get() = _traits.value

    private val _candidateIds = mutableStateOf<List<Int>>(emptyList())
    val candidateIds: List<Int> get() = _candidateIds.value

    fun addPhoto(uri: Uri) {
        if (uri !in _photoUris.value && _photoUris.value.size < MAX_PHOTOS) {
            _photoUris.value = _photoUris.value + uri
        }
    }
    fun addPhotos(uris: List<Uri>) {
        _photoUris.value = (_photoUris.value + uris).distinct().take(MAX_PHOTOS)
    }
    fun removePhoto(uri: Uri) {
        _photoUris.value = _photoUris.value - uri
    }
    fun clearPhotos() { _photoUris.value = emptyList() }
    fun setTraits(t: FeatureTraits) { _traits.value = t }
    fun setCandidateIds(ids: List<Int>) { _candidateIds.value = ids }

    companion object {
        const val MAX_PHOTOS = 6
    }
}

@Composable
fun rememberConsultationFlowState(): ConsultationFlowState = remember { ConsultationFlowState() }
