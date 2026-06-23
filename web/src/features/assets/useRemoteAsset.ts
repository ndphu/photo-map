import { liveQuery } from "dexie";
import { useEffect, useState } from "react";
import { appDb, type RemoteAssetRow } from "../../db/appDb";

interface RemoteAssetSnapshot {
  assetId: string;
  asset: RemoteAssetRow | undefined;
  errorMessage: string | null;
  hasLoaded: boolean;
}

interface RemoteAssetState {
  asset: RemoteAssetRow | undefined;
  isLoading: boolean;
  errorMessage: string | null;
}

export function useRemoteAsset(assetId: string | undefined): RemoteAssetState {
  const [snapshot, setSnapshot] = useState<RemoteAssetSnapshot | null>(null);

  useEffect(() => {
    if (!assetId) {
      return;
    }

    const subscription = liveQuery(() => appDb.remote_assets.get(assetId)).subscribe({
      next: (nextAsset) => {
        setSnapshot({
          assetId,
          asset: nextAsset,
          errorMessage: null,
          hasLoaded: true,
        });
      },
      error: (error) => {
        setSnapshot({
          assetId,
          asset: undefined,
          errorMessage:
            error instanceof Error
              ? error.message
              : "Failed to read cached asset from IndexedDB",
          hasLoaded: true,
        });
      },
    });

    return () => {
      subscription.unsubscribe();
    };
  }, [assetId]);

  const hasActiveSnapshot =
    Boolean(assetId) && snapshot !== null && snapshot.assetId === assetId;

  const effectiveAsset = hasActiveSnapshot ? snapshot?.asset : undefined;
  const effectiveIsLoading = Boolean(assetId) && !hasActiveSnapshot;
  const effectiveErrorMessage = hasActiveSnapshot
    ? snapshot?.errorMessage ?? null
    : null;

  return {
    asset: effectiveAsset,
    isLoading: effectiveIsLoading,
    errorMessage: effectiveErrorMessage,
  };
}
