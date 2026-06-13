package com.yangzhiguo.mushroom.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonDecoder

@Serializable
data class ApiResponse(val code: Int = 0, val msg: String? = null, val data: SpecimenPageData? = null)

@Serializable
data class SpecimenDetailResponse(
    val code: Int = 0,
    val msg: String? = null,
    val data: Specimen? = null,
)

@Serializable
data class SpecimenPageData(
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
    val data: SpeciesListPageData? = null,
)

@Serializable
data class SpeciesListPageData(
    val total: Long = 0,
    val specimenSpeciesList: List<Specimen> = emptyList(),
)

@Serializable
data class SpeciesDetailApiResponse(
    val code: Int = 0,
    val msg: String? = null,
    val data: SpeciesDetailData? = null,
)

@Serializable
data class SpeciesDetailData(
    val kibSpecimen: Specimen? = null,
)

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
    @Serializable(with = NullableFlexibleStringSerializer::class)
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
    @SerialName("sysFileList") val sysFileList: JsonElement? = null,
    @SerialName("kibSpeciesPictures") val kibSpeciesPictures: JsonElement? = null,
)

/**
 * The upstream API inconsistently returns textual fields as either JSON strings
 * or numbers (for example `calmSeed: 0`). Preserve the value instead of
 * failing the whole record during deserialization.
 */
@OptIn(ExperimentalSerializationApi::class)
object NullableFlexibleStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NullableFlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            return when (val value = jsonDecoder.decodeJsonElement()) {
                JsonNull -> null
                is JsonPrimitive -> value.content
                else -> value.toString()
            }
        }
        return if (decoder.decodeNotNullMark()) decoder.decodeString() else {
            decoder.decodeNull()
            null
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}