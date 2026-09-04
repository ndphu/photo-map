import type { CSSProperties, FormEvent, MouseEvent } from "react";
import {
  memo,
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import type { Location } from "react-router-dom";
import { Link, useLocation, useNavigate } from "react-router-dom";
import type { RemoteAssetRow } from "../db/appDb";
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
  getRemoteAsset,
  updateRemoteAsset,
} from "../features/assets/assetReplica";
import {
  MAX_GALLERY_COLUMNS,
  MIN_GALLERY_COLUMNS,
  useGalleryPreferencesStore,
} from "../features/assets/galleryPreferencesStore";
import { useRemoteAssetsReplica } from "../features/assets/useRemoteAssetsReplica";
import { PagePanel } from "../components/PagePanel";
import { useAssetSyncStore } from "../features/assets/assetSyncStore";
import {
  applyDateRangeFilter,
  applyNavViewFilter,
  applyQuickFilter,
  buildMonthIndex,
  getRangeSelectionIds,
  groupAssetsByDate,
  isValidDateFilterValue,
  sortAssetsForTimeline,
  type GalleryNavView,
  type GalleryQuickFilter,
} from "../features/gallery/galleryUtils";
import {
  clearGalleryScrollState,
  readGalleryScrollState,
  saveGalleryScrollState,
  saveViewerContext,
} from "../features/gallery/viewerContext";
import { isPresignedUrlUsable } from "../lib/presignedUrl";
import { useAuthStore } from "../store/authStore";

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
const INITIAL_RENDER_LIMIT = 420;
const RENDER_CHUNK_SIZE = 300;
const GALLERY_ASSET_ELEMENT_ID_PREFIX = "gallery-asset-";

function getGalleryAssetElementId(assetId: string): string {
  return `${GALLERY_ASSET_ELEMENT_ID_PREFIX}${assetId}`;
}

interface GalleryCardProps {
  asset: RemoteAssetRow;
  backgroundLocation: Location;
  sourceUrl: string | null;
  sourceVariant: AssetReadUrlVariant | null;
  imageNonce: number;
  isSelected: boolean;
  isSelectionMode: boolean;
  onToggleSelected: (assetId: string) => void;
  onToggleSelectedRange: (assetId: string) => void;
  onOpenAsset: (assetId: string) => void;
  onImageError: (asset: RemoteAssetRow) => Promise<void>;
  measureRef?: (node: HTMLElement | null) => void;
}

interface SelectionState {
  routeKey: string;
  ids: string[];
  anchorId: string | null;
}

interface GalleryDateRangeFilterProps {
  fromDate: string;
  toDate: string;
  onApply: (fromDate: string, toDate: string) => void;
}

