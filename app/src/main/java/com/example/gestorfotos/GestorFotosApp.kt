package com.example.gestorfotos

import android.app.Application
import androidx.work.*
import com.example.gestorfotos.workers.IndexingWorker
import java.util.concurrent.TimeUnit

class GestorFotosApp : Application() {

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

        // También se lanza una vez al abrir la app para que el OCR/hash no tarde 6 horas
        // en aplicarse a fotos recién importadas.
        WorkManager.getInstance(this).enqueue(
            OneTimeWorkRequestBuilder<IndexingWorker>().build()
        )
    }
}
