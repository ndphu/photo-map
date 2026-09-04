import { create } from "zustand";
import {
  syncAssetsChanges,
  type AssetSyncProgress,
} from "./assetChanges";

export type AssetSyncStatus = "idle" | "syncing" | "error";

interface AssetSyncState {
  status: AssetSyncStatus;
  errorMessage: string | null;
  lastSyncedAt: string | null;
  completedCount: number;
  remainingCount: number | null;
  totalCount: number | null;
  percent: number | null;
  syncAssetsChanges: (
    ownerUserId: string,
    options?: { full?: boolean },
  ) => Promise<void>;
  clearError: () => void;
  resetSyncState: () => void;
}

interface InFlightSync {
  generation: number;
  promise: Promise<void>;
}

const inFlightSyncs = new Map<string, InFlightSync>();
let syncGeneration = 0;

const EMPTY_PROGRESS: AssetSyncProgress = {
  completedCount: 0,
  remainingCount: null,
  totalCount: null,
  percent: null,
};

export const useAssetSyncStore = create<AssetSyncState>()((set, get) => ({
  status: "idle",
  errorMessage: null,
  lastSyncedAt: null,
  ...EMPTY_PROGRESS,
  syncAssetsChanges: async (ownerUserId, options) => {
    const existing = inFlightSyncs.get(ownerUserId);
    if (existing) {
      if (existing.generation === syncGeneration) {
        return existing.promise;
      }
      await existing.promise;
      return get().syncAssetsChanges(ownerUserId, options);
    }

    const generation = syncGeneration;
    set({ status: "syncing", errorMessage: null, ...EMPTY_PROGRESS });

    const inFlight: InFlightSync = {
      generation,
      promise: Promise.resolve(),
    };
    inFlight.promise = (async () => {
      try {
        await syncAssetsChanges(ownerUserId, {
          ...options,
          onProgress: (progress) => {
            if (generation === syncGeneration) {
              set(progress);
            }
          },
        });
        if (generation === syncGeneration) {
          set({
            status: "idle",
            errorMessage: null,
            lastSyncedAt: new Date().toISOString(),
          });
        }
      } catch (error) {
        const message =
          error instanceof Error ? error.message : "Metadata sync failed";

        if (generation === syncGeneration) {
          set({
            status: "error",
            errorMessage: message,
          });
        }
      } finally {
        if (inFlightSyncs.get(ownerUserId) === inFlight) {
          inFlightSyncs.delete(ownerUserId);
        }
      }
    })();

    inFlightSyncs.set(ownerUserId, inFlight);
    return inFlight.promise;
  },
  clearError: () => {
    set((state) => ({
      ...state,
      errorMessage: null,
      status: state.status === "error" ? "idle" : state.status,
    }));
  },
  resetSyncState: () => {
    syncGeneration += 1;
    set({
      status: "idle",
      errorMessage: null,
      lastSyncedAt: null,
      ...EMPTY_PROGRESS,
    });
  },
}));