function GalleryDateRangeFilter({
  fromDate,
  toDate,
  onApply,
}: GalleryDateRangeFilterProps) {
  const [draftFromDate, setDraftFromDate] = useState(fromDate);
  const [draftToDate, setDraftToDate] = useState(toDate);
  const errorMessage =
    draftFromDate && draftToDate && draftFromDate > draftToDate
      ? "From date must be on or before To date."
      : null;

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!errorMessage) {
      onApply(draftFromDate, draftToDate);
    }
  };

  const handleClear = () => {
    setDraftFromDate("");
    setDraftToDate("");
    onApply("", "");
  };

  return (
    <div className="gallery-date-filter-wrap">
      <form className="gallery-date-filter" onSubmit={handleSubmit}>
        <label>
          From
          <input
            type="date"
            value={draftFromDate}
            max={draftToDate || undefined}
            onChange={(event) => setDraftFromDate(event.target.value)}
          />
        </label>
        <label>
          To
          <input
            type="date"
            value={draftToDate}
            min={draftFromDate || undefined}
            onChange={(event) => setDraftToDate(event.target.value)}
          />
        </label>
        <div className="gallery-date-filter-actions">
          <button
            type="submit"
            className="secondary-btn"
            disabled={Boolean(errorMessage)}
          >
            Apply
          </button>
          <button
            type="button"
            className="secondary-btn"
            onClick={handleClear}
            disabled={!draftFromDate && !draftToDate && !fromDate && !toDate}
          >
            Clear
          </button>
        </div>
      </form>

      {errorMessage ? (
        <div className="error-banner gallery-date-filter-error" role="alert">
          {errorMessage}
        </div>
      ) : null}
    </div>
  );
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
  backgroundLocation,
  sourceUrl,
  sourceVariant,
  imageNonce,
  isSelected,
  isSelectionMode,
  onToggleSelected,
  onToggleSelectedRange,
  onOpenAsset,
  onImageError,
  measureRef,
}: GalleryCardProps) {
  const rawRatio =
    asset.width && asset.height ? asset.width / asset.height : asset.mediaType === "video" ? 16 / 9 : 1;
  const cardRatio = Math.min(2.5, Math.max(0.55, rawRatio));
  const cardStyle = {
    "--card-ratio": String(cardRatio),
  } as CSSProperties;

  const handleCheckboxClick = (event: MouseEvent<HTMLInputElement>) => {
    if (event.shiftKey) {
      onToggleSelectedRange(asset.id);
      return;
    }
    onToggleSelected(asset.id);
  };

  return (
    <article
      id={getGalleryAssetElementId(asset.id)}
      className={
        isSelected
          ? "gallery-card is-selected"
          : isSelectionMode
            ? "gallery-card selection-mode"
            : "gallery-card"
      }
      style={cardStyle}
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
        state={{ backgroundLocation }}
        onClick={() => {
          onOpenAsset(asset.id);
        }}
      >
        {sourceUrl && sourceVariant ? (
          <img
            key={`${asset.id}-${sourceUrl}-${imageNonce}`}
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
  const navigate = useNavigate();
  const ownerUserId = useAuthStore((state) => state.user?.id ?? null);
  const status = useAssetSyncStore((state) => state.status);
  const errorMessage = useAssetSyncStore((state) => state.errorMessage);
  const lastSyncedAt = useAssetSyncStore((state) => state.lastSyncedAt);
  const remainingCount = useAssetSyncStore((state) => state.remainingCount);
  const syncPercent = useAssetSyncStore((state) => state.percent);
  const sync = useAssetSyncStore((state) => state.syncAssetsChanges);
  const { assets, isLoading, errorMessage: replicaErrorMessage } =
    useRemoteAssetsReplica();

  const columns = useGalleryPreferencesStore((state) => state.columns);
  const setColumns = useGalleryPreferencesStore((state) => state.setColumns);

  const selectedFilter = useMemo<GalleryQuickFilter>(() => {
    const filter = new URLSearchParams(location.search).get("filter");
    return filter === "photos" || filter === "videos" ? filter : "all";
  }, [location.search]);
  const appliedDateRange = useMemo(() => {
    const query = new URLSearchParams(location.search);
    const fromDate = query.get("from") ?? "";
    const toDate = query.get("to") ?? "";

    return {
      fromDate: isValidDateFilterValue(fromDate) ? fromDate : "",
      toDate: isValidDateFilterValue(toDate) ? toDate : "",
    };
  }, [location.search]);
  const [fallbackVariantByAssetId, setFallbackVariantByAssetId] = useState<
    Record<string, AssetReadUrlVariant>
  >({});
  const [assetUrlOverridesByAssetId, setAssetUrlOverridesByAssetId] = useState<
    Record<string, Partial<Record<AssetReadUrlVariant, string>>>
  >({});
  const [imageNonceByAssetId, setImageNonceByAssetId] = useState<
    Record<string, number>
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
  const [renderState, setRenderState] = useState<{ key: string; limit: number }>({
    key: "",
    limit: INITIAL_RENDER_LIMIT,
  });

  const refreshAttemptsRef = useRef<Record<string, number>>({});
  const inFlightRefreshesRef = useRef<Record<string, boolean>>({});
  const restoredScrollRouteRef = useRef<string | null>(null);
  const urlRefreshQueueRef = useRef<{ active: number; waiters: Array<() => void> }>(
    {
      active: 0,
      waiters: [],
    },
  );
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
    return `${location.pathname}${location.search}`;
  }, [location.pathname, location.search]);
  const savedScrollState = useMemo(
    () => readGalleryScrollState(routeKey),
    [routeKey],
  );

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
    const mediaFilteredAssets = applyQuickFilter(navFilteredAssets, selectedFilter);
    return applyDateRangeFilter(
      mediaFilteredAssets,
      appliedDateRange.fromDate,
      appliedDateRange.toDate,
    );
  }, [appliedDateRange, navFilteredAssets, navView, selectedFilter]);

  const hasActiveDateRange = Boolean(
    appliedDateRange.fromDate || appliedDateRange.toDate,
  );

  const renderKey = `${routeKey}:${visibleAssets.length}`;
  const baseRenderLimit =
    renderState.key === renderKey ? renderState.limit : INITIAL_RENDER_LIMIT;
  const restoreAnchorIndex = savedScrollState?.anchorAssetId
    ? visibleAssets.findIndex((asset) => asset.id === savedScrollState.anchorAssetId)
    : -1;
  const restoreRenderLimit =
    restoreAnchorIndex >= 0 ? restoreAnchorIndex + 1 : INITIAL_RENDER_LIMIT;
  const renderLimit = Math.max(baseRenderLimit, restoreRenderLimit);

  const renderedAssets = useMemo(
    () => visibleAssets.slice(0, Math.min(renderLimit, visibleAssets.length)),
    [renderLimit, visibleAssets],
  );
  const renderedAssetIdSet = useMemo(
    () => new Set(renderedAssets.map((asset) => asset.id)),
    [renderedAssets],
  );
  const dateGroups = useMemo(
    () => groupAssetsByDate(renderedAssets),
    [renderedAssets],
  );
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

  const rowHeight = useMemo(() => {
    if (columns <= 2) {
      return 260;
    }
    if (columns === 3) {
      return 220;
    }
    if (columns === 4) {
      return 190;
    }
    if (columns === 5) {
      return 165;
    }
    return 145;
  }, [columns]);

  const gridStyle = {
    "--gallery-row-height": `${rowHeight}px`,
  } as CSSProperties;

  const triggerSync = useCallback(() => {
    if (ownerUserId) {
      void sync(ownerUserId);
    }
  }, [ownerUserId, sync]);

  const updateMediaFilter = useCallback(
    (filter: GalleryQuickFilter) => {
      const query = new URLSearchParams(location.search);
      if (filter === "all") {
        query.delete("filter");
      } else {
        query.set("filter", filter);
      }

      const queryString = query.toString();
      navigate(`${location.pathname}${queryString ? `?${queryString}` : ""}`, {
        replace: true,
      });
    },
    [location.pathname, location.search, navigate],
  );

  const updateDateRangeQuery = useCallback(
    (fromDate: string, toDate: string) => {
      const query = new URLSearchParams(location.search);

      if (fromDate) {
        query.set("from", fromDate);
      } else {
        query.delete("from");
      }

      if (toDate) {
        query.set("to", toDate);
      } else {
        query.delete("to");
      }

      const queryString = query.toString();
      navigate(`${location.pathname}${queryString ? `?${queryString}` : ""}`, {
        replace: true,
      });
    },
    [location.pathname, location.search, navigate],
  );

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
    const anchorElement = document.getElementById(
      getGalleryAssetElementId(assetId),
    );
    const anchorViewportOffset = anchorElement?.getBoundingClientRect().top ?? 0;
    saveGalleryScrollState(
      routeKey,
      scrollY,
      assetId,
      anchorViewportOffset,
    );

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
      if (!ownerUserId) {
        setActionFeedback("Authenticated user is required.");
        return;
      }

      clearActionFeedback();
      setIsActionRunning(true);

      const previousRows = await Promise.all(
        ids.map(async (id) => {
          const current = ownerUserId
            ? await getRemoteAsset(ownerUserId, id)
            : undefined;
          return [id, current ?? null] as const;
        }),
      );

      const previousMap = new Map(previousRows);

      try {
        if (action === "restore") {
          await Promise.all(
            ids.map((id) =>
              optimisticUpdateAsset(ownerUserId, id, {
                isTrashed: false,
              }),
            ),
          );

          const restoreBatches = await runMultiAction("restore", ids, options);
          const failedRestore = restoreBatches.failedIds;
          const succeededRestore = restoreBatches.succeededIds;

          for (const failedId of failedRestore) {
            await rollbackAsset(
              ownerUserId,
              previousMap.get(failedId) ?? null,
              failedId,
            );
          }

          await sync(ownerUserId);
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
              optimisticUpdateAsset(ownerUserId, id, {
                isFavorite: Boolean(options?.isFavorite),
              }),
            ),
          );
        }

        if (action === "archive") {
          await Promise.all(
            ids.map((id) =>
              optimisticUpdateAsset(ownerUserId, id, {
                isArchived: Boolean(options?.isArchived),
              }),
            ),
          );
        }

        if (action === "trash") {
          await Promise.all(
            ids.map((id) =>
              optimisticUpdateAsset(ownerUserId, id, {
                isTrashed: true,
              }),
            ),
          );
        }

        const result = await runMultiAction(action, ids, options);

        for (const failedId of result.failedIds) {
          await rollbackAsset(
            ownerUserId,
            previousMap.get(failedId) ?? null,
            failedId,
          );
        }

        await sync(ownerUserId);

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
          await rollbackAsset(ownerUserId, previous, assetId);
        }

        await sync(ownerUserId);
        setActionFeedback(
          error instanceof Error ? error.message : "Bulk action failed",
        );
      } finally {
        setIsActionRunning(false);
      }
    },
    [ownerUserId, routeKey, selectedAssetIds, sync],
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

      if (variant === "thumbnail") {
        if (ownerUserId) {
          await updateRemoteAsset(ownerUserId, assetId, { thumbnailUrl: nextUrl });
        }
      } else {
        if (ownerUserId) {
          await updateRemoteAsset(ownerUserId, assetId, { previewUrl: nextUrl });
        }
      }

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
    [ownerUserId, runWithUrlRefreshLimit],
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
          const thumbnailUrl =
            assetUrlOverridesByAssetId[asset.id]?.thumbnail ?? asset.thumbnailUrl;
          const thumbnailKey = `${asset.id}:thumbnail`;
          const thumbnailAttempts = refreshAttemptsRef.current[thumbnailKey] ?? 0;
          if (
            thumbnailUrl &&
            !inFlightRefreshesRef.current[thumbnailKey] &&
            thumbnailAttempts < 1
          ) {
            refreshAttemptsRef.current[thumbnailKey] = thumbnailAttempts + 1;
            inFlightRefreshesRef.current[thumbnailKey] = true;

            try {
              setFallbackVariantByAssetId((current) => ({
                ...current,
                [asset.id]: "thumbnail",
              }));

              if (isPresignedUrlUsable(thumbnailUrl) !== true) {
                await refreshSourceVariant(asset.id, "thumbnail");
              }
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
        if (isPresignedUrlUsable(source.url) === true) {
          setImageNonceByAssetId((current) => ({
            ...current,
            [asset.id]: (current[asset.id] ?? 0) + 1,
          }));
        } else {
          await refreshSourceVariant(asset.id, source.variant);
        }
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

  useLayoutEffect(() => {
    if (
      isLoading ||
      !savedScrollState ||
      restoredScrollRouteRef.current === routeKey
    ) {
      return;
    }

    if (savedScrollState.anchorAssetId && restoreAnchorIndex >= 0) {
      const anchorElement = document.getElementById(
        getGalleryAssetElementId(savedScrollState.anchorAssetId),
      );
      if (!anchorElement) {
        return;
      }

      const targetOffset = savedScrollState.anchorViewportOffset ?? 0;
      const currentOffset = anchorElement.getBoundingClientRect().top;
      window.scrollBy({
        top: currentOffset - targetOffset,
        behavior: "auto",
      });
    } else {
      window.scrollTo({ top: savedScrollState.scrollY, behavior: "auto" });
    }

    restoredScrollRouteRef.current = routeKey;
    clearGalleryScrollState(routeKey);
  }, [isLoading, restoreAnchorIndex, routeKey, savedScrollState]);

  useEffect(() => {
    const onScroll = () => {
      const y =
        window.scrollY ||
        document.documentElement.scrollTop ||
        document.body.scrollTop ||
        0;
      setScrollTop(y);

      const doc = document.documentElement;
      const remaining = doc.scrollHeight - (y + window.innerHeight);
      if (remaining < 900) {
        setRenderState((current) => {
          const currentLimit =
            current.key === renderKey ? current.limit : INITIAL_RENDER_LIMIT;
          if (currentLimit >= visibleAssets.length) {
            if (current.key === renderKey) {
              return current;
            }
            return { key: renderKey, limit: currentLimit };
          }

          const nextLimit = Math.min(
            visibleAssets.length,
            currentLimit + RENDER_CHUNK_SIZE,
          );

          if (current.key === renderKey && current.limit === nextLimit) {
            return current;
          }

          return {
            key: renderKey,
            limit: nextLimit,
          };
        });
      }
    };

    const onResize = () => {
      onScroll();
    };

    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onResize);
    onResize();

    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onResize);
    };
  }, [renderKey, visibleAssets.length]);

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
        <div className="info-banner metadata-sync-progress" role="status">
          <span>
            {syncPercent === null || remainingCount === null
              ? "Syncing metadata..."
              : `Syncing metadata — ${syncPercent}% · ${remainingCount.toLocaleString()} changes remaining`}
          </span>
          <progress
            className="metadata-sync-progress-bar"
            max={100}
            value={syncPercent ?? undefined}
            aria-label="Cloud metadata sync progress"
          />
        </div>
      ) : null}

      {navView === "photos" ? (
        <div className="gallery-filter-bar">
          <div className="gallery-filters" role="tablist" aria-label="Media filters">
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
                onClick={() => updateMediaFilter(option.value)}
              >
                {option.label}
              </button>
            ))}
          </div>

          <GalleryDateRangeFilter
            key={`${appliedDateRange.fromDate}:${appliedDateRange.toDate}`}
            fromDate={appliedDateRange.fromDate}
            toDate={appliedDateRange.toDate}
            onApply={updateDateRangeQuery}
          />
        </div>
      ) : null}

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
          <p>
            {hasActiveDateRange
              ? "No items match the selected media and date filters."
              : "No items match the selected filter."}
          </p>
        </div>
      ) : null}

      {!showLoading &&
      !showReplicaError &&
      !showBlockingSyncError &&
      !showOfflineEmpty &&
      visibleAssets.length > 0 ? (
        <div className="timeline-layout">
          <div className="timeline-main">
            {dateGroups.map((group) => {
              const groupRenderedAssets = group.assets.filter((asset) =>
                renderedAssetIdSet.has(asset.id),
              );

              if (groupRenderedAssets.length === 0) {
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
                    {groupRenderedAssets.map((asset) => {
                      const source = getAssetDisplaySource(
                        asset,
                        fallbackVariantByAssetId[asset.id],
                        assetUrlOverridesByAssetId[asset.id],
                      );

                      return (
                        <GalleryCard
                          key={asset.id}
                          asset={asset}
                          backgroundLocation={location}
                          sourceUrl={source?.url ?? null}
                          sourceVariant={source?.variant ?? null}
                          imageNonce={imageNonceByAssetId[asset.id] ?? 0}
                          isSelected={selectedSet.has(asset.id)}
                          isSelectionMode={isSelectionMode}
                          onToggleSelected={toggleSelectedAsset}
                          onToggleSelectedRange={toggleSelectedAssetRange}
                          onOpenAsset={handleOpenAsset}
                          onImageError={handleImageError}
                          measureRef={undefined}
                        />
                      );
                    })}
                  </div>
                </section>
              );
            })}

            {renderLimit < visibleAssets.length ? (
              <div className="gallery-state" role="status">
                <h2>Loading more items</h2>
                <p>
                  Showing {Math.min(renderLimit, visibleAssets.length)} / {visibleAssets.length}
                  . Scroll down to continue.
                </p>
              </div>
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
