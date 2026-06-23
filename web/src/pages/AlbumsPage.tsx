import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { PagePanel } from "../components/PagePanel";
import {
  createAlbum,
  deleteAlbum,
  listAlbums,
  updateAlbum,
  type Album,
} from "../features/albums/albumsApi";

interface AlbumDraft {
  name: string;
  description: string;
}

const defaultDraft: AlbumDraft = {
  name: "",
  description: "",
};

function formatTimestamp(value: string): string {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "Unknown";
  }
  return parsed.toLocaleString();
}

export function AlbumsPage() {
  const [albums, setAlbums] = useState<Album[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [createDraft, setCreateDraft] = useState<AlbumDraft>(defaultDraft);
  const [editingAlbumId, setEditingAlbumId] = useState<string | null>(null);
  const [editDraft, setEditDraft] = useState<AlbumDraft>(defaultDraft);

  const sortedAlbums = useMemo(
    () => [...albums].sort((left, right) => right.createdAt.localeCompare(left.createdAt)),
    [albums],
  );

  useEffect(() => {
    let cancelled = false;

    const run = async () => {
      try {
        const items = await listAlbums();
        if (cancelled) {
          return;
        }

        setAlbums(items);
        setErrorMessage(null);
      } catch (error) {
        if (cancelled) {
          return;
        }

        setErrorMessage(error instanceof Error ? error.message : "Failed to load albums");
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };

    void run();

    return () => {
      cancelled = true;
    };
  }, []);

  const handleCreateAlbum = async (): Promise<void> => {
    const name = createDraft.name.trim();
    if (!name) {
      setErrorMessage("Album name is required.");
      return;
    }

    setIsSaving(true);
    setErrorMessage(null);

    try {
      const created = await createAlbum({
        name,
        description: createDraft.description.trim() || undefined,
      });

      setAlbums((current) => [created, ...current]);
      setCreateDraft(defaultDraft);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Create album failed");
    } finally {
      setIsSaving(false);
    }
  };

  const startEdit = (album: Album) => {
    setEditingAlbumId(album.id);
    setEditDraft({
      name: album.name,
      description: album.description ?? "",
    });
    setErrorMessage(null);
  };

  const cancelEdit = () => {
    setEditingAlbumId(null);
    setEditDraft(defaultDraft);
  };

  const handleSaveEdit = async (album: Album): Promise<void> => {
    const name = editDraft.name.trim();
    if (!name) {
      setErrorMessage("Album name is required.");
      return;
    }

    setIsSaving(true);
    setErrorMessage(null);

    try {
      const updated = await updateAlbum(album.id, {
        name,
        description: editDraft.description.trim() || null,
      });

      setAlbums((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      );
      cancelEdit();
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Update album failed");
    } finally {
      setIsSaving(false);
    }
  };

  const handleToggleArchive = async (album: Album): Promise<void> => {
    setIsSaving(true);
    setErrorMessage(null);

    try {
      const updated = await updateAlbum(album.id, {
        isArchived: !album.isArchived,
      });

      setAlbums((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      );
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Archive update failed");
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeleteAlbum = async (album: Album): Promise<void> => {
    const confirmed = window.confirm(
      "Delete this album? This only removes album metadata and membership links, not cloud assets.",
    );

    if (!confirmed) {
      return;
    }

    setIsSaving(true);
    setErrorMessage(null);

    try {
      await deleteAlbum(album.id);
      setAlbums((current) => current.filter((item) => item.id !== album.id));
      if (editingAlbumId === album.id) {
        cancelEdit();
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Delete album failed");
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <PagePanel title="Albums">
      <div className="albums-create-panel">
        <h2>Create album</h2>
        <div className="albums-form-grid">
          <label>
            Name
            <input
              value={createDraft.name}
              onChange={(event) =>
                setCreateDraft((current) => ({
                  ...current,
                  name: event.target.value,
                }))
              }
              placeholder="Summer road trip"
            />
          </label>
          <label>
            Description
            <input
              value={createDraft.description}
              onChange={(event) =>
                setCreateDraft((current) => ({
                  ...current,
                  description: event.target.value,
                }))
              }
              placeholder="Optional"
            />
          </label>
        </div>
        <div className="albums-actions-row">
          <button
            type="button"
            className="secondary-btn"
            disabled={isSaving}
            onClick={() => {
              void handleCreateAlbum();
            }}
          >
            Create album
          </button>
        </div>
      </div>

      {errorMessage ? (
        <div className="error-banner" role="alert">
          {errorMessage}
        </div>
      ) : null}

      {isLoading ? (
        <div className="albums-state">
          <h2>Loading albums</h2>
          <p>Fetching your album list.</p>
        </div>
      ) : null}

      {!isLoading && sortedAlbums.length === 0 ? (
        <div className="albums-state">
          <h2>No albums yet</h2>
          <p>Create your first album to organize assets.</p>
        </div>
      ) : null}

      {!isLoading && sortedAlbums.length > 0 ? (
        <div className="albums-list">
          {sortedAlbums.map((album) => {
            const isEditing = editingAlbumId === album.id;

            return (
              <article key={album.id} className="albums-item">
                <div className="albums-item-main">
                  {isEditing ? (
                    <div className="albums-form-grid">
                      <label>
                        Name
                        <input
                          value={editDraft.name}
                          onChange={(event) =>
                            setEditDraft((current) => ({
                              ...current,
                              name: event.target.value,
                            }))
                          }
                        />
                      </label>
                      <label>
                        Description
                        <input
                          value={editDraft.description}
                          onChange={(event) =>
                            setEditDraft((current) => ({
                              ...current,
                              description: event.target.value,
                            }))
                          }
                        />
                      </label>
                    </div>
                  ) : (
                    <>
                      <h2>{album.name}</h2>
                      <p>{album.description ?? "No description"}</p>
                      <p className="albums-item-meta">
                        Created: {formatTimestamp(album.createdAt)}
                      </p>
                      <p className="albums-item-meta">
                        Updated: {formatTimestamp(album.updatedAt)}
                      </p>
                      {album.isArchived ? <span className="gallery-flag">Archived</span> : null}
                    </>
                  )}
                </div>
                <div className="albums-actions-row">
                  <Link to={`/albums/${album.id}`} className="secondary-btn albums-link-btn">
                    Open
                  </Link>

                  {isEditing ? (
                    <>
                      <button
                        type="button"
                        className="secondary-btn"
                        disabled={isSaving}
                        onClick={() => {
                          void handleSaveEdit(album);
                        }}
                      >
                        Save
                      </button>
                      <button
                        type="button"
                        className="secondary-btn"
                        disabled={isSaving}
                        onClick={cancelEdit}
                      >
                        Cancel
                      </button>
                    </>
                  ) : (
                    <>
                      <button
                        type="button"
                        className="secondary-btn"
                        disabled={isSaving}
                        onClick={() => startEdit(album)}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="secondary-btn"
                        disabled={isSaving}
                        onClick={() => {
                          void handleToggleArchive(album);
                        }}
                      >
                        {album.isArchived ? "Unarchive" : "Archive"}
                      </button>
                      <button
                        type="button"
                        className="secondary-btn danger"
                        disabled={isSaving}
                        onClick={() => {
                          void handleDeleteAlbum(album);
                        }}
                      >
                        Delete
                      </button>
                    </>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      ) : null}
    </PagePanel>
  );
}
