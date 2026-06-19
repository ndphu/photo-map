# Roadmap

## P0 - Reliability và security baseline

- [x] JWT auth, bcrypt và user-scoped queries.
- [x] Device registration và stable local asset mapping.
- [x] Direct-to-R2 upload bằng presigned URL.
- [x] Idempotency theo device/local ID và SHA-256.
- [x] Resume session, status transitions và WorkManager retry.
- [x] Parallel/background uploads, Wi-Fi setting và foreground notification.
- [x] Orphan cleanup dry-run/live, admin guard, CLI và audit logs.
- [x] Gallery retry khi backend tạm thời unavailable.
- [x] Paging 3 gallery, pull-to-refresh, offline banner và foreground refresh.
- [x] Gallery filters và detail actions cho favorite/archive/trash/restore/delete.
- [ ] Backend integration tests với PostgreSQL và S3-compatible test storage.
- [ ] Structured observability: request ID, upload session ID, latency/error metrics.
- [ ] Rate limiting cho auth, presign và maintenance endpoints.
- [ ] Production secret management, TLS-only API và credential rotation runbook.

## P1 - Gallery completeness

- [x] Paging 3 cho search; album assets use the current non-paginated backend response.
- [x] Album UI và search UI trên Android.
- [x] Timeline date grouping with stable Paging 3 keys and null-date handling.
- [x] Android multi-select with bounded client-side favorite/archive/trash/album actions.
- [x] Gallery sync status banner and refresh after confirmed local upload completion.
- [x] Zoomable single-photo viewer with signed preview URL refresh and separate video state.
- [ ] Archive, restore, hard-delete confirmation và trash retention policy.
- [ ] Server-side derivative rebuild queue cho thumbnail/preview/poster bị thiếu.
- [ ] Video playback với signed read URL và range request validation.
- [ ] Backend batch mutation APIs for large selections; Android currently uses concurrency capped at four.
- [ ] Full month-index fast scrolling; Android currently provides date headers and jump-to-top.
- [ ] Location enrichment thành country/region/city/place name.
- [ ] Checksum cache cục bộ để tránh hash lại file lớn.
- [ ] Multi-account local data isolation và logout cleanup rõ ràng.

## P2 - Intelligence và scale

- [ ] OCR pipeline ghi `ocr_text`.
- [ ] Image labels và caption pipeline ghi `labels_text`/`ai_caption`.
- [ ] Perceptual hash để tìm near-duplicate.
- [ ] Timeline grouping theo địa điểm và sự kiện; date grouping is complete in P1.
- [ ] Shared albums và invitation model với ACL riêng.
- [ ] Multipart upload cho video lớn.
- [ ] Lifecycle policy, cold storage và quota per user.
- [ ] Background reconciliation giữa R2 inventory và PostgreSQL.
- [ ] Web admin dashboard cho cleanup, audit và failed processing.

## Definition of done

Mỗi roadmap item cần migration/API compatibility plan nếu đổi contract, automated tests tương ứng, structured logs không chứa credential/presigned URL, và tài liệu trong `/docs` được cập nhật cùng code.
