import { appDb, type RemoteAssetRow } from "../../db/appDb";
import { apiRequest } from "../../lib/apiClient";

interface FavoriteRequest {
  isFavorite: boolean;
}

interface ArchiveRequest {
  isArchived: boolean;
}

interface AddToAlbumRequest {
  assetId: string;
  sortOrder: number | null;
}

const CONCURRENCY_LIMIT = 4;

export type MultiActionType =
  | "favorite"
  | "archive"
  | "trash"
  | "restore"
  | "add_to_album";

export interface MultiActionResult {
  succeededIds: string[];
  failedIds: string[];
}

export async function patchFavorite(assetId: string, isFavorite: boolean): Promise<void> {
  await apiRequest<unknown, FavoriteRequest>(`/assets/${assetId}/favorite`, {
    method: "PATCH",
    body: { isFavorite },
  });
}

export async function patchArchive(assetId: string, isArchived: boolean): Promise<void> {
  await apiRequest<unknown, ArchiveRequest>(`/assets/${assetId}/archive`, {
    method: "PATCH",
    body: { isArchived },
  });
}

export async function postTrash(assetId: string): Promise<void> {
  await apiRequest<unknown>(`/assets/${assetId}/trash`, {
    method: "POST",
  });
}

export async function postRestore(assetId: string): Promise<void> {
  await apiRequest<unknown>(`/assets/${assetId}/restore`, {
    method: "POST",
  });
}

export async function deleteAsset(assetId: string): Promise<void> {
  await apiRequest<void>(`/assets/${assetId}`, {
    method: "DELETE",
  });
}

export async function addAssetToAlbum(
  albumId: string,
  assetId: string,
  sortOrder: number | null = null,
): Promise<void> {
  await apiRequest<unknown, AddToAlbumRequest>(`/albums/${albumId}/assets`, {
    method: "POST",
    body: {
      assetId,
      sortOrder,
    },
  });
}

function mergePartial(
  current: RemoteAssetRow,
  patch: Partial<RemoteAssetRow>,
): RemoteAssetRow {
  return {
    ...current,
    ...patch,
  };
}

export async function optimisticUpdateAsset(
  assetId: string,
  patch: Partial<RemoteAssetRow>,
): Promise<RemoteAssetRow | null> {
  const previous = await appDb.remote_assets.get(assetId);
  if (!previous) {
    return null;
  }

  await appDb.remote_assets.put(mergePartial(previous, patch));
  return previous;
}

export async function rollbackAsset(
  previous: RemoteAssetRow | null,
  fallbackAssetId: string,
): Promise<void> {
  if (!previous) {
    return;
  }

  await appDb.remote_assets.put({
    ...previous,
    id: fallbackAssetId,
  });
}

async function runBounded<T>(
  values: T[],
  worker: (value: T) => Promise<void>,
): Promise<{ succeeded: T[]; failed: T[] }> {
  const succeeded: T[] = [];
  const failed: T[] = [];

  let nextIndex = 0;

  async function runWorker(): Promise<void> {
    while (nextIndex < values.length) {
      const currentIndex = nextIndex;
      nextIndex += 1;
      const value = values[currentIndex];

      try {
        await worker(value);
        succeeded.push(value);
      } catch {
        failed.push(value);
      }
    }
  }

  const workers = Array.from(
    { length: Math.min(CONCURRENCY_LIMIT, Math.max(1, values.length)) },
    () => runWorker(),
  );

  await Promise.all(workers);

  return { succeeded, failed };
}

export async function runMultiAction(
  action: MultiActionType,
  assetIds: string[],
  options?: { isFavorite?: boolean; isArchived?: boolean; albumId?: string },
): Promise<MultiActionResult> {
  const run = async (assetId: string): Promise<void> => {
    switch (action) {
      case "favorite":
        await patchFavorite(assetId, Boolean(options?.isFavorite));
        break;
      case "archive":
        await patchArchive(assetId, Boolean(options?.isArchived));
        break;
      case "trash":
        await postTrash(assetId);
        break;
      case "restore":
        await postRestore(assetId);
        break;
      case "add_to_album": {
        const albumId = options?.albumId?.trim();
        if (!albumId) {
          throw new Error("albumId is required for add_to_album");
        }
        await addAssetToAlbum(albumId, assetId, null);
        break;
      }
      default:
        throw new Error("Unsupported action");
    }
  };

  const result = await runBounded(assetIds, run);

  return {
    succeededIds: result.succeeded,
    failedIds: result.failed,
  };
}

export { CONCURRENCY_LIMIT };
