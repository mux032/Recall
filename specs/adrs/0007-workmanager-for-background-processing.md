---
status: accepted
date: 2024-06-01
---
# ADR-0007: WorkManager Over Foreground Service for Background Processing

## Context and Problem Statement

Recall must process screenshots in the background: scanning the MediaStore for new files, running ML Kit OCR, and generating ONNX embeddings. These tasks can be long-running (seconds to minutes for a large initial scan). The two primary options were a persistent Foreground Service (with user-visible notification) or WorkManager (system-managed background execution).

## Decision

Use WorkManager for all background processing (scanning, OCR, embedding, model download). A Foreground Service permission (`FOREGROUND_SERVICE_DATA_SYNC`) is declared in the manifest but is not actively used in v1.0.

## Rationale

- WorkManager is the Android-recommended solution for deferrable, guaranteed background work that must survive process death.
- WorkManager's constraint system (`setRequiresBatteryNotLow`, `setRequiresStorageNotLow`) enforces device-health conditions without manual polling.
- Work request persistence in WorkManager's internal DB means a partially complete indexing job survives app restart or OS-initiated process kill.
- A Foreground Service requires a persistent user-visible notification — acceptable for a one-time bulk import but disruptive for ongoing incremental indexing that runs every 6 hours.
- The `BackgroundOcrWorker` self-chaining pattern (process a batch, re-enqueue if more work remains) keeps individual work units short and allows the OS to reschedule fairly.
- `INDEXING_TAG` across all workers enables atomic cancellation with a single `WorkManager.cancelAllWorkByTag()` call.
- Battery impact target (< 1%/day) is better served by WorkManager's system-managed scheduling than a long-running service.

## Consequences

### Positive
- System-managed scheduling respects Doze mode, battery optimization, and storage constraints automatically.
- Work survives process death with no additional persistence code.
- No persistent notification required in the common case.
- `ExistingPeriodicWorkPolicy.KEEP` and `ExistingWorkPolicy.KEEP` prevent duplicate enqueueing without manual deduplication logic.

### Negative / Trade-offs
- WorkManager has a minimum scheduling interval of 15 minutes for periodic work; the 6-hour interval is well above this, but immediate re-triggering for testing requires explicit `TestDriver` or debug tooling.
- WorkManager execution timing is approximate and subject to OS scheduling constraints — not suitable for latency-sensitive real-time tasks (handled by `ScreenshotContentObserver` + expedited `ScreenshotProcessingWorker` for real-time new screenshots).
- The `FOREGROUND_SERVICE_DATA_SYNC` permission in the manifest is unused in v1.0 — it will need to be activated and a notification channel created before the foreground service path is used.
