---
status: accepted
date: 2024-06-01
---
# ADR-0005: Embedding Stored as BLOB on ScreenshotEntity

## Context and Problem Statement

Recall stores a 384-dimensional FloatArray embedding for each indexed screenshot. The original design placed embeddings in a separate `embeddings` table (one-to-one relationship with `screenshots`). During implementation, the trade-off between schema normalization and query simplicity was re-evaluated.

## Decision

Store `embeddingByteArray` as a `BLOB` column directly on `ScreenshotEntity` (the `screenshots` table) rather than a separate `embeddings` table.

## Rationale

- Each screenshot has exactly one embedding (or none); there is no one-to-many relationship that would justify a separate table.
- Denormalized storage eliminates a JOIN when loading all embeddings for the vector index bootstrap — a critical hot path executed on every cold start.
- `ScreenshotDao.getAllEmbeddings()` returns a single flat query result; no correlated subquery or JOIN needed.
- An embedding is meaningless without its corresponding screenshot metadata; keeping them co-located in one row makes transactional updates (OCR text → embedding → state change) atomic within a single Room `update()`.
- The `embeddingRetryCount` column (added in DB v5) can track per-row embedding state without a separate tracking table.
- BLOB size is fixed at 384 × 4 = 1,536 bytes per row — SQLite handles this efficiently as inline or overflow storage.

## Consequences

### Positive
- Simpler schema with fewer tables and no JOIN for the hot path.
- Atomic update of `ocrText + embeddingByteArray + processingState` in a single Room transaction.
- Easier to reason about row completeness — one row tells the full processing story of one screenshot.

### Negative / Trade-offs
- Denormalization means a wider `screenshots` table; SELECT * queries pull the BLOB even when it is not needed (mitigated by using projection-specific DAO queries).
- If a future multi-modal embedding strategy requires multiple embeddings per screenshot, this schema requires migration.
- The `embeddingByteArray` BLOB is not visible or queryable via standard SQL predicates (it is treated as opaque bytes); any corruption is only detectable at the application layer.
