package com.yangzhiguo.mushroom.data.favorite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoriteStoreTest {
    private lateinit var context: Context
    private lateinit var file: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        file = File(context.filesDir, FavoriteStore.FILE_NAME)
        file.delete()
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun togglePersistsFavoritesAcrossStoreInstances() = runBlocking {
        val store = FavoriteStore(context)

        assertTrue(store.toggle(42))
        assertTrue(store.isFavorite(42))

        val restored = FavoriteStore(context)
        assertTrue(restored.isFavorite(42))
        assertFalse(restored.toggle(42))
        assertFalse(FavoriteStore(context).isFavorite(42))
    }

    @Test
    fun legacyFavoritesAreImportedOnlyOnce() {
        val store = FavoriteStore(context)

        store.importLegacyFavorites(setOf(7, 9))
        store.importLegacyFavorites(setOf(11))

        assertEquals(setOf(7, 9), FavoriteStore(context).ids.value)
        assertTrue(JSONObject(file.readText()).getBoolean("legacyImported"))
    }
}
