package com.yangzhiguo.mushroom.ui.consultation.controller

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.yangzhiguo.mushroom.domain.model.FeatureTraits

@Stable
class ConsultationFlowState internal constructor(
    private val savedPhotoUris: MutableState<List<String>> = mutableStateOf(emptyList()),
    private val savedUserInfo: MutableState<String> = mutableStateOf(""),
) {
    internal val photoUriStrings: List<String> get() = savedPhotoUris.value
    val photoUris: List<Uri> get() = savedPhotoUris.value.map(Uri::parse)
    val photoUri: Uri? get() = savedPhotoUris.value.firstOrNull()?.let(Uri::parse)
    val userInfo: String get() = savedUserInfo.value

    private val _traits = mutableStateOf(FeatureTraits())
    val traits: FeatureTraits get() = _traits.value

    private val _candidateIds = mutableStateOf<List<Int>>(emptyList())
    val candidateIds: List<Int> get() = _candidateIds.value

    fun addPhoto(uri: Uri) {
        val value = uri.toString()
        if (value !in savedPhotoUris.value && savedPhotoUris.value.size < MAX_PHOTOS) {
            savedPhotoUris.value = savedPhotoUris.value + value
        }
    }
    fun addPhotos(uris: List<Uri>) {
        savedPhotoUris.value = (savedPhotoUris.value + uris.map(Uri::toString))
            .distinct()
            .take(MAX_PHOTOS)
    }
    fun removePhoto(uri: Uri) {
        savedPhotoUris.value = savedPhotoUris.value - uri.toString()
    }
    fun clearPhotos() { savedPhotoUris.value = emptyList() }
    fun setUserInfo(value: String) { savedUserInfo.value = value }
    fun setTraits(t: FeatureTraits) { _traits.value = t }
    fun setCandidateIds(ids: List<Int>) { _candidateIds.value = ids }

    companion object {
        const val MAX_PHOTOS = 6
    }
}

@Composable
fun rememberConsultationFlowState(): ConsultationFlowState {
    val savedPhotoUris = rememberSaveable { mutableStateOf(emptyList<String>()) }
    val savedUserInfo = rememberSaveable { mutableStateOf("") }
    return remember(savedPhotoUris, savedUserInfo) {
        ConsultationFlowState(savedPhotoUris, savedUserInfo)
    }
}
