package com.yangzhiguo.mushroom.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yangzhiguo.mushroom.domain.model.Edibility
import com.yangzhiguo.mushroom.domain.model.ToxicityLevel
import com.yangzhiguo.mushroom.domain.model.UseType

/**
 * Mirrors a row in `mushroom_species` table. Schema follows `design_doc §10.1`
 * (23 fields, with 13 MVP-required and 10 optional).
 *
 * JSON list fields (`identificationPoints`, `images`) are stored as JSON-encoded
 * strings via [Converters] — we keep one table and avoid a join table for the
 * prototype. Comma-joined fields (`habitat`, `season`, `distribution`, `lookAlikeIds`)
 * are split at render time.
 */
@Entity(tableName = "mushroom_species")
data class SpeciesEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo("scientific_name") val scientificName: String,
    @ColumnInfo("chinese_name") val chineseName: String,
    @ColumnInfo("family_zh") val familyZh: String,
    @ColumnInfo("family_la") val familyLa: String,
    @ColumnInfo("genus_zh") val genusZh: String,
    @ColumnInfo("genus_la") val genusLa: String,
    @ColumnInfo("authority") val authority: String = "",
    @ColumnInfo("use_type") val useType: UseType,
    @ColumnInfo("toxicity_level") val toxicityLevel: ToxicityLevel,
    @ColumnInfo("edibility") val edibility: Edibility,
    @ColumnInfo("cap_description") val capDescription: String,
    @ColumnInfo("gill_description") val gillDescription: String,
    @ColumnInfo("stipe_description") val stipeDescription: String,
    @ColumnInfo("ring_description") val ringDescription: String,
    @ColumnInfo("volva_description") val volvaDescription: String,
    @ColumnInfo("spore_description") val sporeDescription: String = "",
    @ColumnInfo("habitat") val habitat: String,           // comma-joined: 针叶林,混交林
    @ColumnInfo("season") val season: String,             // comma-joined: 夏,秋
    @ColumnInfo("distribution") val distribution: String = "",
    @ColumnInfo("altitude_range") val altitudeRange: String = "",
    @ColumnInfo("identification_points") val identificationPoints: String, // JSON list<String>
    @ColumnInfo("look_alike_ids") val lookAlikeIds: String = "",            // comma-joined
    @ColumnInfo("toxicity_symptoms") val toxicitySymptoms: String = "",
    @ColumnInfo("images") val images: String,                              // JSON list<SpeciesImage>
    @ColumnInfo("model_3d_url") val model3dUrl: String? = null,
    @ColumnInfo("dna_barcode") val dnaBarcode: String? = null,
    @ColumnInfo("source_url") val sourceUrl: String = "",
    @ColumnInfo("image_url") val imageUrl: String? = null,                // 远端主图 URL，scrape 时填
    @ColumnInfo("image_local_path") val imageLocalPath: String? = null,    // 本地缓存路径（filesDir 相对），查看页面后填充
    @ColumnInfo("is_favorite") val isFavorite: Boolean = false,           // 我的收藏(用户标记)
    @ColumnInfo("last_updated") val lastUpdated: Long,
)
