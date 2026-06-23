# Photo Map Web Client

React web client for the private cloud gallery.

## Stack

- Vite
- React + TypeScript
- React Router
- TanStack Query
- Zustand (with localStorage persistence)
- Dexie (IndexedDB initialization)

## Environment

Copy `.env.example` to `.env` and adjust values if needed:

```bash
cp .env.example .env
```

Available variables:

- `VITE_API_BASE_URL` (default: `http://localhost:8080`)

## Development

```bash
npm install
npm run dev
```

## Build

```bash
npm run build
```

## Current Scope

- Login flow with `POST /auth/login`
- Typed `fetch` API client with backend error parsing:
  - `{"error":{"code":"...","message":"..."}}`
- Auth persistence in Zustand
- Browser-side metadata replication with `GET /assets/changes`
  - Dexie tables:
    - `remote_assets` (`id` primary key)
    - `remote_sync_state` (`key="asset_metadata"`, value=`last committed changeId`)
  - Paged sync (`limit=1000`) with per-page Dexie transaction
  - Cursor commit (`nextCursor`) only after all page items are applied
  - Upsert on `upsert`/`trash`/`restore`, delete row on tombstone (`asset: null`)
  - Cache is preserved on failed refresh
- Protected routes:
  - `/login`
  - `/gallery`
  - `/assets/:id`
  - `/search`
  - `/albums`
  - `/settings`
- Automatic logout and redirect to `/login` on HTTP 401
- Logout clears `remote_assets` and `remote_sync_state`
- Responsive app shell (sidebar + topbar)
- Sync state exposed as `idle | syncing | error` with `lastSyncedAt`

Out of scope for this phase:

- Upload implementation
- Manual R2 URL construction
- Storing media binaries in IndexedDB

### Signed URL handling

Signed URLs from change snapshots are temporary and are never treated as
durable metadata. The client only caches:

- `thumbnailUrl`
- `previewUrl`

as short-lived convenience fields.
