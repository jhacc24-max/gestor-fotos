package com.example.gestorfotos.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gestorfotos.ui.GalleryViewModel
import com.example.gestorfotos.ui.components.PngIcon
import com.example.gestorfotos.ui.components.PngIconButton
import com.example.gestorfotos.ui.components.PhotoCard
import com.example.gestorfotos.ui.components.SkeuoIconButton
import com.example.gestorfotos.ui.components.SkeuoPlate
import com.example.gestorfotos.ui.components.SkeuoStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(vm: GalleryViewModel, onBack: () -> Unit) {
    val trashed by vm.trashed.collectAsState()
    val needingConsent by vm.needingDeleteConsent.collectAsState()
    var confirmEmpty by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Android 11+: un solo diálogo de sistema borra todo el lote de una vez.
    val batchDeleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            vm.confirmPermanentDeleteBatch(trashed.map { it.id })
        }
    }

    // Android 9/10: cada foto que no pertenece a la app puede pedir su propio consentimiento puntual.
    var pendingLegacyId by remember { mutableStateOf<Long?>(null) }
    val legacyConsentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingLegacyId?.let { vm.confirmPermanentDelete(it) }
        }
        pendingLegacyId = null
    }

    fun emptyTrashNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uris = trashed.map { it.uri }
            val sender = vm.buildDeleteIntentSender(uris)
            if (sender != null) {
                batchDeleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
        } else {
            // Recorre una por una; las que choquen con RecoverableSecurityException
            // quedan marcadas y aparecen abajo con un aviso para confirmarlas manualmente.
            scope.launch {
                trashed.forEach { photo ->
                    val outcome = vm.attemptPermanentDelete(photo.id)
                    if (outcome is com.example.gestorfotos.repository.PhotoRepository.DeleteOutcome.NeedsConsent) {
                        pendingLegacyId = photo.id
                        legacyConsentLauncher.launch(IntentSenderRequest.Builder(outcome.intentSender).build())
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Papelera", fontWeight = FontWeight.Bold) },
                navigationIcon = { PngIconButton(com.example.gestorfotos.R.drawable.ic_atras, "Volver", 36.dp, onBack) },
                actions = {
                    if (trashed.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        PngIconButton(com.example.gestorfotos.R.drawable.ic_vaciar_papelera, "Vaciar", 36.dp) { confirmEmpty = true }
                        Spacer(Modifier.width(12.dp))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Text(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    "Las fotos se eliminan definitivamente 30 días después de moverlas aquí (lo gestiona el propio sistema)."
                else
                    "Las fotos se eliminan definitivamente 30 días después. En tu versión de Android, si alguna pide confirmación puntual aparecerá abajo.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (needingConsent.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("${needingConsent.size} foto(s) necesitan tu confirmación para borrarse", style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = {
                                needingConsent.firstOrNull()?.let { meta ->
                                    scope.launch {
                                        val outcome = vm.attemptPermanentDelete(meta.mediaStoreId)
                                        if (outcome is com.example.gestorfotos.repository.PhotoRepository.DeleteOutcome.NeedsConsent) {
                                            pendingLegacyId = meta.mediaStoreId
                                            legacyConsentLauncher.launch(IntentSenderRequest.Builder(outcome.intentSender).build())
                                        }
                                    }
                                }
                            }) { Text("Confirmar ahora") }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (trashed.isEmpty()) {
                Text("La papelera está vacía.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.height(420.dp)
                ) {
                    items(trashed, key = { it.id }) { photo ->
                        Column {
                            PhotoCard(photo = photo, selectMode = false, isSelected = false, onTap = {}, onLongPress = {})
                            TextButton(onClick = { vm.restoreFromTrash(listOf(photo.id)) }) { Text("Restaurar") }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("¿Vaciar la papelera?") },
            text = { Text("Esto elimina las fotos de forma permanente ahora mismo.") },
            confirmButton = {
                TextButton(onClick = { confirmEmpty = false; emptyTrashNow() }) {
                    Text("Vaciar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancelar") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(vm: GalleryViewModel, onBack: () -> Unit) {
    val allPhotos by vm.photos.collectAsState()
    val duplicateGroups by vm.duplicateGroups.collectAsState()
    val blurry by vm.blurry.collectAsState()

    var pendingTrashIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) vm.confirmTrash(pendingTrashIds)
    }
    fun trashGroupExceptFirst(group: List<Long>) {
        val toTrash = group.drop(1)
        pendingTrashIds = toTrash
        val uris = allPhotos.filter { it.id in toTrash }.map { it.uri }
        val sender = vm.buildTrashIntentSender(uris)
        if (sender != null) trashLauncher.launch(IntentSenderRequest.Builder(sender).build())
        else vm.confirmTrash(toTrash)
    }

    LaunchedEffect(Unit) { vm.refreshCleanupSuggestions() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sugerencias de limpieza", fontWeight = FontWeight.Bold) },
                navigationIcon = { PngIconButton(com.example.gestorfotos.R.drawable.ic_atras, "Volver", 36.dp, onBack) }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                PngIcon(com.example.gestorfotos.R.drawable.ic_limpieza, null, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Basado en similitud visual (pHash) y nitidez, calculado en segundo plano. Revisa antes de borrar.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Posibles duplicados", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            if (duplicateGroups.isEmpty()) {
                Text("No se detectaron duplicados por ahora.", style = MaterialTheme.typography.bodyMedium)
            }
            duplicateGroups.forEach { group ->
                val groupPhotos = allPhotos.filter { it.id in group }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    groupPhotos.forEach { p ->
                        Box(Modifier.weight(1f)) {
                            PhotoCard(photo = p, selectMode = false, isSelected = false, onTap = {}, onLongPress = {})
                        }
                    }
                }
                TextButton(onClick = { trashGroupExceptFirst(group) }) { Text("Conservar solo la primera") }
            }

            Spacer(Modifier.height(16.dp))
            Text("Fotos borrosas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
            if (blurry.isEmpty()) {
                Text("No se detectaron fotos borrosas.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.height(220.dp)
                ) {
                    items(blurry, key = { it.mediaStoreId }) { meta ->
                        val photo = allPhotos.find { it.id == meta.mediaStoreId }
                        if (photo != null) {
                            PhotoCard(photo = photo, selectMode = false, isSelected = false, onTap = {}, onLongPress = {})
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
