package com.yangzhiguo.scraper

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.SerialName

/**
 * iflora.cn 列表 API 的响应包装。
 * 实际抓取的所有"蘑菇 specimen"字段均保留（含 NULLable 字段）。
 */
@Serializable
data class ApiResponse(
    val code: Int = 0,
    val msg: String? = null,
    val data: PageData? = null,
)

@Serializable
data class PageData(
    val records: List<Specimen> = emptyList(),
    val total: Long = 0,
    val size: Int = 0,
    val current: Long = 0,
    val pages: Long = 0,
)

@Serializable
data class SpeciesApiResponse(
    val code: Int = 0,
    val msg: String? = null,
    val data: SpeciesPageData? = null,
)

@Serializable
data class SpeciesPageData(
    val total: Long = 0,
    val specimenSpeciesList: List<Specimen> = emptyList(),
)

/**
 * 单条 specimen。仅保留可持久化的字段；
 * API 中的图片列表（sysFileList / kibSpeciesPictures / 各种 FileList）由 JsonElement 占位，
 * 配合 Json { ignoreUnknownKeys = true } 容忍其存在，但不写入数据库。
 */
@Serializable
data class Specimen(
    val id: Long = 0,
    val specimenDescribe: String? = null,
    val specimenGroup: String? = null,
    val collectUser: String? = null,
    val collectUnit: String? = null,
    val researchTeam: String? = null,
    val resourceType: String? = null,
    val gatherNum: String? = null,
    val collectionNum: String? = null,
    val collectTime: String? = null,
    val collectCountry: String? = null,
    val collectProvince: String? = null,
    val collectCity: String? = null,
    val collectDistrict: String? = null,
    val collectVillage: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val altitude: String? = null,
    val specimenHabitat: String? = null,
    val fieldNote: String? = null,
    val speciesCommon: String? = null,
    val speciesLatin: String? = null,
    val fieldIdentification: String? = null,
    val notes: String? = null,
    val isSouthwest: String? = null,
    val isXizang: String? = null,
    val isSichuan: String? = null,
    val isGuizhou: String? = null,
    val isGaoligong: String? = null,
    val isYunnan: String? = null,
    val photoNum: String? = null,
    val createTime: String? = null,
    val communityEnglish: String? = null,
    val communityChinese: String? = null,
    val phylumEnglish: String? = null,
    val phylumChinese: String? = null,
    val classEnglish: String? = null,
    val classChinese: String? = null,
    val orderEnglish: String? = null,
    val orderChinese: String? = null,
    val suborderEnglish: String? = null,
    val suborderChinese: String? = null,
    val familyEnglish: String? = null,
    val familyChinese: String? = null,
    val subfamilyEnglish: String? = null,
    val subfamilyChinese: String? = null,
    val genusEnglish: String? = null,
    val genusChinese: String? = null,
    val subgenusEnglish: String? = null,
    val subgenusChinese: String? = null,
    val sectionEnglish: String? = null,
    val sectionChinese: String? = null,
    val speciesLatinGenus: String? = null,
    val specificEpithet: String? = null,
    val famousPerson: String? = null,
    val speciesChinese: String? = null,
    val speciesDescription: String? = null,
    val speciesHabitat: String? = null,
    val descriptionReference: String? = null,
    val southwestSpecific: String? = null,
    val yunnanSpecific: String? = null,
    val tropicalSpecies: String? = null,
    val subtropicalSpecies: String? = null,
    val temperateSpecies: String? = null,
    val edibleFungus: String? = null,
    val medicinalFungus: String? = null,
    val toxicFungus: String? = null,
    val mycorrhizalFungus: String? = null,
    val saprophyticFungus: String? = null,
    val parasiticFungus: String? = null,
    val purposeReferences: String? = null,
    val habitReferences: String? = null,
    val directoryGrade: String? = null,
    val directoryReferences: String? = null,
    val itsGenbank: String? = null,
    val itsGenbankUrl: String? = null,
    val nrlsuGenbank: String? = null,
    val nrlsuGenbankUrl: String? = null,
    val tef1Genbank: String? = null,
    val tef1GenbankUrl: String? = null,
    val rpb1Genbank: String? = null,
    val rpb1GenbankUrl: String? = null,
    val rpb2Genbank: String? = null,
    val rpb2GenbankUrl: String? = null,
    val speciesChecker: String? = null,
    val treeSpecies: String? = null,
    val climateZone: String? = null,
    val cap: String? = null,
    val capContext: String? = null,
    val lamella: String? = null,
    val stipe: String? = null,
    val stipeContext: String? = null,
    val odor: String? = null,
    val calmSeed: String? = null,
    val otherData: String? = null,
    val isApprove: String? = null,
    val fillUser: String? = null,
    val ssu: String? = null,
    val tub2: String? = null,
    val conditionallyFungus: String? = null,
    val substrate: String? = null,
    val strainNumber: String? = null,
    val isOpen: Int? = null,
    val borrowStatus: Int? = null,
    val assigningUser: String? = null,
    val assigningId: String? = null,
    val distributionLocation: String? = null,
    val economicUse: String? = null,
    // API 中存在但**不持久化**的字段：图片列表与 DNA 文件列表。
    // 用 JsonElement 占位，配合 Json { ignoreUnknownKeys = true } 容忍其存在。
    @SerialName("sysFileList") val sysFileList: JsonElement? = null,
    @SerialName("kibSpeciesPictures") val kibSpeciesPictures: JsonElement? = null,
    @SerialName("itsGenbankFileList") val itsGenbankFileList: JsonElement? = null,
    @SerialName("nrlsuGenbankFileList") val nrlsuGenbankFileList: JsonElement? = null,
    @SerialName("tef1GenbankFileList") val tef1GenbankFileList: JsonElement? = null,
    @SerialName("rpb1GenbankFileList") val rpb1GenbankFileList: JsonElement? = null,
    @SerialName("rpb2GenbankFileList") val rpb2GenbankFileList: JsonElement? = null,
    @SerialName("ssuFileList") val ssuFileList: JsonElement? = null,
    @SerialName("tub2FileList") val tub2FileList: JsonElement? = null,
)
