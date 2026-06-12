package com.yangzhiguo.mushroom.domain.model

import kotlinx.serialization.Serializable

/**
 * User-entered traits on S6 FeatureForm. Four fields are required (cap color, ring,
 * volva, habitat); two are optional (season, note).
 *
 * `candidateSpeciesIds` is filled by MatchLocalSpeciesUseCase at submit time and
 * surfaced on S7 as "已识别 3 个候选".
 */
@Serializable
data class FeatureTraits(
    val capColor: String? = null,        // 白/黄/红/褐/黑/紫/其他
    val ring: String? = null,            // 有/无/看不清
    val volva: String? = null,           // 有/无/看不清
    val habitat: String? = null,         // 针叶林/阔叶林/草地/腐木/田边
    val season: String? = null,          // 春/夏/秋/冬
    val note: String = "",
)
