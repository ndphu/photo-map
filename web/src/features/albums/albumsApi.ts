import { ApiError } from "../../types/api";
import { apiRequest } from "../../lib/apiClient";
import type { SearchAssetItem } from "../search/searchApi";

export interface Album {
  id: string;
  name: string;
  description: string | null;
  coverAssetId: string | null;
  isArchived: boolean;
  createdAt: string;
  updatedAt: string;
}

interface AlbumListResponse {
  items: Album[];
}

interface AlbumAssetsResponse {
  items: SearchAssetItem[];
}

interface CreateAlbumRequest {
  name: string;
  description?: string;
}

interface UpdateAlbumRequest {
  name?: string;
  description?: string | null;
  coverAssetId?: string | null;
  isArchived?: boolean;
}

interface AddAssetToAlbumRequest {
  assetId: string;
  sortOrder: number | null;
}

function isDuplicateMembershipError(error: unknown): boolean {
  if (!(error instanceof ApiError)) {
    return false;
  }

  if (error.status === 409) {
    return true;
  }

  const value = `${error.code} ${error.message}`.toLowerCase();
  return (
    value.includes("conflict") ||
    value.includes("exists") ||
    value.includes("already") ||
    value.includes("duplicate")
  );
}

export async function listAlbums(): Promise<Album[]> {
  const response = await apiRequest<AlbumListResponse>("/albums");
  return response.items;
}

export function getAlbum(albumId: string): Promise<Album> {
  return apiRequest<Album>(`/albums/${albumId}`);
}

export function createAlbum(request: CreateAlbumRequest): Promise<Album> {
  return apiRequest<Album, CreateAlbumRequest>("/albums", {
    method: "POST",
    body: request,
  });
}

export function updateAlbum(albumId: string, request: UpdateAlbumRequest): Promise<Album> {
  return apiRequest<Album, UpdateAlbumRequest>(`/albums/${albumId}`, {
    method: "PATCH",
    body: request,
  });
}

export function deleteAlbum(albumId: string): Promise<void> {
  return apiRequest<void>(`/albums/${albumId}`, {
    method: "DELETE",
  });
}

export async function addAssetToAlbum(
  albumId: string,
  assetId: string,
  sortOrder: number | null = null,
): Promise<void> {
  try {
    await apiRequest<void, AddAssetToAlbumRequest>(`/albums/${albumId}/assets`, {
      method: "POST",
      body: {
        assetId,
        sortOrder,
      },
    });
  } catch (error) {
    if (isDuplicateMembershipError(error)) {
      return;
    }
    throw error;
  }
}

export function removeAssetFromAlbum(albumId: string, assetId: string): Promise<void> {
  return apiRequest<void>(`/albums/${albumId}/assets/${assetId}`, {
    method: "DELETE",
  });
}

export async function listAlbumAssets(albumId: string): Promise<SearchAssetItem[]> {
  const response = await apiRequest<AlbumAssetsResponse>(`/albums/${albumId}/assets`);
  return response.items;
}
