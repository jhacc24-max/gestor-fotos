package com.example.gestorfotos

import android.app.Application
import androidx.work.*
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.gestorfotos.workers.IndexingWorker
import java.util.concurrent.TimeUnit

class GestorFotosApp : Application(), ImageLoaderFactory {

    /** Un único cargador con caché grande en memoria y disco, y soporte para miniaturas de video. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.30).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("thumbs"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .crossfade(false)
        .allowRgb565(true)
        .build()

    override fun onCreate() {
        super.onCreate()

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val indexRequest = PeriodicWorkRequestBuilder<IndexingWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "photo_indexing",
            ExistingPeriodicWorkPolicy.KEEP,
            indexRequest
        )

        // El indexado pesado (OCR/hash) se lanza al abrir la app y no compite con la galería.
        WorkManager.getInstance(this).enqueueUniqueWork(
            "startup_indexing",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<IndexingWorker>().build()
        )
    }
}
