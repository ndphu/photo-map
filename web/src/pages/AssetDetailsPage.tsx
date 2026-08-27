import type { CSSProperties, MouseEvent, PointerEvent, WheelEvent } from "react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Location } from "react-router-dom";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { PagePanel } from "../components/PagePanel";
import { appDb } from "../db/appDb";
import {
  enrichRemoteAssetFromDetail,
  getAssetReadUrl,
  type ReadUrlVariant,
} from "../features/assets/assetDetailsApi";
import { patchFavorite } from "../features/assets/assetActionsApi";
import { useRemoteAsset } from "../features/assets/useRemoteAsset";
import { readViewerContext } from "../features/gallery/viewerContext";
import { isPresignedUrlUsable } from "../lib/presignedUrl";
import { ApiError } from "../types/api";

const MIN_ZOOM = 1;
const MAX_ZOOM = 5;
const ZOOM_STEP = 0.2;

interface MetadataRow {
  label: string;
  value: string;
}

interface Point {
  x: number;
  y: number;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function getContainedMediaSize(
  frameWidth: number,
  frameHeight: number,
  mediaWidth: number | null,
  mediaHeight: number | null,
): { width: number; height: number } {
  if (frameWidth <= 0 || frameHeight <= 0) {
    return { width: 0, height: 0 };
  }

  if (!mediaWidth || !mediaHeight) {
    return { width: frameWidth, height: frameHeight };
  }

  const fitScale = Math.min(frameWidth / mediaWidth, frameHeight / mediaHeight);
  return {
    width: Math.max(1, mediaWidth * fitScale),
    height: Math.max(1, mediaHeight * fitScale),
  };
}

function formatBytes(value: number | null): string | null {
  if (value === null || Number.isNaN(value)) {
    return null;
  }

  const units = ["B", "KB", "MB", "GB", "TB"];
  let size = value;
  let index = 0;

  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index += 1;
  }

  return `${size.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

function formatDateTime(value: string | null): string | null {
  if (!value) {
    return null;
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }

  return date.toLocaleString();
}

function formatDuration(durationMs: number | null): string | null {
  if (durationMs === null || durationMs < 0) {
    return null;
  }

  const totalSeconds = Math.floor(durationMs / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (hours > 0) {
    return `${hours}h ${minutes}m ${seconds}s`;
  }

  if (minutes > 0) {
    return `${minutes}m ${seconds}s`;
  }

  return `${seconds}s`;
}

function formatResolution(width: number | null, height: number | null): string | null {
  if (!width || !height) {
    return null;
  }

  return `${width} x ${height}`;
}

function formatLocation(
  placeName: string | null,
  city: string | null,
  region: string | null,
  country: string | null,
  latitude: number | null,
  longitude: number | null,
): string | null {
  const nameParts = [placeName, city, region, country]
    .map((part) => part?.trim() ?? "")
    .filter((part) => part.length > 0);

  if (nameParts.length > 0) {
    return nameParts.join(", ");
  }

  if (latitude !== null && longitude !== null) {
    return `${latitude.toFixed(6)}, ${longitude.toFixed(6)}`;
  }

  return null;
}

function buildGoogleMapsUrl(
  latitude: number | null,
  longitude: number | null,
): string | null {
  if (
    latitude === null ||
    longitude === null ||
    !Number.isFinite(latitude) ||
    !Number.isFinite(longitude) ||
    latitude < -90 ||
    latitude > 90 ||
    longitude < -180 ||
    longitude > 180
  ) {
    return null;
  }

  const query = encodeURIComponent(`${latitude},${longitude}`);
  return `https://www.google.com/maps/search/?api=1&query=${query}`;
}

function likelyExpiredUrlError(error: unknown): boolean {
  if (!(error instanceof ApiError)) {
    return false;
  }

  return error.status === 403;
}

async function fetchReadUrlWithSingle403Retry(
  assetId: string,
  variant: ReadUrlVariant,
): Promise<string> {
  try {
    const first = await getAssetReadUrl(assetId, variant);
    return first.url;
  } catch (error) {
    if (!likelyExpiredUrlError(error)) {
      throw error;
    }

    const second = await getAssetReadUrl(assetId, variant);
    return second.url;
  }
}

