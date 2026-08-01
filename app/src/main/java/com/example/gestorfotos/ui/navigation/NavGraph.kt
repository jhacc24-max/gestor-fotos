package com.example.gestorfotos.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.gestorfotos.ui.GalleryViewModel
import com.example.gestorfotos.ui.components.SkeuoIcon
import com.example.gestorfotos.ui.components.SkeuoStyle
import com.example.gestorfotos.ui.screens.*

private sealed class Dest(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val style: SkeuoStyle) {
    object Fotos : Dest("fotos", "Fotos", Icons.Filled.Image, SkeuoStyle.CHROME)
    object Albumes : Dest("albumes", "Álbumes", Icons.Outlined.Folder, SkeuoStyle.LEATHER)
    object Buscar : Dest("buscar", "Buscar", Icons.Outlined.Search, SkeuoStyle.BRASS)
    object Favoritos : Dest("favoritos", "Favoritos", Icons.Filled.Star, SkeuoStyle.BRASS)
}

private val bottomDestinations = listOf(Dest.Fotos, Dest.Albumes, Dest.Buscar, Dest.Favoritos)

@Composable
fun GestorFotosNavHost(viewModel: GalleryViewModel, activity: ComponentActivity) {
    val navController = rememberNavController()
    val undo by viewModel.undo.collectAsState()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination
            // La barra inferior solo se muestra en las 4 pantallas principales.
            if (bottomDestinations.any { it.route == currentRoute?.route }) {
                NavigationBar {
                    bottomDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute?.hierarchy?.any { it.route == dest.route } == true,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                val isSelected = currentRoute?.hierarchy?.any { it.route == dest.route } == true
                                if (dest == Dest.Fotos) {
                                    // Mismo glifo del ícono principal de la app (mancha turquesa + foto),
                                    // para que la pestaña "Fotos" se vea idéntica al ícono de instalación.
                                    Icon(
                                        painter = painterResource(id = com.example.gestorfotos.R.drawable.ic_launcher_foreground),
                                        contentDescription = dest.label,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(28.dp).alpha(if (isSelected) 1f else 0.55f)
                                    )
                                } else {
                                    SkeuoIcon(dest.icon, dest.label, dest.style, size = 28.dp, selected = isSelected)
                                }
                            },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        },
        snackbarHost = {
            UndoSnackbarHostWrapper(message = undo?.message, onUndo = { viewModel.performUndo() })
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = Dest.Fotos.route,
                enterTransition = { fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 6 } },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { it / 6 } }
            ) {
                composable(Dest.Fotos.route) {
                    PhotosScreen(
                        vm = viewModel,
                        onOpenDetail = { id -> navController.navigate("detalle/$id") },
                        onOpenTrash = { navController.navigate("papelera") },
                        onOpenCleanup = { navController.navigate("limpieza") }
                    )
                }
                composable(Dest.Albumes.route) {
                    AlbumsScreen(
                        vm = viewModel,
                        onOpenAlbum = { id -> navController.navigate("album/$id") },
                        onOpenFolder = { bucketId -> navController.navigate("carpeta/$bucketId") }
                    )
                }
                composable(
                    "album/{albumId}",
                    arguments = listOf(navArgument("albumId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
                    AlbumDetailScreen(
                        vm = viewModel,
                        albumId = albumId,
                        onBack = { navController.popBackStack() },
                        onOpenDetail = { id -> navController.navigate("detalle/$id") }
                    )
                }
                composable(
                    "carpeta/{bucketId}",
                    arguments = listOf(navArgument("bucketId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val bucketId = backStackEntry.arguments?.getString("bucketId") ?: return@composable
                    SystemFolderScreen(
                        vm = viewModel,
                        bucketId = bucketId,
                        onBack = { navController.popBackStack() },
                        onOpenDetail = { id -> navController.navigate("detalle/$id") }
                    )
                }
                composable(Dest.Buscar.route) {
                    SearchScreen(vm = viewModel, onOpenDetail = { id -> navController.navigate("detalle/$id") })
                }
                composable(Dest.Favoritos.route) {
                    FavoritesScreen(vm = viewModel, onOpenDetail = { id -> navController.navigate("detalle/$id") })
                }
                composable("papelera") {
                    TrashScreen(vm = viewModel, onBack = { navController.popBackStack() })
                }
                composable("limpieza") {
                    CleanupScreen(vm = viewModel, onBack = { navController.popBackStack() })
                }
                composable(
                    "detalle/{photoId}",
                    arguments = listOf(navArgument("photoId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val photoId = backStackEntry.arguments?.getLong("photoId") ?: return@composable
                    PhotoDetailScreen(vm = viewModel, photoId = photoId, onClose = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun UndoSnackbarHostWrapper(message: String?, onUndo: () -> Unit) {
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(tween(200)) { it } + fadeIn(),
        exit = slideOutVertically(tween(150)) { it } + fadeOut()
    ) {
        Snackbar(
            modifier = Modifier.padding(12.dp),
            action = { TextButton(onClick = onUndo) { Text("DESHACER") } }
        ) { Text(message ?: "") }
    }
}
