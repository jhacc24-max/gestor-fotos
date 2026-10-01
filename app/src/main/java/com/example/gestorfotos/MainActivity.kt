package com.example.gestorfotos

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gestorfotos.data.MediaStoreObserver
import com.example.gestorfotos.ui.GalleryViewModel
import com.example.gestorfotos.ui.navigation.GestorFotosNavHost
import com.example.gestorfotos.ui.theme.GestorFotosTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: GalleryViewModel by viewModels()
    private lateinit var mediaStoreObserver: MediaStoreObserver

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Observa cambios en el MediaStore para relanzar el indexado (OCR/hash/blur)
        // apenas entra una foto nueva, en vez de esperar al ciclo de 6h del Worker periódico.
        mediaStoreObserver = MediaStoreObserver(applicationContext)
        mediaStoreObserver.register()

        setContent {
            GestorFotosTheme {
                val permissions = if (Build.VERSION.SDK_INT >= 33) {
                    listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
                } else {
                    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                val permissionState = rememberMultiplePermissionsState(permissions)
                // Basta con tener fotos O videos para entrar.
                val granted = permissionState.permissions.any { it.status.isGranted }
                val scope = rememberCoroutineScope()

                Surface(color = MaterialTheme.colorScheme.background) {
                    if (granted) {
                        LaunchedEffect(Unit) {
                            scope.launch { viewModel.repo.refreshMediaStore() }
                        }
                        GestorFotosNavHost(viewModel = viewModel, activity = this)
                    } else {
                        PermissionRequest(onRequest = { permissionState.launchMultiplePermissionRequest() })
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaStoreObserver.unregister()
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Necesitamos acceso a tus fotos y videos",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Para mostrar, organizar y editar las fotos y videos de tu galería.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest) { Text("Conceder acceso") }
    }
}
