package com.mappingsolution

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.SingletonImageLoader
import com.mappingsolution.ui.image.AppImageLoader
import com.mappingsolution.data.map.MbTilesInterceptor
import com.mappingsolution.data.migration.LegacyDbMigration
import com.mappingsolution.data.migration.StorageV2Migration
import com.mappingsolution.data.util.AppLog
import com.mappingsolution.data.util.StorageManager
import com.mappingsolution.service.ImportWorker
import com.mappingsolution.service.MbtilesImportWorker
import com.mappingsolution.service.RecordingService
import com.mappingsolution.service.RouteRefinementWorker
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
        AppLog.sink = object : AppLog.Sink {
            override fun d(tag: String, message: String) { Log.d(tag, message) }
            override fun i(tag: String, message: String) { Log.i(tag, message) }
            override fun w(tag: String, message: String, error: Throwable?) { Log.w(tag, message, error) }
            override fun e(tag: String, message: String, error: Throwable?) { Log.e(tag, message, error) }
        }

        // MapLibre must be initialized before anything that touches its static context
        org.maplibre.android.MapLibre.getInstance(this)

        // Shared Coil loader: Wikimedia User-Agent plus zip-backed POI images.
        SingletonImageLoader.setSafe { context -> AppImageLoader.create(context) }

        // Register custom OkHttp client so MapLibre serves local MBTiles tiles
        HttpRequestUtil.setOkHttpClient(
            OkHttpClient.Builder()
                .addInterceptor(mbTilesInterceptor)
                .build()
        )

        val storageManager = StorageManager(getExternalFilesDir(null) ?: filesDir, cacheDir)
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
            manager.createNotificationChannel(
                NotificationChannel(
                    RouteRefinementWorker.NOTIF_CHANNEL_ID,
                    "Route Refinement",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Shows progress while refining recorded routes" }
            )
        }
    }
}
