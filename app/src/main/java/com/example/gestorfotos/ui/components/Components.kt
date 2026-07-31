package com.example.gestorfotos.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.gestorfotos.repository.UiPhoto
import com.example.gestorfotos.ui.theme.Amber
import com.example.gestorfotos.ui.theme.Teal

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoGrid(
    photos: List<UiPhoto>,
    selectMode: Boolean,
    selected: Set<Long>,
    emptyMessage: String,
    onTap: (UiPhoto) -> Unit,
    onLongPress: (UiPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    if (photos.isEmpty()) {
        EmptyState(emptyMessage, modifier)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        items(photos, key = { it.id }) { photo ->
            PhotoCard(
                photo = photo,
                selectMode = selectMode,
                isSelected = photo.id in selected,
                onTap = { onTap(photo) },
                onLongPress = { onLongPress(photo) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoCard(
    photo: UiPhoto,
    selectMode: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val borderColor by animateColorAsState(
        if (isSelected) Teal else Color.Transparent, label = "border"
    )
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
    ) {
        AsyncImage(
            model = photo.displayUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .rotate(photo.rotationDegrees.toFloat())
        )

        AnimatedVisibility(
            visible = selectMode,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
        ) {
            SkeuoIcon(
                icon = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                style = if (isSelected) SkeuoStyle.EMERALD else SkeuoStyle.CHROME,
                size = 24.dp,
                selected = isSelected
            )
        }

        AnimatedVisibility(
            visible = !selectMode && photo.isFavorite,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
        ) {
            SkeuoIcon(Icons.Filled.Star, contentDescription = "Favorita", style = SkeuoStyle.BRASS, size = 22.dp)
        }
    }
}

@Composable
private fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SkeuoIcon(Icons.Outlined.Image, contentDescription = null, style = SkeuoStyle.CHROME, size = 40.dp, selected = false)
        Spacer(Modifier.height(10.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SelectionBar(
    count: Int,
    albumNames: List<Pair<Long, String>>,
    onMoveTo: (Long, String) -> Unit,
    onNewAlbum: () -> Unit,
    onFavorite: () -> Unit,
    onTrash: () -> Unit,
    onCancel: () -> Unit
) {
    AnimatedVisibility(
        visible = count > 0,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Surface(tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$count seleccionada${if (count > 1) "s" else ""}", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onCancel) { Text("Cancelar") }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    albumNames.forEach { (id, name) ->
                        AssistChip(onClick = { onMoveTo(id, name) }, label = { Text("Mover a $name") })
                    }
                    AssistChip(onClick = onNewAlbum, label = { Text("+ Nuevo álbum") })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onFavorite, modifier = Modifier.weight(1f)) {
                        SkeuoIcon(Icons.Filled.Star, null, SkeuoStyle.BRASS, size = 22.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Favorito")
                    }
                    Button(
                        onClick = onTrash,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        SkeuoIcon(Icons.Filled.DeleteOutline, null, SkeuoStyle.RUBY, size = 22.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Papelera")
                    }
                }
            }
        }
    }
}

@Composable
fun UndoSnackbarHost(message: String?, onUndo: () -> Unit, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Snackbar(
            action = { TextButton(onClick = onUndo) { Text("DESHACER", color = Teal) } },
            modifier = Modifier.padding(12.dp)
        ) { Text(message ?: "") }
    }
}
