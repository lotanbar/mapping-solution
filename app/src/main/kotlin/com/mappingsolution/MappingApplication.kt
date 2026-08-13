package com.mappingsolution

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import okhttp3.Interceptor
import com.mappingsolution.data.image.ZipImageFetcher
import com.mappingsolution.data.map.MbTilesInterceptor
import com.mappingsolution.data.migration.LegacyDbMigration
import com.mappingsolution.data.migration.StorageV2Migration
import com.mappingsolution.data.util.StorageManager
import com.mappingsolution.service.ImportWorker
import com.mappingsolution.service.MbtilesImportWorker
import com.mappingsolution.service.RecordingService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class MappingApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var mbTilesInterceptor: MbTilesInterceptor

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // MapLibre must be initialized before anything that touches its static context
        org.maplibre.android.MapLibre.getInstance(this)

        // Configure Coil with a User-Agent so Wikimedia Commons doesn't 403 image downloads
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(
                    OkHttpClient.Builder()
                        .addInterceptor(Interceptor { chain ->
                            chain.proceed(
                                chain.request().newBuilder()
                                    .header(
                                        "User-Agent",
                                        "mapping-solution/1.0 (https://github.com/lotanbar/mapping-solution)",
                                    )
                                    .build()
                            )
                        })
                        .build()
                )
                .components {
                    add(VideoFrameDecoder.Factory())
                    add(ZipImageFetcher.Factory())
                }
                .build()
        )

        // Register custom OkHttp client so MapLibre serves local MBTiles tiles
        HttpRequestUtil.setOkHttpClient(
            OkHttpClient.Builder()
                .addInterceptor(mbTilesInterceptor)
                .build()
        )

        val storageManager = StorageManager(this)
        val marker = File(storageManager.rootDir, ".migrated")
        if (!marker.exists()) {
            runBlocking(Dispatchers.IO) {
                LegacyDbMigration(this@MappingApplication, storageManager).run()
                marker.createNewFile()
            }
        }

        val v2Marker = File(storageManager.rootDir, ".storage_v2")
        if (!v2Marker.exists()) {
            runBlocking(Dispatchers.IO) {
                StorageV2Migration(this@MappingApplication, storageManager).run()
                v2Marker.createNewFile()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    RecordingService.NOTIF_CHANNEL_ID,
                    "Route Recording",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Shows active route recording status" }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    ImportWorker.NOTIF_CHANNEL_ID,
                    "POI Import",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Shows progress while importing POIs" }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    MbtilesImportWorker.NOTIF_CHANNEL_ID,
                    "Raster Layer Import",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Shows progress while importing MBTiles raster layers" }
            )
        }
    }
}
