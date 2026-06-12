package com.yangzhiguo.mushroom.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Schema v6 — full Specimen payload + 4 child tables.
 *
 * Migration policy: **dev phase skips compat**. The provider configures
 * `fallbackToDestructiveMigration()`, so any old `mushroom.db` is wiped on
 * first launch. Re-enable an explicit `MIGRATION_X_Y` only when shipping
 * a release build that must preserve existing user data.
 */
@Database(
    entities = [
        SpeciesEntity::class,
        SpecimenEntity::class,
        DnaBarcodeEntity::class,
        DistributionPointEntity::class,
        SpeciesImageEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun speciesDao(): SpeciesDao
    abstract fun specimenDao(): SpecimenDao
    abstract fun dnaBarcodeDao(): DnaBarcodeDao
    abstract fun distributionPointDao(): DistributionPointDao
    abstract fun speciesImageDao(): SpeciesImageDao

    companion object {
        const val DB_NAME = "mushroom.db"
    }
}
