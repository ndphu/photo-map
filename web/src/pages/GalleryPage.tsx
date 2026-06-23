import type { CSSProperties } from "react";
import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { appDb, type RemoteAssetRow } from "../db/appDb";
import {
  addAssetToAlbum,
  CONCURRENCY_LIMIT,
  deleteAsset,
  optimisticUpdateAsset,
  patchArchive,
  patchFavorite,
  postRestore,
  postTrash,
  rollbackAsset,
  runMultiAction,
} from "../features/assets/assetActionsApi";
import {
  getAssetReplicaReadUrl,
  type AssetReadUrlVariant,
} from "../features/assets/assetChanges";
import {
  MAX_GALLERY_COLUMNS,
  MIN_GALLERY_COLUMNS,
  useGalleryPreferencesStore,
} from "../features/assets/galleryPreferencesStore";
import { useRemoteAssetsReplica } from "../features/assets/useRemoteAssetsReplica";
import { PagePanel } from "../components/PagePanel";
import { useAssetSyncStore } from "../features/assets/assetSyncStore";

type GalleryFilter =
  | "all"
  | "photos"
  | "videos"
  | "favorites"
  | "archive"
  | "trash";

interface FilterOption {
  value: GalleryFilter;
  label: string;
}

const filterOptions: FilterOption[] = [
  { value: "all", label: "All" },
  { value: "photos", label: "Photos" },
  { value: "videos", label: "Videos" },
  { value: "favorites", label: "Favorites" },
  { value: "archive", label: "Archive" },
  { value: "trash", label: "Trash" },
];

const IMAGE_URL_REFRESH_CONCURRENCY = 4;
const DEFAULT_GALLERY_CARD_HEIGHT = 360;
const GALLERY_ROW_GAP_PX = 12;
const VIRTUAL_OVERSCAN_ROWS = 2;
const MIN_ASSETS_FOR_VIRTUALIZATION = 80;

interface GalleryCardProps {
  asset: RemoteAssetRow;
  sourceUrl: string | null;
  sourceVariant: AssetReadUrlVariant | null;
  isSelected: boolean;
  isActionRunning: boolean;
  onToggleSelected: (assetId: string) => void;
  onFavoriteToggle: (asset: RemoteAssetRow) => Promise<void>;
  onArchiveToggle: (asset: RemoteAssetRow) => Promise<void>;
  onTrashToggle: (asset: RemoteAssetRow) => Promise<void>;
  onSingleAddToAlbum: (assetId: string) => Promise<void>;
  onHardDelete: (asset: RemoteAssetRow) => Promise<void>;
  onImageError: (asset: RemoteAssetRow) => Promise<void>;
  measureRef?: (node: HTMLElement | null) => void;
}

interface VirtualWindowResult {
  renderedAssets: RemoteAssetRow[];
  startIndex: number;
  topSpacerHeight: number;
  bottomSpacerHeight: number;
  isVirtualized: boolean;
}

function applyGalleryFilter(assets: RemoteAssetRow[], filter: GalleryFilter): RemoteAssetRow[] {
  switch (filter) {
    case "photos":
      return assets.filter(
        (asset) =>
          asset.mediaType === "image" && !asset.isArchived && !asset.isTrashed,
      );
    case "videos":
      return assets.filter(
        (asset) =>
          asset.mediaType === "video" && !asset.isArchived && !asset.isTrashed,
      );
    case "favorites":
      return assets.filter(
        (asset) => asset.isFavorite && !asset.isArchived && !asset.isTrashed,
      );
    case "archive":
      return assets.filter((asset) => asset.isArchived && !asset.isTrashed);
    case "trash":
      return assets.filter((asset) => asset.isTrashed);
    case "all":
    default:
      return assets.filter((asset) => !asset.isArchived && !asset.isTrashed);
  }
}

function sortGalleryAssets(assets: RemoteAssetRow[]): RemoteAssetRow[] {
  return [...assets].sort((left, right) => {
    const leftTakenAt = left.takenAt ? Date.parse(left.takenAt) : Number.NEGATIVE_INFINITY;
    const rightTakenAt = right.takenAt
      ? Date.parse(right.takenAt)
      : Number.NEGATIVE_INFINITY;

    if (leftTakenAt !== rightTakenAt) {
      return rightTakenAt - leftTakenAt;
    }

    return right.id.localeCompare(left.id);
  });
}

