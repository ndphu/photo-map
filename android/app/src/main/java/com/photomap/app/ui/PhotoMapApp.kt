package com.photomap.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.photomap.app.AppContainer
import com.photomap.app.ui.screens.AssetDetailScreen
import com.photomap.app.ui.screens.GalleryScreen
import com.photomap.app.ui.screens.LoginScreen
import com.photomap.app.ui.screens.MediaPermissionGate
import com.photomap.app.ui.screens.RegisterScreen
import com.photomap.app.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{assetId}"

    fun detail(assetId: String) = "detail/$assetId"
}

@Composable
fun PhotoMapApp(container: AppContainer) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val startDestination = if (container.authRepository.isLoggedIn()) {
        Routes.GALLERY
    } else {
        Routes.LOGIN
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            val viewModel: AuthViewModel = viewModel(
                factory = AuthViewModelFactory(container.authRepository),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            LoginScreen(
                state = state,
                onLogin = { email, password ->
                    viewModel.login(email, password) {
                        container.syncRepository.scheduleBackgroundSync()
                        navController.navigate(Routes.GALLERY) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                },
                onRegister = { navController.navigate(Routes.REGISTER) },
            )
        }

        composable(Routes.REGISTER) {
            val viewModel: AuthViewModel = viewModel(
                factory = AuthViewModelFactory(container.authRepository),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            RegisterScreen(
                state = state,
                onRegister = { email, password, displayName ->
                    viewModel.register(email, password, displayName) {
                        container.syncRepository.scheduleBackgroundSync()
                        navController.navigate(Routes.GALLERY) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                },
                onLogin = { navController.popBackStack() },
            )
        }

        composable(Routes.GALLERY) {
            val viewModel: GalleryViewModel = viewModel(
                factory = GalleryViewModelFactory(container.assetRepository),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            MediaPermissionGate(
                onGranted = {
                    scope.launch { container.syncRepository.scanAndSync() }
                },
            ) {
                GalleryScreen(
                    state = state,
                    onAsset = { navController.navigate(Routes.detail(it)) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onLoadNext = viewModel::loadNext,
                )
            }
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
        ) { entry ->
            val assetId = requireNotNull(entry.arguments?.getString("assetId"))
            val viewModel: AssetDetailViewModel = viewModel(
                key = assetId,
                factory = AssetDetailViewModelFactory(assetId, container.assetRepository),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            AssetDetailScreen(
                state = state,
                onBack = navController::popBackStack,
                onFavorite = viewModel::toggleFavorite,
                onTrash = viewModel::trash,
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(container.syncRepository),
            )
            val pending by viewModel.pendingCount.collectAsStateWithLifecycle()
            val failed by viewModel.failedCount.collectAsStateWithLifecycle()
            val maxParallelUploads by viewModel.maxParallelUploads.collectAsStateWithLifecycle()
            val backgroundSyncEnabled by viewModel.backgroundSyncEnabled.collectAsStateWithLifecycle()
            val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
            SettingsScreen(
                pendingCount = pending,
                failedCount = failed,
                maxParallelUploads = maxParallelUploads,
                backgroundSyncEnabled = backgroundSyncEnabled,
                wifiOnly = wifiOnly,
                onBack = navController::popBackStack,
                onSync = viewModel::sync,
                onRetry = viewModel::retryFailed,
                onMaxParallelUploadsChange = viewModel::setMaxParallelUploads,
                onBackgroundSyncChange = viewModel::setBackgroundSyncEnabled,
                onWifiOnlyChange = viewModel::setWifiOnly,
                onLogout = {
                    container.syncRepository.cancelAllSync()
                    container.authRepository.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.GALLERY) { inclusive = true }
                    }
                },
            )
        }
    }
}
