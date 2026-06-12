package com.yangzhiguo.mushroom.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 物种分布点 (1:N of [SpeciesEntity]).
 *
 * Source: `getSpeciesDnaAndLib.detailsLatAndLon[*]`.
 * (speciesId, lng, lat) 联合主键;同一经纬度累加 count 即可。
 */
@Entity(
    tableName = "mushroom_distribution_point",
    primaryKeys = ["species_id", "lng", "lat"],
    foreignKeys = [ForeignKey(
        entity = SpeciesEntity::class,
        parentColumns = ["id"],
        childColumns = ["species_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("species_id"),
        Index("province"),
    ],
)
data class DistributionPointEntity(
    @ColumnInfo("species_id") val speciesId: Int,
    @ColumnInfo("lng") val lng: Double,
    @ColumnInfo("lat") val lat: Double,
    @ColumnInfo("value") val value: String? = null,
    @ColumnInfo("province") val province: String? = null,
    @ColumnInfo("count") val count: Int = 1,
)
