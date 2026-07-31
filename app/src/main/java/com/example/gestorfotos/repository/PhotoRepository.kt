package com.example.gestorfotos.repository

import com.example.gestorfotos.utils.PerceptualHash
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.gestorfotos.data.AppDatabase
import com.example.gestorfotos.data.PhotoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

data class MediaImage(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAddedMillis: Long
)

/** Modelo unificado que usa la UI: fusiona la foto real (MediaStore) con sus metadatos (Room). */
data class UiPhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAddedMillis: Long,
    val albumId: Long?,
    val isFavorite: Boolean,
    val isTrashed: Boolean,
    val trashedAt: Long?,
    val rotationDegrees: Int,
    val croppedUri: String?,
    val tags: List<String>,
    val ocrText: String,
    val perceptualHash: String?,
    val blurScore: Double?
) {
    val displayUri: Uri get() = croppedUri?.let(Uri::parse) ?: uri
}

class PhotoRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val albumDao = db.albumDao()
    private val metaDao = db.photoMetaDao()

    private val _mediaImages = MutableStateFlow<List<MediaImage>>(emptyList())

    val albums = albumDao.observeAlbums()

    val photos: Flow<List<UiPhoto>> = combine(_mediaImages, metaDao.observeAll()) { media, metas ->
        val metaById = metas.associateBy { it.mediaStoreId }
        media.map { m ->
            val meta = metaById[m.id]
            UiPhoto(
                id = m.id,
                uri = m.uri,
                displayName = m.displayName,
                dateAddedMillis = m.dateAddedMillis,
                albumId = meta?.albumId,
                isFavorite = meta?.isFavorite ?: false,
                isTrashed = meta?.isTrashed ?: false,
                trashedAt = meta?.trashedAt,
                rotationDegrees = meta?.rotationDegrees ?: 0,
                croppedUri = meta?.croppedUri,
                tags = meta?.manualTags?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                ocrText = meta?.ocrText ?: "",
                perceptualHash = meta?.perceptualHash,
                blurScore = meta?.blurScore
            )
        }
    }

    /** Lee el MediaStore real del dispositivo. Llamar al iniciar y tras cambios de permisos. */
    suspend fun refreshMediaStore() = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaImage>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                list += MediaImage(
                    id = id,
                    uri = uri,
                    displayName = cursor.getString(nameCol) ?: "",
                    dateAddedMillis = cursor.getLong(dateCol) * 1000L
                )
            }
        }
        _mediaImages.value = list
        // Crea una fila de metadatos por defecto para fotos nuevas que la app aún no conoce.
        metaDao.insertDefaults(list.map { PhotoMeta(mediaStoreId = it.id) })
    }

    // ---- álbumes ----

    suspend fun createAlbum(name: String): Long = albumDao.insert(
        com.example.gestorfotos.data.Album(name = name)
    )

    suspend fun renameAlbum(album: com.example.gestorfotos.data.Album, newName: String) =
        albumDao.update(album.copy(name = newName))

    /** Borra el álbum; las fotos vuelven a "sin clasificar". Devuelve sus ids para poder deshacer. */
    suspend fun deleteAlbum(album: com.example.gestorfotos.data.Album): List<Long> {
        val affected = metaDao.observeAll().first().filter { it.albumId == album.id }
        metaDao.upsertAll(affected.map { it.copy(albumId = null) })
        albumDao.delete(album)
        return affected.map { it.mediaStoreId }
    }

    suspend fun setAlbum(ids: List<Long>, albumId: Long?) = withMetas(ids) { it.copy(albumId = albumId) }

    // ---- favoritos / etiquetas / edición ----

    suspend fun setFavorite(ids: List<Long>, favorite: Boolean) =
        withMetas(ids) { it.copy(isFavorite = favorite) }

    suspend fun addTag(id: Long, tag: String) = withMetas(listOf(id)) {
        val current = it.manualTags.split(",").filter { t -> t.isNotBlank() }.toMutableList()
        if (!current.contains(tag)) current += tag
        it.copy(manualTags = current.joinToString(","))
    }

    suspend fun removeTag(id: Long, tag: String) = withMetas(listOf(id)) {
        val current = it.manualTags.split(",").filter { t -> t.isNotBlank() && t != tag }
        it.copy(manualTags = current.joinToString(","))
    }

    suspend fun rotate(id: Long) = withMetas(listOf(id)) {
        it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360)
    }

    suspend fun setCroppedUri(id: Long, uri: String) = withMetas(listOf(id)) {
        it.copy(croppedUri = uri)
    }

    suspend fun markViewed(id: Long) = withMetas(listOf(id)) {
        it.copy(lastViewedAt = System.currentTimeMillis())
    }

    // ---- papelera ----

    /**
     * En Android 11+ (API 30+) delega en la papelera real del sistema, que requiere
     * confirmación del usuario vía IntentSender. Devuelve null en versiones anteriores,
     * en cuyo caso se debe llamar directamente a confirmTrash().
     */
    fun buildTrashIntentSender(uris: List<Uri>, trash: Boolean): IntentSender? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createTrashRequest(context.contentResolver, uris, trash).intentSender
        } else null
    }

    /** Llamar tras confirmar el IntentSender (o directamente en API < 30). */
    suspend fun confirmTrash(ids: List<Long>, trash: Boolean) = withMetas(ids) {
        it.copy(isTrashed = trash, trashedAt = if (trash) System.currentTimeMillis() else null)
    }

    /** Resultado de intentar borrar un archivo real del MediaStore. */
    sealed class DeleteOutcome {
        object Deleted : DeleteOutcome()
        data class NeedsConsent(val intentSender: IntentSender) : DeleteOutcome()
        object Failed : DeleteOutcome()
    }

    /**
     * Borra el archivo real ya (sin esperar 30 días). En Android 11+ es mejor usar
     * buildDeleteIntentSender (pide consentimiento una sola vez para todo el lote).
     * Este método es el que cubre Android 9 y 10, donde no existe ese lote:
     * en API 29 el sistema puede negarse con RecoverableSecurityException porque la
     * foto no la creó esta app (scoped storage); en ese caso devolvemos el intentSender
     * de esa excepción para que la UI pida el permiso puntual. En API 26-28 no hay
     * scoped storage, así que con WRITE_EXTERNAL_STORAGE concedido el borrado es directo.
     */
    suspend fun attemptPermanentDelete(mediaStoreId: Long): DeleteOutcome = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
        try {
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows > 0) {
                metaDao.delete(mediaStoreId)
                DeleteOutcome.Deleted
            } else {
                DeleteOutcome.Failed
            }
        } catch (e: RecoverableSecurityException) {
            metaDao.upsert((metaDao.getById(mediaStoreId) ?: PhotoMeta(mediaStoreId = mediaStoreId)).copy(needsDeleteConsent = true))
            DeleteOutcome.NeedsConsent(e.userAction.actionIntent.intentSender)
        } catch (_: SecurityException) {
            DeleteOutcome.Failed
        }
    }

    /** Tras obtener el consentimiento del IntentSender de arriba, reintentar el borrado. */
    suspend fun confirmPermanentDelete(mediaStoreId: Long) = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: SecurityException) {
            // el usuario denegó el permiso puntual; se deja como estaba
        }
        metaDao.delete(mediaStoreId)
    }

    /** Android 11+: un solo IntentSender de sistema para borrar todo un lote de una vez. */
    fun buildDeleteIntentSender(uris: List<Uri>): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        } else null

    suspend fun confirmPermanentDeleteBatch(ids: List<Long>) = withContext(Dispatchers.IO) {
        ids.forEach { metaDao.delete(it) }
    }

    fun observeNeedingDeleteConsent() = metaDao.observeNeedingConsent()

    /**
     * Purga automática (30 días). En Android 11+ el propio sistema ya borra las fotos
     * trasheadas por su cuenta al vencer el plazo — createTrashRequest delega esa
     * responsabilidad en el SO. Por eso aquí solo actuamos en API < 30, donde la
     * "papelera" es una marca propia de Room y el archivo sigue existiendo de verdad.
     */
    suspend fun purgeOldTrash(days: Int = 30) {
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val candidates = metaDao.getTrashedOlderThan(cutoff)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // El sistema ya se encarga; solo limpiamos filas de Room cuyas fotos ya no existan.
            metaDao.purgeOldTrash(cutoff)
        } else {
            candidates.forEach { meta ->
                val outcome = attemptPermanentDelete(meta.mediaStoreId)
                // Si devuelve NeedsConsent, la fila queda marcada (needsDeleteConsent) para
                // que la pantalla de Papelera la muestre con un aviso y el usuario confirme
                // el borrado puntual la próxima vez que abra la app (ver TrashScreen).
            }
        }
    }

    // ---- duplicados / borrosas (lectura de resultados calculados por IndexingWorker) ----

    fun observeBlurry(threshold: Double = 60.0) = metaDao.observeBlurry(threshold)

    suspend fun findDuplicateGroups(maxHammingDistance: Int = 6): List<List<Long>> {
        val withHash = metaDao.getAllWithHash()
        val visited = mutableSetOf<Long>()
        val groups = mutableListOf<List<Long>>()
        for (a in withHash) {
            if (a.mediaStoreId in visited) continue
            val group = mutableListOf(a.mediaStoreId)
            visited += a.mediaStoreId
            for (b in withHash) {
                if (b.mediaStoreId in visited) continue
                if (hammingDistance(a.perceptualHash!!, b.perceptualHash!!) <= maxHammingDistance) {
                    group += b.mediaStoreId
                    visited += b.mediaStoreId
                }
            }
            if (group.size > 1) groups += group
        }
        return groups
    }

    private fun hammingDistance(a: String, b: String): Int {
        val len = minOf(a.length, b.length)
        var dist = 0
        for (i in 0 until len) if (a[i] != b[i]) dist++
        return dist
    }

    private suspend fun withMetas(ids: List<Long>, transform: (PhotoMeta) -> PhotoMeta) {
        val updated = ids.map { id ->
            val existing = metaDao.getById(id) ?: PhotoMeta(mediaStoreId = id)
            transform(existing)
        }
        metaDao.upsertAll(updated)
    }

}
