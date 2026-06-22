# Photo Map Android

Android client for the private cloud photo and video gallery backend.

## Requirements

- Android Studio with JDK 17
- Android SDK 36.1
- Backend reachable from the device or emulator

The build default backend URL is configured as `BuildConfig.API_BASE_URL` in
`app/build.gradle.kts` and currently points to the deployed Render API. Login,
Register, and Settings can override it at runtime without restarting the app.
Custom endpoints must use HTTPS, or HTTP with localhost/private IP addresses.

## Build

```powershell
.\gradlew.bat assembleDebug
```

## Sync behavior

After login, the app registers the device and requests media permissions.
Media metadata is scanned into Room. WorkManager uploads originals and generated
WebP derivatives directly to Cloudflare R2 using backend-provided presigned URLs.
Only metadata and completion requests pass through the backend.

- Background upload is disabled by default; manual sync remains available.
- Wi-Fi only is enabled by default.
- Parallel upload presets are 8, 16, 32, 64, and 128; uploads can be paused.
- Upload sessions are idempotent and resumable after process death or URL expiry.
- Cloud metadata is replicated through `/assets/changes` into Room and rendered offline-first.
- Favorite/archive/trash/restore/delete mutations use a durable metadata operation queue.
- Existing uploaded photos/videos receive GPS and EXIF updates through a metadata-only foreground worker; media is not re-uploaded.
- Coil caches thumbnail/preview variants with stable keys; originals are loaded or downloaded only on demand.
- Changing the backend endpoint logs out and clears server-scoped Room/cache state before local uploads are reset for the new server.
