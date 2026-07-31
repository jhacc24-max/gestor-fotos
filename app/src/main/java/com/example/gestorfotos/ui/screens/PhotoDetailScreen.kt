package com.example.gestorfotos.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.gestorfotos.ui.GalleryViewModel
import com.example.gestorfotos.ui.components.SkeuoIconButton
import com.example.gestorfotos.ui.components.SkeuoStyle
import com.yalantis.ucrop.UCrop
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

    var tagInput by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }

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

    val suggestions = remember(tagInput, photo.tags) {
        if (tagInput.isBlank()) emptyList()
        else TAG_VOCAB.filter { it.startsWith(tagInput.trim().lowercase()) && it !in photo.tags }.take(5)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = { SkeuoIconButton(Icons.Filled.Close, "Cerrar", SkeuoStyle.CHROME, 36.dp, onClose) },
                actions = {
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
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollStateCompat())
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = photo.displayUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().rotate(photo.rotationDegrees.toFloat())
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DetailAction(Icons.Filled.RotateRight, "Rotar", SkeuoStyle.CHROME) { vm.rotate(photo.id) }
                DetailAction(Icons.Filled.Crop, "Recortar", SkeuoStyle.EMERALD) { launchCrop(photo.uri) }
                DetailAction(Icons.Filled.Share, "Compartir", SkeuoStyle.BRASS) { showShareSheet = true }
                DetailAction(Icons.Filled.DeleteOutline, "Eliminar", SkeuoStyle.RUBY) { confirmDelete = true }
            }

            Spacer(Modifier.height(20.dp))
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

            Spacer(Modifier.height(20.dp))
            Text("Etiquetas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                photo.tags.forEach { tag ->
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
                Spacer(Modifier.height(20.dp))
                Text("Texto detectado en la imagen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(photo.ocrText, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(32.dp))
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
private fun rememberScrollStateCompat() = androidx.compose.foundation.rememberScrollState()
