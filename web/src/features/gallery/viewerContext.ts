export type ViewerContextSource =
  | "gallery"
  | "favorites"
  | "archive"
  | "trash"
  | "search"
  | "album";

export interface ViewerContext {
  source: ViewerContextSource;
  backTo: string;
  assetIds: string[];
  selectedFilter?: string;
  updatedAt: string;
}

const VIEWER_CONTEXT_KEY = "photo-map-web-viewer-context";
const GALLERY_SCROLL_KEY = "photo-map-web-gallery-scroll";

export function saveViewerContext(context: ViewerContext): void {
  if (typeof window === "undefined") {
    return;
  }

  try {
    sessionStorage.setItem(VIEWER_CONTEXT_KEY, JSON.stringify(context));
  } catch {
    // Ignore storage failures.
  }
}

export function readViewerContext(): ViewerContext | null {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    const raw = sessionStorage.getItem(VIEWER_CONTEXT_KEY);
    if (!raw) {
      return null;
    }

    const parsed = JSON.parse(raw) as Partial<ViewerContext>;
    if (
      !parsed ||
      typeof parsed.source !== "string" ||
      typeof parsed.backTo !== "string" ||
      !Array.isArray(parsed.assetIds)
    ) {
      return null;
    }

    return {
      source: parsed.source as ViewerContextSource,
      backTo: parsed.backTo,
      assetIds: parsed.assetIds.filter((value): value is string => typeof value === "string"),
      selectedFilter: typeof parsed.selectedFilter === "string" ? parsed.selectedFilter : undefined,
      updatedAt: typeof parsed.updatedAt === "string" ? parsed.updatedAt : new Date().toISOString(),
    };
  } catch {
    return null;
  }
}

export interface GalleryScrollState {
  routeKey: string;
  scrollY: number;
  anchorAssetId: string | null;
  anchorViewportOffset: number | null;
  updatedAt: string;
}

export function saveGalleryScrollState(
  routeKey: string,
  scrollY: number,
  anchorAssetId: string,
  anchorViewportOffset: number,
): void {
  if (typeof window === "undefined") {
    return;
  }

  const payload: GalleryScrollState = {
    routeKey,
    scrollY,
    anchorAssetId,
    anchorViewportOffset,
    updatedAt: new Date().toISOString(),
  };

  try {
    sessionStorage.setItem(GALLERY_SCROLL_KEY, JSON.stringify(payload));
  } catch {
    // Ignore storage failures.
  }
}

export function readGalleryScrollState(routeKey: string): GalleryScrollState | null {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    const raw = sessionStorage.getItem(GALLERY_SCROLL_KEY);
    if (!raw) {
      return null;
    }

    const parsed = JSON.parse(raw) as Partial<GalleryScrollState>;
    if (
      !parsed ||
      parsed.routeKey !== routeKey ||
      typeof parsed.scrollY !== "number" ||
      !Number.isFinite(parsed.scrollY)
    ) {
      return null;
    }

    return {
      routeKey,
      scrollY: Math.max(0, parsed.scrollY),
      anchorAssetId:
        typeof parsed.anchorAssetId === "string" ? parsed.anchorAssetId : null,
      anchorViewportOffset:
        typeof parsed.anchorViewportOffset === "number" &&
        Number.isFinite(parsed.anchorViewportOffset)
          ? parsed.anchorViewportOffset
          : null,
      updatedAt:
        typeof parsed.updatedAt === "string"
          ? parsed.updatedAt
          : new Date().toISOString(),
    };
  } catch {
    return null;
  }
}

export function clearGalleryScrollState(routeKey: string): void {
  if (typeof window === "undefined") {
    return;
  }

  try {
    const raw = sessionStorage.getItem(GALLERY_SCROLL_KEY);
    if (!raw) {
      return;
    }

    const parsed = JSON.parse(raw) as Partial<GalleryScrollState>;
    if (parsed.routeKey === routeKey) {
      sessionStorage.removeItem(GALLERY_SCROLL_KEY);
    }
  } catch {
    // Ignore storage failures.
  }
}
