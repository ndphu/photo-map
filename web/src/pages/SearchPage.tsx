import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { AssetGridCard } from "../components/AssetGridCard";
import { PagePanel } from "../components/PagePanel";
import { saveViewerContext } from "../features/gallery/viewerContext";
import {
  getSearchAssetReadUrl,
  searchAssets,
  type SearchAssetItem,
  type SearchReadUrlVariant,
} from "../features/search/searchApi";
import { isPresignedUrlUsable } from "../lib/presignedUrl";

type SearchFallbackVariant = SearchReadUrlVariant | undefined;

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

function getSearchDisplaySource(
  asset: SearchAssetItem,
  fallbackVariant: SearchFallbackVariant,
): { variant: SearchReadUrlVariant; url: string } | null {
  if (fallbackVariant === "preview" && asset.previewUrl) {
    return { variant: "preview", url: asset.previewUrl };
  }
  if (asset.thumbnailUrl) {
    return { variant: "thumbnail", url: asset.thumbnailUrl };
  }
  if (asset.previewUrl) {
    return { variant: "preview", url: asset.previewUrl };
  }
  return null;
}

async function shouldRefreshSource(url: string): Promise<boolean> {
  const isUsable = isPresignedUrlUsable(url);
  if (isUsable !== null) {
    return !isUsable;
  }

  try {
    const response = await fetch(url, {
      method: "HEAD",
      mode: "cors",
    });
    return response.status === 403;
  } catch {
    return true;
  }
}

function updateAssetUrl(
  items: SearchAssetItem[],
  assetId: string,
  variant: SearchReadUrlVariant,
  nextUrl: string,
): SearchAssetItem[] {
  return items.map((item) => {
    if (item.id !== assetId) {
      return item;
    }

    if (variant === "thumbnail") {
      return {
        ...item,
        thumbnailUrl: nextUrl,
      };
    }

    return {
      ...item,
      previewUrl: nextUrl,
    };
  });
}

