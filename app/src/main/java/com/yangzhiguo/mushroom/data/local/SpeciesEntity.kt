package com.yangzhiguo.mushroom.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.yangzhiguo.mushroom.domain.model.Edibility
import com.yangzhiguo.mushroom.domain.model.ToxicityLevel
import com.yangzhiguo.mushroom.domain.model.UseType

/**
 * Core mushroom species row in `mushroom_species` (schema v12).
 *
 * v12 重设计:
 * - 复合主键是 `(mushroom_id, scraw_source)`,精确标识同一来源下的物种记录。
 * - 图片 URL 直接保存在 `image_url` / `images`,不再通过 `mushroom_image`
 *   按不唯一的 `mushroom_id` 关联。
 *
 * v11 增量:`alias_names` 列(JSON 数组字符串),DAO 名称匹配在
 * `scientific_name` / `chinese_name` miss 时回退到本字段。
 *
 * **索引名称必须与 `app/src/main/assets/mushroom.db` 中实际索引名完全一致**:
 * Room 在 `createFromAsset` 首次打开时调用 `checkIdentity(db)` 验证 schema,索引
 * 名错位会抛 `Pre-packaged database has an invalid schema` 让 App 启动失败。
 * 离线数据库由 Room 建库并通过 App 的 [com.yangzhiguo.mushroom.sync.SyncRepository]
 * 写入,不再手工维护镜像 schema。
 */
