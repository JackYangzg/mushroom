package com.yangzhiguo.mushroom.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 馆藏标本 (1:N of [SpeciesEntity]).
 *
 * 一个 species 可以挂多条实际馆藏标本;每条来自
 * `getSpeciesDnaAndLib.specimenDnaLibList` 或独立 `getSpecimenById`。
 * `collectProvince/City/District` 保留后端 6 位行政区划码;同时落一份中文名
 * (`*_zh`) 方便 UI 直接展示 — 由 [com.yangzhiguo.mushroom.util.RegionCodeDecoder]
 * 解析;解析不到的留 null + mapper 写 WARN 日志。
 */
@Entity(
    tableName = "mushroom_specimen",
    foreignKeys = [ForeignKey(
        entity = SpeciesEntity::class,
        parentColumns = ["id"],
        childColumns = ["species_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("species_id"),
        Index("gather_num"),
        Index("collection_num"),
    ],
)
data class SpecimenEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo("species_id") val speciesId: Int,
    @ColumnInfo("gather_num") val gatherNum: String? = null,
    @ColumnInfo("collection_num") val collectionNum: String? = null,
    @ColumnInfo("strain_number") val strainNumber: String? = null,
    @ColumnInfo("collect_user") val collectUser: String? = null,
    @ColumnInfo("collect_unit") val collectUnit: String? = null,
    @ColumnInfo("research_team") val researchTeam: String? = null,
    @ColumnInfo("collect_time") val collectTime: String? = null,
    @ColumnInfo("collect_country") val collectCountry: String? = null,
    @ColumnInfo("collect_province") val collectProvince: String? = null,
    @ColumnInfo("collect_province_zh") val collectProvinceZh: String? = null,
    @ColumnInfo("collect_city") val collectCity: String? = null,
    @ColumnInfo("collect_city_zh") val collectCityZh: String? = null,
    @ColumnInfo("collect_district") val collectDistrict: String? = null,
    @ColumnInfo("collect_district_zh") val collectDistrictZh: String? = null,
    @ColumnInfo("collect_village") val collectVillage: String? = null,
    @ColumnInfo("latitude") val latitude: String? = null,
    @ColumnInfo("longitude") val longitude: String? = null,
    @ColumnInfo("altitude") val altitude: String? = null,
    @ColumnInfo("specimen_habitat") val specimenHabitat: String? = null,
    @ColumnInfo("field_note") val fieldNote: String? = null,
    @ColumnInfo("resource_type") val resourceType: String? = null,
    @ColumnInfo("create_time") val createTime: String? = null,
    @ColumnInfo("notes") val notes: String? = null,
    @ColumnInfo("source_url") val sourceUrl: String? = null,
)
