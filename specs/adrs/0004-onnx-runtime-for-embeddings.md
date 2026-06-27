---
status: accepted
date: 2024-06-01
---
# ADR-0004: ONNX Runtime for On-Device Embedding Inference

## Context and Problem Statement

Recall generates 384-dimensional sentence embeddings from OCR text entirely on-device to maintain its privacy-first guarantee. The embedding model (bge-small-en-v1.5) must run as an inference session in the Android app process. Candidate inference runtimes evaluated: ONNX Runtime (Microsoft), TensorFlow Lite, and MediaPipe.

## Decision

Use Microsoft ONNX Runtime for Android (`com.microsoft.onnxruntime:onnxruntime-android:1.16.0`) plus `onnxruntime-extensions-android:0.10.0` for tokenizer operations.

## Rationale

- ONNX is the native export format for HuggingFace transformer models (bge-small-en-v1.5 is published as `model.onnx`); no additional conversion step required.
- ONNX Runtime for Android has mature CPU and optional NNAPI backends, with well-documented session options.
- TFLite requires a separate model conversion pipeline (ONNX → TFLite); this adds maintenance cost and risks precision loss during quantization.
- MediaPipe is optimised for vision/audio tasks; its text embedding pipeline does not cover arbitrary transformer sentence encoders.
- `onnxruntime-extensions` provides pre/post-processing operators (e.g. tokenizer ops) that reduce the need for a fully custom tokenizer, though Recall uses its own `WordPieceTokenizer` for maximum control.
- ONNX Runtime is Apache 2.0 licensed — no GPL or commercial license concerns.

## Consequences

### Positive
- Direct use of HuggingFace-published ONNX model; no conversion pipeline.
- Apache 2.0 license compatible with commercial distribution.
- NNAPI acceleration available as a future opt-in without changing the inference API.
- `OrtSession` is reused across inference calls — no per-query session initialization cost.

### Negative / Trade-offs
- Native `.so` adds ~15 MB to APK size (armeabi-v7a + arm64-v8a).
- NNAPI is disabled by default due to vendor fragmentation; CPU-only inference in v1.0.
- `OrtSession` must be explicitly closed on app termination to release native resources — handled in `RecallApplication.onTerminate()`.
