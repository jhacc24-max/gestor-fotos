package com.example.gestorfotos.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.gestorfotos.ui.GalleryViewModel
import com.example.gestorfotos.ui.components.SkeuoIconButton
import com.example.gestorfotos.ui.components.SkeuoStyle
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File

private val TAG_VOCAB = listOf(
    "playa", "atardecer", "montana", "comida", "cafe", "perro", "familia",
    "viaje", "cumpleanos", "bicicleta", "fiesta", "auto", "mascota",
    "paisaje", "retrato", "ciudad", "noche", "amigos"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(vm: GalleryViewModel, photoId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val photos by vm.photos.collectAsState()
    val albums by vm.albums.collectAsState()
    val photo = photos.find { it.id == photoId }

    var confirmDelete by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    // Al tocar la foto se ocultan los controles para que se vea a pantalla casi completa;
    // se tocan de nuevo para que vuelvan a aparecer.
    var chromeVisible by remember { mutableStateOf(true) }
    // Zoom: se reinicia cada vez que cambias de foto (photoId como key de remember).
    var scale by remember(photoId) { mutableStateOf(1f) }
    var offset by remember(photoId) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(photoId) { vm.openDetail(photoId) }

    // ---- recorte con uCrop ----
    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            UCrop.getOutput(result.data!!)?.let { vm.setCroppedUri(photoId, it) }
        }
    }
    fun launchCrop(sourceUri: Uri) {
        val destFile = File(context.cacheDir, "cropped_${photoId}_${System.currentTimeMillis()}.jpg")
        val destUri = Uri.fromFile(destFile)
        val intent = UCrop.of(sourceUri, destUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1600, 1600)
            .getIntent(context)
        cropLauncher.launch(intent)
    }

    // ---- papelera con confirmación del sistema ----
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            vm.confirmTrash(listOf(photoId))
            onClose()
        }
    }
    fun requestTrash() {
        val p = photo ?: return
        val sender = vm.buildTrashIntentSender(listOf(p.uri))
        if (sender != null) trashLauncher.launch(IntentSenderRequest.Builder(sender).build())
        else { vm.confirmTrash(listOf(photoId)); onClose() }
    }

    if (photo == null) {
        onClose()
        return
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(visible = chromeVisible, enter = fadeIn(), exit = fadeOut()) {
                TopAppBar(
                    title = { Text("") },
                    navigationIcon = { SkeuoIconButton(Icons.Filled.Close, "Cerrar", SkeuoStyle.CHROME, 36.dp, onClose) },
                    actions = {
                        SkeuoIconButton(Icons.Filled.Sell, "Etiquetas", SkeuoStyle.LEATHER, 34.dp) { showTags = true }
                        Spacer(Modifier.width(6.dp))
                        SkeuoIconButton(
                            icon = if (photo.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "Favorita",
                            style = if (photo.isFavorite) SkeuoStyle.BRASS else SkeuoStyle.CHROME,
                            size = 36.dp
                        ) { vm.toggleFavorite(photo.id, photo.isFavorite) }
                        Spacer(Modifier.width(8.dp))
                    }
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // La foto ocupa todo el espacio disponible y se ve COMPLETA (sin recortar),
            // ajustándose al ancho o alto según su proporción real. Admite pellizcar para
            // hacer zoom, arrastrar con el zoom activo, doble toque para acercar/alejar,
            // y un toque simple para ocultar/mostrar los controles.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(photoId) {
                        coroutineScope {
                            launch {
                                detectTapGestures(
                                    onTap = { chromeVisible = !chromeVisible },
                                    onDoubleTap = {
                                        if (scale > 1f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.5f
                                        }
                                    }
                                )
                            }
                            launch {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 6f)
                                    scale = newScale
                                    offset = if (newScale <= 1f) Offset.Zero else offset + pan
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = photo.displayUri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .rotate(photo.rotationDegrees.toFloat())
                )
            }

            AnimatedVisibility(visible = chromeVisible, enter = fadeIn(), exit = fadeOut()) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DetailAction(Icons.Filled.RotateRight, "Rotar", SkeuoStyle.CHROME) { vm.rotate(photo.id) }
                        DetailAction(Icons.Filled.Crop, "Recortar", SkeuoStyle.EMERALD) { launchCrop(photo.uri) }
                        DetailAction(Icons.Filled.Share, "Compartir", SkeuoStyle.BRASS) { showShareSheet = true }
                        DetailAction(Icons.Filled.DeleteOutline, "Eliminar", SkeuoStyle.RUBY) { confirmDelete = true }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Mover a", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(albums) { album ->
                            FilterChip(
                                selected = photo.albumId == album.id,
                                onClick = { vm.moveSingleTo(photo.id, album.id, album.name) },
                                label = { Text(album.name) }
                            )
                        }
                    }

                    if (photo.ocrText.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text("Texto detectado en la imagen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(photo.ocrText, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                    }

                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Mover a la papelera?") },
            text = { Text("Podrás restaurarla durante 30 días.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; requestTrash() }) {
                    Text("Mover a papelera", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }

    if (showShareSheet) {
        ShareSheet(
            onPick = { label ->
                showShareSheet = false
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, photo.displayUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartir foto"))
            },
            onDismiss = { showShareSheet = false }
        )
    }

    if (showTags) {
        TagsDialog(
            currentTags = photo.tags,
            onAddTag = { vm.addTag(photo.id, it) },
            onRemoveTag = { vm.removeTag(photo.id, it) },
            onDismiss = { showTags = false }
        )
    }
}

@Composable
private fun DetailAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, style: SkeuoStyle, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SkeuoIconButton(icon = icon, contentDescription = label, style = style, size = 46.dp, onClick = onClick)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ShareSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartir") },
        text = {
            Column {
                listOf("Mensajes", "Correo", "Más apps").forEach { app ->
                    TextButton(onClick = { onPick(app) }, modifier = Modifier.fillMaxWidth()) { Text(app) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun TagsDialog(
    currentTags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tagInput by remember { mutableStateOf("") }
    val suggestions = remember(tagInput, currentTags) {
        if (tagInput.isBlank()) emptyList()
        else TAG_VOCAB.filter { it.startsWith(tagInput.trim().lowercase()) && it !in currentTags }.take(5)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etiquetas") },
        text = {
            Column {
                if (currentTags.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        currentTags.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(tag) },
                                trailingIcon = {
                                    IconButton(onClick = { onRemoveTag(tag) }, modifier = Modifier.size(18.dp)) {
                                        Icon(Icons.Filled.Close, null, modifier = Modifier.size(13.dp))
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text("Escribe una etiqueta…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                        if (tagInput.isNotBlank()) { onAddTag(tagInput.trim().lowercase()); tagInput = "" }
                    })
                )
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestions.forEach { s ->
                            AssistChip(onClick = { onAddTag(s); tagInput = "" }, label = { Text("+ $s") })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (tagInput.isNotBlank()) { onAddTag(tagInput.trim().lowercase()); tagInput = "" }
                onDismiss()
            }) { Text("Listo") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
