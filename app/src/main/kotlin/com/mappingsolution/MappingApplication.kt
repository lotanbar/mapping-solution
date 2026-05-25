package com.mappingsolution

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mappingsolution.data.map.MbTilesInterceptor
import com.mappingsolution.data.migration.LegacyDbMigration
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
