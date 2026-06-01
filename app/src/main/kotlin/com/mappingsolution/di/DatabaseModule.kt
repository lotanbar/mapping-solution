package com.mappingsolution.di

import android.content.Context
import com.mappingsolution.data.fs.GroupFileRepository
import com.mappingsolution.data.fs.PlanFileRepository
import com.mappingsolution.data.fs.PoiFileRepository
import com.mappingsolution.data.fs.RouteFileRepository
import com.mappingsolution.data.util.StorageManager
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
    fun provideStorageManager(@ApplicationContext context: Context): StorageManager = StorageManager(context)

    @Provides
    @Singleton
    fun provideGroupFileRepository(
        @ApplicationContext context: Context,
        storageManager: StorageManager,
    ): GroupFileRepository = GroupFileRepository(context, storageManager)

    @Provides
    @Singleton
    fun providePoiFileRepository(storageManager: StorageManager): PoiFileRepository =
        PoiFileRepository(storageManager)

    @Provides
    @Singleton
    fun provideRouteFileRepository(storageManager: StorageManager): RouteFileRepository =
        RouteFileRepository(storageManager)

    @Provides
    @Singleton
    fun providePlanFileRepository(storageManager: StorageManager): PlanFileRepository =
        PlanFileRepository(storageManager)

    @Provides
    @Singleton
    fun provideRasterLayerRepository(storageManager: StorageManager): com.mappingsolution.data.fs.RasterLayerRepository =
        com.mappingsolution.data.fs.RasterLayerRepository(storageManager)
}
