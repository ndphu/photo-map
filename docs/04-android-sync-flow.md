# Android sync flow

## Local state

Room table `local_assets` lưu:

| Field | Mục đích |
|---|---|
| `localAssetId` | Stable MediaStore identity, primary key |
| `uri` | Content URI; original được stream trực tiếp từ đây |
| `mediaType`, `mimeType`, `displayName`, `sizeBytes` | Upload session input |
| `width`, `height`, `durationMs`, `takenAt`, `modifiedAt` | Metadata cơ bản |
| `syncStatus` | `pending`, `uploading`, `uploaded`, `failed`, `skipped` |
| `uploadSessionId` | Session dùng để resume sau process death |
| `uploadAttemptCount`, `nextRetryAt`, `lastError` | Retry state |
| `remoteAssetId`, `lastSyncedAt` | Kết quả backend đã xác nhận |

## Scheduling

- Manual sync: scan MediaStore rồi enqueue unique work `media-sync`.
- Background sync: periodic worker `periodic-media-scan`, chu kỳ 1 giờ, best-effort.
- Upload work là unique work nên không có hai queue chạy chồng nhau.
- `wifiOnly=true` dùng `NetworkType.UNMETERED`; nếu tắt dùng `CONNECTED`.
- WorkManager exponential backoff bắt đầu từ 30 giây.
- Logout hủy cả upload work và periodic work.

## Upload flow

```mermaid
flowchart TD
    S["Scan MediaStore"] --> R["Insert new rows into Room"]
    R --> Q["Load pending / retry-ready failed assets"]
    Q --> C["Compute SHA-256"]
    C --> H{"uploadSessionId exists?"}
    H -->|Yes| RES["POST session/resume"]
    H -->|No| NEW["POST upload-sessions with checksum"]
    RES --> OUT{"Response status"}
    NEW --> OUT
    OUT -->|"completed / already_uploaded / duplicate_found"| DONE["Mark Room uploaded"]
    OUT -->|"created / existing_session / resumed"| ACTIVE["Persist uploadSessionId"]
    ACTIVE --> U1["PATCH status uploading"]
    U1 --> U2["PUT original directly to R2"]
    U2 --> U3["Generate and PUT thumbnail / preview / poster"]
    U3 --> U4["PATCH status uploaded"]
    U4 --> CMP["POST session/complete"]
    CMP --> DONE
```

Mỗi worker đọc `maxParallelUploads` khi bắt đầu. Tối đa 1-16 asset được xử lý đồng thời; các variant trong một asset được upload tuần tự. Original luôn được stream từ MediaStore, không ghi file tạm.

## Metadata

Scanner/extractor đọc MediaStore ID, filename, MIME type, size, timestamps, dimensions và duration. EXIF bổ sung DateTimeOriginal, GPS, orientation, make, model và software. Complete request gửi metadata có sẵn; derivative thiếu không chặn việc tạo asset nếu original đã tồn tại.

## Retry và resume

1. Khi worker khởi động, row mắc ở `uploading` được reset về `pending`.
2. Nếu Room có `uploadSessionId`, worker gọi resume và tái sử dụng object keys.
3. Presigned PUT trả `403`: gọi resume để lấy fresh URL rồi thử lại.
4. Resume trả `403`, `404` hoặc `409`: clear `uploadSessionId`, đánh dấu failed và lần sau tạo session mới.
5. Network failure: lưu lỗi, `nextRetryAt`, trả `Result.retry()`.
6. HTTP `401`: xóa auth local và dừng queue, không complete bằng token cũ.
7. `CancellationException` luôn được propagate, không bị đổi thành failed upload.
8. Một asset lỗi không hủy các asset song song khác vì batch dùng supervisor semantics.

Chỉ các response `completed`, `already_uploaded`, `duplicate_found` hoặc complete thành công mới được phép chuyển Room sang `uploaded`.

## Foreground execution

Upload worker chạy foreground với notification channel `media_uploads`, hiển thị số file đã xử lý. Manifest khai báo `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` và service type `dataSync`. Android 13+ có `POST_NOTIFICATIONS`; từ chối quyền notification không làm thay đổi tính đúng đắn của sync.

## UI recovery

- Login/register cho phép submit lại sau lỗi.
- Gallery có refresh/retry và refresh khi app quay lại foreground.
- Asset detail có retry.
- Settings hiển thị số pending/uploading/uploaded/failed, manual sync và retry failed.
