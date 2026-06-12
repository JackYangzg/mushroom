package com.yangzhiguo.mushroom.di

import android.content.Context
import androidx.room.Room
import com.yangzhiguo.mushroom.data.local.AppDatabase
import com.yangzhiguo.mushroom.data.local.SpeciesDao
import com.yangzhiguo.mushroom.data.repository.SpeciesRepository
import com.yangzhiguo.mushroom.data.repository.SpeciesRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .createFromAsset("mushroom.db")  // 首次安装把 assets/mushroom.db 拷到 /data/data/.../databases/
            .fallbackToDestructiveMigration()  // dev 阶段:v5→v6 直接重建
            .build()

    @Provides
    fun provideSpeciesDao(db: AppDatabase): SpeciesDao = db.speciesDao()

    @Provides
    fun provideSpecimenDao(db: AppDatabase): com.yangzhiguo.mushroom.data.local.SpecimenDao = db.specimenDao()

    @Provides
    fun provideDnaBarcodeDao(db: AppDatabase): com.yangzhiguo.mushroom.data.local.DnaBarcodeDao = db.dnaBarcodeDao()

    @Provides
    fun provideDistributionPointDao(db: AppDatabase): com.yangzhiguo.mushroom.data.local.DistributionPointDao = db.distributionPointDao()

    @Provides
    fun provideSpeciesImageDao(db: AppDatabase): com.yangzhiguo.mushroom.data.local.SpeciesImageDao = db.speciesImageDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSpeciesRepository(impl: SpeciesRepositoryImpl): SpeciesRepository
}

@Module
@InstallIn(SingletonComponent::class)
object ScraperModule {
    @Provides @Singleton
    fun provideApiClient(): com.yangzhiguo.mushroom.scraper.ApiClient = com.yangzhiguo.mushroom.scraper.ApiClient()
}
