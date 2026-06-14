package com.yangzhiguo.mushroom.di

import com.yangzhiguo.mushroom.BuildConfig
import com.yangzhiguo.mushroom.data.local.SpeciesDao
import com.yangzhiguo.mushroom.recognition.DetailLruCache
import com.yangzhiguo.mushroom.recognition.DoubaoApiClient
import com.yangzhiguo.mushroom.recognition.MiniMaxApiClient
import com.yangzhiguo.mushroom.recognition.MushroomIndex
import com.yangzhiguo.mushroom.recognition.MushroomRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 识别流程的 Hilt 注入集中地。
 */
@Module
@InstallIn(SingletonComponent::class)
object RecognitionModule {

    @Provides
    @Singleton
    fun provideDoubaoApiClient(): DoubaoApiClient = DoubaoApiClient(
        apiKey = BuildConfig.ARK_API_KEY,
        baseUrl = BuildConfig.ARK_API_BASE,
        model = BuildConfig.ARK_MODEL,
    )

    @Provides
    @Singleton
    fun provideMiniMaxApiClient(): MiniMaxApiClient = MiniMaxApiClient(
        apiKey = BuildConfig.MINIMAX_API_KEY,
        baseUrl = BuildConfig.MINIMAX_API_BASE,
        model = BuildConfig.MINIMAX_MODEL,
    )

    @Provides
    @Singleton
    fun provideDetailLruCache(): DetailLruCache = DetailLruCache()

    @Provides
    @Singleton
    fun provideMushroomRepository(
        index: MushroomIndex,
        cache: DetailLruCache,
        speciesDao: SpeciesDao,
    ): MushroomRepository = MushroomRepository(
        index = index,
        cache = cache,
        speciesDao = speciesDao,
    )
}
