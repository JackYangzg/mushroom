package com.yangzhiguo.mushroom.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * 物种图片 (关联到 [SpeciesEntity.mushroomId],**不是** PK)。
 *
 * 合并 `kibSpeciesPictures[*]` (uf_id/uf_name/uf_src/uf_size/ident) 和
 * `sysFileList[*]` (id/fileName/original/bucketName/speciesId/url/name) 两个
 * 来源;`source` 字段标注出处,`sort_order` 0..N 用于 UI 主图选择。
 *
 * v10 设计:
 * - `mushroom_id` 直接对应 iflora API 业务 id,**不**依赖 SpeciesEntity PK(id Long 自增)。
 * - 同一 mushroom_id 可能在 `mushroom_species` 中有 N 行(每个 scraw_source 一行),
 *   图片独立存在这张表里,跨 source 累加;UI 通过 mushroom_id 聚合读取。
 *   修复「不同蘑菇图片混到一个蘑菇下」bug:旧版用 species_id(=旧 PK)关联,resync 时 PK 重
 *   分配会让图片错位;新版按业务 id 关联,resync 时业务 id 不变,图片始终绑到正确蘑菇。
 */
@Entity(
    tableName = "mushroom_image",
    primaryKeys = ["mushroom_id", "uf_id"],
    indices = [
        Index("mushroom_id"),
        Index("source"),
        Index("sort_order"),
    ],
)
data class SpeciesImageEntity(
    @ColumnInfo("mushroom_id") val mushroomId: Int,
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