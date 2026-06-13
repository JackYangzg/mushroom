package com.yangzhiguo.mushroom.data.seed

import android.content.Context
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads `app/src/main/assets/seed_species.json` and parses it to a list of
 * [SpeciesEntity]. Pure I/O; idempotent.
 */
@Singleton
class SeedAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val SEED_FILE_NAME = "seed_species.json"

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    fun load(): List<SpeciesEntity> {
        val raw = context.assets.open(SEED_FILE_NAME).bufferedReader().use { it.readText() }
        val dtoList = json.decodeFromString<List<SeedSpeciesDto>>(raw)
        return dtoList.map { it.toEntity() }
    }

    @Serializable
    data class SeedSpeciesDto(
        val scientific_name: String,
        val chinese_name: String,
        val alias_names: List<String> = emptyList(),
        val family_zh: String,
        val family_la: String,
        val genus_zh: String,
        val genus_la: String,
        val authority: String = "",
        val use_type: String,
        val toxicity_level: Int,
        val edibility: String,
        val cap_description: String,
        val lamella_description: String = "",
        val stipe_description: String,
        val ring_description: String = "",
        val volva_description: String = "",
        val spore_description: String = "",
        val habitat: String,
        val season: String,
        val altitude_range: String = "",
        val identification_points: List<String> = emptyList(),
        val look_alike_ids: List<Int> = emptyList(),
        val toxicity_symptoms: String = "",
        val images: List<SeedImageDto> = emptyList(),
        val source_url: String = "",
        val scraw_source: String = SpeciesEntity.SCRAW_SOURCE_GENERAL_DIRECTORY,
    ) {
        fun toEntity(): SpeciesEntity = SpeciesEntity(
            id = 0L,  // SQLite 自增
            mushroomId = 0,  // 种子数据无业务 id;SeedDataInitializer 走 INSERT OR IGNORE,允许共用 0
            scientificName = scientific_name,
            chineseName = chinese_name,
            aliasNames = alias_names,
            familyZh = family_zh,
            familyLa = family_la,
            genusZh = genus_zh,
            genusLa = genus_la,
            authority = authority,
            useType = com.yangzhiguo.mushroom.domain.model.UseType.valueOf(use_type),
            toxicityLevel = com.yangzhiguo.mushroom.domain.model.ToxicityLevel.fromInt(toxicity_level),
            edibility = com.yangzhiguo.mushroom.domain.model.Edibility.valueOf(edibility),
            capDescription = cap_description,
            lamellaDescription = lamella_description,
            stipeDescription = stipe_description,
            ringDescription = ring_description,
            volvaDescription = volva_description,
            sporeDescription = spore_description,
            habitat = habitat,
            season = season,
            altitudeRange = altitude_range,
            identificationPoints = SeedAssetLoader.json.encodeToString(
                ListSerializer(String.serializer()),
                identification_points,
            ),
            lookAlikeIds = look_alike_ids.joinToString(","),
            toxicitySymptoms = toxicity_symptoms,
            images = SeedAssetLoader.json.encodeToString(
                ListSerializer(SeedImageDto.serializer()),
                images,
            ),
            sourceUrl = source_url,
            scrawSource = scraw_source,
            lastUpdated = System.currentTimeMillis() / 1000,
        )
    }

    @Serializable
    data class SeedImageDto(
        val url: String,
        val caption: String = "",
        val photographer: String = "",
        val license: String = "",
        val captured_at: String = "",
    )


}
