package com.example.gestorfotos.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.gestorfotos.data.Album
import com.example.gestorfotos.repository.SystemFolder
import com.example.gestorfotos.ui.GalleryViewModel
import com.example.gestorfotos.ui.components.PngIcon
import com.example.gestorfotos.ui.components.PngIconButton
import com.example.gestorfotos.ui.components.PhotoGrid
import com.example.gestorfotos.ui.components.SelectionBar
import com.example.gestorfotos.ui.components.SkeuoIconButton
import com.example.gestorfotos.ui.components.SkeuoPlate
import com.example.gestorfotos.ui.components.SkeuoStyle
import com.example.gestorfotos.ui.theme.SurfaceRaised

/** Comparte una o varias fotos de una sola vez. */
private fun shareUris(context: android.content.Context, uris: List<android.net.Uri>) {
    if (uris.isEmpty()) return
    val list = ArrayList(uris)
    val intent = if (list.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, list.first())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, list)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(intent, "Compartir"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(vm: GalleryViewModel, onOpenAlbum: (Long) -> Unit, onOpenFolder: (String) -> Unit) {
    val albums by vm.albums.collectAsState()
    val photos by vm.photos.collectAsState()
    val folders by vm.systemFolders.collectAsState()
    val groupNames by vm.folderGroupNames.collectAsState()
    var showNewAlbumDialog by remember { mutableStateOf(false) }
    var showOrganizeDialog by remember { mutableStateOf(false) }
    var folderToGroup by remember { mutableStateOf<SystemFolder?>(null) }

    val groupedFolders = remember(folders) {
        folders.groupBy { it.groupName ?: "Sin clasificar" }
            .toSortedMap(compareBy { if (it == "Sin clasificar") "\uFFFF" else it })
    }

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
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Mis álbumes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            items(albums, key = { "album_${it.id}" }) { album ->
                val albumPhotos = photos.filter { it.albumId == album.id }
                val cover = albumPhotos.maxByOrNull { it.dateAddedMillis }
                Card(
                    onClick = { onOpenAlbum(album.id) },
                    colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column {
                        AlbumCover(cover?.displayUri, Icons.Outlined.Folder, SkeuoStyle.LEATHER)
                        Column(Modifier.padding(10.dp)) {
                            Text(album.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${albumPhotos.size} fotos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
                        PngIcon(com.example.gestorfotos.R.drawable.ic_nuevo_album, null, size = 36.dp)
                        Spacer(Modifier.height(6.dp))
                        Text("Nuevo álbum", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (folders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Carpetas del teléfono",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Manten presionada una carpeta para agruparla.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showOrganizeDialog = true }) { Text("Organizar") }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                groupedFolders.forEach { (groupLabel, groupFolders) ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "grouphdr_$groupLabel") {
                        Text(
                            groupLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(groupFolders, key = { "folder_${it.bucketId}" }) { folder ->
                        FolderCard(
                            folder = folder,
                            onOpen = { onOpenFolder(folder.bucketId) },
                            onLongPress = { folderToGroup = folder },
                            onToggleFavorite = { vm.toggleFolderFavorite(folder.bucketId, folder.isFavorite) }
                        )
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

    if (showOrganizeDialog) {
        OrganizeFoldersDialog(
            folders = folders,
            existingGroups = groupNames,
            onAssign = { bucketId, group -> vm.setFolderGroup(bucketId, group) },
            onDismiss = { showOrganizeDialog = false }
        )
    }

    folderToGroup?.let { folder ->
        FolderGroupDialog(
            folder = folder,
            existingGroups = groupNames,
            onAssign = { group -> vm.setFolderGroup(folder.bucketId, group); folderToGroup = null },
            onDismiss = { folderToGroup = null }
        )
    }
}

@Composable
private fun AlbumCover(uri: android.net.Uri?, fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector, style: SkeuoStyle) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            SkeuoPlate(fallbackIcon, null, style, 40.dp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(
    folder: SystemFolder,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceRaised.copy(alpha = 0.85f),
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongPress)
    ) {
        Column {
            Box {
                AlbumCover(folder.coverUri, Icons.Outlined.PhoneAndroid, SkeuoStyle.CHROME)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clickable(onClick = onToggleFavorite)
                ) {
                    PngIcon(
                        com.example.gestorfotos.R.drawable.ic_favorito,
                        contentDescription = "Favorita",
                        size = 22.dp,
                        selected = folder.isFavorite
                    )
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(folder.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    "${folder.photoCount} fotos" + (folder.groupName?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FolderGroupDialog(
    folder: SystemFolder,
    existingGroups: List<String>,
    onAssign: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var newGroupName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agrupar \"${folder.name}\"") },
        text = {
            Column {
                if (existingGroups.isNotEmpty()) {
                    Text("Grupos existentes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    existingGroups.forEach { g ->
                        TextButton(onClick = { onAssign(g) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { Text(g) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("Nuevo grupo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (folder.groupName != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { onAssign(null) }) { Text("Quitar del grupo actual") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (newGroupName.isNotBlank()) onAssign(newGroupName) }) { Text("Crear y asignar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun OrganizeFoldersDialog(
    folders: List<SystemFolder>,
    existingGroups: List<String>,
    onAssign: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var editingFolder by remember { mutableStateOf<SystemFolder?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Organizar carpetas") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                folders.forEach { folder ->
                    TextButton(onClick = { editingFolder = folder }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(folder.name, maxLines = 1)
                            Text(
                                folder.groupName ?: "Sin grupo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Listo") } }
    )

    editingFolder?.let { folder ->
        FolderGroupDialog(
            folder = folder,
            existingGroups = existingGroups,
            onAssign = { group -> onAssign(folder.bucketId, group); editingFolder = null },
            onDismiss = { editingFolder = null }
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
    val context = LocalContext.current
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

    LaunchedEffect(albumPhotos) { vm.setDetailContext(albumPhotos.map { it.id }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(album?.name ?: "", fontWeight = FontWeight.Bold) },
                navigationIcon = { PngIconButton(com.example.gestorfotos.R.drawable.ic_atras, "Volver", 34.dp, onBack) },
                actions = {
                    PngIconButton(com.example.gestorfotos.R.drawable.ic_renombrar, "Renombrar", 34.dp) { renaming = true }
                    Spacer(Modifier.width(8.dp))
                    PngIconButton(com.example.gestorfotos.R.drawable.ic_papelera, "Eliminar álbum", 34.dp) { deleteConfirm = true }
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
                onShare = { shareUris(context, photos.filter { it.id in selected }.map { it.displayUri }) },
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

/**
 * Vista de una carpeta REAL del teléfono (no un álbum propio de la app): mismas
 * fotos que verías en el explorador de archivos del sistema en esa carpeta.
 * Es de solo lectura respecto al nombre/existencia de la carpeta (no se puede
 * renombrar ni "eliminar" la carpeta desde aquí), pero sí se puede seleccionar,
 * marcar como favorita, mover a un álbum propio o mandar a la papelera, igual
 * que en cualquier otra vista de fotos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemFolderScreen(vm: GalleryViewModel, bucketId: String, onBack: () -> Unit, onOpenDetail: (Long) -> Unit) {
    val context = LocalContext.current
    val folders by vm.systemFolders.collectAsState()
    val photos by vm.photos.collectAsState()
    val albums by vm.albums.collectAsState()
    val folder = folders.find { it.bucketId == bucketId }
    val folderPhotos = photos.filter { it.bucketId == bucketId && !it.isTrashed }
    val selectMode by vm.selectMode.collectAsState()
    val selected by vm.selected.collectAsState()

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

    LaunchedEffect(folderPhotos) { vm.setDetailContext(folderPhotos.map { it.id }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folder?.name ?: "Carpeta", fontWeight = FontWeight.Bold) },
                navigationIcon = { PngIconButton(com.example.gestorfotos.R.drawable.ic_atras, "Volver", 34.dp, onBack) }
            )
        },
        bottomBar = {
            SelectionBar(
                count = selected.size,
                albumNames = albums.map { it.id to it.name },
                onMoveTo = { id, name -> vm.moveSelectedTo(id, name) },
                onNewAlbum = { vm.createAlbum("Nuevo álbum", assignSelected = true) },
                onFavorite = { vm.setFavoriteSelected(true) },
                onShare = { shareUris(context, folderPhotos.filter { it.id in selected }.map { it.displayUri }) },
                onTrash = { requestTrash(selected.toList()) },
                onCancel = { vm.exitSelectMode() }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).padding(horizontal = 12.dp)) {
            PhotoGrid(
                photos = folderPhotos,
                selectMode = selectMode,
                selected = selected,
                emptyMessage = "No hay fotos en esta carpeta.",
                onTap = { if (selectMode) vm.toggleSelect(it.id) else onOpenDetail(it.id) },
                onLongPress = { vm.enterSelectMode(); vm.toggleSelect(it.id) }
            )
        }
    }
}
