package com.yangzhiguo.mushroom.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [SpeciesEntity::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun speciesDao(): SpeciesDao

    companion object {
        const val DB_NAME = "mushroom.db"
    }
}
