package com.yangzhiguo.mushroom.data.seed

import android.util.Log
import com.yangzhiguo.mushroom.data.local.SpeciesDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Idempotent first-launch importer: reads [SeedAssetLoader] and inserts into
 * Room only when the species table is empty. Per `design_doc §6` "S1 弹 Toast +
 * 重试按钮,不直接退出" — we surface failure via a thrown exception; SplashViewModel
 * catches and shows a retry button.
 */
@Singleton
class SeedDataInitializer @Inject constructor(
    private val loader: SeedAssetLoader,
    private val speciesDao: SpeciesDao,
) {
    suspend fun ensureSeeded() {
        val existing = speciesDao.count()
        if (existing > 0) {
            Log.d(TAG, "Seed already present ($existing rows), skipping.")
            return
        }
        val species = loader.load()
        speciesDao.insertAll(species)
        Log.d(TAG, "Seeded ${species.size} species.")
    }

    companion object {
        private const val TAG = "SeedDataInitializer"
    }
}
