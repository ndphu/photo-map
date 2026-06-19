package com.photomap.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
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
import com.photomap.app.ui.screens.SearchScreen
import com.photomap.app.ui.screens.AlbumsScreen
import com.photomap.app.ui.screens.AlbumDetailScreen
import kotlinx.coroutines.launch

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val ALBUMS = "albums"
    const val ALBUM_DETAIL = "albums/{albumId}"
    const val DETAIL = "detail/{assetId}"

    fun detail(assetId: String) = "detail/$assetId"
    fun albumDetail(albumId: String) = "albums/$albumId"
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
                factory = GalleryViewModelFactory(
                    container.galleryRepository,
                    container.connectivityObserver,
                    container.galleryBatchService,
                    container.syncRepository,
                ),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val assets = viewModel.pagingData.collectAsLazyPagingItems()
            androidx.compose.runtime.LaunchedEffect(viewModel, assets) {
                viewModel.commands.collect { command ->
                    when (command) {
                        GalleryCommand.REFRESH -> assets.refresh()
                        GalleryCommand.RETRY -> assets.retry()
                    }
                }
            }
            LifecycleResumeEffect(viewModel) {
                viewModel.onAppForeground()
                onPauseOrDispose {}
            }
            MediaPermissionGate(
                onGranted = {
                    scope.launch { container.syncRepository.scanAndSync() }
                },
            ) {
                GalleryScreen(
                    assets = assets,
                    state = state,
                    onAssetTap = { assetId ->
                        if (viewModel.onAssetTap(assetId)) {
                            viewModel.clearSelection()
                            navController.navigate(Routes.detail(assetId))
                        }
                    },
                    onAssetLongPress = viewModel::selectAsset,
                    onCloseSelection = viewModel::clearSelection,
                    onFavoriteSelected = viewModel::favoriteSelected,
                    onArchiveSelected = viewModel::archiveSelected,
                    onTrashSelected = viewModel::trashSelected,
                    onAddSelectedToAlbum = viewModel::showAlbumPicker,
                    onRetryBatch = viewModel::retryFailedBatch,
                    onDismissResult = viewModel::dismissResult,
                    onDismissAlbumPicker = viewModel::dismissAlbumPicker,
                    onAlbumSelected = viewModel::addSelectedToAlbum,
                    onStartSync = viewModel::startSync,
                    onRetryFailedUploads = viewModel::retryFailedUploads,
                    onClearFilter = viewModel::clearFilters,
                    onSettings = {
                        viewModel.clearSelection()
                        navController.navigate(Routes.SETTINGS)
                    },
                    onSearch = {
                        viewModel.clearSelection()
                        navController.navigate(Routes.SEARCH)
                    },
                    onAlbums = {
                        viewModel.clearSelection()
                        navController.navigate(Routes.ALBUMS)
                    },
                    onRefresh = viewModel::refresh,
                    onRetry = viewModel::retry,
                    onQuickFilter = viewModel::selectQuickFilter,
                    onSection = viewModel::showSection,
                )
            }
        }

        composable(Routes.SEARCH) {
            val viewModel: SearchViewModel = viewModel(
                factory = SearchViewModelFactory(container.searchRepository),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val assets = viewModel.pagingData.collectAsLazyPagingItems()
            SearchScreen(
                state = state,
                assets = assets,
                onBack = navController::popBackStack,
                onQueryChange = viewModel::updateQuery,
                onClear = viewModel::clearQuery,
                onAsset = { navController.navigate(Routes.detail(it)) },
                onRetry = viewModel::retry,
            )
        }

        composable(Routes.ALBUMS) {
            val viewModel: AlbumsViewModel = viewModel(
                factory = AlbumsViewModelFactory(container.albumRepository),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            AlbumsScreen(
                state = state,
                onBack = navController::popBackStack,
                onAlbum = { navController.navigate(Routes.albumDetail(it)) },
                onCreate = viewModel::showCreate,
                onEdit = viewModel::showEdit,
                onDelete = viewModel::requestDelete,
                onSave = viewModel::saveAlbum,
                onDismissEditor = viewModel::dismissEditor,
                onConfirmDelete = viewModel::confirmDelete,
                onCancelDelete = viewModel::cancelDelete,
                onRetry = viewModel::load,
            )
        }

        composable(
            route = Routes.ALBUM_DETAIL,
            arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
        ) { entry ->
            val albumId = requireNotNull(entry.arguments?.getString("albumId"))
            val viewModel: AlbumDetailViewModel = viewModel(
                key = albumId,
                factory = AlbumDetailViewModelFactory(albumId, container.albumRepository),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            AlbumDetailScreen(
                state = state,
                onBack = navController::popBackStack,
                onAsset = { navController.navigate(Routes.detail(it)) },
                onRemove = viewModel::removeAsset,
                onRetry = viewModel::load,
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
        ) { entry ->
            val assetId = requireNotNull(entry.arguments?.getString("assetId"))
            val viewModel: AssetDetailViewModel = viewModel(
                key = assetId,
                factory = AssetDetailViewModelFactory(
                    assetId,
                    container.assetRepository,
                    container.albumRepository,
                ),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(viewModel, assetId) {
                viewModel.loadAsset(assetId)
            }
            androidx.compose.runtime.LaunchedEffect(viewModel) {
                viewModel.events.collect { event ->
                    if (event == AssetDetailEvent.NAVIGATE_BACK) navController.popBackStack()
                }
            }
            AssetDetailScreen(
                state = state,
                onBack = navController::popBackStack,
                onFavorite = viewModel::toggleFavorite,
                onArchive = viewModel::toggleArchive,
                onTrash = viewModel::trash,
                onRestore = viewModel::restore,
                onRequestDelete = viewModel::requestHardDelete,
                onConfirmDelete = viewModel::confirmHardDelete,
                onCancelDelete = viewModel::cancelHardDelete,
                onRetry = viewModel::retry,
                onRetryPreview = viewModel::retryPreview,
                onPreviewError = viewModel::onPreviewLoadFailed,
                onPreviewLoaded = viewModel::onPreviewLoaded,
                onShowAlbumPicker = viewModel::showAlbumPicker,
                onDismissAlbumPicker = viewModel::dismissAlbumPicker,
                onAddToAlbum = viewModel::addToAlbum,
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(container.syncRepository),
            )
            val pending by viewModel.pendingCount.collectAsStateWithLifecycle()
            val failed by viewModel.failedCount.collectAsStateWithLifecycle()
            val uploading by viewModel.uploadingCount.collectAsStateWithLifecycle()
            val uploaded by viewModel.uploadedCount.collectAsStateWithLifecycle()
            val maxParallelUploads by viewModel.maxParallelUploads.collectAsStateWithLifecycle()
            val backgroundSyncEnabled by viewModel.backgroundSyncEnabled.collectAsStateWithLifecycle()
            val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
            val includeVideos by viewModel.includeVideos.collectAsStateWithLifecycle()
            SettingsScreen(
                pendingCount = pending,
                failedCount = failed,
                uploadingCount = uploading,
                uploadedCount = uploaded,
                maxParallelUploads = maxParallelUploads,
                backgroundSyncEnabled = backgroundSyncEnabled,
                wifiOnly = wifiOnly,
                includeVideos = includeVideos,
                onBack = navController::popBackStack,
                onSync = viewModel::sync,
                onRetry = viewModel::retryFailed,
                onMaxParallelUploadsChange = viewModel::setMaxParallelUploads,
                onBackgroundSyncChange = viewModel::setBackgroundSyncEnabled,
                onWifiOnlyChange = viewModel::setWifiOnly,
                onIncludeVideosChange = viewModel::setIncludeVideos,
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
