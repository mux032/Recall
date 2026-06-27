---
status: accepted
date: 2024-06-01
---
# ADR-0006: bge-small-en-v1.5 Over all-MiniLM-L6-v2

## Context and Problem Statement

The original design chose `all-MiniLM-L6-v2` as the embedding model for Recall's semantic search. During implementation, a model evaluation was conducted against the MTEB (Massive Text Embedding Benchmark) leaderboard to identify a higher-accuracy alternative that maintains the same output dimensionality (384 dims) so that no changes to `VectorIndexOptimized`, `OnnxEmbeddingGenerator`, or the embedding storage schema were required.

## Decision

Replace `all-MiniLM-L6-v2` with `BAAI/bge-small-en-v1.5` as the production embedding model. Both FP32 (133 MB) and INT8-quantized (34 MB, Xenova conversion) variants are provided.

## Rationale

- bge-small-en-v1.5 scores **62.17 MTEB average** vs. all-MiniLM-L6-v2 at 56.26 — a ~10% improvement in retrieval quality with the same 384-dimensional output.
- The identical output dimension means zero changes to downstream components (vector index, BLOB schema, cosine similarity logic).
- HuggingFace publishes official ONNX exports for both variants (`BAAI/bge-small-en-v1.5` and `Xenova/bge-small-en-v1.5`), simplifying the download pipeline.
- The Xenova INT8 quantization reduces model size from 133 MB to 34 MB with only a minor accuracy reduction (~59.x MTEB) — acceptable for the LOW/MEDIUM RAM tier.
- The same `vocab.txt` (bundled in `assets/`) works for both variants, as both use the same WordPiece tokenizer vocabulary.

## Consequences

### Positive
- Measurably better semantic search accuracy for the same inference infrastructure cost.
- No schema migration required — same 384 dims, same BLOB layout.
- Tiered model delivery (FP32 for high-RAM devices, INT8 for lower-RAM) provides a good accuracy/memory trade-off across the device fleet.

### Negative / Trade-offs
- FP32 model is 133 MB vs. ~90 MB for all-MiniLM-L6-v2 — larger one-time download on high-RAM devices.
- SHA-256 checksums must be updated if HuggingFace re-publishes the model weights (e.g. after a bug fix release).
- Users who downloaded the app with the old model will need to re-download; there is no in-place model upgrade path in v1.0.