function getAssetDisplaySource(
  asset: RemoteAssetRow,
  fallbackVariant: AssetReadUrlVariant | undefined,
  overrideUrls: Partial<Record<AssetReadUrlVariant, string>> | undefined,
): { variant: AssetReadUrlVariant; url: string } | null {
  const thumbnailUrl = overrideUrls?.thumbnail ?? asset.thumbnailUrl;
  const previewUrl = overrideUrls?.preview ?? asset.previewUrl;

  if (fallbackVariant === "preview" && previewUrl) {
    return { variant: "preview", url: previewUrl };
  }
  if (thumbnailUrl) {
    return { variant: "thumbnail", url: thumbnailUrl };
  }
  if (previewUrl) {
    return { variant: "preview", url: previewUrl };
  }
  return null;
}

function formatTakenAt(value: string | null): string {
  if (!value) {
    return "Unknown date";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "Unknown date";
  }

  return date.toLocaleDateString();
}

const GalleryCard = memo(function GalleryCard({
  asset,
  sourceUrl,
  sourceVariant,
  isSelected,
  isActionRunning,
  onToggleSelected,
  onFavoriteToggle,
  onArchiveToggle,
  onTrashToggle,
  onSingleAddToAlbum,
  onHardDelete,
  onImageError,
  measureRef,
}: GalleryCardProps) {
  return (
    <article className="gallery-card" ref={measureRef}>
      <div className="gallery-card-actions-top">
        <label className="gallery-select-toggle">
          <input
            type="checkbox"
            checked={isSelected}
            onChange={() => onToggleSelected(asset.id)}
          />
          Select
        </label>
      </div>

      <Link className="gallery-thumb-wrap" to={`/assets/${asset.id}`}>
        {sourceUrl && sourceVariant ? (
          <img
            key={`${asset.id}-${sourceUrl}`}
            className="gallery-thumb"
            src={sourceUrl}
            alt={asset.originalFilename ?? "Gallery asset"}
            loading="lazy"
            onError={() => {
              void onImageError(asset);
            }}
          />
        ) : (
          <div className="gallery-thumb-placeholder">Preview unavailable</div>
        )}
      </Link>

      <div className="gallery-card-meta">
        <p className="gallery-card-title">
          {asset.originalFilename ?? `${asset.mediaType.toUpperCase()} asset`}
        </p>
        <p className="gallery-card-date">{formatTakenAt(asset.takenAt)}</p>
        <div className="gallery-card-flags">
          {asset.mediaType === "video" ? <span className="gallery-flag">Video</span> : null}
          {asset.isFavorite ? <span className="gallery-flag">Favorite</span> : null}
          {asset.isArchived ? <span className="gallery-flag">Archived</span> : null}
          {asset.isTrashed ? <span className="gallery-flag">Trash</span> : null}
        </div>
        <div className="gallery-card-actions">
          <button
            type="button"
            className="secondary-btn"
            disabled={isActionRunning}
            onClick={() => {
              void onFavoriteToggle(asset);
            }}
          >
            {asset.isFavorite ? "Unfavorite" : "Favorite"}
          </button>
          <button
            type="button"
            className="secondary-btn"
            disabled={isActionRunning}
            onClick={() => {
              void onArchiveToggle(asset);
            }}
          >
            {asset.isArchived ? "Unarchive" : "Archive"}
          </button>
          <button
            type="button"
            className="secondary-btn"
            disabled={isActionRunning}
            onClick={() => {
              void onTrashToggle(asset);
            }}
          >
            {asset.isTrashed ? "Restore" : "Trash"}
          </button>
          <button
            type="button"
            className="secondary-btn"
            disabled={isActionRunning}
            onClick={() => {
              void onSingleAddToAlbum(asset.id);
            }}
          >
            Add to album
          </button>
          <button
            type="button"
            className="secondary-btn danger"
            disabled={isActionRunning}
            onClick={() => {
              void onHardDelete(asset);
            }}
          >
            Delete permanently
          </button>
        </div>
      </div>
    </article>
  );
});

function useOnlineStatus(): boolean {
  const [isOnline, setIsOnline] = useState(() => {
    if (typeof navigator === "undefined") {
      return true;
    }
    return navigator.onLine;
  });

  useEffect(() => {
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);

    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);

    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  return isOnline;
}

