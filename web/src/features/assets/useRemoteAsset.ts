import { liveQuery } from "dexie";
import { useEffect, useState } from "react";
import type { RemoteAssetRow } from "../../db/appDb";
import { useAuthStore } from "../../store/authStore";
import { getRemoteAsset } from "./assetReplica";

interface RemoteAssetSnapshot {
  ownerUserId: string;
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
  const ownerUserId = useAuthStore((state) => state.user?.id ?? null);
  const [snapshot, setSnapshot] = useState<RemoteAssetSnapshot | null>(null);

  useEffect(() => {
    if (!assetId || !ownerUserId) {
      return;
    }

    const subscription = liveQuery(() => getRemoteAsset(ownerUserId, assetId)).subscribe({
      next: (nextAsset) => {
        setSnapshot({
          ownerUserId,
          assetId,
          asset: nextAsset,
          errorMessage: null,
          hasLoaded: true,
        });
      },
      error: (error) => {
        setSnapshot({
          ownerUserId,
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
  }, [assetId, ownerUserId]);

  const hasActiveSnapshot =
    Boolean(assetId && ownerUserId) &&
    snapshot !== null &&
    snapshot.ownerUserId === ownerUserId &&
    snapshot.assetId === assetId;

  const effectiveAsset = hasActiveSnapshot ? snapshot?.asset : undefined;
  const effectiveIsLoading = Boolean(assetId && ownerUserId) && !hasActiveSnapshot;
  const effectiveErrorMessage = hasActiveSnapshot
    ? snapshot?.errorMessage ?? null
    : null;

  return {
    asset: effectiveAsset,
    isLoading: effectiveIsLoading,
    errorMessage: effectiveErrorMessage,
  };
}
