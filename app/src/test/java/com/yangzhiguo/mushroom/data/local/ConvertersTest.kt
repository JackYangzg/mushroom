package com.yangzhiguo.mushroom.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the [Converters] `List<String>` ↔ JSON string round-trip used by
 * `SpeciesEntity.aliasNames`.
 *
 * These tests pin the on-disk format so:
 *  1. The DAO's `LIKE '%query%' COLLATE NOCASE` against `alias_names` keeps
 *     working — the encoded JSON must always surround each element with
 *     double quotes so `"foo"` does not match `"foobar"` in `LIKE`.
 *  2. A null/blank string deserializes to an empty list (graceful upgrade path
 *     for legacy rows pre-v11).
 */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun emptyListEncodesToBracketedJson() {
        val encoded = converters.stringListToString(emptyList())

        // Must look like `[]` so legacy rows that store `[]` round-trip safely.
        assertEquals("[]", encoded)
    }

    @Test
    fun listWithSingleElementIsQuoted() {
        val encoded = converters.stringListToString(listOf("鹅膏菌"))

        assertEquals("[\"鹅膏菌\"]", encoded)
    }

    @Test
    fun listWithMultipleElementsIsCommaSeparated() {
        val encoded = converters.stringListToString(listOf("鹅膏菌", "Amanita", "毒蝇伞"))

        assertEquals("[\"鹅膏菌\",\"Amanita\",\"毒蝇伞\"]", encoded)
    }

    @Test
    fun nullEncodesToNull() {
        assertEquals(null, converters.stringListToString(null))
    }

    @Test
    fun nullOrBlankDecodesToEmptyList() {
        assertEquals(emptyList<String>(), converters.stringToStringList(null))
        assertEquals(emptyList<String>(), converters.stringToStringList(""))
        assertEquals(emptyList<String>(), converters.stringToStringList("   "))
    }

    @Test
    fun roundTripPreservesOrderAndContent() {
        val original = listOf("鹅膏菌", "Amanita muscaria", "fly agaric", "毒蝇伞")
        val encoded = converters.stringListToString(original) ?: error("encode returned null")

        assertEquals(original, converters.stringToStringList(encoded))
    }

    @Test
    fun substringSearchInEncodedJsonFindsElement() {
        // The DAO uses `alias_names LIKE '%query%' COLLATE NOCASE`. Verify the
        // encoded form makes a per-element substring search feasible: a search
        // for an exact element value must succeed, and the JSON quotes must be
        // adjacent to the element so a fuzzy LIKE doesn't drift across the
        // comma boundary.
        val encoded = converters.stringListToString(listOf("鹅膏菌", "毒蝇伞"))!!

        // Sanity: exact element appears, surrounded by JSON quotes / brackets.
        assertTrue("substring '\"鹅膏菌\"' must appear in $encoded", encoded.contains("\"鹅膏菌\""))
        assertTrue("substring '\"毒蝇伞\"' must appear in $encoded", encoded.contains("\"毒蝇伞\""))
    }

    @Test
    fun garbageStringDecodesToEmptyListGracefully() {
        // Legacy rows may have a non-JSON value; the converter must not throw.
        assertEquals(emptyList<String>(), converters.stringToStringList("not json"))
    }
}
