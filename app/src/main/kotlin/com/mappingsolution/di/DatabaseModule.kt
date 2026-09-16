package com.mappingsolution.di

import java.io.File
import com.mappingsolution.data.fs.ExportRepository
import android.content.Context
import com.mappingsolution.data.fs.GroupFileRepository
import com.mappingsolution.data.fs.PlanFileRepository
import com.mappingsolution.data.fs.PoiFileRepository
import com.mappingsolution.data.fs.RouteFileRepository
import com.mappingsolution.BuildConfig
import com.mappingsolution.data.util.ApiKeys
import com.mappingsolution.data.util.KeyValueStore
import com.mappingsolution.data.util.SharedPreferencesStore
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
    fun provideStorageManager(@ApplicationContext context: Context): StorageManager =
        StorageManager(context.getExternalFilesDir(null) ?: context.filesDir, context.cacheDir)

    @Provides
    @Singleton
    fun provideKeyValueStoreFactory(@ApplicationContext context: Context): KeyValueStore.Factory =
        KeyValueStore.Factory { name -> SharedPreferencesStore(context.getSharedPreferences(name, Context.MODE_PRIVATE)) }

    @Provides
    @Singleton
    fun provideApiKeys(): ApiKeys = ApiKeys(
        mapTiler = BuildConfig.MAPTILER_API_KEY,
        mapillary = BuildConfig.MAPILLARY_ACCESS_TOKEN,
    )

    @Provides
    @Singleton
    fun provideGroupFileRepository(
        storageManager: StorageManager,
        stores: KeyValueStore.Factory,
    ): GroupFileRepository = GroupFileRepository(storageManager, stores)

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

    @Provides
    @Singleton
    fun provideExportRepository(
        @ApplicationContext context: Context,
        poiRepository: PoiFileRepository,
        routeRepository: RouteFileRepository,
    ): ExportRepository = ExportRepository(File(context.filesDir, "exports"), poiRepository, routeRepository)
}
