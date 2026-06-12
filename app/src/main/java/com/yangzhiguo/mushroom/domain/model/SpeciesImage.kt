package com.yangzhiguo.mushroom.domain.model

import kotlinx.serialization.Serializable

/**
 * One image attached to a [Species]. `url` may be a remote HTTPS link or a local
 * asset path like `species/123_01.jpg`. In the prototype we use local paths that
 * don't exist; the species row composable falls back to the MushroomIcon glyph.
 */
@Serializable
data class SpeciesImage(
    val url: String,
    val caption: String = "",
    val photographer: String = "",
    val license: String = "",
    val capturedAt: String = "",
)
