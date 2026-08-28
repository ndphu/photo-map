import { create } from "zustand";
import { syncAssetsChanges } from "./assetChanges";

export type AssetSyncStatus = "idle" | "syncing" | "error";

interface AssetSyncState {
  status: AssetSyncStatus;
  errorMessage: string | null;
  lastSyncedAt: string | null;
  syncAssetsChanges: (
    ownerUserId: string,
    options?: { full?: boolean },
  ) => Promise<void>;
  clearError: () => void;
  resetSyncState: () => void;
}

export const useAssetSyncStore = create<AssetSyncState>()((set) => ({
  status: "idle",
  errorMessage: null,
  lastSyncedAt: null,
  syncAssetsChanges: async (ownerUserId, options) => {
    set({ status: "syncing", errorMessage: null });

    try {
      await syncAssetsChanges(ownerUserId, options);
      set({
        status: "idle",
        errorMessage: null,
        lastSyncedAt: new Date().toISOString(),
      });
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Metadata sync failed";

      set({
        status: "error",
        errorMessage: message,
      });
    }
  },
  clearError: () => {
    set((state) => ({
      ...state,
      errorMessage: null,
      status: state.status === "error" ? "idle" : state.status,
    }));
  },
  resetSyncState: () => {
    set({ status: "idle", errorMessage: null, lastSyncedAt: null });
  },
}));
