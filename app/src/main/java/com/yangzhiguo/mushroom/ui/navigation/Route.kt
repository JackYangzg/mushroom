package com.yangzhiguo.mushroom.ui.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Route(val path: String) {

    data object Splash : Route("splash")
    data object Onboarding : Route("onboarding")
    data object MainShell : Route("main_shell")

    data object Home : Route("home")
    data object SpeciesList : Route("species_list")
    data class SpeciesDetail(val speciesId: Int) : Route("species_detail/$speciesId") {
        companion object {
            const val PATTERN = "species_detail/{speciesId}"
            fun build(id: Int) = "species_detail/$id"
        }
    }
    data object Profile : Route("profile")
    data object Settings : Route("settings")

    // ---- "我的" tab 内部子页(从 ProfileScreen 跳转)----
    data object MyFavorites : Route("my_favorites")
    data object RecognitionHistory : Route("recognition_history")
    data object RecognitionHistoryDetail : Route("recognition_history/{historyId}") {
        const val ARG_HISTORY_ID = "historyId"
        fun build(id: String): String = "recognition_history/$id"
    }
    data object Disclaimer : Route("disclaimer")
    data object About : Route("about")

    data object Camera : Route("flow/camera")
    data object PhotoPreview : Route("flow/photo_preview")
    data object FeatureForm : Route("flow/feature_form")
    data object Result : Route("flow/result")

    /** 拍照 → AI 识别（设计文档 §2 主链路）。 */
    data object AiRecognitionFlow : Route("flow/ai_recognition/{source}") {
        const val ARG_SOURCE = "source"
        const val CAMERA = "camera"
        const val GALLERY = "gallery"
        fun build(source: String) = "flow/ai_recognition/$source"
    }

    // ---- 拍照识别流程（设计文档 §2）----

    /** 拍照后进入识别：参数为 photoUri。 */
    data object Recognition : Route("flow/recognition") {
        fun build(photoUri: String): String {
            val encoded = URLEncoder.encode(photoUri, StandardCharsets.UTF_8.name())
            return "flow/recognition?photoUri=$encoded"
        }
        const val PATTERN = "flow/recognition?photoUri={photoUri}"
        const val ARG_PHOTO = "photoUri"
    }

    /** 3D 查看：参数为 scientificName。 */
    data object ThreeD : Route("flow/threed") {
        fun build(scientificName: String): String {
            val encoded = URLEncoder.encode(scientificName, StandardCharsets.UTF_8.name())
            return "flow/threed?name=$encoded"
        }
        const val PATTERN = "flow/threed?name={name}"
        const val ARG_NAME = "name"
    }
}
