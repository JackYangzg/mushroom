package com.yangzhiguo.mushroom.data.repository

import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.scraper.Specimen
import kotlin.math.abs

internal object SpeciesIdentity {
    fun matches(current: SpeciesEntity, detail: Specimen): Boolean =
        detail.id == abs(current.mushroomId.toLong()) &&
            normalize(detail.speciesLatin) == normalize(current.scientificName)

    private fun normalize(value: String?): String =
        value.orEmpty()
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
}
