package com.example.gestorfotos.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.gestorfotos.workers.IndexingWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MediaStoreObserver(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var debounceJob: Job? = null

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        
        // Debounce: Evita disparar múltiples Workers si el sistema guarda imágenes en ráfaga
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(1500) // Espera 1.5s de inactividad
            triggerImmediateIndexing()
        }
    }

    private fun triggerImmediateIndexing() {
        val request = OneTimeWorkRequestBuilder<IndexingWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "immediate_indexing_work",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun register() {
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            this
        )
    }

    fun unregister() {
        context.contentResolver.unregisterContentObserver(this)
    }
}
