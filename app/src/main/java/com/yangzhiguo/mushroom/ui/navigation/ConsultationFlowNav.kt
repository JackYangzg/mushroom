package com.yangzhiguo.mushroom.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yangzhiguo.mushroom.ui.consultation.FeatureFormScreen
import com.yangzhiguo.mushroom.ui.consultation.ResultScreen
import com.yangzhiguo.mushroom.ui.consultation.controller.rememberConsultationFlowState
import com.yangzhiguo.mushroom.ui.recognition.RecognitionScreen

/**
 * Free consultation flow: Camera → Preview → (Form → Result | Recognition).
 *
 * - [useAi] = false（默认）：原有"问诊"流程，让用户填特征
 * - [useAi] = true：新"AI 识别"流程，PhotoPreview 后直跳 [RecognitionScreen]
 *   （设计文档 §2 主链路）
 */
@Composable
fun ConsultationFlowNav(
    onExit: () -> Unit,
    useAi: Boolean = false,
    onOpen3D: (String) -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val state = rememberConsultationFlowState()

    NavHost(
        navController = navController,
        startDestination = Route.Camera.path,
    ) {
        composable(Route.Camera.path) {
            com.yangzhiguo.mushroom.ui.camera.CameraScreen(
                onPhotoReady = { uri ->
                    state.setPhotoUri(uri)
                    navController.navigate(Route.PhotoPreview.path)
                },
                onClose = onExit,
            )
        }
        composable(Route.PhotoPreview.path) {
            com.yangzhiguo.mushroom.ui.camera.PhotoPreviewScreen(
                photoUri = state.photoUri,
                onRetake = { navController.popBackStack() },
                onUseThis = {
                    if (useAi) {
                        val uri = state.photoUri?.toString().orEmpty()
                        navController.navigate(Route.Recognition.build(uri)) {
                            popUpTo(Route.Camera.path) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Route.FeatureForm.path)
                    }
                },
            )
        }
        composable(Route.Recognition.PATTERN) {
            val uri = state.photoUri?.toString().orEmpty()
            RecognitionScreen(
                photoUri = uri,
                onOpen3D = onOpen3D,
                onRetake = { navController.popBackStack(Route.Camera.path, inclusive = false) },
            )
        }
        composable(Route.FeatureForm.path) {
            FeatureFormScreen(
                traits = state.traits,
                onTraitsChange = state::setTraits,
                onSubmit = { traits, candidateIds ->
                    state.setTraits(traits)
                    state.setCandidateIds(candidateIds)
                    navController.navigate(Route.Result.path) {
                        popUpTo(Route.Camera.path) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.Result.path) {
            ResultScreen(
                photoUri = state.photoUri,
                traits = state.traits,
                candidateIds = state.candidateIds,
                onDone = onExit,
            )
        }
    }
}
