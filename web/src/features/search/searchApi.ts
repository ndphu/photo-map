import { apiRequest } from "../../lib/apiClient";

export interface SearchAssetItem {
  id: string;
  mediaType: string;
  mimeType: string;
  thumbnailKey: string | null;
  previewKey: string | null;
  thumbnailUrl: string | null;
  previewUrl: string | null;
  takenAt: string | null;
  width: number | null;
  height: number | null;
  durationMs: number | null;
  isFavorite: boolean;
}

export interface SearchResponse {
  items: SearchAssetItem[];
  nextCursor: string | null;
}

interface ReadUrlResponse {
  url: string;
}

export type SearchReadUrlVariant = "thumbnail" | "preview";

interface SearchAssetsOptions {
  q: string;
  cursor?: string | null;
  limit?: number;
  signal?: AbortSignal;
}

export async function searchAssets(options: SearchAssetsOptions): Promise<SearchResponse> {
  const query = new URLSearchParams({
    q: options.q,
    limit: String(options.limit ?? 50),
  });

  if (options.cursor) {
    query.set("cursor", options.cursor);
  }

  return apiRequest<SearchResponse>(`/search?${query.toString()}`, {
    signal: options.signal,
  });
}

export async function getSearchAssetReadUrl(
  assetId: string,
  variant: SearchReadUrlVariant,
): Promise<string> {
  const query = new URLSearchParams({ variant });
  const response = await apiRequest<ReadUrlResponse>(
    `/assets/${assetId}/read-url?${query.toString()}`,
  );

  return response.url;
}