@Entity(
    tableName = "mushroom_species",
    primaryKeys = ["mushroom_id", "scraw_source"],
    indices = [
        // 名称匹配 / 搜索用 (searchByName / findBestNameMatch)
        Index(value = ["scientific_name"], name = "idx_species_scientific_name"),
        Index(value = ["chinese_name"], name = "idx_species_chinese_name"),
    ],
)
data class SpeciesEntity(
    // ─── 主键 ────────────────────────────────────────────────────────────
    @ColumnInfo("mushroom_id") val mushroomId: Int,
    @ColumnInfo("scraw_source") val scrawSource: String = SCRAW_SOURCE_GENERAL_DIRECTORY,
    @ColumnInfo("last_updated") val lastUpdated: Long,

    // ─── 命名 ────────────────────────────────────────────────────────────
    @ColumnInfo("scientific_name") val scientificName: String = "",
    @ColumnInfo("chinese_name") val chineseName: String = "",
    /**
     * 别名 / 俗名 list（中文、英文、地方名等）。
     * 名称匹配（[SpeciesDao.searchByName] / [SpeciesDao.findBestNameMatch]）在
     * `scientific_name` / `chinese_name` miss 时,会回退到本字段继续匹配。
     */
    @ColumnInfo("alias_names") val aliasNames: List<String> = emptyList(),
    @ColumnInfo("authority") val authority: String = "",
    @ColumnInfo("species_latin_genus") val speciesLatinGenus: String? = null,
    @ColumnInfo("specific_epithet") val specificEpithet: String? = null,
    @ColumnInfo("species_common") val speciesCommon: String? = null,
    @ColumnInfo("field_identification") val fieldIdentification: String? = null,

    // ─── 分类(中/英) ──────────────────────────────────────────────────────
    @ColumnInfo("community_zh") val communityZh: String? = null,
    @ColumnInfo("community_la") val communityLa: String? = null,
    @ColumnInfo("phylum_zh") val phylumZh: String? = null,
    @ColumnInfo("phylum_la") val phylumLa: String? = null,
    @ColumnInfo("class_zh") val classZh: String? = null,
    @ColumnInfo("class_la") val classLa: String? = null,
    @ColumnInfo("order_zh") val orderZh: String? = null,
    @ColumnInfo("order_la") val orderLa: String? = null,
    @ColumnInfo("suborder_zh") val suborderZh: String? = null,
    @ColumnInfo("suborder_la") val suborderLa: String? = null,
    @ColumnInfo("family_zh") val familyZh: String = "",
    @ColumnInfo("family_la") val familyLa: String = "",
    @ColumnInfo("subfamily_zh") val subfamilyZh: String? = null,
    @ColumnInfo("subfamily_la") val subfamilyLa: String? = null,
    @ColumnInfo("genus_zh") val genusZh: String = "",
    @ColumnInfo("genus_la") val genusLa: String = "",
    @ColumnInfo("subgenus_zh") val subgenusZh: String? = null,
    @ColumnInfo("subgenus_la") val subgenusLa: String? = null,
    @ColumnInfo("section_zh") val sectionZh: String? = null,
    @ColumnInfo("section_la") val sectionLa: String? = null,

    // ─── 用途/毒性(枚举 + 原始字符串) ────────────────────────────────────
    @ColumnInfo("use_type") val useType: UseType = UseType.UNREPORTED,
    @ColumnInfo("toxicity_level") val toxicityLevel: ToxicityLevel = ToxicityLevel.NONE,
    @ColumnInfo("edibility") val edibility: Edibility = Edibility.UNKNOWN,
    @ColumnInfo("edible_fungus") val edibleFungus: String? = null,
    @ColumnInfo("medicinal_fungus") val medicinalFungus: String? = null,
    @ColumnInfo("toxic_fungus") val toxicFungus: String? = null,
    @ColumnInfo("conditionally_fungus") val conditionallyFungus: String? = null,
    @ColumnInfo("mycorrhizal_fungus") val mycorrhizalFungus: String? = null,
    @ColumnInfo("saprophytic_fungus") val saprophyticFungus: String? = null,
    @ColumnInfo("parasitic_fungus") val parasiticFungus: String? = null,
    @ColumnInfo("economic_use") val economicUse: String? = null,

    // ─── 生态/分布 ───────────────────────────────────────────────────────
    @ColumnInfo("habitat") val habitat: String = "",
    @ColumnInfo("substrate") val substrate: String? = null,
    @ColumnInfo("tree_species") val treeSpecies: String? = null,
    @ColumnInfo("climate_zone") val climateZone: String? = null,
    @ColumnInfo("tropical_species") val tropicalSpecies: String? = null,
    @ColumnInfo("subtropical_species") val subtropicalSpecies: String? = null,
    @ColumnInfo("temperate_species") val temperateSpecies: String? = null,
    @ColumnInfo("southwest_specific") val southwestSpecific: String? = null,
    @ColumnInfo("yunnan_specific") val yunnanSpecific: String? = null,
    @ColumnInfo("is_southwest") val isSouthwest: String? = null,
    @ColumnInfo("is_xizang") val isXizang: String? = null,
    @ColumnInfo("is_sichuan") val isSichuan: String? = null,
    @ColumnInfo("is_guizhou") val isGuizhou: String? = null,
    @ColumnInfo("is_gaoligong") val isGaoligong: String? = null,
    @ColumnInfo("is_yunnan") val isYunnan: String? = null,
    @ColumnInfo("distribution_location") val distributionLocation: String? = null,
    @ColumnInfo("altitude_range") val altitudeRange: String? = null,

    // ─── 长描述(TEXT 不截断) ─────────────────────────────────────────────
    @ColumnInfo("species_description") val speciesDescription: String? = null,
    @ColumnInfo("cap_description") val capDescription: String? = null,
    @ColumnInfo("cap_context") val capContext: String? = null,
    @ColumnInfo("lamella_description") val lamellaDescription: String? = null,
    @ColumnInfo("stipe_description") val stipeDescription: String? = null,
    @ColumnInfo("stipe_context") val stipeContext: String? = null,
    @ColumnInfo("odor") val odor: String? = null,
    @ColumnInfo("spore_description") val sporeDescription: String? = null,
    @ColumnInfo("ring_description") val ringDescription: String = "",
    @ColumnInfo("volva_description") val volvaDescription: String = "",
    @ColumnInfo("description_reference") val descriptionReference: String? = null,
    @ColumnInfo("purpose_references") val purposeReferences: String? = null,
    @ColumnInfo("habit_references") val habitReferences: String? = null,
    @ColumnInfo("directory_references") val directoryReferences: String? = null,
    @ColumnInfo("directory_grade") val directoryGrade: String? = null,

    // ─── 图片(完整保存在主表,images 为 JSON 数组) ────────────────────────
    @ColumnInfo("image_url") val imageUrl: String? = null,
    @ColumnInfo("image_local_path") val imageLocalPath: String? = null,
    @ColumnInfo("images") val images: String = "[]",

    // ─── 用户/来源 ──────────────────────────────────────────────────────
    @ColumnInfo("source_url") val sourceUrl: String = "",
    @ColumnInfo("source_types") val sourceTypes: String = "",
    @ColumnInfo("is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo("model_3d_url") val model3dUrl: String? = null,
    @ColumnInfo("identification_points") val identificationPoints: String = "[]",
    @ColumnInfo("look_alike_ids") val lookAlikeIds: String = "",
    @ColumnInfo("toxicity_symptoms") val toxicitySymptoms: String = "",
    @ColumnInfo("season") val season: String = "",
) {
    companion object {
        /** 来自 https://fungi.iflora.cn/#/species_specimen/retrieve 的标本记录(已废弃,仅作常量保留兼容旧 DB) */
        const val SCRAW_SOURCE_SPECIES_SPECIMEN = "species_specimen"
        /** 来自 https://fungi.iflora.cn/#/list_species/general_directory 的名录记录 */
        const val SCRAW_SOURCE_GENERAL_DIRECTORY = "general_directory"
        /** 来自 https://fungi.iflora.cn/#/list_species/economic_fungi_list/edible_fungi_list */
        const val SCRAW_SOURCE_EDIBLE_FUNGI = "edible_fungi"
        /** 来自 https://fungi.iflora.cn/#/list_species/economic_fungi_list/toxic_fungi_list */
        const val SCRAW_SOURCE_TOXIC_FUNGI = "toxic_fungi"
    }
}
