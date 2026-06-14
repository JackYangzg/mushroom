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

    @Test
    fun userInfoSurvivesFlowStateRecreation() {
        val savedUserInfo = mutableStateOf("上海，雨后草地，菌盖约 5 厘米")

        val recreated = ConsultationFlowState(
            savedPhotoUris = mutableStateOf(emptyList()),
            savedUserInfo = savedUserInfo,
        )

        assertEquals("上海，雨后草地，菌盖约 5 厘米", recreated.userInfo)
    }
}
