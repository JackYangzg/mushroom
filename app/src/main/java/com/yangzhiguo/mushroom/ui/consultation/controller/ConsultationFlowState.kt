package com.yangzhiguo.mushroom.ui.consultation.controller

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.yangzhiguo.mushroom.domain.model.FeatureTraits

@Stable
class ConsultationFlowState {
    private val _photoUri = mutableStateOf<Uri?>(null)
    val photoUri: Uri? get() = _photoUri.value

    private val _traits = mutableStateOf(FeatureTraits())
    val traits: FeatureTraits get() = _traits.value

    private val _candidateIds = mutableStateOf<List<Int>>(emptyList())
    val candidateIds: List<Int> get() = _candidateIds.value

    fun setPhotoUri(uri: Uri) { _photoUri.value = uri }
    fun setTraits(t: FeatureTraits) { _traits.value = t }
    fun setCandidateIds(ids: List<Int>) { _candidateIds.value = ids }
}

@Composable
fun rememberConsultationFlowState(): ConsultationFlowState = remember { ConsultationFlowState() }
