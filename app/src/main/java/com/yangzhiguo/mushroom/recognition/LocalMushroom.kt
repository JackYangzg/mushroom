package com.yangzhiguo.mushroom.recognition

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 本地索引条目（设计文档 §3.3）。
 *
 * 与 SpeciesEntity 的差异：LocalMushroom 是「识别流程的快速命中表」，只保留
 * 详情首屏需要的最小字段；完整结构化数据走 iflora 远端拉取。
 */
@Serializable
data class LocalMushroom(
    /** 唯一键（拉丁名）。匹配时 case-insensitive 且 trim。 */
    @SerialName("scientificName") val scientificName: String,
    /** 中文俗名。 */
    @SerialName("commonName") val commonName: String? = null,
    /** 1~2 句简介（详情首屏的占位文案）。 */
    @SerialName("shortDesc") val shortDesc: String,
    /** iflora 详情页的相对路径（baseUrl = https://fungi.iflora.cn）。 */
    @SerialName("detailRemotePath") val detailRemotePath: String,
    /** 本地缩略图资源（drawable 资源名）。 */
    @SerialName("previewImages") val previewImages: List<String> = emptyList(),
    /** 是否支持 3D 查看（影响详情页按钮的可见性）。 */
    @SerialName("has3D") val has3D: Boolean = true,
    /** 食性 / 毒性标签（用于详情页红框警示）。 */
    @SerialName("edibility") val edibility: Edibility = Edibility.UNKNOWN,
    /** 毒性等级 0-3（0 无毒 / 1 轻微 / 2 中毒 / 3 致命），与 seed_species.json 对齐。 */
    @SerialName("toxicityLevel") val toxicityLevel: Int = 0,
)

/** 食性枚举（与 SpeciesEntity 用法对齐）。 */
@Serializable
enum class Edibility {
    @SerialName("EDIBLE") EDIBLE,
    @SerialName("EDIBLE_WHEN_COOKED") EDIBLE_WHEN_COOKED,
    @SerialName("INEDIBLE") INEDIBLE,
    @SerialName("POISONOUS") POISONOUS,
    @SerialName("DEADLY") DEADLY,
    @SerialName("PSYCHOACTIVE") PSYCHOACTIVE,
    @SerialName("MEDICINAL") MEDICINAL,
    @SerialName("UNKNOWN") UNKNOWN,
    ;

    /** 是否属于"不可食用 / 高危"档，用于详情页警示色判定。 */
    val isDangerous: Boolean
        get() = this == POISONOUS || this == DEADLY || this == INEDIBLE

    /** 折叠为 UI 用的 4 类（用户要求：可食 / 可药用 / 有毒 / 需谨慎）。 */
    fun toDisplayCategory(): DisplayCategory = when (this) {
        EDIBLE -> DisplayCategory.EDIBLE
        MEDICINAL -> DisplayCategory.MEDICINAL
        POISONOUS, DEADLY, PSYCHOACTIVE -> DisplayCategory.TOXIC
        // 需谨慎：生食有毒但处理后可食 / 不可食 / 未知
        EDIBLE_WHEN_COOKED, INEDIBLE, UNKNOWN -> DisplayCategory.NEEDS_CAUTION
    }
}

/**
 * UI 展示用的 4 类（用户指定）。
 *  - 可食：普通可食
 *  - 可药用：可食用但传统药用
 *  - 有毒：含致毒 / 致幻 / 致命成分
 *  - 需谨慎：生食有毒但处理后可食 / 不可食 / 未知
 */
@Serializable
enum class DisplayCategory(val zh: String) {
    @SerialName("EDIBLE") EDIBLE("可食"),
    @SerialName("MEDICINAL") MEDICINAL("可药用"),
    @SerialName("TOXIC") TOXIC("有毒"),
    @SerialName("NEEDS_CAUTION") NEEDS_CAUTION("需谨慎"),
}
