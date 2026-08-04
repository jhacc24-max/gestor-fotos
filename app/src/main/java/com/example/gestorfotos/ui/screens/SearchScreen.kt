package com.example.gestorfotos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gestorfotos.ui.GalleryViewModel
import com.example.gestorfotos.ui.components.PhotoGrid
import com.example.gestorfotos.ui.components.SkeuoIcon
import com.example.gestorfotos.ui.components.SkeuoStyle

private val QUICK_TAGS = listOf(
    "playa", "familia", "comida", "cumpleanos", "viaje", "mascota", "atardecer", "fiesta"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: GalleryViewModel, onOpenDetail: (Long) -> Unit) {
    val photos by vm.photos.collectAsState()
    var query by remember { mutableStateOf("") }

    val results = remember(query, photos) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList() else photos.filter {
            !it.isTrashed && (it.tags.any { t -> t.contains(q) } ||
                    it.ocrText.lowercase().contains(q) ||
                    it.displayName.lowercase().contains(q))
        }
    }
    LaunchedEffect(results) { vm.setDetailContext(results.map { it.id }) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Buscar", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { SkeuoIcon(Icons.Filled.Search, null, SkeuoStyle.BRASS, size = 26.dp) },
                placeholder = { Text("Busca por lo que aparece o dice la foto…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(QUICK_TAGS) { tag ->
                    AssistChip(onClick = { query = tag }, label = { Text(tag) })
                }
            }
            Spacer(Modifier.height(12.dp))
            if (query.isBlank()) {
                Text(
                    "Toca una etiqueta o escribe una palabra. También busca texto que aparezca dentro de la imagen (carteles, tickets, letreros, etc.), gracias al reconocimiento de texto en segundo plano.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "${results.size} resultado${if (results.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                PhotoGrid(
                    photos = results,
                    selectMode = false,
                    selected = emptySet(),
                    emptyMessage = "Sin coincidencias. Prueba otra palabra.",
                    onTap = { onOpenDetail(it.id) },
                    onLongPress = {}
                )
            }
        }
    }
}
