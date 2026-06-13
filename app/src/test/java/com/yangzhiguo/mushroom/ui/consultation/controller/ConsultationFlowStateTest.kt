package com.yangzhiguo.mushroom.ui.consultation.controller

import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Test

class ConsultationFlowStateTest {

    @Test
    fun photoUrisSurviveFlowStateRecreation() {
        val savedUris = mutableStateOf(
            listOf(
                "content://photos/first",
                "content://photos/second",
            ),
        )

        val recreated = ConsultationFlowState(savedUris)

        assertEquals(
            listOf("content://photos/first", "content://photos/second"),
            recreated.photoUriStrings,
        )
    }
}
