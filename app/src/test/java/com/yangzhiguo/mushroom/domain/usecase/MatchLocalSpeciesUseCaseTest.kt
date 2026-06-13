package com.yangzhiguo.mushroom.domain.usecase

import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.data.repository.SpeciesRepository
import com.yangzhiguo.mushroom.domain.model.Edibility
import com.yangzhiguo.mushroom.domain.model.FeatureTraits
import com.yangzhiguo.mushroom.domain.model.ToxicityLevel
import com.yangzhiguo.mushroom.domain.model.UseType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the v11 behavior: `MatchLocalSpeciesUseCase` counts keyword hits
 * against `aliasNames` (in addition to the pre-existing
 * `identificationPoints` / `chineseName` sources). This guarantees that the
 * memory-side fallback path is symmetric with the DAO's `LIKE` against
 * `alias_names`.
 */
class MatchLocalSpeciesUseCaseTest {

    @Test
    fun traitKeywordMatchingAliasOnlyReturnsTheSpecies() = runBlocking {
        val species = listOf(
            species(
                id = 1,
                chineseName = "松茸",
                identificationPoints = "[]",
                aliasNames = listOf("松口蘑", "Tricholoma matsutake"),
            ),
            species(
                id = 2,
                chineseName = "美味牛肝菌",
                identificationPoints = "[]",
                aliasNames = emptyList(),
            ),
        )
        val useCase = MatchLocalSpeciesUseCase(FakeRepository(species))

        // "松口蘑" is only present in `aliasNames`. The use case must still
        // recognize this as a keyword match.
        val hits = useCase(FeatureTraits(habitat = "松口蘑"))

        assertEquals(1, hits.size)
        assertEquals(1, hits.single().mushroomId)
    }

    @Test
    fun traitKeywordMatchingBothChineseNameAndAliasPicksBoth() = runBlocking {
        // One species matches via `chineseName` only; the other matches via
        // `aliasNames` only. Both must appear in the result set.
        val species = listOf(
            species(
                id = 1,
                chineseName = "红菇",
                identificationPoints = "[]",
                aliasNames = emptyList(),
            ),
            species(
                id = 2,
                chineseName = "青头菌",
                identificationPoints = "[]",
                aliasNames = listOf("红菇", "Red Russula"),
            ),
        )
        val useCase = MatchLocalSpeciesUseCase(FakeRepository(species))

        val hits = useCase(FeatureTraits(habitat = "红菇"))

        // Both rows are candidates (each scores 1 hit, sort order between
        // equal-score rows is implementation-defined).
        assertEquals(2, hits.size)
        val ids = hits.map { it.mushroomId }.toSet()
        assertTrue("alias-having row (id=2) must be in result", 2 in ids)
        assertTrue("chineseName-having row (id=1) must be in result", 1 in ids)
    }

    @Test
    fun emptyKeywordsReturnsEmpty() = runBlocking {
        val species = listOf(species(id = 1, chineseName = "红菇"))
        val useCase = MatchLocalSpeciesUseCase(FakeRepository(species))

        val hits = useCase(FeatureTraits(capColor = "  ", habitat = ""))

        assertTrue(hits.isEmpty())
    }

    @Test
    fun aliasHitsAreScoredHigherThanChineseNameOnly() = runBlocking {
        // Use two keywords: one ("红") that matches `chineseName` of species 1
        // and one of species 2's aliases; another ("别") that only matches
        // species 2's alias entries. The alias-having row scores 2; the
        // chineseName-only row scores 1. The alias row must come first.
        val species = listOf(
            species(
                id = 1,
                chineseName = "红菇",
                identificationPoints = "[]",
                aliasNames = emptyList(),
            ),
            species(
                id = 2,
                chineseName = "青头菌",
                identificationPoints = "[]",
                aliasNames = listOf("红菇别名A", "红菇别名B"),
            ),
        )
        val useCase = MatchLocalSpeciesUseCase(FakeRepository(species))

        val hits = useCase(FeatureTraits(capColor = "红", habitat = "别"))

        assertEquals(2, hits.size)
        assertEquals(2, hits.first().mushroomId)
    }

    // ── helpers ───────────────────────────────────────────────────────

    private fun species(
        id: Int,
        chineseName: String,
        identificationPoints: String = "[]",
        aliasNames: List<String> = emptyList(),
    ): SpeciesEntity = SpeciesEntity(
        mushroomId = id,
        scientificName = "Genus species$id",
        chineseName = chineseName,
        aliasNames = aliasNames,
        identificationPoints = identificationPoints,
        lastUpdated = 1L,
        useType = UseType.UNREPORTED,
        toxicityLevel = ToxicityLevel.NONE,
        edibility = Edibility.UNKNOWN,
        familyZh = "",
        familyLa = "",
        genusZh = "",
        genusLa = "",
    )

    private class FakeRepository(items: List<SpeciesEntity>) : SpeciesRepository {
        private val flow = MutableStateFlow(items)
        override fun observeAll(): Flow<List<SpeciesEntity>> = flow.asStateFlow()
        override fun filterByUseType(useType: UseType) = flow.asStateFlow()
        override fun search(query: String) = flow.asStateFlow()
        override fun observeFavorites() = flow.asStateFlow()
        override suspend fun findByMushroomId(mushroomId: Int) = flow.value.find { it.mushroomId == mushroomId }
        override suspend fun findByMushroomIds(mushroomIds: List<Int>) =
            flow.value.filter { it.mushroomId in mushroomIds }
        override suspend fun refreshDetails(mushroomId: Int) = null
        override suspend fun toggleFavorite(mushroomId: Int) = Unit
    }
}
