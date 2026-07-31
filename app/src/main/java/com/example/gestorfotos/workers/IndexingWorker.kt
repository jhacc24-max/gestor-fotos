package com.example.gestorfotos.workers

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.gestorfotos.data.AppDatabase
import com.example.gestorfotos.repository.PhotoRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * Procesa fotos nuevas en lotes: texto visible (OCR), hash perceptual (duplicados)
 * y nitidez (borrosas). Se agenda periódicamente y también se puede lanzar una sola vez
 * justo después de tomar/importar una foto.
 */
class IndexingWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repo = PhotoRepository(applicationContext)
        repo.refreshMediaStore()

        val dao = AppDatabase.getInstance(applicationContext).photoMetaDao()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val pending = dao.getUnanalyzed(limit = 25)
        for (meta in pending) {
            try {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, meta.mediaStoreId
                )

                val bitmap = loadDownsampledBitmap(applicationContext, uri, 512) ?: continue

                val ocrText = try {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    recognizer.process(image).await().text
                } catch (_: Exception) {
                    ""
                }

                val hash = computePerceptualHash(bitmap)
                val blur = computeBlurVariance(bitmap)

                dao.upsert(
                    meta.copy(
                        ocrText = ocrText,
                        perceptualHash = hash,
                        blurScore = blur,
                        analyzedAt = System.currentTimeMillis()
                    )
                )
                bitmap.recycle()
            } catch (_: Exception) {
                // Se ignora esta foto y se continúa; quedará pendiente para el próximo ciclo
                // si analyzedAt sigue en null (aquí se podría además marcar un contador de reintentos).
            }
        }

        if (pending.size == 25) Result.success() else Result.success()
    }

    private fun loadDownsampledBitmap(context: Context, uri: android.net.Uri, targetSize: Int): Bitmap? {
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        var sample = 1
        while (options.outWidth / (sample * 2) >= targetSize && options.outHeight / (sample * 2) >= targetSize) {
            sample *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
    }

    /**
     * pHash de 64 bits: reduce a 32x32 en escala de grises, aplica una DCT separable
     * (fila por fila y luego columna por columna, O(n^3) en vez de O(n^4)), se queda con
     * el bloque 8x8 de baja frecuencia (sin el término DC) y umbraliza por la mediana.
     * A diferencia de un aHash simple, esto sobrevive bien a recortes leves, cambios de
     * brillo/contraste y recompresión JPEG — el caso típico de "casi duplicados" reales.
     */
    private fun computePerceptualHash(source: Bitmap): String {
        val n = 32
        val small = Bitmap.createScaledBitmap(source, n, n, true)
        val gray = Array(n) { y ->
            DoubleArray(n) { x ->
                val p = small.getPixel(x, y)
                (android.graphics.Color.red(p) + android.graphics.Color.green(p) + android.graphics.Color.blue(p)) / 3.0
            }
        }
        small.recycle()

        // DCT sobre las filas
        val rowsTransformed = Array(n) { y -> dct1D(gray[y]) }
        // DCT sobre las columnas del resultado anterior
        val dct = Array(n) { DoubleArray(n) }
        for (x in 0 until n) {
            val column = DoubleArray(n) { y -> rowsTransformed[y][x] }
            val transformedColumn = dct1D(column)
            for (y in 0 until n) dct[y][x] = transformedColumn[y]
        }

        val low = mutableListOf<Double>()
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (x == 0 && y == 0) continue // término DC: domina la escala, no aporta a similitud estructural
                low += dct[y][x]
            }
        }
        val median = low.sorted()[low.size / 2]
        return low.joinToString("") { if (it > median) "1" else "0" }
    }

    /** DCT-II de 1 dimensión con normalización estándar. */
    private fun dct1D(vector: DoubleArray): DoubleArray {
        val n = vector.size
        val result = DoubleArray(n)
        for (u in 0 until n) {
            var sum = 0.0
            for (x in 0 until n) {
                sum += vector[x] * Math.cos(Math.PI / n * (x + 0.5) * u)
            }
            val cu = if (u == 0) 1.0 / Math.sqrt(2.0) else 1.0
            result[u] = sum * cu * Math.sqrt(2.0 / n)
        }
        return result
    }

    /** Varianza del Laplaciano sobre una versión pequeña en escala de grises: bajo = imagen borrosa. */
    private fun computeBlurVariance(source: Bitmap): Double {
        val w = 200
        val h = (source.height * (w.toFloat() / source.width)).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, w, h, true)

        val gray = Array(h) { y -> DoubleArray(w) { x ->
            val p = small.getPixel(x, y)
            (android.graphics.Color.red(p) + android.graphics.Color.green(p) + android.graphics.Color.blue(p)) / 3.0
        } }

        val laplacian = mutableListOf<Double>()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val value = -4 * gray[y][x] + gray[y - 1][x] + gray[y + 1][x] + gray[y][x - 1] + gray[y][x + 1]
                laplacian += value
            }
        }
        small.recycle()
        if (laplacian.isEmpty()) return 0.0
        val mean = laplacian.average()
        return laplacian.sumOf { (it - mean).pow(2) } / laplacian.size
    }
}
