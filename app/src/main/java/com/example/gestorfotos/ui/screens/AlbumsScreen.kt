package com.example.gestorfotos.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gestorfotos.data.Album
import com.example.gestorfotos.ui.GalleryViewModel
import com.example.gestorfotos.ui.components.PhotoGrid
import com.example.gestorfotos.ui.components.SelectionBar
import com.example.gestorfotos.ui.components.SkeuoIconButton
import com.example.gestorfotos.ui.components.SkeuoPlate
import com.example.gestorfotos.ui.components.SkeuoStyle
import com.example.gestorfotos.ui.theme.SurfaceRaised

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(vm: GalleryViewModel, onOpenAlbum: (Long) -> Unit) {
    val albums by vm.albums.collectAsState()
    val photos by vm.photos.collectAsState()
    var showNewAlbumDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Álbumes", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding)
        ) {
            items(albums, key = { it.id }) { album ->
                val count = photos.count { it.albumId == album.id }
                Card(
                    onClick = { onOpenAlbum(album.id) },
                    colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        SkeuoPlate(Icons.Outlined.Folder, null, SkeuoStyle.LEATHER, 40.dp)
                        Spacer(Modifier.height(14.dp))
                        Text(album.name, fontWeight = FontWeight.SemiBold)
                        Text("$count fotos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Card(
                    onClick = { showNewAlbumDialog = true },
                    colors = CardDefaults.cardColors(containerColor = SurfaceRaised.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        Modifier.padding(14.dp).fillMaxWidth().height(96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        SkeuoPlate(Icons.Filled.Add, null, SkeuoStyle.CHROME, 36.dp)
                        Spacer(Modifier.height(6.dp))
                        Text("Nuevo álbum", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    if (showNewAlbumDialog) {
        NewAlbumDialog(
            onDismiss = { showNewAlbumDialog = false },
            onCreate = { name -> vm.createAlbum(name); showNewAlbumDialog = false }
        )
    }
}

@Composable
private fun NewAlbumDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo álbum") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCreate(name) }) { Text("Crear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(vm: GalleryViewModel, albumId: Long, onBack: () -> Unit, onOpenDetail: (Long) -> Unit) {
    val albums by vm.albums.collectAsState()
    val photos by vm.photos.collectAsState()
    val album = albums.find { it.id == albumId }
    val albumPhotos = photos.filter { it.albumId == albumId && !it.isTrashed }
    val selectMode by vm.selectMode.collectAsState()
    val selected by vm.selected.collectAsState()

    var renaming by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var renameValue by remember(album) { mutableStateOf(album?.name ?: "") }

    var pendingTrashIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) vm.confirmTrash(pendingTrashIds)
    }
    fun requestTrash(ids: List<Long>) {
        pendingTrashIds = ids
        val uris = photos.filter { it.id in ids }.map { it.uri }
        val sender = vm.buildTrashIntentSender(uris)
        if (sender != null) trashLauncher.launch(IntentSenderRequest.Builder(sender).build())
        else vm.confirmTrash(ids)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(album?.name ?: "", fontWeight = FontWeight.Bold) },
                navigationIcon = { SkeuoIconButton(Icons.Filled.ArrowBack, "Volver", SkeuoStyle.CHROME, 34.dp, onBack) },
                actions = {
                    SkeuoIconButton(Icons.Filled.Edit, "Renombrar", SkeuoStyle.BRASS, 34.dp) { renaming = true }
                    Spacer(Modifier.width(8.dp))
                    SkeuoIconButton(Icons.Filled.Delete, "Eliminar álbum", SkeuoStyle.RUBY, 34.dp) { deleteConfirm = true }
                    Spacer(Modifier.width(8.dp))
                }
            )
        },
        bottomBar = {
            SelectionBar(
                count = selected.size,
                albumNames = albums.filter { it.id != albumId }.map { it.id to it.name },
                onMoveTo = { id, name -> vm.moveSelectedTo(id, name) },
                onNewAlbum = { vm.createAlbum("Nuevo álbum", assignSelected = true) },
                onFavorite = { vm.setFavoriteSelected(true) },
                onTrash = { requestTrash(selected.toList()) },
                onCancel = { vm.exitSelectMode() }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).padding(horizontal = 12.dp)) {
            PhotoGrid(
                photos = albumPhotos,
                selectMode = selectMode,
                selected = selected,
                emptyMessage = "Aún no hay fotos aquí. Muévelas desde Fotos.",
                onTap = { if (selectMode) vm.toggleSelect(it.id) else onOpenDetail(it.id) },
                onLongPress = { vm.enterSelectMode(); vm.toggleSelect(it.id) }
            )
        }
    }

    if (renaming && album != null) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Renombrar álbum") },
            text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { vm.renameAlbum(album, renameValue); renaming = false }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancelar") } }
        )
    }

    if (deleteConfirm && album != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("¿Eliminar \"${album.name}\"?") },
            text = { Text("Las fotos no se borran: vuelven a Fotos sin clasificar.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAlbum(album); deleteConfirm = false; onBack() }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Cancelar") } }
        )
    }
}
