package com.yangzhiguo.mushroom.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 物种图片 (1:N of [SpeciesEntity]).
 *
 * 合并 `kibSpeciesPictures[*]` (uf_id/uf_name/uf_src/uf_size/ident) 和
 * `sysFileList[*]` (id/fileName/original/bucketName/speciesId/url/name) 两个
 * 来源;`source` 字段标注出处,`sort_order` 0..N 用于 UI 主图选择。
 *
 * [mushroom_species.imageUrl] / [mushroom_species.images] 仍由 mapper 从
 * 此表聚合写入,作为旧 UI 的快速路径。
 */
@Entity(
    tableName = "mushroom_image",
    primaryKeys = ["species_id", "uf_id"],
    foreignKeys = [ForeignKey(
        entity = SpeciesEntity::class,
        parentColumns = ["id"],
        childColumns = ["species_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("species_id"),
        Index("source"),
        Index("sort_order"),
    ],
)
data class SpeciesImageEntity(
    @ColumnInfo("species_id") val speciesId: Int,
    @ColumnInfo("uf_id") val ufId: String,
    @ColumnInfo("uf_name") val ufName: String? = null,
    @ColumnInfo("uf_src") val ufSrc: String,
    @ColumnInfo("uf_size") val ufSize: Long? = null,
    @ColumnInfo("ident") val ident: String? = null,
    @ColumnInfo("source") val source: String,
    @ColumnInfo("sort_order") val sortOrder: Int = 0,
    @ColumnInfo("local_path") val localPath: String? = null,
) {
    companion object {
        const val SOURCE_KIB_PICTURES = "kibSpeciesPictures"
        const val SOURCE_SYS_FILE = "sysFileList"
    }
}
