package com.yangzhiguo.mushroom.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yangzhiguo.mushroom.domain.model.Edibility
import com.yangzhiguo.mushroom.domain.model.ToxicityLevel
import com.yangzhiguo.mushroom.domain.model.UseType

/**
 * Core mushroom species row in `mushroom_species` (schema v6).
 *
 * Mirrors the full 117-column `Specimen` payload from fungi.iflora.cn, with
 * descriptive text fields stored as TEXT (no truncation) and image/photo
 * metadata delegated to child tables (`mushroom_image`,
 * `mushroom_dna_barcode`, `mushroom_specimen`, `mushroom_distribution_point`).
 *
 * The 1:N child tables (see design report §4) replace the previous JSON-encoded
 * `images` column and the lost DNA / specimen / map data. The `images` JSON
 * column is retained for legacy UI compatibility — it's reconstructed from
 * [mushroom_image] at sync time.
 */
@Entity(tableName = "mushroom_species")
data class SpeciesEntity(
    // ─── 主键 ────────────────────────────────────────────────────────────
    @PrimaryKey val id: Int,
    @ColumnInfo("scraw_source") val scrawSource: String = SCRAW_SOURCE_GENERAL_DIRECTORY,
    @ColumnInfo("last_updated") val lastUpdated: Long,

    // ─── 命名 ────────────────────────────────────────────────────────────
    @ColumnInfo("scientific_name") val scientificName: String = "",
    @ColumnInfo("chinese_name") val chineseName: String = "",
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
    @ColumnInfo("ring_description") val ringDescription: String? = "",
    @ColumnInfo("volva_description") val volvaDescription: String? = "",
    @ColumnInfo("description_reference") val descriptionReference: String? = null,
    @ColumnInfo("purpose_references") val purposeReferences: String? = null,
    @ColumnInfo("habit_references") val habitReferences: String? = null,
    @ColumnInfo("directory_references") val directoryReferences: String? = null,
    @ColumnInfo("directory_grade") val directoryGrade: String? = null,

    // ─── DNA 编号 / URL(冗余主表,完整文件入 mushroom_dna_barcode) ─────────
    @ColumnInfo("its_genbank") val itsGenbank: String? = null,
    @ColumnInfo("its_genbank_url") val itsGenbankUrl: String? = null,
    @ColumnInfo("nrlsu_genbank") val nrlsuGenbank: String? = null,
    @ColumnInfo("nrlsu_genbank_url") val nrlsuGenbankUrl: String? = null,
    @ColumnInfo("tef1_genbank") val tef1Genbank: String? = null,
    @ColumnInfo("tef1_genbank_url") val tef1GenbankUrl: String? = null,
    @ColumnInfo("rpb1_genbank") val rpb1Genbank: String? = null,
    @ColumnInfo("rpb1_genbank_url") val rpb1GenbankUrl: String? = null,
    @ColumnInfo("rpb2_genbank") val rpb2Genbank: String? = null,
    @ColumnInfo("rpb2_genbank_url") val rpb2GenbankUrl: String? = null,
    @ColumnInfo("ssu") val ssu: String? = null,
    @ColumnInfo("tub2") val tub2: String? = null,

    // ─── 采集元信息 ──────────────────────────────────────────────────────
    @ColumnInfo("collect_user") val collectUser: String? = null,
    @ColumnInfo("collect_unit") val collectUnit: String? = null,
    @ColumnInfo("research_team") val researchTeam: String? = null,
    @ColumnInfo("gather_num") val gatherNum: String? = null,
    @ColumnInfo("collection_num") val collectionNum: String? = null,
    @ColumnInfo("collect_time") val collectTime: String? = null,
    @ColumnInfo("specimen_habitat") val specimenHabitat: String? = null,
    @ColumnInfo("field_note") val fieldNote: String? = null,
    @ColumnInfo("specimen_describe") val specimenDescribe: String? = null,
    @ColumnInfo("resource_type") val resourceType: String? = null,
    @ColumnInfo("specimen_group") val specimenGroup: String? = null,
    @ColumnInfo("photo_num") val photoNum: String? = null,
    @ColumnInfo("create_time") val createTime: String? = null,
    @ColumnInfo("species_checker") val speciesChecker: String? = null,
    @ColumnInfo("fill_user") val fillUser: String? = null,
    @ColumnInfo("is_approve") val isApprove: String? = null,
    @ColumnInfo("is_open") val isOpen: Int? = null,
    @ColumnInfo("borrow_status") val borrowStatus: Int? = null,
    @ColumnInfo("assigning_user") val assigningUser: String? = null,
    @ColumnInfo("assigning_id") val assigningId: String? = null,
    @ColumnInfo("strain_number") val strainNumber: String? = null,
    @ColumnInfo("notes") val notes: String? = null,
    @ColumnInfo("other_data") val otherData: String? = null,

    // ─── 图片(主表冗余首图快取,完整入 mushroom_image) ───────────────────
    @ColumnInfo("image_url") val imageUrl: String? = null,
    @ColumnInfo("image_local_path") val imageLocalPath: String? = null,
    @ColumnInfo("images") val images: String = "[]",

    // ─── 用户/来源(旧字段保留) ──────────────────────────────────────────
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
        /** 来自 https://fungi.iflora.cn/#/species_specimen/retrieve 的标本记录 */
        const val SCRAW_SOURCE_SPECIES_SPECIMEN = "species_specimen"
        /** 来自 https://fungi.iflora.cn/#/list_species/general_directory 的名录记录 */
        const val SCRAW_SOURCE_GENERAL_DIRECTORY = "general_directory"
    }
}
