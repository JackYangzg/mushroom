package com.yangzhiguo.mushroom.domain.model

import kotlinx.serialization.Serializable

/**
 * Five-tab classification from `design_doc §10.2` (matched against iflora.cn's tabs).
 * Drives the top filter on S11 SpeciesList and the ToxicityBanner color on S9/S12.
 */
@Serializable
enum class UseType {
    EDIBLE,        // 可食
    MEDICINAL,     // 可药用
    POISONOUS,     // 有毒
    CAUTION,       // 需谨慎
    UNREPORTED,    // 无用途报道
}

/** iflora-derived edibility grade. */
@Serializable
enum class Edibility {
    EDIBLE,        // 可食
    INEDIBLE,      // 不可食(无毒但难吃/伤胃)
    DEADLY,        // 致命
    UNKNOWN,
}

/** 0 = 无毒, 3 = 致命. Numeric for sorting in the field guide. */
@Serializable
enum class ToxicityLevel(val level: Int, val label: String) {
    NONE(0, "无毒"),
    MILD(1, "轻微"),
    TOXIC(2, "有毒"),
    DEADLY(3, "致命");

    companion object {
        fun fromInt(level: Int): ToxicityLevel =
            entries.firstOrNull { it.level == level } ?: NONE
    }
}
