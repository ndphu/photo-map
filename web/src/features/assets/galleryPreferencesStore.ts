import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

const MIN_GALLERY_COLUMNS = 2;
const MAX_GALLERY_COLUMNS = 6;
const DEFAULT_GALLERY_COLUMNS = 3;

function clampColumns(columns: number): number {
  if (!Number.isFinite(columns)) {
    return DEFAULT_GALLERY_COLUMNS;
  }
  return Math.min(MAX_GALLERY_COLUMNS, Math.max(MIN_GALLERY_COLUMNS, Math.round(columns)));
}

interface GalleryPreferencesState {
  columns: number;
  assetDetailsInfoPanelOpen: boolean;
  setColumns: (columns: number) => void;
  setAssetDetailsInfoPanelOpen: (open: boolean) => void;
}

export const useGalleryPreferencesStore = create<GalleryPreferencesState>()(
  persist(
    (set) => ({
      columns: DEFAULT_GALLERY_COLUMNS,
      assetDetailsInfoPanelOpen: true,
      setColumns: (columns) => {
        set({ columns: clampColumns(columns) });
      },
      setAssetDetailsInfoPanelOpen: (open) => {
        set({ assetDetailsInfoPanelOpen: open });
      },
    }),
    {
      name: "photo-map-web-gallery-preferences",
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        columns: state.columns,
        assetDetailsInfoPanelOpen: state.assetDetailsInfoPanelOpen,
      }),
    },
  ),
);

export { DEFAULT_GALLERY_COLUMNS, MAX_GALLERY_COLUMNS, MIN_GALLERY_COLUMNS };
