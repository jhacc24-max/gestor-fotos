package com.example.gestorfotos.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.example.gestorfotos.repository.UiPhoto
import com.example.gestorfotos.ui.GalleryViewModel
import com.example.gestorfotos.ui.components.SkeuoIconButton
import com.example.gestorfotos.ui.components.SkeuoStyle
import com.example.gestorfotos.ui.components.formatDuration
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val TAG_VOCAB = listOf(
    "playa", "atardecer", "montana", "comida", "cafe", "perro", "familia",
    "viaje", "cumpleanos", "bicicleta", "fiesta", "auto", "mascota",
    "paisaje", "retrato", "ciudad", "noche", "amigos"
)

private const val MAX_ZOOM = 6f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Visor a pantalla completa.
 *  - Al abrir: solo el contenido (foto o video) sobre fondo negro.
 *  - Un toque: muestra / oculta las opciones.
 *  - Doble toque: amplía / restaura la foto. Pellizco: zoom libre. Arrastre: mover la foto ampliada.
 */
@Composable
fun PhotoDetailScreen(vm: GalleryViewModel, photoId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val photos by vm.photos.collectAsState()
    val albums by vm.albums.collectAsState()
    val photo = remember(photos, photoId) { photos.find { it.id == photoId } }

    var tagInput by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }

    LaunchedEffect(photoId) { vm.openDetail(photoId) }
    // Si la foto desapareció (papelera/borrado) se cierra fuera de la composición.
    LaunchedEffect(photo == null) { if (photo == null) onClose() }

    ImmersiveMode(hideBars = !showControls)
    BackHandler(enabled = showControls) { showControls = false }

    // ---- recorte con uCrop ----
    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            UCrop.getOutput(result.data!!)?.let { vm.setCroppedUri(photoId, it) }
        }
    }
    fun launchCrop(sourceUri: Uri) {
        // Subcarpeta "cropped/" para que el FileProvider pueda compartir el resultado.
        val dir = File(context.cacheDir, "cropped").apply { mkdirs() }
        val destFile = File(dir, "cropped_${photoId}_${System.currentTimeMillis()}.jpg")
        val intent = UCrop.of(sourceUri, Uri.fromFile(destFile))
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1600, 1600)
            .getIntent(context)
        cropLauncher.launch(intent)
    }

    // ---- papelera con confirmación del sistema ----
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
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

    fun share(p: UiPhoto) {
        var uri = p.displayUri
        if (uri.scheme == "file") {
            uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(uri.path!!))
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (p.isVideo) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, if (p.isVideo) "Compartir video" else "Compartir foto")
        )
    }

    if (photo == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val suggestions = remember(tagInput, photo.tags) {
        if (tagInput.isBlank()) emptyList()
        else TAG_VOCAB.filter { it.startsWith(tagInput.trim().lowercase()) && it !in photo.tags }.take(5)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val maxPanelHeight = maxHeight * 0.6f

        // ---- contenido a pantalla completa ----
        if (photo.isVideo) {
            VideoPlayer(
                uri = photo.uri,
                onToggleControls = { showControls = !showControls },
                controlsVisible = showControls
            )
        } else {
            ZoomableImage(
                photo = photo,
                onSingleTap = { showControls = !showControls }
            )
        }

        // ---- barra superior ----
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeuoIconButton(Icons.Filled.Close, "Cerrar", SkeuoStyle.CHROME, 36.dp, onClose)
                Text(
                    photo.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                )
                SkeuoIconButton(
                    icon = if (photo.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Favorita",
                    style = if (photo.isFavorite) SkeuoStyle.BRASS else SkeuoStyle.CHROME,
                    size = 36.dp
                ) { vm.toggleFavorite(photo.id, photo.isFavorite) }
            }
        }

        // ---- panel inferior: acciones, mover, etiquetas ----
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                // Absorbe los toques para que tocar el panel no lo oculte.
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
            ) {
                Column(
                    Modifier
                        .heightIn(max = maxPanelHeight)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        if (!photo.isVideo) {
                            DetailAction(Icons.Filled.RotateRight, "Rotar", SkeuoStyle.CHROME) { vm.rotate(photo.id) }
                            DetailAction(Icons.Filled.Crop, "Recortar", SkeuoStyle.EMERALD) { launchCrop(photo.uri) }
                        }
                        DetailAction(Icons.Filled.Share, "Compartir", SkeuoStyle.BRASS) { share(photo) }
                        DetailAction(Icons.Filled.DeleteOutline, "Eliminar", SkeuoStyle.RUBY) { confirmDelete = true }
                    }

                    Spacer(Modifier.height(18.dp))
                    Text("Mover a", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(albums, key = { it.id }) { album ->
                            FilterChip(
                                selected = photo.albumId == album.id,
                                onClick = { vm.moveSingleTo(photo.id, album.id, album.name) },
                                label = { Text(album.name) }
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Text("Etiquetas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(photo.tags, key = { it }) { tag ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(tag) },
                                trailingIcon = {
                                    IconButton(onClick = { vm.removeTag(photo.id, tag) }, modifier = Modifier.size(18.dp)) {
                                        Icon(Icons.Filled.Close, null, modifier = Modifier.size(13.dp))
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        placeholder = { Text("Escribe una etiqueta…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                            if (tagInput.isNotBlank()) { vm.addTag(photo.id, tagInput.trim().lowercase()); tagInput = "" }
                        })
                    )
                    if (suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            suggestions.forEach { s ->
                                AssistChip(onClick = { vm.addTag(photo.id, s); tagInput = "" }, label = { Text("+ $s") })
                            }
                        }
                    }

                    if (photo.ocrText.isNotBlank()) {
                        Spacer(Modifier.height(18.dp))
                        Text("Texto detectado en la imagen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(photo.ocrText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Mover a la papelera?") },
            text = { Text("Podrás restaurarlo durante 30 días.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; requestTrash() }) {
                    Text("Mover a papelera", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

/** Oculta/muestra barras del sistema; al salir del visor las restaura siempre. */
@Composable
private fun ImmersiveMode(hideBars: Boolean) {
    val view = LocalView.current
    DisposableEffect(hideBars) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideBars) controller?.hide(WindowInsetsCompat.Type.systemBars())
        else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** Foto con zoom por pellizco, doble toque para ampliar/restaurar y arrastre al estar ampliada. */
@Composable
private fun ZoomableImage(photo: UiPhoto, onSingleTap: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var zoomLevel by remember(photo.id) { mutableStateOf(1f) }
    var offset by remember(photo.id) { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = box.width * (s - 1f) / 2f
        val maxY = box.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    // Pantalla completa: tamaño de la pantalla, sin ampliar más de lo necesario.
    val request = remember(photo.displayUri) {
        ImageRequest.Builder(context)
            .data(photo.displayUri)
            .size(Size(2048, 2048))
            .crossfade(false)
            .build()
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(photo.id) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { tap ->
                        scope.launch {
                            if (zoomLevel > 1.05f) {
                                val from = zoomLevel
                                val fromOffset = offset
                                animate(0f, 1f) { t, _ ->
                                    zoomLevel = from + (1f - from) * t
                                    offset = fromOffset * (1f - t)
                                }
                            } else {
                                // Amplía hacia el punto tocado.
                                val target = DOUBLE_TAP_ZOOM
                                val center = Offset(box.width / 2f, box.height / 2f)
                                val toOffset = clamp((center - tap) * (target - 1f), target)
                                animate(0f, 1f) { t, _ ->
                                    zoomLevel = 1f + (target - 1f) * t
                                    offset = toOffset * t
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(photo.id) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (zoomLevel * zoom).coerceIn(1f, MAX_ZOOM)
                    val center = Offset(box.width / 2f, box.height / 2f)
                    // Mantiene fijo el punto bajo los dedos mientras se hace zoom.
                    val moved = offset + (centroid - center - offset) * (1f - newScale / zoomLevel) + pan
                    offset = if (newScale <= 1f) Offset.Zero else clamp(moved, newScale)
                    zoomLevel = newScale
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomLevel
                    scaleY = zoomLevel
                    translationX = offset.x
                    translationY = offset.y
                    rotationZ = photo.rotationDegrees.toFloat()
                }
        )
    }
}

/** Reproductor de video a pantalla completa con VideoView (sin dependencias extra). */
@Composable
private fun VideoPlayer(uri: Uri, onToggleControls: () -> Unit, controlsVisible: Boolean) {
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var prepared by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0) }
    var position by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf(false) }

    // Actualiza la posición mientras se reproduce.
    LaunchedEffect(videoView, isPlaying, dragging) {
        while (videoView != null && isPlaying && !dragging) {
            position = videoView?.currentPosition ?: 0
            delay(250)
        }
    }
    DisposableEffect(Unit) { onDispose { videoView?.stopPlayback() } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(uri)
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        duration = mp.duration
                        prepared = true
                        start()
                    }
                    setOnCompletionListener { isPlaying = false; position = duration }
                    videoView = this
                }
            }
        )

        // Capa transparente: un toque muestra/oculta las opciones.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { onToggleControls() }) }
        )

        AnimatedVisibility(
            visible = controlsVisible || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FilledIconButton(
                onClick = {
                    val v = videoView ?: return@FilledIconButton
                    if (isPlaying) { v.pause(); isPlaying = false }
                    else {
                        if (duration > 0 && position >= duration - 300) v.seekTo(0)
                        v.start(); isPlaying = true
                    }
                },
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Barra de progreso, justo encima del panel de opciones.
        AnimatedVisibility(
            visible = controlsVisible && prepared,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatDuration(position.toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                    onValueChange = { dragging = true; position = it.toInt() },
                    onValueChangeFinished = { videoView?.seekTo(position); dragging = false },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(formatDuration(duration.toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
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
