import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { PagePanel } from "../components/PagePanel";
import {
  addAssetToAlbum,
  getAlbum,
  listAlbumAssets,
  removeAssetFromAlbum,
  type Album,
} from "../features/albums/albumsApi";
import { useRemoteAssetsReplica } from "../features/assets/useRemoteAssetsReplica";
import type { SearchAssetItem } from "../features/search/searchApi";

function formatDateTime(value: string): string {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "Unknown";
  }
  return parsed.toLocaleString();
}

function formatTakenAt(value: string | null): string {
  if (!value) {
    return "Unknown date";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "Unknown date";
  }

  return parsed.toLocaleDateString();
}

function getDisplayUrl(asset: SearchAssetItem): string | null {
  if (asset.thumbnailUrl) {
    return asset.thumbnailUrl;
  }
  if (asset.previewUrl) {
    return asset.previewUrl;
  }
  return null;
}

export function AlbumDetailsPage() {
  const { id } = useParams();
  const albumId = id ?? "";
  const { assets: replicaAssets } = useRemoteAssetsReplica();

  const [album, setAlbum] = useState<Album | null>(null);
  const [albumAssets, setAlbumAssets] = useState<SearchAssetItem[]>([]);
  const [selectedReplicaAssetIds, setSelectedReplicaAssetIds] = useState<string[]>([]);
  const [pickerQuery, setPickerQuery] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isMutating, setIsMutating] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [infoMessage, setInfoMessage] = useState<string | null>(null);

  const albumAssetIdSet = useMemo(
    () => new Set(albumAssets.map((asset) => asset.id)),
    [albumAssets],
  );

  const addableReplicaAssets = useMemo(() => {
    const query = pickerQuery.trim().toLowerCase();

    return replicaAssets
      .filter((asset) => !asset.isTrashed && !albumAssetIdSet.has(asset.id))
      .filter((asset) => {
        if (!query) {
          return true;
        }

        const haystack = [
          asset.originalFilename ?? "",
          asset.mimeType,
          asset.city ?? "",
          asset.placeName ?? "",
          asset.id,
        ]
          .join(" ")
          .toLowerCase();

        return haystack.includes(query);
      })
      .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt));
  }, [albumAssetIdSet, pickerQuery, replicaAssets]);

  useEffect(() => {
    let cancelled = false;

    const run = async () => {
      if (!albumId) {
        if (!cancelled) {
          setErrorMessage("Album ID is missing from route.");
          setIsLoading(false);
        }
        return;
      }

      try {
        const [albumResponse, albumAssetsResponse] = await Promise.all([
          getAlbum(albumId),
          listAlbumAssets(albumId),
        ]);

        if (cancelled) {
          return;
        }

        setAlbum(albumResponse);
        setAlbumAssets(albumAssetsResponse);
        setSelectedReplicaAssetIds((current) =>
          current.filter((assetId) => !albumAssetsResponse.some((item) => item.id === assetId)),
        );
        setErrorMessage(null);
      } catch (error) {
        if (cancelled) {
          return;
        }

        setErrorMessage(error instanceof Error ? error.message : "Failed to load album details");
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };

    void run();

    return () => {
      cancelled = true;
    };
  }, [albumId]);

  const reload = async (): Promise<void> => {
    if (!albumId) {
      setErrorMessage("Album ID is missing from route.");
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);

    try {
      const [albumResponse, albumAssetsResponse] = await Promise.all([
        getAlbum(albumId),
        listAlbumAssets(albumId),
      ]);

      setAlbum(albumResponse);
      setAlbumAssets(albumAssetsResponse);
      setSelectedReplicaAssetIds((current) =>
        current.filter((assetId) => !albumAssetsResponse.some((item) => item.id === assetId)),
      );
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Failed to load album details");
    } finally {
      setIsLoading(false);
    }
  };

  const toggleReplicaSelection = (assetId: string) => {
    setInfoMessage(null);
    setSelectedReplicaAssetIds((current) => {
      if (current.includes(assetId)) {
        return current.filter((idValue) => idValue !== assetId);
      }
      return [...current, assetId];
    });
  };

  const handleAddSelected = async (): Promise<void> => {
    if (!albumId || selectedReplicaAssetIds.length === 0) {
      return;
    }

    setIsMutating(true);
    setErrorMessage(null);
    setInfoMessage(null);

    let succeeded = 0;
    let failed = 0;

    try {
      for (const assetId of selectedReplicaAssetIds) {
        try {
          await addAssetToAlbum(albumId, assetId, null);
          succeeded += 1;
        } catch {
          failed += 1;
        }
      }

      await reload();
      setSelectedReplicaAssetIds([]);
      setInfoMessage(`Add complete: ${succeeded} succeeded, ${failed} failed.`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Failed to add selected assets");
    } finally {
      setIsMutating(false);
    }
  };

  const handleRemoveFromAlbum = async (assetId: string): Promise<void> => {
    if (!albumId) {
      return;
    }

    const confirmed = window.confirm(
      "Remove this asset from the album? This only removes album membership and never deletes cloud assets.",
    );

    if (!confirmed) {
      return;
    }

    setIsMutating(true);
    setErrorMessage(null);
    setInfoMessage(null);

    try {
      await removeAssetFromAlbum(albumId, assetId);
      setAlbumAssets((current) => current.filter((item) => item.id !== assetId));
      setInfoMessage("Asset removed from album.");
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Failed to remove asset from album");
    } finally {
      setIsMutating(false);
    }
  };

  return (
    <PagePanel title="Album Details">
      <div className="album-detail-topbar">
        <Link to="/albums" className="secondary-btn albums-link-btn">
          Back to albums
        </Link>
        <button
          type="button"
          className="secondary-btn"
          onClick={() => {
            void reload();
          }}
          disabled={isLoading || isMutating}
        >
          Refresh
        </button>
      </div>

      {errorMessage ? (
        <div className="error-banner" role="alert">
          {errorMessage}
        </div>
      ) : null}

      {infoMessage ? (
        <div className="info-banner" role="status">
          {infoMessage}
        </div>
      ) : null}

      {isLoading ? (
        <div className="albums-state">
          <h2>Loading album</h2>
          <p>Fetching album metadata and members.</p>
        </div>
      ) : null}

      {!isLoading && album ? (
        <section className="album-meta-panel">
          <h2>{album.name}</h2>
          <p>{album.description ?? "No description"}</p>
          <p className="albums-item-meta">Created: {formatDateTime(album.createdAt)}</p>
          <p className="albums-item-meta">Updated: {formatDateTime(album.updatedAt)}</p>
          {album.isArchived ? <span className="gallery-flag">Archived</span> : null}
        </section>
      ) : null}

      {!isLoading && album ? (
        <section className="album-members-panel">
          <div className="album-members-head">
            <h2>Album assets</h2>
            <p>Removing from album never deletes cloud assets.</p>
          </div>
          {albumAssets.length === 0 ? (
            <div className="albums-state">
              <h2>No members</h2>
              <p>Add assets from the replica picker below.</p>
            </div>
          ) : (
            <div className="search-grid">
              {albumAssets.map((asset) => {
                const source = getDisplayUrl(asset);

                return (
                  <article key={asset.id} className="search-card">
                    <Link to={`/assets/${asset.id}`} className="search-thumb-wrap">
                      {source ? (
                        <img
                          className="search-thumb"
                          src={source}
                          alt={asset.id}
                          loading="lazy"
                        />
                      ) : (
                        <div className="search-thumb-placeholder">Preview unavailable</div>
                      )}
                    </Link>
                    <div className="search-card-meta">
                      <p className="search-card-title">{asset.mimeType}</p>
                      <p className="search-card-date">{formatTakenAt(asset.takenAt)}</p>
                      <div className="search-card-flags">
                        {asset.mediaType === "video" ? (
                          <span className="gallery-flag">Video</span>
                        ) : null}
                        {asset.isFavorite ? (
                          <span className="gallery-flag">Favorite</span>
                        ) : null}
                      </div>
                      <div className="albums-actions-row">
                        <button
                          type="button"
                          className="secondary-btn danger"
                          disabled={isMutating}
                          onClick={() => {
                            void handleRemoveFromAlbum(asset.id);
                          }}
                        >
                          Remove from album
                        </button>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </section>
      ) : null}

      {!isLoading && album ? (
        <section className="album-picker-panel">
          <div className="album-members-head">
            <h2>Add assets from replica</h2>
            <p>Select cached assets to add with POST /albums/{albumId}/assets.</p>
          </div>

          <label className="search-query-field" htmlFor="album-picker-filter">
            Filter available assets
            <input
              id="album-picker-filter"
              value={pickerQuery}
              onChange={(event) => setPickerQuery(event.target.value)}
              placeholder="Filename, city, place, mime, or asset id"
            />
          </label>

          {addableReplicaAssets.length === 0 ? (
            <div className="albums-state">
              <h2>No addable assets</h2>
              <p>Sync gallery metadata first or adjust the filter.</p>
            </div>
          ) : (
            <>
              <div className="album-picker-list">
                {addableReplicaAssets.map((asset) => (
                  <label key={asset.id} className="album-picker-item">
                    <input
                      type="checkbox"
                      checked={selectedReplicaAssetIds.includes(asset.id)}
                      onChange={() => toggleReplicaSelection(asset.id)}
                    />
                    <span>
                      <strong>{asset.originalFilename ?? asset.id}</strong>
                      <span className="albums-item-meta">{asset.mimeType}</span>
                    </span>
                  </label>
                ))}
              </div>
              <div className="albums-actions-row">
                <button
                  type="button"
                  className="secondary-btn"
                  disabled={selectedReplicaAssetIds.length === 0 || isMutating}
                  onClick={() => {
                    void handleAddSelected();
                  }}
                >
                  Add selected ({selectedReplicaAssetIds.length})
                </button>
                <button
                  type="button"
                  className="secondary-btn"
                  disabled={selectedReplicaAssetIds.length === 0 || isMutating}
                  onClick={() => setSelectedReplicaAssetIds([])}
                >
                  Clear selection
                </button>
              </div>
            </>
          )}
        </section>
      ) : null}
    </PagePanel>
  );
}
