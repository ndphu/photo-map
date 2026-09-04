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
    - `remote_assets_by_user` (`[ownerUserId+id]` compound primary key)
    - `remote_sync_state_by_user` (`[ownerUserId+key]` compound primary key,
      `key="asset_metadata"`, value=`last committed changeId`)
  - Paged sync (`limit=400`) with per-page Dexie transaction
  - Cursor commit (`nextCursor`) only after all page items are applied
  - Per-user single-flight requests and Gallery progress from `remainingCount`
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
- Logout preserves each user's isolated IndexedDB replica and committed cursor
- The version 3 IndexedDB migration discards legacy unscoped rows once
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
