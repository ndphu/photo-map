# Photo Map Android

Android client for the private cloud photo and video gallery backend.

## Requirements

- Android Studio with JDK 17
- Android SDK 36.1
- Backend reachable from the device or emulator

The default debug backend URL is `http://10.0.2.2:8080/`. Change
`API_BASE_URL` in `app/build.gradle.kts` for a physical device or deployed API.

## Build

```powershell
.\gradlew.bat assembleDebug
```

## Sync behavior

After login, the app registers the device and requests media permissions.
Media metadata is scanned into Room. WorkManager uploads originals and generated
WebP derivatives directly to Cloudflare R2 using backend-provided presigned URLs.
Only metadata and completion requests pass through the backend.
