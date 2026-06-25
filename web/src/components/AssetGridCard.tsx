import type { ReactNode } from "react";
import { Link } from "react-router-dom";

interface AssetGridCardProps {
  id: string;
  to: string;
  sourceUrl: string | null;
  alt: string;
  mimeType: string;
  takenAtLabel: string;
  mediaType: string;
  isFavorite: boolean;
  imageKey?: string;
  placeholderText?: string;
  onOpen?: () => void;
  onImageError?: () => void;
  actions?: ReactNode;
}

export function AssetGridCard({
  id,
  to,
  sourceUrl,
  alt,
  mimeType,
  takenAtLabel,
  mediaType,
  isFavorite,
  imageKey,
  placeholderText = "Preview unavailable",
  onOpen,
  onImageError,
  actions,
}: AssetGridCardProps) {
  return (
    <article className="search-card">
      <Link
        to={to}
        className="search-thumb-wrap"
        onClick={onOpen}
        aria-label={`Open asset ${id}`}
      >
        {sourceUrl ? (
          <img
            key={imageKey ?? id}
            className="search-thumb"
            src={sourceUrl}
            alt={alt}
            loading="lazy"
            onError={onImageError}
          />
        ) : (
          <div className="search-thumb-placeholder">{placeholderText}</div>
        )}
      </Link>
      <div className="search-card-meta">
        <p className="search-card-title">{mimeType}</p>
        <p className="search-card-date">{takenAtLabel}</p>
        <div className="search-card-flags">
          {mediaType === "video" ? <span className="gallery-flag">Video</span> : null}
          {isFavorite ? <span className="gallery-flag">Favorite</span> : null}
        </div>
        {actions ? <div className="albums-actions-row">{actions}</div> : null}
      </div>
    </article>
  );
}