export function GalleryPage() {
  const status = useAssetSyncStore((state) => state.status);
  const errorMessage = useAssetSyncStore((state) => state.errorMessage);
  const lastSyncedAt = useAssetSyncStore((state) => state.lastSyncedAt);
  const sync = useAssetSyncStore((state) => state.syncAssetsChanges);
  const clearSyncError = useAssetSyncStore((state) => state.clearError);
  const { assets, isLoading, errorMessage: replicaErrorMessage } =
    useRemoteAssetsReplica();

  const columns = useGalleryPreferencesStore((state) => state.columns);
  const setColumns = useGalleryPreferencesStore((state) => state.setColumns);

  const [selectedFilter, setSelectedFilter] = useState<GalleryFilter>("all");
  const [fallbackVariantByAssetId, setFallbackVariantByAssetId] = useState<
    Record<string, AssetReadUrlVariant>
  >({});
  const [assetUrlOverridesByAssetId, setAssetUrlOverridesByAssetId] = useState<
    Record<string, Partial<Record<AssetReadUrlVariant, string>>>
  >({});
  const [selectedAssetIds, setSelectedAssetIds] = useState<string[]>([]);
  const [isActionRunning, setIsActionRunning] = useState(false);
  const [actionFeedback, setActionFeedback] = useState<string | null>(null);
  const [albumPromptOpen, setAlbumPromptOpen] = useState(false);
  const [albumIdInput, setAlbumIdInput] = useState("");
  const [scrollTop, setScrollTop] = useState(0);
  const [viewportHeight, setViewportHeight] = useState(() => {
    if (typeof window === "undefined") {
      return 800;
    }
    return window.innerHeight;
  });
  const [measuredCardHeight, setMeasuredCardHeight] = useState<number | null>(null);

  const refreshAttemptsRef = useRef<Record<string, number>>({});
  const inFlightRefreshesRef = useRef<Record<string, boolean>>({});
  const urlRefreshQueueRef = useRef<{ active: number; waiters: Array<() => void> }>(
    {
      active: 0,
      waiters: [],
    },
  );
  const firstCardMeasureRef = useRef<HTMLElement | null>(null);
  const isOnline = useOnlineStatus();

  const sortedAssets = useMemo(() => sortGalleryAssets(assets), [assets]);
  const visibleAssets = useMemo(
    () => applyGalleryFilter(sortedAssets, selectedFilter),
    [selectedFilter, sortedAssets],
  );

  const hasAnyAssets = assets.length > 0;
  const showLoading = isLoading || (status === "syncing" && !hasAnyAssets);
  const showReplicaError = !showLoading && !!replicaErrorMessage;
  const showBlockingSyncError = !showLoading && status === "error" && !hasAnyAssets;
  const showOfflineEmpty = !showLoading && !hasAnyAssets && !isOnline;
  const showEmptyState =
    !showLoading &&
    !showReplicaError &&
    !showBlockingSyncError &&
    !showOfflineEmpty &&
    visibleAssets.length === 0;

  const gridStyle = {
    "--gallery-columns": columns,
  } as CSSProperties;

  const cardHeight = measuredCardHeight ?? DEFAULT_GALLERY_CARD_HEIGHT;

  const shouldVirtualize = visibleAssets.length >= MIN_ASSETS_FOR_VIRTUALIZATION;

  const virtualWindow = useMemo<VirtualWindowResult>(() => {
    if (!shouldVirtualize) {
      return {
        renderedAssets: visibleAssets,
        startIndex: 0,
        topSpacerHeight: 0,
        bottomSpacerHeight: 0,
        isVirtualized: false,
      };
    }

    const rowHeight = cardHeight + GALLERY_ROW_GAP_PX;
    const rowsPerViewport = Math.max(1, Math.ceil(viewportHeight / rowHeight));
    const startRow = Math.max(0, Math.floor(scrollTop / rowHeight) - VIRTUAL_OVERSCAN_ROWS);
    const totalRows = Math.ceil(visibleAssets.length / columns);
    const endRow = Math.min(
      totalRows,
      startRow + rowsPerViewport + VIRTUAL_OVERSCAN_ROWS * 2,
    );

    const startIndex = startRow * columns;
    const endIndex = Math.min(visibleAssets.length, endRow * columns);

    return {
      renderedAssets: visibleAssets.slice(startIndex, endIndex),
      startIndex,
      topSpacerHeight: startRow * rowHeight,
      bottomSpacerHeight: Math.max(0, (totalRows - endRow) * rowHeight),
      isVirtualized: true,
    };
  }, [cardHeight, columns, scrollTop, shouldVirtualize, viewportHeight, visibleAssets]);

  const triggerSync = useCallback(() => {
    void sync();
  }, [sync]);

  const selectedSet = useMemo(() => new Set(selectedAssetIds), [selectedAssetIds]);

  const clearActionFeedback = () => {
    setActionFeedback(null);
  };

  const toggleSelectedAsset = (assetId: string) => {
    clearActionFeedback();
    setSelectedAssetIds((current) => {
      if (current.includes(assetId)) {
        return current.filter((id) => id !== assetId);
      }
      return [...current, assetId];
    });
  };

  const runSingleOptimisticAction = useCallback(
    async (
      assetId: string,
      patch: Partial<RemoteAssetRow>,
      execute: () => Promise<void>,
      successMessage: string,
    ) => {
      clearActionFeedback();
      setIsActionRunning(true);

      const previous = await optimisticUpdateAsset(assetId, patch);

      try {
        await execute();
        await sync();
        clearSyncError();
        setActionFeedback(successMessage);
      } catch (error) {
        await rollbackAsset(previous, assetId);
        await sync();
        setActionFeedback(
          error instanceof Error ? error.message : "Action failed",
        );
      } finally {
        setIsActionRunning(false);
      }
    },
    [clearSyncError, sync],
  );

  const handleFavoriteToggle = useCallback(
    async (asset: RemoteAssetRow) => {
      await runSingleOptimisticAction(
        asset.id,
        { isFavorite: !asset.isFavorite },
        () => patchFavorite(asset.id, !asset.isFavorite),
        !asset.isFavorite ? "Added to favorites." : "Removed from favorites.",
      );
    },
    [runSingleOptimisticAction],
  );

  const handleArchiveToggle = useCallback(
    async (asset: RemoteAssetRow) => {
      await runSingleOptimisticAction(
        asset.id,
        { isArchived: !asset.isArchived },
        () => patchArchive(asset.id, !asset.isArchived),
        !asset.isArchived ? "Moved to archive." : "Removed from archive.",
      );
    },
    [runSingleOptimisticAction],
  );

  const handleTrashToggle = useCallback(
    async (asset: RemoteAssetRow) => {
      await runSingleOptimisticAction(
        asset.id,
        { isTrashed: !asset.isTrashed },
        () => (asset.isTrashed ? postRestore(asset.id) : postTrash(asset.id)),
        asset.isTrashed ? "Restored from trash." : "Moved to trash.",
      );
    },
    [runSingleOptimisticAction],
  );

  const handleHardDelete = useCallback(
    async (asset: RemoteAssetRow) => {
      const confirmed = window.confirm(
        "Permanently delete this asset from cloud metadata and storage? This cannot be undone.",
      );
      if (!confirmed) {
        return;
      }

      clearActionFeedback();
      setIsActionRunning(true);

      const previous = await optimisticUpdateAsset(asset.id, { isTrashed: true });

      try {
        await deleteAsset(asset.id);
        await sync();
        clearSyncError();
        setActionFeedback("Asset permanently deleted.");
      } catch (error) {
        await rollbackAsset(previous, asset.id);
        await sync();
        setActionFeedback(
          error instanceof Error ? error.message : "Delete failed",
        );
      } finally {
        setIsActionRunning(false);
      }
    },
    [clearSyncError, sync],
  );

  const runMultiAssetAction = useCallback(
    async (
      action: "favorite" | "archive" | "trash" | "add_to_album",
      options?: { isFavorite?: boolean; isArchived?: boolean; albumId?: string },
    ) => {
      const ids = [...selectedAssetIds];
      if (ids.length === 0) {
        setActionFeedback("Select at least one asset.");
        return;
      }

      clearActionFeedback();
      setIsActionRunning(true);

      const previousRows = await Promise.all(
        ids.map(async (id) => {
          const current = await appDb.remote_assets.get(id);
          return [id, current ?? null] as const;
        }),
      );

      const previousMap = new Map(previousRows);

      try {
        if (action === "favorite") {
          await Promise.all(
            ids.map((id) =>
              optimisticUpdateAsset(id, {
                isFavorite: Boolean(options?.isFavorite),
              }),
            ),
          );
        }

        if (action === "archive") {
          await Promise.all(
            ids.map((id) =>
              optimisticUpdateAsset(id, {
                isArchived: Boolean(options?.isArchived),
              }),
            ),
          );
        }

        if (action === "trash") {
          await Promise.all(
            ids.map((id) =>
              optimisticUpdateAsset(id, {
                isTrashed: true,
              }),
            ),
          );
        }

        const result = await runMultiAction(action, ids, options);

        for (const failedId of result.failedIds) {
          await rollbackAsset(previousMap.get(failedId) ?? null, failedId);
        }

        await sync();

        setSelectedAssetIds(result.failedIds);
        setActionFeedback(
          `Action complete: ${result.succeededIds.length} succeeded, ${result.failedIds.length} failed (concurrency ${CONCURRENCY_LIMIT}).`,
        );
      } catch (error) {
        for (const [assetId, previous] of previousMap.entries()) {
          await rollbackAsset(previous, assetId);
        }

        await sync();
        setActionFeedback(
          error instanceof Error ? error.message : "Bulk action failed",
        );
      } finally {
        setIsActionRunning(false);
      }
    },
    [selectedAssetIds, sync],
  );

  const handleSingleAddToAlbum = useCallback(async (assetId: string) => {
    const albumId = window.prompt("Album ID to add this asset:", "");
    const trimmedAlbumId = albumId?.trim();
    if (!trimmedAlbumId) {
      return;
    }

    clearActionFeedback();
    setIsActionRunning(true);

    try {
      await addAssetToAlbum(trimmedAlbumId, assetId, null);
      await sync();
      clearSyncError();
      setActionFeedback("Asset added to album.");
    } catch (error) {
      setActionFeedback(
        error instanceof Error ? error.message : "Add to album failed",
      );
    } finally {
      setIsActionRunning(false);
    }
  }, [clearSyncError, sync]);

  const runWithUrlRefreshLimit = useCallback(
    async <T,>(task: () => Promise<T>): Promise<T> => {
      const queue = urlRefreshQueueRef.current;

      if (queue.active >= IMAGE_URL_REFRESH_CONCURRENCY) {
        await new Promise<void>((resolve) => {
          queue.waiters.push(resolve);
        });
      }

      queue.active += 1;

      try {
        return await task();
      } finally {
        queue.active -= 1;
        const next = queue.waiters.shift();
        if (next) {
          next();
        }
      }
    },
    [],
  );

  const refreshSourceVariant = useCallback(
    async (assetId: string, variant: AssetReadUrlVariant): Promise<void> => {
      const nextUrl = await runWithUrlRefreshLimit(() =>
        getAssetReplicaReadUrl(assetId, variant),
      );

      setAssetUrlOverridesByAssetId((current) => {
        const currentAssetUrls = current[assetId] ?? {};
        return {
          ...current,
          [assetId]: {
            ...currentAssetUrls,
            [variant]: nextUrl,
          },
        };
      });
    },
    [runWithUrlRefreshLimit],
  );

  const handleImageError = useCallback(
    async (asset: RemoteAssetRow): Promise<void> => {
      if (!isOnline) {
        return;
      }

      const source = getAssetDisplaySource(
        asset,
        fallbackVariantByAssetId[asset.id],
        assetUrlOverridesByAssetId[asset.id],
      );
      if (!source) {
        return;
      }

      const refreshKey = `${asset.id}:${source.variant}`;
      if (inFlightRefreshesRef.current[refreshKey]) {
        return;
      }

      const attempts = refreshAttemptsRef.current[refreshKey] ?? 0;
      if (attempts >= 1) {
        if (source.variant === "thumbnail" && (asset.previewUrl || assetUrlOverridesByAssetId[asset.id]?.preview)) {
          const previewKey = `${asset.id}:preview`;
          const previewAttempts = refreshAttemptsRef.current[previewKey] ?? 0;
          if (!inFlightRefreshesRef.current[previewKey] && previewAttempts < 1) {
            refreshAttemptsRef.current[previewKey] = previewAttempts + 1;
            inFlightRefreshesRef.current[previewKey] = true;

            try {
              setFallbackVariantByAssetId((current) => ({
                ...current,
                [asset.id]: "preview",
              }));

              await refreshSourceVariant(asset.id, "preview");
            } finally {
              inFlightRefreshesRef.current[previewKey] = false;
            }
          }
        }
        return;
      }

      refreshAttemptsRef.current[refreshKey] = attempts + 1;
      inFlightRefreshesRef.current[refreshKey] = true;

      try {
        await refreshSourceVariant(asset.id, source.variant);
      } finally {
        inFlightRefreshesRef.current[refreshKey] = false;
      }
    },
    [
      assetUrlOverridesByAssetId,
      fallbackVariantByAssetId,
      isOnline,
      refreshSourceVariant,
    ],
  );

  useEffect(() => {
    if (status === "idle" && !lastSyncedAt) {
      triggerSync();
    }
  }, [lastSyncedAt, status, triggerSync]);

  useEffect(() => {
    const onScroll = () => {
      const y =
        window.scrollY ||
        document.documentElement.scrollTop ||
        document.body.scrollTop ||
        0;
      setScrollTop(y);
    };

    const onResize = () => {
      setViewportHeight(window.innerHeight);
      onScroll();
    };

    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onResize);
    onResize();

    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onResize);
    };
  }, []);

  useEffect(() => {
    if (!firstCardMeasureRef.current) {
      return;
    }

    const measured = firstCardMeasureRef.current.getBoundingClientRect().height;
    if (!measured || Number.isNaN(measured)) {
      return;
    }

    if (Math.abs(measured - (measuredCardHeight ?? 0)) > 1) {
      setMeasuredCardHeight(measured);
    }
  }, [measuredCardHeight, virtualWindow.renderedAssets.length, columns]);

  useEffect(() => {
    firstCardMeasureRef.current = null;
  }, [selectedFilter, columns]);

  return (
    <PagePanel title="Gallery">
      <div className="gallery-toolbar">
        <div>
          <p className="gallery-subtitle">
            Rendered from IndexedDB replica table <code>remote_assets</code>.
          </p>
          <p className="gallery-sync-meta">
            Last synced: <strong>{lastSyncedAt ?? "Never"}</strong>
          </p>
        </div>
        <div className="gallery-toolbar-actions">
          <label className="gallery-density-control">
            Columns
            <select
              value={columns}
              onChange={(event) => setColumns(Number(event.target.value))}
            >
              {Array.from(
                { length: MAX_GALLERY_COLUMNS - MIN_GALLERY_COLUMNS + 1 },
                (_, index) => {
                  const value = MIN_GALLERY_COLUMNS + index;
                  return (
                    <option key={value} value={value}>
                      {value}
                    </option>
                  );
                },
              )}
            </select>
          </label>
          <button
            type="button"
            className="secondary-btn"
            onClick={triggerSync}
            disabled={status === "syncing"}
          >
            {status === "syncing" ? "Refreshing..." : "Refresh"}
          </button>
        </div>
      </div>

      <div className="gallery-filters" role="tablist" aria-label="Gallery filters">
        {filterOptions.map((option) => (
          <button
            key={option.value}
            type="button"
            role="tab"
            aria-selected={selectedFilter === option.value}
            className={
              selectedFilter === option.value
                ? "gallery-filter active"
                : "gallery-filter"
            }
            onClick={() => setSelectedFilter(option.value)}
          >
            {option.label}
          </button>
        ))}
      </div>

      {!isOnline ? (
        <div className="info-banner" role="status">
          Offline mode: showing locally cached metadata only.
        </div>
      ) : null}

      {errorMessage && hasAnyAssets ? (
        <div className="error-banner" role="alert">
          Refresh failed. Showing cached gallery rows. {errorMessage}
        </div>
      ) : null}

      {actionFeedback ? (
        <div className="info-banner" role="status">
          {actionFeedback}
        </div>
      ) : null}

      <div className="gallery-bulk-bar">
        <p>
          Selected: <strong>{selectedAssetIds.length}</strong>
        </p>
        <div className="gallery-bulk-actions">
          <button
            type="button"
            className="secondary-btn"
            disabled={selectedAssetIds.length === 0 || isActionRunning}
            onClick={() => {
              void runMultiAssetAction("favorite", { isFavorite: true });
            }}
          >
            Favorite
          </button>
          <button
            type="button"
            className="secondary-btn"
            disabled={selectedAssetIds.length === 0 || isActionRunning}
            onClick={() => {
              void runMultiAssetAction("archive", { isArchived: true });
            }}
          >
            Archive
          </button>
          <button
            type="button"
            className="secondary-btn"
            disabled={selectedAssetIds.length === 0 || isActionRunning}
            onClick={() => {
              void runMultiAssetAction("trash");
            }}
          >
            Trash
          </button>
          <button
            type="button"
            className="secondary-btn"
            disabled={selectedAssetIds.length === 0 || isActionRunning}
            onClick={() => setAlbumPromptOpen(true)}
          >
            Add to album
          </button>
          <button
            type="button"
            className="secondary-btn"
            disabled={selectedAssetIds.length === 0 || isActionRunning}
            onClick={() => setSelectedAssetIds([])}
          >
            Clear selection
          </button>
        </div>
      </div>

      {albumPromptOpen ? (
        <div className="gallery-album-prompt">
          <label htmlFor="album-id-input">Album ID</label>
          <input
            id="album-id-input"
            value={albumIdInput}
            onChange={(event) => setAlbumIdInput(event.target.value)}
            placeholder="Enter album UUID"
          />
          <div className="gallery-album-prompt-actions">
            <button
              type="button"
              className="secondary-btn"
              onClick={() => {
                setAlbumPromptOpen(false);
                setAlbumIdInput("");
              }}
            >
              Cancel
            </button>
            <button
              type="button"
              className="secondary-btn"
              disabled={isActionRunning || albumIdInput.trim().length === 0}
              onClick={() => {
                const albumId = albumIdInput.trim();
                if (!albumId) {
                  return;
                }

                setAlbumPromptOpen(false);
                setAlbumIdInput("");
                void runMultiAssetAction("add_to_album", { albumId });
              }}
            >
              Apply
            </button>
          </div>
        </div>
      ) : null}

      {showLoading ? (
        <div className="gallery-state">
          <h2>Loading gallery</h2>
          <p>Syncing replica metadata from the server.</p>
        </div>
      ) : null}

      {showReplicaError ? (
        <div className="gallery-state error" role="alert">
          <h2>Replica read error</h2>
          <p>{replicaErrorMessage}</p>
        </div>
      ) : null}

      {showBlockingSyncError ? (
        <div className="gallery-state error" role="alert">
          <h2>Sync failed</h2>
          <p>{errorMessage ?? "Unable to refresh metadata."}</p>
          <button type="button" className="secondary-btn" onClick={triggerSync}>
            Retry sync
          </button>
        </div>
      ) : null}

      {showOfflineEmpty ? (
        <div className="gallery-state">
          <h2>Offline</h2>
          <p>No cached gallery rows are available yet.</p>
        </div>
      ) : null}

      {showEmptyState ? (
        <div className="gallery-state">
          <h2>No assets found</h2>
          <p>No items match the selected filter.</p>
        </div>
      ) : null}

      {!showLoading &&
      !showReplicaError &&
      !showBlockingSyncError &&
      !showOfflineEmpty &&
      visibleAssets.length > 0 ? (
        <div className="gallery-grid" style={gridStyle}>
          {virtualWindow.isVirtualized ? (
            <div className="gallery-virtual-spacer" style={{ height: virtualWindow.topSpacerHeight }} />
          ) : null}

          {virtualWindow.renderedAssets.map((asset, index) => {
            const source = getAssetDisplaySource(
              asset,
              fallbackVariantByAssetId[asset.id],
              assetUrlOverridesByAssetId[asset.id],
            );

            return (
              <GalleryCard
                key={asset.id}
                asset={asset}
                sourceUrl={source?.url ?? null}
                sourceVariant={source?.variant ?? null}
                isSelected={selectedSet.has(asset.id)}
                isActionRunning={isActionRunning}
                onToggleSelected={toggleSelectedAsset}
                onFavoriteToggle={handleFavoriteToggle}
                onArchiveToggle={handleArchiveToggle}
                onTrashToggle={handleTrashToggle}
                onSingleAddToAlbum={handleSingleAddToAlbum}
                onHardDelete={handleHardDelete}
                onImageError={handleImageError}
                measureRef={
                  index === 0
                    ? (node) => {
                        if (node) {
                          firstCardMeasureRef.current = node;
                        }
                      }
                    : undefined
                }
              />
            );
          })}

          {virtualWindow.isVirtualized ? (
            <div className="gallery-virtual-spacer" style={{ height: virtualWindow.bottomSpacerHeight }} />
          ) : null}
        </div>
      ) : null}
    </PagePanel>
  );
}
