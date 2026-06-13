package com.yangzhiguo.mushroom.domain.usecase

import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.data.repository.SpeciesRepository
import com.yangzhiguo.mushroom.domain.model.FeatureTraits
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Cheap keyword-based candidate match. Real AI pre-screen would call PlantNet
 * or iflora API; the prototype does substring match on identificationPoints.
 *
 * Returns 0-3 species. An empty list means the user's traits are too sparse
 * to confidently match anything — FeatureFormScreen then surfaces the
 * "您填的特征较少匹配" warning before letting the user submit.
 */
class MatchLocalSpeciesUseCase @Inject constructor(
    private val repo: SpeciesRepository,
) {
    suspend operator fun invoke(traits: FeatureTraits): List<SpeciesEntity> {
        val all: List<SpeciesEntity> = repo.observeAll().first()
        if (all.isEmpty()) return emptyList()

        val keywords = buildList {
            traits.capColor?.takeIf { it.isNotBlank() }?.let { add(it) }
            traits.habitat?.takeIf { it.isNotBlank() }?.let { add(it) }
            traits.ring?.let { if (it == "有") add("菌环") }
            traits.volva?.let { if (it == "有") add("菌托") }
            if (isEmpty()) return emptyList()
        }

        val scored = all.mapNotNull { sp ->
            val idp = sp.identificationPoints
            val hits = keywords.count { kw ->
                idp.contains(kw) ||
                    sp.chineseName.contains(kw) ||
                    sp.aliasNames.any { it.contains(kw) }
            }
            if (hits == 0) null else sp to hits
        }.sortedByDescending { it.second }
        return scored.take(3).map { it.first }
    }
}
