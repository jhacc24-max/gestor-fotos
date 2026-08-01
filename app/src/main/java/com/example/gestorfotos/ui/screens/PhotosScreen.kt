package com.example.gestorfotos.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gestorfotos.ui.GalleryViewModel
import com.example.gestorfotos.ui.components.PhotoCard
import com.example.gestorfotos.ui.components.PhotoGrid
import com.example.gestorfotos.ui.components.SelectionBar
import com.example.gestorfotos.ui.components.SkeuoIconButton
import com.example.gestorfotos.ui.components.SkeuoStyle
import java.text.SimpleDateFormat
import java.util.*

/** Lanza la papelera real del sistema (API 30+) pidiendo confirmación al usuario;
 *  en versiones anteriores marca la papelera directamente en Room. */
@Composable
private fun rememberTrashRequester(vm: GalleryViewModel): (List<Long>) -> Unit {
    val allPhotos by vm.photos.collectAsState()
    var pendingIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) vm.confirmTrash(pendingIds)
    }
    return { ids ->
        pendingIds = ids
        val uris = allPhotos.filter { it.id in ids }.map { it.uri }
        val sender = vm.buildTrashIntentSender(uris)
        if (sender != null) launcher.launch(IntentSenderRequest.Builder(sender).build())
        else vm.confirmTrash(ids)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    vm: GalleryViewModel,
    onOpenDetail: (Long) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenCleanup: () -> Unit
) {
    val photos by vm.unclassified.collectAsState()
    val selectMode by vm.selectMode.collectAsState()
    val selected by vm.selected.collectAsState()
    val albums by vm.albums.collectAsState()
    val requestTrash = rememberTrashRequester(vm)

    val sdf = remember { SimpleDateFormat("d MMM", Locale("es", "ES")) }
    val grouped = remember(photos) {
        photos.groupBy { p ->
            val cal = Calendar.getInstance().apply { timeInMillis = p.dateAddedMillis }
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            when {
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> "Hoy"
                cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) &&
                        cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) -> "Ayer"
                else -> sdf.format(Date(p.dateAddedMillis))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fotos", fontWeight = FontWeight.Bold) },
                actions = {
                    SkeuoIconButton(Icons.Filled.AutoFixHigh, "Sugerencias de limpieza", SkeuoStyle.EMERALD, 34.dp, onOpenCleanup)
                    Spacer(Modifier.width(8.dp))
                    SkeuoIconButton(Icons.Filled.DeleteOutline, "Papelera", SkeuoStyle.RUBY, 34.dp, onOpenTrash)
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { if (selectMode) vm.exitSelectMode() else vm.enterSelectMode() }) {
                        Text(if (selectMode) "Cancelar" else "Seleccionar")
                    }
                }
            )
        },
        bottomBar = {
            SelectionBar(
                count = selected.size,
                albumNames = albums.map { it.id to it.name },
                onMoveTo = { id, name -> vm.moveSelectedTo(id, name) },
                onNewAlbum = { vm.createAlbum("Nuevo álbum", assignSelected = true) },
                onFavorite = { vm.setFavoriteSelected(true) },
                onTrash = { requestTrash(selected.toList()) },
                onCancel = { vm.exitSelectMode() }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 12.dp)) {
            if (grouped.isEmpty()) {
                PhotoGrid(
                    photos = emptyList(), selectMode = selectMode, selected = selected,
                    emptyMessage = "Todo clasificado. No quedan fotos sin álbum.",
                    onTap = {}, onLongPress = {}
                )
            } else {
                // Una sola cuadrícula continua para TODAS las fechas, con los rótulos
                // (Hoy, Ayer, fecha) como encabezados dentro de ella. Antes cada fecha
                // tenía su propia cuadrícula metida en un Column sin scroll propio, y
                // por eso no se podía bajar más allá de lo que cabía en una pantalla.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    grouped.forEach { (label, items) ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "header_$label") {
                            Text(
                                label,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(items, key = { it.id }) { photo ->
                            PhotoCard(
                                photo = photo,
                                selectMode = selectMode,
                                isSelected = photo.id in selected,
                                onTap = { if (selectMode) vm.toggleSelect(photo.id) else onOpenDetail(photo.id) },
                                onLongPress = { vm.enterSelectMode(); vm.toggleSelect(photo.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(vm: GalleryViewModel, onOpenDetail: (Long) -> Unit) {
    val favorites by vm.favorites.collectAsState()
    val selectMode by vm.selectMode.collectAsState()
    val selected by vm.selected.collectAsState()
    val albums by vm.albums.collectAsState()
    val requestTrash = rememberTrashRequester(vm)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favoritos", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { if (selectMode) vm.exitSelectMode() else vm.enterSelectMode() }) {
                        Text(if (selectMode) "Cancelar" else "Seleccionar")
                    }
                }
            )
        },
        bottomBar = {
            SelectionBar(
                count = selected.size,
                albumNames = albums.map { it.id to it.name },
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
                photos = favorites,
                selectMode = selectMode,
                selected = selected,
                emptyMessage = "Marca una foto con la estrella para verla aquí.",
                onTap = { if (selectMode) vm.toggleSelect(it.id) else onOpenDetail(it.id) },
                onLongPress = { vm.enterSelectMode(); vm.toggleSelect(it.id) }
            )
        }
    }
}