export function SearchPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const routeQuery = useMemo(
    () => new URLSearchParams(location.search).get("q") ?? "",
    [location.search],
  );
  const activeQuery = useMemo(() => routeQuery.trim(), [routeQuery]);
  const [results, setResults] = useState<SearchAssetItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [isSearching, setIsSearching] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [fallbackVariantByAssetId, setFallbackVariantByAssetId] = useState<
    Record<string, SearchFallbackVariant>
  >({});
  const [imageNonceByAssetId, setImageNonceByAssetId] = useState<
    Record<string, number>
  >({});

  const debounceTimerRef = useRef<number | null>(null);
  const refreshAttemptsRef = useRef<Record<string, number>>({});
  const inFlightRefreshesRef = useRef<Record<string, boolean>>({});

  const queryIsBlank = activeQuery.length === 0;
  const visibleResults = useMemo(
    () => (activeQuery ? results : []),
    [activeQuery, results],
  );
  const visibleAssetIds = useMemo(
    () => visibleResults.map((asset) => asset.id),
    [visibleResults],
  );
  const backToPath = useMemo(
    () => `${location.pathname}${location.search}`,
    [location.pathname, location.search],
  );
  const visibleErrorMessage = activeQuery ? errorMessage : null;

  useEffect(() => {
    return () => {
      if (debounceTimerRef.current !== null) {
        window.clearTimeout(debounceTimerRef.current);
      }
    };
  }, []);

  const handleQueryChange = (value: string) => {
    setErrorMessage(null);

    if (debounceTimerRef.current !== null) {
      window.clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = null;
    }

    const trimmed = value.trim();
    if (!trimmed) {
      navigate("/search", { replace: true });
      setResults([]);
      setNextCursor(null);
      setHasSearched(false);
      setFallbackVariantByAssetId({});
      setImageNonceByAssetId({});
      refreshAttemptsRef.current = {};
      inFlightRefreshesRef.current = {};
      return;
    }

    debounceTimerRef.current = window.setTimeout(() => {
      navigate(`/search?q=${encodeURIComponent(trimmed)}`, { replace: true });
    }, 350);
  };

  useEffect(() => {
    if (!activeQuery) {
      return;
    }

    const controller = new AbortController();
    let cancelled = false;

    const run = async () => {
      setIsSearching(true);
      setErrorMessage(null);

      try {
        const response = await searchAssets({
          q: activeQuery,
          limit: 50,
          signal: controller.signal,
        });

        if (cancelled) {
          return;
        }

        setResults(response.items);
        setNextCursor(response.nextCursor);
        setHasSearched(true);
        setFallbackVariantByAssetId({});
        setImageNonceByAssetId({});
        refreshAttemptsRef.current = {};
        inFlightRefreshesRef.current = {};
      } catch (error: unknown) {
        if (cancelled || controller.signal.aborted) {
          return;
        }

        setResults([]);
        setNextCursor(null);
        setHasSearched(true);
        setErrorMessage(
          error instanceof Error ? error.message : "Search request failed",
        );
      } finally {
        if (!cancelled && !controller.signal.aborted) {
          setIsSearching(false);
        }
      }
    };

    void run();

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [activeQuery]);

  const handleLoadMore = async (): Promise<void> => {
    if (!activeQuery || !nextCursor || isLoadingMore) {
      return;
    }

    setIsLoadingMore(true);
    setErrorMessage(null);

    try {
      const response = await searchAssets({
        q: activeQuery,
        cursor: nextCursor,
        limit: 50,
      });

      setResults((current) => [...current, ...response.items]);
      setNextCursor(response.nextCursor);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Load more failed");
    } finally {
      setIsLoadingMore(false);
    }
  };

  const refreshSourceVariant = useCallback(
    async (assetId: string, variant: SearchReadUrlVariant): Promise<void> => {
      const nextUrl = await getSearchAssetReadUrl(assetId, variant);

      setResults((current) => updateAssetUrl(current, assetId, variant, nextUrl));
      setImageNonceByAssetId((current) => ({
        ...current,
        [assetId]: (current[assetId] ?? 0) + 1,
      }));
    },
    [],
  );

  const handleResultImageError = useCallback(
    async (asset: SearchAssetItem): Promise<void> => {
      const source = getSearchDisplaySource(asset, fallbackVariantByAssetId[asset.id]);
      if (!source) {
        return;
      }

      const refreshKey = `${asset.id}:${source.variant}`;
      if (inFlightRefreshesRef.current[refreshKey]) {
        return;
      }

      const attempts = refreshAttemptsRef.current[refreshKey] ?? 0;
      if (attempts >= 1) {
        if (source.variant === "thumbnail" && asset.previewUrl) {
          const previewKey = `${asset.id}:preview`;
          const previewAttempts = refreshAttemptsRef.current[previewKey] ?? 0;

          if (previewAttempts < 1 && !inFlightRefreshesRef.current[previewKey]) {
            inFlightRefreshesRef.current[previewKey] = true;
            refreshAttemptsRef.current[previewKey] = previewAttempts + 1;

            try {
              const shouldRefreshPreview = await shouldRefreshSource(asset.previewUrl);
              setFallbackVariantByAssetId((current) => ({
                ...current,
                [asset.id]: "preview",
              }));

              if (shouldRefreshPreview) {
                await refreshSourceVariant(asset.id, "preview");
              } else {
                setImageNonceByAssetId((current) => ({
                  ...current,
                  [asset.id]: (current[asset.id] ?? 0) + 1,
                }));
              }
            } finally {
              inFlightRefreshesRef.current[previewKey] = false;
            }
          }
        }

        return;
      }

      inFlightRefreshesRef.current[refreshKey] = true;
      refreshAttemptsRef.current[refreshKey] = attempts + 1;

      try {
        const shouldRefresh = await shouldRefreshSource(source.url);
        if (shouldRefresh) {
          await refreshSourceVariant(asset.id, source.variant);
        } else {
          setImageNonceByAssetId((current) => ({
            ...current,
            [asset.id]: (current[asset.id] ?? 0) + 1,
          }));
        }
      } catch {
        if (source.variant === "thumbnail" && asset.previewUrl) {
          setFallbackVariantByAssetId((current) => ({
            ...current,
            [asset.id]: "preview",
          }));
        }
      } finally {
        inFlightRefreshesRef.current[refreshKey] = false;
      }
    },
    [fallbackVariantByAssetId, refreshSourceVariant],
  );

  const handleOpenAsset = useCallback(
    () => {
      saveViewerContext({
        source: "search",
        backTo: backToPath,
        assetIds: visibleAssetIds,
        updatedAt: new Date().toISOString(),
      });
    },
    [backToPath, visibleAssetIds],
  );

  return (
    <PagePanel title="Search">
      <div className="search-toolbar">
        <label className="search-query-field" htmlFor="search-input">
          Search query
          <input
            key={routeQuery}
            id="search-input"
            defaultValue={routeQuery}
            onChange={(event) => handleQueryChange(event.target.value)}
            placeholder="Try camera model, city, place, or keywords"
            autoComplete="off"
          />
        </label>
      </div>

      {queryIsBlank ? (
        <div className="search-state">
          <h2>Start a search</h2>
          <p>Type a non-blank query to search cloud metadata.</p>
        </div>
      ) : null}

      {isSearching ? (
        <div className="search-state">
          <h2>Searching</h2>
          <p>Fetching matching assets.</p>
        </div>
      ) : null}

      {visibleErrorMessage ? (
        <div className="error-banner" role="alert">
          {visibleErrorMessage}
        </div>
      ) : null}

      {!queryIsBlank && hasSearched && !isSearching && !visibleErrorMessage && visibleResults.length === 0 ? (
        <div className="search-state">
          <h2>No results</h2>
          <p>No assets matched this query.</p>
        </div>
      ) : null}

      {visibleResults.length > 0 ? (
        <>
          <div className="search-grid">
            {visibleResults.map((asset) => {
              const source = getSearchDisplaySource(
                asset,
                fallbackVariantByAssetId[asset.id],
              );

              return (
                <AssetGridCard
                  key={asset.id}
                  id={asset.id}
                  to={`/assets/${asset.id}`}
                  sourceUrl={source?.url ?? null}
                  alt={`Search result ${asset.id}`}
                  mimeType={asset.mimeType}
                  takenAtLabel={formatTakenAt(asset.takenAt)}
                  mediaType={asset.mediaType}
                  isFavorite={asset.isFavorite}
                  imageKey={`${asset.id}-${imageNonceByAssetId[asset.id] ?? 0}-${source?.variant ?? "none"}`}
                  onOpen={() => handleOpenAsset()}
                  onImageError={() => {
                    void handleResultImageError(asset);
                  }}
                />
              );
            })}
          </div>

          {nextCursor ? (
            <div className="search-load-more-wrap">
              <button
                type="button"
                className="secondary-btn"
                onClick={() => {
                  void handleLoadMore();
                }}
                disabled={isLoadingMore}
              >
                {isLoadingMore ? "Loading..." : "Load more"}
              </button>
            </div>
          ) : null}
        </>
      ) : null}
    </PagePanel>
  );
}