interface AssetDetailsContentProps {
  assetId: string;
  isModal?: boolean;
}

function AssetDetailsContent({
  assetId,
  isModal = false,
}: AssetDetailsContentProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { asset, isLoading, errorMessage: replicaErrorMessage } = useRemoteAsset(assetId);

  const [previewUrlOverride, setPreviewUrlOverride] = useState<string | null>(null);
  const [originalViewerUrl, setOriginalViewerUrl] = useState<string | null>(null);
  const [isOriginalLoading, setIsOriginalLoading] = useState(false);
  const [isDownloadLoading, setIsDownloadLoading] = useState(false);
  const [detailErrorMessage, setDetailErrorMessage] = useState<string | null>(null);
  const [viewerErrorMessage, setViewerErrorMessage] = useState<string | null>(null);
  const [viewerImageNonce, setViewerImageNonce] = useState(0);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [infoPanelOpen, setInfoPanelOpen] = useState(true);
  const [isPanning, setIsPanning] = useState(false);

  const viewerFrameRef = useRef<HTMLDivElement | null>(null);
  const panStartRef = useRef<{
    pointerX: number;
    pointerY: number;
    startPanX: number;
    startPanY: number;
  } | null>(null);
  const panPointerIdRef = useRef<number | null>(null);
  const originalUrlRef = useRef<string | null>(null);
  const viewerRefreshInFlightRef = useRef(false);
  const viewerRetryAttemptsRef = useRef<Record<string, number>>({});
  const zoomRef = useRef(zoom);
  const panRef = useRef(pan);

  useEffect(() => {
    zoomRef.current = zoom;
  }, [zoom]);

  useEffect(() => {
    panRef.current = pan;
  }, [pan]);

  const viewerContext = readViewerContext();
  const currentIndex = viewerContext ? viewerContext.assetIds.indexOf(assetId) : -1;
  const previousAssetId =
    viewerContext && currentIndex > 0
      ? (viewerContext.assetIds[currentIndex - 1] ?? null)
      : null;
  const nextAssetId =
    viewerContext && currentIndex >= 0
      ? (viewerContext.assetIds[currentIndex + 1] ?? null)
      : null;
  const backPath = viewerContext?.backTo;
  const routeState = location.state as
    | { backgroundLocation?: Location }
    | null;
  const backgroundLocation = routeState?.backgroundLocation;

  const isVideo = asset?.mediaType === "video";

  const baseViewerUrl = useMemo(() => {
    if (previewUrlOverride) {
      return previewUrlOverride;
    }
    if (asset?.previewUrl) {
      return asset.previewUrl;
    }
    return asset?.thumbnailUrl ?? null;
  }, [asset?.previewUrl, asset?.thumbnailUrl, previewUrlOverride]);

  const activeViewerUrl = originalViewerUrl ?? baseViewerUrl;
  const googleMapsUrl = useMemo(() => {
    if (!asset || asset.mediaType !== "image") {
      return null;
    }

    return buildGoogleMapsUrl(asset.latitude, asset.longitude);
  }, [asset]);

  const metadataRows = useMemo<MetadataRow[]>(() => {
    if (!asset) {
      return [];
    }

    const cameraValue = [asset.cameraMake, asset.cameraModel]
      .map((value) => value?.trim() ?? "")
      .filter((value) => value.length > 0)
      .join(" ");

    const sizeValue = formatBytes(asset.fileSizeBytes);
    const resolutionValue = formatResolution(asset.width, asset.height);
    const durationValue = formatDuration(asset.durationMs);
    const takenAtValue = formatDateTime(asset.takenAt);
    const locationValue = formatLocation(
      asset.placeName,
      asset.city,
      asset.region,
      asset.country,
      asset.latitude,
      asset.longitude,
    );
    const uploadedAtValue = formatDateTime(asset.uploadedAt);

    const rows: Array<MetadataRow | null> = [
      asset.originalFilename
        ? { label: "File name", value: asset.originalFilename }
        : null,
      asset.mimeType ? { label: "MIME type", value: asset.mimeType } : null,
      sizeValue ? { label: "Size", value: sizeValue } : null,
      resolutionValue ? { label: "Resolution", value: resolutionValue } : null,
      durationValue ? { label: "Duration", value: durationValue } : null,
      takenAtValue ? { label: "Taken at", value: takenAtValue } : null,
      locationValue ? { label: "Location", value: locationValue } : null,
      cameraValue.length > 0 ? { label: "Camera", value: cameraValue } : null,
      uploadedAtValue ? { label: "Uploaded at", value: uploadedAtValue } : null,
      { label: "Asset ID", value: asset.id },
      asset.checksumSha256
        ? { label: "Checksum", value: asset.checksumSha256 }
        : null,
    ];

    return rows.filter((row): row is MetadataRow => row !== null);
  }, [asset]);

  const clampPan = useCallback(
    (nextPan: Point, atZoom: number): Point => {
      if (atZoom <= MIN_ZOOM) {
        return { x: 0, y: 0 };
      }

      const frame = viewerFrameRef.current;
      if (!frame) {
        return nextPan;
      }

      const frameWidth = frame.clientWidth;
      const frameHeight = frame.clientHeight;

      const mediaSize = getContainedMediaSize(
        frameWidth,
        frameHeight,
        asset?.width ?? null,
        asset?.height ?? null,
      );

      const scaledWidth = mediaSize.width * atZoom;
      const scaledHeight = mediaSize.height * atZoom;

      const maxPanX = Math.max(0, (scaledWidth - frameWidth) / 2);
      const maxPanY = Math.max(0, (scaledHeight - frameHeight) / 2);

      return {
        x: clamp(nextPan.x, -maxPanX, maxPanX),
        y: clamp(nextPan.y, -maxPanY, maxPanY),
      };
    },
    [asset?.height, asset?.width],
  );

  const resetZoomAndPan = useCallback(() => {
    setZoom(1);
    setPan({ x: 0, y: 0 });
    setIsPanning(false);
  }, []);

  const zoomAtPoint = useCallback(
    (nextZoomValue: number, point?: Point) => {
      const currentZoom = zoomRef.current;
      const currentPan = panRef.current;
      const nextZoom = clamp(nextZoomValue, MIN_ZOOM, MAX_ZOOM);

      if (nextZoom <= MIN_ZOOM) {
        setZoom(1);
        setPan({ x: 0, y: 0 });
        setIsPanning(false);
        return;
      }

      if (point && currentZoom > 0) {
        const scaleRatio = nextZoom / currentZoom;
        const anchoredPan = {
          x: point.x - (point.x - currentPan.x) * scaleRatio,
          y: point.y - (point.y - currentPan.y) * scaleRatio,
        };
        setPan(clampPan(anchoredPan, nextZoom));
      } else {
        setPan(clampPan(currentPan, nextZoom));
      }

      setZoom(nextZoom);
    },
    [clampPan],
  );

  useEffect(() => {
    let isCancelled = false;

    const run = async () => {
      try {
        await enrichRemoteAssetFromDetail(assetId);
      } catch (error) {
        if (isCancelled) {
          return;
        }

        setDetailErrorMessage(
          error instanceof Error ? error.message : "Unable to fetch latest metadata",
        );
      }
    };

    void run();

    return () => {
      isCancelled = true;
    };
  }, [assetId]);

  useEffect(() => {
    if (!asset) {
      return;
    }

    if (asset.previewUrl || asset.mediaType === "video") {
      return;
    }

    let isCancelled = false;

    const run = async () => {
      try {
        const nextPreview = await fetchReadUrlWithSingle403Retry(assetId, "preview");
        await appDb.remote_assets.update(assetId, { previewUrl: nextPreview });
      } catch (error) {
        if (isCancelled) {
          return;
        }

        setViewerErrorMessage(
          error instanceof Error ? error.message : "Unable to load preview URL",
        );
      }
    };

    void run();

    return () => {
      isCancelled = true;
    };
  }, [asset, assetId]);

  useEffect(() => {
    const frame = viewerFrameRef.current;
    if (!frame || typeof ResizeObserver === "undefined") {
      return;
    }

    const observer = new ResizeObserver(() => {
      if (zoomRef.current <= MIN_ZOOM) {
        return;
      }

      setPan((current) => clampPan(current, zoomRef.current));
    });

    observer.observe(frame);

    return () => {
      observer.disconnect();
    };
  }, [clampPan]);

  const updateZoom = useCallback(
    (nextZoom: number) => {
      zoomAtPoint(nextZoom);
    },
    [zoomAtPoint],
  );

  const handleViewerWheel = useCallback(
    (event: WheelEvent<HTMLDivElement>) => {
      event.preventDefault();

      const frame = viewerFrameRef.current;
      if (!frame) {
        return;
      }

      const rect = frame.getBoundingClientRect();
      const point = {
        x: event.clientX - rect.left - rect.width / 2,
        y: event.clientY - rect.top - rect.height / 2,
      };

      const zoomDelta = clamp(-event.deltaY * 0.0015, -0.45, 0.45);
      if (zoomDelta === 0) {
        return;
      }

      const nextZoom = zoomRef.current * (1 + zoomDelta);
      zoomAtPoint(nextZoom, point);
    },
    [zoomAtPoint],
  );

  const handleViewerDoubleClick = useCallback((event: MouseEvent<HTMLDivElement>) => {
    if (zoomRef.current > 1.4) {
      resetZoomAndPan();
      return;
    }

    const frame = viewerFrameRef.current;
    if (!frame) {
      updateZoom(2.2);
      return;
    }

    const rect = frame.getBoundingClientRect();
    const point = {
      x: event.clientX - rect.left - rect.width / 2,
      y: event.clientY - rect.top - rect.height / 2,
    };

    zoomAtPoint(2.2, point);
  }, [resetZoomAndPan, updateZoom, zoomAtPoint]);

  const handlePointerDown = useCallback(
    (event: PointerEvent<HTMLDivElement>) => {
      if (event.button !== 0 || zoomRef.current <= 1) {
        return;
      }

      event.preventDefault();

      panPointerIdRef.current = event.pointerId;
      panStartRef.current = {
        pointerX: event.clientX,
        pointerY: event.clientY,
        startPanX: panRef.current.x,
        startPanY: panRef.current.y,
      };
      setIsPanning(true);

      event.currentTarget.setPointerCapture(event.pointerId);
    },
    [],
  );

  const handlePointerMove = useCallback(
    (event: PointerEvent<HTMLDivElement>) => {
      if (zoomRef.current <= 1) {
        return;
      }

      if (panPointerIdRef.current !== event.pointerId || !panStartRef.current) {
        return;
      }

      event.preventDefault();

      const deltaX = event.clientX - panStartRef.current.pointerX;
      const deltaY = event.clientY - panStartRef.current.pointerY;

      const nextPan = clampPan(
        {
          x: panStartRef.current.startPanX + deltaX,
          y: panStartRef.current.startPanY + deltaY,
        },
        zoomRef.current,
      );

      setPan(nextPan);
    },
    [clampPan],
  );

  const releasePointer = useCallback((event: PointerEvent<HTMLDivElement>) => {
    if (panPointerIdRef.current === event.pointerId) {
      panPointerIdRef.current = null;
      panStartRef.current = null;
      setIsPanning(false);
      if (event.currentTarget.hasPointerCapture(event.pointerId)) {
        event.currentTarget.releasePointerCapture(event.pointerId);
      }
    }
  }, []);

  const handleViewerImageError = useCallback(async () => {
    if (!asset || !activeViewerUrl || viewerRefreshInFlightRef.current) {
      return;
    }

    const variant: ReadUrlVariant = originalViewerUrl ? "original" : "preview";
    const retryKey = `${variant}:${activeViewerUrl}`;
    const retryAttempts = viewerRetryAttemptsRef.current[retryKey] ?? 0;

    if (isPresignedUrlUsable(activeViewerUrl) === true) {
      if (retryAttempts < 1) {
        viewerRetryAttemptsRef.current[retryKey] = retryAttempts + 1;
        setViewerImageNonce((current) => current + 1);
        return;
      }

      setViewerErrorMessage("Unable to load image from storage.");
      return;
    }

    viewerRefreshInFlightRef.current = true;

    try {
      const nextUrl = await fetchReadUrlWithSingle403Retry(assetId, variant);
      if (variant === "original") {
        originalUrlRef.current = nextUrl;
        setOriginalViewerUrl(nextUrl);
      } else {
        await appDb.remote_assets.update(assetId, { previewUrl: nextUrl });
        setPreviewUrlOverride(nextUrl);
      }
      setViewerErrorMessage(null);
    } catch (error) {
      setViewerErrorMessage(
        error instanceof Error
          ? error.message
          : "Image URL refresh failed",
      );
    } finally {
      viewerRefreshInFlightRef.current = false;
    }
  }, [activeViewerUrl, asset, assetId, originalViewerUrl]);

  const handleLoadOriginal = useCallback(async () => {
    setIsOriginalLoading(true);

    try {
      let url = originalUrlRef.current;
      if (!url || isPresignedUrlUsable(url) !== true) {
        url = await fetchReadUrlWithSingle403Retry(assetId, "original");
        originalUrlRef.current = url;
      }
      setOriginalViewerUrl(url);
      setViewerErrorMessage(null);
      resetZoomAndPan();
    } catch (error) {
      setViewerErrorMessage(
        error instanceof Error ? error.message : "Unable to load original",
      );
    } finally {
      setIsOriginalLoading(false);
    }
  }, [assetId, resetZoomAndPan]);

  const originalFilename = asset?.originalFilename;

  const handleDownloadOriginal = useCallback(async () => {
    setIsDownloadLoading(true);

    try {
      let url = originalUrlRef.current;
      if (!url || isPresignedUrlUsable(url) !== true) {
        url = await fetchReadUrlWithSingle403Retry(assetId, "original");
        originalUrlRef.current = url;
      }
      const link = document.createElement("a");
      link.href = url;
      link.download = originalFilename ?? `asset-${assetId}`;
      link.rel = "noopener";
      document.body.append(link);
      link.click();
      link.remove();
    } catch (error) {
      setViewerErrorMessage(
        error instanceof Error
          ? error.message
          : "Unable to download original",
      );
    } finally {
      setIsDownloadLoading(false);
    }
  }, [assetId, originalFilename]);

  const handleNavigatePrevious = useCallback(() => {
    if (!previousAssetId) {
      return;
    }
    navigate(`/assets/${previousAssetId}`, {
      replace: isModal,
      state: backgroundLocation ? { backgroundLocation } : undefined,
    });
  }, [backgroundLocation, isModal, navigate, previousAssetId]);

  const handleNavigateNext = useCallback(() => {
    if (!nextAssetId) {
      return;
    }
    navigate(`/assets/${nextAssetId}`, {
      replace: isModal,
      state: backgroundLocation ? { backgroundLocation } : undefined,
    });
  }, [backgroundLocation, isModal, navigate, nextAssetId]);

  const handleCloseViewer = useCallback(() => {
    if (isModal && backgroundLocation) {
      navigate(-1);
      return;
    }

    if (backPath) {
      navigate(backPath);
      return;
    }
    navigate("/gallery");
  }, [backPath, backgroundLocation, isModal, navigate]);

  const handleToggleFavorite = useCallback(async () => {
    if (!asset) {
      return;
    }

    try {
      await patchFavorite(asset.id, !asset.isFavorite);
      await appDb.remote_assets.update(asset.id, {
        isFavorite: !asset.isFavorite,
      });
    } catch (error) {
      setViewerErrorMessage(
        error instanceof Error ? error.message : "Unable to update favorite",
      );
    }
  }, [asset]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.defaultPrevented) {
        return;
      }

      const key = event.key;

      if (key === "Escape") {
        event.preventDefault();
        handleCloseViewer();
        return;
      }

      if (key === "ArrowLeft") {
        event.preventDefault();
        handleNavigatePrevious();
        return;
      }

      if (key === "ArrowRight") {
        event.preventDefault();
        handleNavigateNext();
        return;
      }

      if (key.toLowerCase() === "f") {
        event.preventDefault();
        void handleToggleFavorite();
        return;
      }

      if (key.toLowerCase() === "i") {
        event.preventDefault();
        setInfoPanelOpen((current) => !current);
        return;
      }

      if (key === "+" || key === "=") {
        event.preventDefault();
        updateZoom(zoomRef.current + ZOOM_STEP);
        return;
      }

      if (key === "-") {
        event.preventDefault();
        updateZoom(zoomRef.current - ZOOM_STEP);
        return;
      }

      if (key === "0") {
        event.preventDefault();
        resetZoomAndPan();
      }
    };

    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [
    handleCloseViewer,
    handleNavigateNext,
    handleNavigatePrevious,
    handleToggleFavorite,
    resetZoomAndPan,
    updateZoom,
  ]);

  if (isLoading) {
    return (
      <div className="detail-state">
        <h2>Loading asset</h2>
        <p>Reading metadata from local IndexedDB replica.</p>
      </div>
    );
  }

  if (!asset) {
    return (
      <div className="detail-state error" role="alert">
        <h2>Asset not found in cache</h2>
        <p>
          This asset is not available in the local replica yet. Open Gallery and
          run refresh.
        </p>
        {replicaErrorMessage ? <p>{replicaErrorMessage}</p> : null}
      </div>
    );
  }

  const mediaTransformStyle = {
    transformOrigin: "center center",
    transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
  } as CSSProperties;

  const viewerImageClassName = isPanning
    ? "detail-viewer-image is-panning"
    : "detail-viewer-image";
  const canZoomIn = zoom < MAX_ZOOM - 0.01;
  const canZoomOut = zoom > MIN_ZOOM + 0.01;

  return (
    <div className="detail-layout">
      <section className="detail-viewer-panel">
        <div className="detail-viewer-toolbar">
          <p className="detail-viewer-caption">
            {asset.originalFilename ?? "Untitled asset"}
          </p>
          <div className="detail-viewer-actions">
            <button
              type="button"
              className="secondary-btn"
              onClick={handleCloseViewer}
              autoFocus={isModal}
            >
              {isModal ? "Close" : "Back"}
            </button>
            <button
              type="button"
              className="secondary-btn"
              onClick={handleNavigatePrevious}
              disabled={!previousAssetId}
            >
              Previous
            </button>
            <button
              type="button"
              className="secondary-btn"
              onClick={handleNavigateNext}
              disabled={!nextAssetId}
            >
              Next
            </button>
            <button
              type="button"
              className="secondary-btn"
              onClick={() => {
                void handleToggleFavorite();
              }}
            >
              {asset.isFavorite ? "Unfavorite" : "Favorite"}
            </button>
            <button
              type="button"
              className="secondary-btn"
              onClick={() => setInfoPanelOpen((current) => !current)}
            >
              {infoPanelOpen ? "Hide info" : "Show info"}
            </button>
            <button
              type="button"
              className="secondary-btn"
              onClick={handleLoadOriginal}
              disabled={isOriginalLoading}
            >
              {isOriginalLoading ? "Loading original..." : "Load original"}
            </button>
            <button
              type="button"
              className="secondary-btn"
              onClick={handleDownloadOriginal}
              disabled={isDownloadLoading}
            >
              {isDownloadLoading ? "Preparing..." : "Download original"}
            </button>
          </div>
        </div>

        <div
          ref={viewerFrameRef}
          className={zoom > MIN_ZOOM ? "detail-viewer-frame zoomed" : "detail-viewer-frame"}
          style={{ cursor: isPanning ? "grabbing" : zoom > MIN_ZOOM ? "grab" : "zoom-in" }}
          onWheel={handleViewerWheel}
          onDoubleClick={handleViewerDoubleClick}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={releasePointer}
          onPointerCancel={releasePointer}
        >
          {isVideo ? (
            <>
              {activeViewerUrl ? (
                <img
                  key={`${activeViewerUrl}:${viewerImageNonce}`}
                  src={activeViewerUrl}
                  className={viewerImageClassName}
                  style={mediaTransformStyle}
                  draggable={false}
                  alt={asset.originalFilename ?? "Video preview"}
                  onError={() => {
                    void handleViewerImageError();
                  }}
                />
              ) : (
                <div className="detail-viewer-fallback">Video preview unavailable</div>
              )}

              <div className="detail-video-overlay">
                Video playback not implemented yet.
              </div>
            </>
          ) : activeViewerUrl ? (
            <img
              key={`${activeViewerUrl}:${viewerImageNonce}`}
              src={activeViewerUrl}
              className={viewerImageClassName}
              style={mediaTransformStyle}
              draggable={false}
              alt={asset.originalFilename ?? "Asset preview"}
              onError={() => {
                void handleViewerImageError();
              }}
            />
          ) : (
            <div className="detail-viewer-fallback">Preview unavailable</div>
          )}
        </div>

        <div className="detail-zoom-meta">
          <div>
            <p>
              Zoom: <strong>{zoom.toFixed(2)}x</strong>
            </p>
            <p className="detail-zoom-hint">Wheel to zoom at cursor, drag to pan.</p>
          </div>
          <div className="detail-zoom-controls">
            <button
              type="button"
              className="secondary-btn"
              onClick={() => updateZoom(zoom - ZOOM_STEP)}
              disabled={!canZoomOut}
            >
              -
            </button>
            <button
              type="button"
              className="secondary-btn"
              onClick={() => updateZoom(zoom + ZOOM_STEP)}
              disabled={!canZoomIn}
            >
              +
            </button>
            <button
              type="button"
              className="secondary-btn"
              onClick={resetZoomAndPan}
              disabled={zoom <= 1 && pan.x === 0 && pan.y === 0}
            >
              Reset view
            </button>
          </div>
        </div>

        {detailErrorMessage ? (
          <div className="info-banner" role="status">
            Background metadata refresh failed: {detailErrorMessage}
          </div>
        ) : null}

        {viewerErrorMessage ? (
          <div className="error-banner" role="alert">
            {viewerErrorMessage}
          </div>
        ) : null}
      </section>

      {infoPanelOpen ? (
        <aside className="detail-metadata-panel">
          <h2>Metadata</h2>
          <dl>
            {metadataRows.map((row) => (
              <div className="detail-meta-row" key={row.label}>
                <dt>{row.label}</dt>
                <dd>
                  <span>{row.value}</span>
                  {row.label === "Location" && googleMapsUrl ? (
                    <a
                      className="detail-map-link"
                      href={googleMapsUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      Open in Google Maps
                    </a>
                  ) : null}
                </dd>
              </div>
            ))}
          </dl>
        </aside>
      ) : null}
    </div>
  );
}

export function AssetDetailsPage() {
  const { id } = useParams<{ id: string }>();

  if (!id) {
    return (
      <PagePanel title="Asset Details">
        <div className="detail-state error" role="alert">
          <h2>Asset not found</h2>
          <p>Missing asset identifier in route.</p>
        </div>
      </PagePanel>
    );
  }

  return (
    <PagePanel title="Asset Details">
      <AssetDetailsContent key={id} assetId={id} />
    </PagePanel>
  );
}

export function AssetDetailsModal() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, []);

  const handleBackdropMouseDown = (event: MouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget) {
      navigate(-1);
    }
  };

  return (
    <div
      className="asset-viewer-modal"
      role="dialog"
      aria-modal="true"
      aria-label="Asset viewer"
      onMouseDown={handleBackdropMouseDown}
    >
      <div className="asset-viewer-modal-content">
        {id ? (
          <AssetDetailsContent key={id} assetId={id} isModal />
        ) : (
          <div className="detail-state error" role="alert">
            <h2>Asset not found</h2>
            <p>Missing asset identifier in route.</p>
          </div>
        )}
      </div>
    </div>
  );
}
