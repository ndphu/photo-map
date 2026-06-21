package com.photomap.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.photomap.app.AppContainer
import com.photomap.app.data.gallery.AssetUiModel
import com.photomap.app.data.gallery.SignedUrlVariant
import com.photomap.app.data.gallery.ViewerAssetSummary
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
    val openAssetDetail: (AssetUiModel) -> Unit = { asset ->
        navController.navigate(Routes.detail(asset.id))
        navController.currentBackStackEntry?.savedStateHandle?.setDetailSeed(asset)
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
                        scope.launch {
                            container.offlineImageCacheCoordinator.clearForAccountChange()
                            container.galleryRepository.clearRemoteReplica()
                            container.assetMutationQueue.enqueueWork()
                            container.syncRepository.scheduleBackgroundSync()
                            container.offlineImageCacheCoordinator.enqueue()
                            navController.navigate(Routes.GALLERY) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
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
                        scope.launch {
                            container.offlineImageCacheCoordinator.clearForAccountChange()
                            container.assetMutationQueue.clearAll()
                            container.galleryRepository.clearRemoteReplica()
                            container.syncRepository.scheduleBackgroundSync()
                            container.offlineImageCacheCoordinator.enqueue()
                            navController.navigate(Routes.GALLERY) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
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
                    container.galleryRepository,
                    container.galleryPreferencesStore,
                ),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val galleryColumnCount by viewModel.galleryColumnCount.collectAsStateWithLifecycle()
            val assets = viewModel.pagingData.collectAsLazyPagingItems()
            MediaPermissionGate(
                onGranted = {
                    if (container.syncRepository.backgroundSyncEnabled.value) {
                        scope.launch { container.syncRepository.scanAndSync() }
                    }
                },
            ) {
                GalleryScreen(
                    assets = assets,
                    state = state,
                    columnCount = galleryColumnCount,
                    onIncreaseColumns = viewModel::increaseGalleryColumns,
                    onDecreaseColumns = viewModel::decreaseGalleryColumns,
                    onAssetTap = { asset ->
                        if (viewModel.onAssetTap(asset.id)) {
                            viewModel.clearSelection()
                            openAssetDetail(asset)
                        }
                    },
                    onAssetLongPress = viewModel::selectAsset,
                    onThumbnailError = viewModel::onThumbnailLoadFailed,
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
                onAsset = openAssetDetail,
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
                onAsset = openAssetDetail,
                onRemove = viewModel::removeAsset,
                onRetry = viewModel::load,
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
        ) { entry ->
            val assetId = requireNotNull(entry.arguments?.getString("assetId"))
            val initialAsset = entry.savedStateHandle.getDetailSeed(assetId)
            val viewModel: AssetDetailViewModel = viewModel(
                key = assetId,
                factory = AssetDetailViewModelFactory(
                    assetId,
                    container.assetRepository,
                    container.albumRepository,
                    container.galleryRepository,
                    initialAsset,
                    container.originalImageService,
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
                onOpenDetails = viewModel::openDetails,
                onCloseDetails = viewModel::closeDetails,
                onAssetChanged = viewModel::loadAsset,
                onLoadOriginal = viewModel::loadOriginal,
                onUsePreview = viewModel::usePreview,
                onDownloadOriginal = viewModel::downloadOriginal,
                onOriginalMessageShown = viewModel::clearOriginalMessage,
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
            val uploadsPaused by viewModel.uploadsPaused.collectAsStateWithLifecycle()
            val backgroundSyncEnabled by viewModel.backgroundSyncEnabled.collectAsStateWithLifecycle()
            val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
            val includeVideos by viewModel.includeVideos.collectAsStateWithLifecycle()
            val offlineImageCacheEnabled by viewModel.offlineImageCacheEnabled.collectAsStateWithLifecycle()
            val imageCacheLimitMb by viewModel.imageCacheLimitMb.collectAsStateWithLifecycle()
            val imageCacheStatus by viewModel.offlineImageCacheStatus.collectAsStateWithLifecycle()
            SettingsScreen(
                pendingCount = pending,
                failedCount = failed,
                uploadingCount = uploading,
                uploadedCount = uploaded,
                maxParallelUploads = maxParallelUploads,
                uploadsPaused = uploadsPaused,
                parallelUploadPresets = viewModel.parallelUploadPresets,
                backgroundSyncEnabled = backgroundSyncEnabled,
                wifiOnly = wifiOnly,
                includeVideos = includeVideos,
                offlineImageCacheEnabled = offlineImageCacheEnabled,
                imageCacheLimitMb = imageCacheLimitMb,
                imageCacheLimitPresetsMb = viewModel.imageCacheLimitPresetsMb,
                imageCacheStatus = imageCacheStatus,
                onBack = navController::popBackStack,
                onSync = viewModel::sync,
                onRetry = viewModel::retryFailed,
                onMaxParallelUploadsChange = viewModel::setMaxParallelUploads,
                onUploadsPausedChange = viewModel::setUploadsPaused,
                onBackgroundSyncChange = viewModel::setBackgroundSyncEnabled,
                onWifiOnlyChange = viewModel::setWifiOnly,
                onIncludeVideosChange = viewModel::setIncludeVideos,
                onOfflineImageCacheEnabledChange = viewModel::setOfflineImageCacheEnabled,
                onImageCacheLimitChange = viewModel::setImageCacheLimitMb,
                onDownloadOfflineImages = viewModel::downloadOfflineImages,
                onClearOfflineImageCache = viewModel::clearOfflineImageCache,
                onLogout = {
                    scope.launch {
                        container.syncRepository.cancelAllSync()
                        container.syncRepository.clearOfflineImageCacheForLogout()
                        container.authRepository.logout()
                        container.assetMutationQueue.clearAll()
                        container.galleryRepository.clearRemoteReplica()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.GALLERY) { inclusive = true }
                        }
                    }
                },
            )
        }
    }
}

private fun SavedStateHandle.setDetailSeed(asset: AssetUiModel) {
    this[DETAIL_SEED_MEDIA_TYPE] = asset.mediaType
    this[DETAIL_SEED_THUMBNAIL_URL] = asset.thumbnailUrl.takeIf {
        asset.galleryImageVariant == SignedUrlVariant.THUMBNAIL.apiValue
    }
    this[DETAIL_SEED_PREVIEW_URL] = asset.previewUrl ?: asset.thumbnailUrl.takeIf {
        asset.galleryImageVariant == SignedUrlVariant.PREVIEW.apiValue
    }
}

private fun SavedStateHandle.getDetailSeed(assetId: String): ViewerAssetSummary = ViewerAssetSummary(
    id = assetId,
    mediaType = get(DETAIL_SEED_MEDIA_TYPE),
    originalFilename = null,
    thumbnailUrl = get(DETAIL_SEED_THUMBNAIL_URL),
    previewUrl = get(DETAIL_SEED_PREVIEW_URL),
)

private const val DETAIL_SEED_MEDIA_TYPE = "detail_seed_media_type"
private const val DETAIL_SEED_THUMBNAIL_URL = "detail_seed_thumbnail_url"
private const val DETAIL_SEED_PREVIEW_URL = "detail_seed_preview_url"
