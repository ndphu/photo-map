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
- [ ] Backend integration tests với PostgreSQL và S3-compatible test storage.
- [ ] Structured observability: request ID, upload session ID, latency/error metrics.
- [ ] Rate limiting cho auth, presign và maintenance endpoints.
- [ ] Production secret management, TLS-only API và credential rotation runbook.

## P1 - Gallery completeness

- [ ] Pull-to-refresh và network-aware UI state chuẩn hóa.
- [ ] Paging 3 cho gallery/search/album assets.
- [ ] Album UI và search UI trên Android.
- [ ] Archive, restore, hard-delete confirmation và trash retention policy.
- [ ] Server-side derivative rebuild queue cho thumbnail/preview/poster bị thiếu.
- [ ] Video playback với signed read URL và range request validation.
- [ ] Location enrichment thành country/region/city/place name.
- [ ] Checksum cache cục bộ để tránh hash lại file lớn.
- [ ] Multi-account local data isolation và logout cleanup rõ ràng.

## P2 - Intelligence và scale

- [ ] OCR pipeline ghi `ocr_text`.
- [ ] Image labels và caption pipeline ghi `labels_text`/`ai_caption`.
- [ ] Perceptual hash để tìm near-duplicate.
- [ ] Timeline grouping theo ngày, địa điểm và sự kiện.
- [ ] Shared albums và invitation model với ACL riêng.
- [ ] Multipart upload cho video lớn.
- [ ] Lifecycle policy, cold storage và quota per user.
- [ ] Background reconciliation giữa R2 inventory và PostgreSQL.
- [ ] Web admin dashboard cho cleanup, audit và failed processing.

## Definition of done

Mỗi roadmap item cần migration/API compatibility plan nếu đổi contract, automated tests tương ứng, structured logs không chứa credential/presigned URL, và tài liệu trong `/docs` được cập nhật cùng code.
