import type { CSSProperties, PointerEvent, WheelEvent } from "react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { PagePanel } from "../components/PagePanel";
import { appDb } from "../db/appDb";
import {
  enrichRemoteAssetFromDetail,
  getAssetReadUrl,
  type ReadUrlVariant,
} from "../features/assets/assetDetailsApi";
import { useRemoteAsset } from "../features/assets/useRemoteAsset";
import { ApiError } from "../types/api";

const MIN_ZOOM = 1;
const MAX_ZOOM = 5;
const ZOOM_STEP = 0.2;

interface MetadataRow {
  label: string;
  value: string;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
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

function AssetDetailsContent({ assetId }: { assetId: string }) {
  const { asset, isLoading, errorMessage: replicaErrorMessage } = useRemoteAsset(assetId);

  const [previewUrlOverride, setPreviewUrlOverride] = useState<string | null>(null);
  const [originalViewerUrl, setOriginalViewerUrl] = useState<string | null>(null);
  const [isOriginalLoading, setIsOriginalLoading] = useState(false);
  const [isDownloadLoading, setIsDownloadLoading] = useState(false);
  const [detailErrorMessage, setDetailErrorMessage] = useState<string | null>(null);
  const [viewerErrorMessage, setViewerErrorMessage] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });

  const panStartRef = useRef<{ x: number; y: number } | null>(null);
  const panPointerIdRef = useRef<number | null>(null);

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

  const resetZoomAndPan = useCallback(() => {
    setZoom(1);
    setPan({ x: 0, y: 0 });
  }, []);

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

  const updateZoom = useCallback((nextZoom: number) => {
    const clamped = clamp(nextZoom, MIN_ZOOM, MAX_ZOOM);
    setZoom(clamped);
    if (clamped <= 1) {
      setPan({ x: 0, y: 0 });
    }
  }, []);

  const handleViewerWheel = useCallback(
    (event: WheelEvent<HTMLDivElement>) => {
      event.preventDefault();

      const direction = event.deltaY < 0 ? 1 : -1;
      const nextZoom = zoom + direction * ZOOM_STEP;
      updateZoom(nextZoom);
    },
    [updateZoom, zoom],
  );

  const handleViewerDoubleClick = useCallback(() => {
    if (zoom > 1) {
      resetZoomAndPan();
      return;
    }

    updateZoom(2);
  }, [resetZoomAndPan, updateZoom, zoom]);

  const handlePointerDown = useCallback(
    (event: PointerEvent<HTMLDivElement>) => {
      if (zoom <= 1) {
        return;
      }

      panPointerIdRef.current = event.pointerId;
      panStartRef.current = {
        x: event.clientX - pan.x,
        y: event.clientY - pan.y,
      };

      event.currentTarget.setPointerCapture(event.pointerId);
    },
    [pan.x, pan.y, zoom],
  );

  const handlePointerMove = useCallback(
    (event: PointerEvent<HTMLDivElement>) => {
      if (zoom <= 1) {
        return;
      }

      if (panPointerIdRef.current !== event.pointerId || !panStartRef.current) {
        return;
      }

      setPan({
        x: event.clientX - panStartRef.current.x,
        y: event.clientY - panStartRef.current.y,
      });
    },
    [zoom],
  );

  const releasePointer = useCallback((event: PointerEvent<HTMLDivElement>) => {
    if (panPointerIdRef.current === event.pointerId) {
      panPointerIdRef.current = null;
      panStartRef.current = null;
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  }, []);

  const handleViewerImageError = useCallback(async () => {
    if (!asset) {
      return;
    }

    if (originalViewerUrl) {
      return;
    }

    try {
      const nextPreview = await fetchReadUrlWithSingle403Retry(assetId, "preview");
      await appDb.remote_assets.update(assetId, { previewUrl: nextPreview });
      setPreviewUrlOverride(nextPreview);
      setViewerErrorMessage(null);
    } catch (error) {
      setViewerErrorMessage(
        error instanceof Error
          ? error.message
          : "Preview URL refresh failed",
      );
    }
  }, [asset, assetId, originalViewerUrl]);

  const handleLoadOriginal = useCallback(async () => {
    setIsOriginalLoading(true);

    try {
      const url = await fetchReadUrlWithSingle403Retry(assetId, "original");
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
      const url = await fetchReadUrlWithSingle403Retry(assetId, "original");
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
    transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
  } as CSSProperties;

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
          className="detail-viewer-frame"
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
                  src={activeViewerUrl}
                  className="detail-viewer-image"
                  style={mediaTransformStyle}
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
              src={activeViewerUrl}
              className="detail-viewer-image"
              style={mediaTransformStyle}
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
          <p>
            Zoom: <strong>{zoom.toFixed(1)}x</strong>
          </p>
          <button
            type="button"
            className="secondary-btn"
            onClick={resetZoomAndPan}
            disabled={zoom <= 1 && pan.x === 0 && pan.y === 0}
          >
            Reset view
          </button>
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

      <aside className="detail-metadata-panel">
        <h2>Metadata</h2>
        <dl>
          {metadataRows.map((row) => (
            <div className="detail-meta-row" key={row.label}>
              <dt>{row.label}</dt>
              <dd>{row.value}</dd>
            </div>
          ))}
        </dl>
      </aside>
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
