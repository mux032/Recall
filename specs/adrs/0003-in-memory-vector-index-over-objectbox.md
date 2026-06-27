---
status: accepted
date: 2024-06-01
---
# ADR-0003: In-Memory Vector Index Over ObjectBox

## Context and Problem Statement

Recall requires fast approximate nearest-neighbour (ANN) search over 384-dimensional embedding vectors. Two primary candidates were evaluated: an in-memory flat index implemented in Kotlin (`VectorIndexOptimized`) and ObjectBox with its built-in HNSW vector search. A third option — SQLite `vec0` extension — was considered but rejected due to missing support in Room.

## Decision

Use a custom in-memory flat vector index (`VectorIndexOptimized`) backed by a `LinkedHashMap<String, FloatArray>` with LRU eviction, rather than introducing ObjectBox as an additional dependency.

## Rationale

- ObjectBox is a commercially licensed database (BSL 1.1 for versions > 3.x); introducing it would require legal review and creates a vendor dependency for a core feature.
- The in-memory approach has zero cold-start disk I/O cost for search once loaded — all queries are pure CPU.
- At v1.0 scale (expected p99 library size < 50,000 screenshots ≈ 75 MB), a parallel chunked cosine similarity scan is fast enough to meet the 1500 ms AI timeout budget.
- The LRU `vectorCacheLimit` (default 50,000 entries) with memory pressure eviction keeps RAM within the 200 MB target.
- No additional NDK or native library compilation required; the index is pure Kotlin/JVM.
- Embeddings are already persisted as BLOBs in Room; the in-memory index is a projection of that data and can be rebuilt on cold start.

## Consequences

### Positive
- No third-party database license concerns.
- Simple, auditable Kotlin implementation with no native dependencies.
- LRU semantics allow the index to function on constrained-RAM devices by evicting least-recently-used vectors.

### Negative / Trade-offs
- O(n) linear scan (parallelised) rather than true O(log n) HNSW graph traversal; performance degrades at very large libraries (>100k vectors).
- Vector index must be rebuilt from Room BLOBs on every cold start — rebuild time scales with library size.
- No persistence of the index itself; if Room BLOB is corrupted, the vector for that screenshot is permanently lost until re-indexed.
- True ANN graph index (HNSW/FAISS) is marked **[PLANNED]** for v2.0 if performance benchmarks show degradation.
