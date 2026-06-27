---
status: accepted
date: 2024-06-01
---
# ADR-0002: FTS4 Chosen Over FTS5

## Context and Problem Statement

Recall uses Room full-text search to provide fast keyword matching over OCR-extracted text. Room supports both FTS4 and FTS5 virtual table backends. FTS5 is the more modern implementation with better ranking (`bm25()`) and incremental merge; however, availability differs across Android API levels and SQLite versions shipped by device manufacturers.

## Decision

Use FTS4 (`@Fts4(contentEntity = ScreenshotEntity::class)`) for the `screenshots_fts` virtual table.

## Rationale

- FTS4 is available on all SQLite versions shipped since Android API 16; FTS5 requires SQLite ≥ 3.9.0, which is not guaranteed on API 26–28 devices in the wild.
- `contentEntity` mode in Room means FTS4 is kept in sync with the `screenshots` table automatically.
- Recall's keyword search requirements are simple `MATCH` queries; the improved BM25 ranking of FTS5 provides no tangible benefit over FTS4's ranking given that AI semantic results take priority and FTS is a supplementary fallback.
- Avoiding FTS5 eliminates a category of "SQLite version not found" crashes that have historically appeared in production on OEM-modified Android builds.

## Consequences

### Positive
- Maximally compatible across the API 26+ target range.
- No runtime crashes due to missing FTS5 SQLite module.
- Content entity sync handled automatically by Room.

### Negative / Trade-offs
- No `bm25()` relevance scoring — FTS4 uses a simpler ranking model.
- FTS4 does not support `highlight()` or `snippet()` auxiliary functions (FTS5 features).
- If future requirements need rich relevance ranking, migration to FTS5 requires a schema migration.
