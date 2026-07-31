package com.example.gestorfotos.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gestorfotos.data.Album
import com.example.gestorfotos.repository.PhotoRepository
import com.example.gestorfotos.repository.UiPhoto
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Descripción + acción para revertir la última operación (Snackbar "Deshacer"). */
data class UndoAction(val message: String, val undo: suspend () -> Unit)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    val repo = PhotoRepository(application)

    val photos: StateFlow<List<UiPhoto>> =
        repo.photos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> =
        repo.albums.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unclassified: StateFlow<List<UiPhoto>> =
        photos.map { list -> list.filter { it.albumId == null && !it.isTrashed } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashed: StateFlow<List<UiPhoto>> =
        photos.map { list -> list.filter { it.isTrashed } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<UiPhoto>> =
        photos.map { list -> list.filter { it.isFavorite && !it.isTrashed } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectMode = MutableStateFlow(false)
    val selectMode: StateFlow<Boolean> = _selectMode

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected

    private val _undo = MutableStateFlow<UndoAction?>(null)
    val undo: StateFlow<UndoAction?> = _undo

    private val _recentIds = MutableStateFlow<List<Long>>(emptyList())
    val recentIds: StateFlow<List<Long>> = _recentIds

    private val _duplicateGroups = MutableStateFlow<List<List<Long>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<Long>>> = _duplicateGroups

    val blurry = repo.observeBlurry()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var undoToken = 0

    init {
        viewModelScope.launch { repo.refreshMediaStore() }
    }

    fun refreshCleanupSuggestions() {
        viewModelScope.launch { _duplicateGroups.value = repo.findDuplicateGroups() }
    }

    fun enterSelectMode() { _selectMode.value = true }
    fun exitSelectMode() { _selectMode.value = false; _selected.value = emptySet() }

    fun toggleSelect(id: Long) {
        _selected.value = _selected.value.let { if (id in it) it - id else it + id }
    }

    fun openDetail(id: Long) {
        _recentIds.value = (listOf(id) + _recentIds.value.filter { it != id }).take(8)
        viewModelScope.launch { repo.markViewed(id) }
    }

    private fun postUndo(message: String, undoFn: suspend () -> Unit) {
        undoToken++
        val myToken = undoToken
        _undo.value = UndoAction(message, undoFn)
        viewModelScope.launch {
            kotlinx.coroutines.delay(4000)
            if (undoToken == myToken) _undo.value = null
        }
    }

    fun performUndo() {
        val action = _undo.value ?: return
        _undo.value = null
        viewModelScope.launch { action.undo() }
    }

    // ---- álbumes ----

    fun createAlbum(name: String, assignSelected: Boolean = false, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.createAlbum(name)
            if (assignSelected && _selected.value.isNotEmpty()) {
                repo.setAlbum(_selected.value.toList(), id)
                exitSelectMode()
            }
            onDone(id)
        }
    }

    fun renameAlbum(album: Album, newName: String) {
        viewModelScope.launch { repo.renameAlbum(album, newName) }
    }

    fun deleteAlbum(album: Album) {
        viewModelScope.launch {
            val affectedIds = repo.deleteAlbum(album)
            postUndo("Álbum \"${album.name}\" eliminado") {
                val newId = repo.createAlbum(album.name)
                if (affectedIds.isNotEmpty()) repo.setAlbum(affectedIds, newId)
            }
        }
    }

    fun moveSelectedTo(albumId: Long?, albumName: String) {
        val ids = _selected.value.toList()
        val previous = photos.value.filter { it.id in ids }.associate { it.id to it.albumId }
        viewModelScope.launch {
            repo.setAlbum(ids, albumId)
            postUndo("${ids.size} movida(s) a $albumName") {
                previous.forEach { (id, prevAlbum) -> repo.setAlbum(listOf(id), prevAlbum) }
            }
        }
        exitSelectMode()
    }

    fun moveSingleTo(id: Long, albumId: Long?, albumName: String) {
        val previous = photos.value.find { it.id == id }?.albumId
        viewModelScope.launch {
            repo.setAlbum(listOf(id), albumId)
            postUndo("Movida a $albumName") { repo.setAlbum(listOf(id), previous) }
        }
    }

    // ---- favoritos / etiquetas / edición ----

    fun setFavoriteSelected(favorite: Boolean) {
        val ids = _selected.value.toList()
        viewModelScope.launch { repo.setFavorite(ids, favorite) }
        exitSelectMode()
    }

    fun toggleFavorite(id: Long, current: Boolean) {
        viewModelScope.launch { repo.setFavorite(listOf(id), !current) }
    }

    fun addTag(id: Long, tag: String) = viewModelScope.launch { repo.addTag(id, tag) }
    fun removeTag(id: Long, tag: String) = viewModelScope.launch { repo.removeTag(id, tag) }
    fun rotate(id: Long) = viewModelScope.launch { repo.rotate(id) }
    fun setCroppedUri(id: Long, uri: Uri) = viewModelScope.launch { repo.setCroppedUri(id, uri.toString()) }

    // ---- papelera ----

    /** Devuelve el IntentSender a lanzar (API 30+) o null si se debe confirmar directo. */
    fun buildTrashIntentSender(uris: List<Uri>) = repo.buildTrashIntentSender(uris, trash = true)

    fun confirmTrash(ids: List<Long>) {
        viewModelScope.launch {
            repo.confirmTrash(ids, true)
            postUndo(if (ids.size > 1) "${ids.size} fotos movidas a la papelera" else "Foto movida a la papelera") {
                repo.confirmTrash(ids, false)
            }
        }
        exitSelectMode()
    }

    fun restoreFromTrash(ids: List<Long>) {
        viewModelScope.launch { repo.confirmTrash(ids, false) }
    }

    /** Android 11+: intentSender de un solo lote para borrado inmediato (botón "Vaciar"). */
    fun buildDeleteIntentSender(uris: List<Uri>) = repo.buildDeleteIntentSender(uris)

    fun confirmPermanentDeleteBatch(ids: List<Long>) {
        viewModelScope.launch { repo.confirmPermanentDeleteBatch(ids) }
    }

    /** API < 30: borra una por una; si alguna pide consentimiento puntual, lo devuelve. */
    suspend fun attemptPermanentDelete(id: Long) = repo.attemptPermanentDelete(id)

    fun confirmPermanentDelete(id: Long) {
        viewModelScope.launch { repo.confirmPermanentDelete(id) }
    }

    val needingDeleteConsent = repo.observeNeedingDeleteConsent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
