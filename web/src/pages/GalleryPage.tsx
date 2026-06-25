import type { CSSProperties } from "react";
import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import type { MouseEvent } from "react";
import { appDb, type RemoteAssetRow } from "../db/appDb";
import {
  CONCURRENCY_LIMIT,
  optimisticUpdateAsset,
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
import {
  applyNavViewFilter,
  applyQuickFilter,
  buildMonthIndex,
  getRangeSelectionIds,
  groupAssetsByDate,
  sortAssetsForTimeline,
  type GalleryNavView,
  type GalleryQuickFilter,
} from "../features/gallery/galleryUtils";
import {
  consumeGalleryScrollState,
  saveGalleryScrollState,
  saveViewerContext,
} from "../features/gallery/viewerContext";

interface FilterOption {
  value: GalleryQuickFilter;
  label: string;
}

const filterOptions: FilterOption[] = [
  { value: "all", label: "All" },
  { value: "photos", label: "Photos" },
  { value: "videos", label: "Videos" },
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
  isSelectionMode: boolean;
  onToggleSelected: (assetId: string) => void;
  onToggleSelectedRange: (assetId: string) => void;
  onOpenAsset: (assetId: string) => void;
  onImageError: (asset: RemoteAssetRow) => Promise<void>;
  measureRef?: (node: HTMLElement | null) => void;
}

interface VirtualWindowResult {
  renderedAssets: RemoteAssetRow[];
  startIndex: number;
  endIndex: number;
  topSpacerHeight: number;
  bottomSpacerHeight: number;
  isVirtualized: boolean;
}

interface SelectionState {
  routeKey: string;
  ids: string[];
  anchorId: string | null;
}

function getAssetDisplaySource(
  asset: RemoteAssetRow,
  fallbackVariant: AssetReadUrlVariant | undefined,
  overrideUrls: Partial<Record<AssetReadUrlVariant, string>> | undefined,
): { variant: AssetReadUrlVariant; url: string } | null {
  const thumbnailUrl = overrideUrls?.thumbnail ?? asset.thumbnailUrl;
  const previewUrl = overrideUrls?.preview ?? asset.previewUrl;

  if (fallbackVariant === "thumbnail" && thumbnailUrl) {
    return { variant: "thumbnail", url: thumbnailUrl };
  }
  if (previewUrl) {
    return { variant: "preview", url: previewUrl };
  }
  if (thumbnailUrl) {
    return { variant: "thumbnail", url: thumbnailUrl };
  }
  return null;
}

const GalleryCard = memo(function GalleryCard({
  asset,
  sourceUrl,
  sourceVariant,
  isSelected,
  isSelectionMode,
  onToggleSelected,
  onToggleSelectedRange,
  onOpenAsset,
  onImageError,
  measureRef,
}: GalleryCardProps) {
  const handleCheckboxClick = (event: MouseEvent<HTMLInputElement>) => {
    if (event.shiftKey) {
      onToggleSelectedRange(asset.id);
      return;
    }
    onToggleSelected(asset.id);
  };

  return (
    <article
      className={
        isSelected
          ? "gallery-card is-selected"
          : isSelectionMode
            ? "gallery-card selection-mode"
            : "gallery-card"
      }
      ref={measureRef}
    >
      <div className="gallery-card-overlay-top">
        <label className="gallery-select-toggle">
          <input
            type="checkbox"
            checked={isSelected}
            onClick={handleCheckboxClick}
            onChange={() => undefined}
          />
          <span className="sr-only">Select</span>
        </label>

        <div className="gallery-card-flags">
          {asset.mediaType === "video" ? <span className="gallery-flag">Video</span> : null}
          {asset.isFavorite ? <span className="gallery-flag">Favorite</span> : null}
          {asset.isArchived ? <span className="gallery-flag">Archived</span> : null}
          {asset.isTrashed ? <span className="gallery-flag">Trash</span> : null}
        </div>
      </div>

      <Link
        className="gallery-thumb-wrap"
        to={`/assets/${asset.id}`}
        onClick={() => {
          onOpenAsset(asset.id);
        }}
      >
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
  const location = useLocation();
  const status = useAssetSyncStore((state) => state.status);
  const errorMessage = useAssetSyncStore((state) => state.errorMessage);
  const lastSyncedAt = useAssetSyncStore((state) => state.lastSyncedAt);
  const sync = useAssetSyncStore((state) => state.syncAssetsChanges);
  const { assets, isLoading, errorMessage: replicaErrorMessage } =
    useRemoteAssetsReplica();

  const columns = useGalleryPreferencesStore((state) => state.columns);
  const setColumns = useGalleryPreferencesStore((state) => state.setColumns);

  const [selectedFilter, setSelectedFilter] = useState<GalleryQuickFilter>("all");
  const [fallbackVariantByAssetId, setFallbackVariantByAssetId] = useState<
    Record<string, AssetReadUrlVariant>
  >({});
  const [assetUrlOverridesByAssetId, setAssetUrlOverridesByAssetId] = useState<
    Record<string, Partial<Record<AssetReadUrlVariant, string>>>
  >({});
  const [selectionState, setSelectionState] = useState<SelectionState>({
    routeKey: "",
    ids: [],
    anchorId: null,
  });
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

  const navView: GalleryNavView = useMemo(() => {
    if (location.pathname.startsWith("/favorites")) {
      return "favorites";
    }
    if (location.pathname.startsWith("/archive")) {
      return "archive";
    }
    if (location.pathname.startsWith("/trash")) {
      return "trash";
    }
    return "photos";
  }, [location.pathname]);

  const routeKey = useMemo(() => {
    if (navView === "photos") {
      return `${location.pathname}?filter=${selectedFilter}`;
    }
    return location.pathname;
  }, [location.pathname, navView, selectedFilter]);

  const galleryHeading = useMemo(() => {
    switch (navView) {
      case "favorites":
        return "Favorites";
      case "archive":
        return "Archive";
      case "trash":
        return "Trash";
      case "photos":
      default:
        return "Photos";
    }
  }, [navView]);

  const sortedAssets = useMemo(() => sortAssetsForTimeline(assets), [assets]);
  const navFilteredAssets = useMemo(
    () => applyNavViewFilter(sortedAssets, navView),
    [navView, sortedAssets],
  );
  const visibleAssets = useMemo(() => {
    if (navView !== "photos") {
      return navFilteredAssets;
    }
    return applyQuickFilter(navFilteredAssets, selectedFilter);
  }, [navFilteredAssets, navView, selectedFilter]);

  const dateGroups = useMemo(() => groupAssetsByDate(visibleAssets), [visibleAssets]);
  const monthIndex = useMemo(() => buildMonthIndex(dateGroups), [dateGroups]);

  const orderedAssetIds = useMemo(
    () => visibleAssets.map((asset) => asset.id),
    [visibleAssets],
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
        endIndex: visibleAssets.length,
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
      endIndex,
      topSpacerHeight: startRow * rowHeight,
      bottomSpacerHeight: Math.max(0, (totalRows - endRow) * rowHeight),
      isVirtualized: true,
    };
  }, [cardHeight, columns, scrollTop, shouldVirtualize, viewportHeight, visibleAssets]);

  const triggerSync = useCallback(() => {
    void sync();
  }, [sync]);

  const selectedAssetIds = useMemo(
    () => (selectionState.routeKey === routeKey ? selectionState.ids : []),
    [routeKey, selectionState.ids, selectionState.routeKey],
  );
  const isSelectionMode = selectedAssetIds.length > 0;
  const lastSelectedAssetId = useMemo(
    () => (selectionState.routeKey === routeKey ? selectionState.anchorId : null),
    [routeKey, selectionState.anchorId, selectionState.routeKey],
  );
  const selectedSet = useMemo(() => new Set(selectedAssetIds), [selectedAssetIds]);

  const clearActionFeedback = () => {
    setActionFeedback(null);
  };

  const toggleSelectedAsset = (assetId: string) => {
    clearActionFeedback();
    setSelectionState((current) => {
      const ids = current.routeKey === routeKey ? current.ids : [];

      if (ids.includes(assetId)) {
        return {
          routeKey,
          ids: ids.filter((id) => id !== assetId),
          anchorId: assetId,
        };
      }

      return {
        routeKey,
        ids: [...ids, assetId],
        anchorId: assetId,
      };
    });
  };

  const toggleSelectedAssetRange = (assetId: string) => {
    clearActionFeedback();
    const rangeIds = getRangeSelectionIds(orderedAssetIds, lastSelectedAssetId, assetId);

    setSelectionState((current) => {
      const ids = current.routeKey === routeKey ? current.ids : [];
      const nextSet = new Set(ids);
      for (const id of rangeIds) {
        nextSet.add(id);
      }

      return {
        routeKey,
        ids: [...nextSet],
        anchorId: assetId,
      };
    });
  };

  const jumpToMonth = (groupKey: string) => {
    const target = document.getElementById(`gallery-group-${groupKey}`);
    if (!target) {
      return;
    }

    target.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  const handleOpenAsset = (assetId: string) => {
    const scrollY =
      window.scrollY ||
      document.documentElement.scrollTop ||
      document.body.scrollTop ||
      0;
    saveGalleryScrollState(routeKey, scrollY);

    const viewerSource = navView === "photos" ? "gallery" : navView;

    saveViewerContext({
      source: viewerSource,
      backTo: routeKey,
      assetIds: orderedAssetIds,
      selectedFilter,
      updatedAt: new Date().toISOString(),
    });

    setSelectionState((current) => ({
      routeKey,
      ids: current.routeKey === routeKey ? current.ids : [],
      anchorId: assetId,
    }));
  };


  const runMultiAssetAction = useCallback(
    async (
      action: "favorite" | "archive" | "trash" | "restore" | "add_to_album",
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
        if (action === "restore") {
          await Promise.all(
            ids.map((id) =>
              optimisticUpdateAsset(id, {
                isTrashed: false,
              }),
            ),
          );

          const restoreBatches = await runMultiAction("restore", ids, options);
          const failedRestore = restoreBatches.failedIds;
          const succeededRestore = restoreBatches.succeededIds;

          for (const failedId of failedRestore) {
            await rollbackAsset(previousMap.get(failedId) ?? null, failedId);
          }

          await sync();
          setSelectionState({
            routeKey,
            ids: failedRestore,
            anchorId: failedRestore.at(-1) ?? null,
          });
          setActionFeedback(
            `Action complete: ${succeededRestore.length} succeeded, ${failedRestore.length} failed (concurrency ${CONCURRENCY_LIMIT}).`,
          );
          return;
        }

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

        setSelectionState({
          routeKey,
          ids: result.failedIds,
          anchorId: result.failedIds.at(-1) ?? null,
        });
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
    [routeKey, selectedAssetIds, sync],
  );

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
        if (source.variant === "preview" && (asset.thumbnailUrl || assetUrlOverridesByAssetId[asset.id]?.thumbnail)) {
          const thumbnailKey = `${asset.id}:thumbnail`;
          const thumbnailAttempts = refreshAttemptsRef.current[thumbnailKey] ?? 0;
          if (!inFlightRefreshesRef.current[thumbnailKey] && thumbnailAttempts < 1) {
            refreshAttemptsRef.current[thumbnailKey] = thumbnailAttempts + 1;
            inFlightRefreshesRef.current[thumbnailKey] = true;

            try {
              setFallbackVariantByAssetId((current) => ({
                ...current,
                [asset.id]: "thumbnail",
              }));

              await refreshSourceVariant(asset.id, "thumbnail");
            } finally {
              inFlightRefreshesRef.current[thumbnailKey] = false;
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
    const restored = consumeGalleryScrollState(routeKey);
    if (restored === null) {
      return;
    }

    window.scrollTo({ top: restored, behavior: "auto" });
  }, [routeKey]);

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
          <p className="gallery-subtitle">{galleryHeading} timeline from local replica.</p>
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
            {status === "syncing" ? "Syncing..." : "Sync now"}
          </button>
        </div>
      </div>

      {status === "syncing" ? (
        <div className="info-banner" role="status">
          Metadata sync in progress. Local timeline remains available.
        </div>
      ) : null}

      <div className="gallery-filters" role="tablist" aria-label="Gallery filters">
        {navView === "photos"
          ? filterOptions.map((option) => (
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
            ))
          : null}
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

      <div
        className={
          selectedAssetIds.length > 0
            ? "gallery-bulk-bar selection-mode"
            : "gallery-bulk-bar"
        }
      >
        <p>
          Selected: <strong>{selectedAssetIds.length}</strong>
        </p>
        <div
          className="gallery-bulk-actions"
          style={{ display: selectedAssetIds.length > 0 ? "flex" : "none" }}
        >
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
              void runMultiAssetAction("favorite", { isFavorite: false });
            }}
          >
            Unfavorite
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
              void runMultiAssetAction("archive", { isArchived: false });
            }}
          >
            Unarchive
          </button>
          <button
            type="button"
            className="secondary-btn"
            disabled={selectedAssetIds.length === 0 || isActionRunning}
            onClick={() => {
              if (navView === "trash") {
                void runMultiAssetAction("restore");
                return;
              }
              void runMultiAssetAction("trash");
            }}
          >
            {navView === "trash" ? "Restore" : "Trash"}
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
            onClick={() => {
              setSelectionState({
                routeKey,
                ids: [],
                anchorId: null,
              });
            }}
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
        <div className="timeline-layout">
          <div className="timeline-main">
            {virtualWindow.isVirtualized ? (
              <div
                className="gallery-virtual-spacer"
                style={{ height: virtualWindow.topSpacerHeight }}
              />
            ) : null}

            {dateGroups.map((group) => {
              const groupedSet = new Set(group.assets.map((asset) => asset.id));
              const groupRenderedAssets = virtualWindow.isVirtualized
                ? virtualWindow.renderedAssets.filter((asset) => groupedSet.has(asset.id))
                : group.assets;

              if (groupRenderedAssets.length === 0 && virtualWindow.isVirtualized) {
                return null;
              }

              return (
                <section
                  className="timeline-group"
                  key={group.key}
                  id={`gallery-group-${group.key}`}
                >
                  <header className="timeline-group-header">
                    <h2>{group.label}</h2>
                    <span>{group.assets.length} items</span>
                  </header>

                  <div className="gallery-grid" style={gridStyle}>
                    {groupRenderedAssets.map((asset, index) => {
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
                          isSelectionMode={isSelectionMode}
                          onToggleSelected={toggleSelectedAsset}
                          onToggleSelectedRange={toggleSelectedAssetRange}
                          onOpenAsset={handleOpenAsset}
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
                  </div>
                </section>
              );
            })}

            {virtualWindow.isVirtualized ? (
              <div
                className="gallery-virtual-spacer"
                style={{ height: virtualWindow.bottomSpacerHeight }}
              />
            ) : null}
          </div>

          {monthIndex.length > 0 ? (
            <aside className="month-rail" aria-label="Month navigation">
              {monthIndex.map((item) => (
                <button
                  key={item.monthKey}
                  type="button"
                  className="month-rail-item"
                  onClick={() => jumpToMonth(item.firstGroupKey)}
                >
                  {item.monthLabel}
                </button>
              ))}
            </aside>
          ) : null}
        </div>
      ) : null}

      {scrollTop > 480 ? (
        <button
          type="button"
          className="jump-top-btn"
          onClick={() => {
            window.scrollTo({ top: 0, behavior: "smooth" });
          }}
        >
          Jump to top
        </button>
      ) : null}
    </PagePanel>
  );
}
