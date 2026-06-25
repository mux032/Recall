# Recall | Screenshot Memory for Android

> Search every screenshot you've ever taken using natural language. Fully offline. No cloud. No data leaves your device.

Recall automatically indexes all screenshots on your device using on-device OCR and vector embeddings, then lets you find them by describing what you remember, not by scrolling endlessly through a grid.

---

## Screenshots

<!-- TODO: Add real screenshots once the app reaches v1.0.
     Place 400×800 px PNG files in docs/screenshots/ and update the paths below.
     Recommended shots: Home grid, Search results, Detail view, Settings/model download. -->

| Home | Search | Detail | Settings |
|------|--------|--------|----------|
| _coming soon_ | _coming soon_ | _coming soon_ | _coming soon_ |

---

## Features

- **Hybrid search** — combines semantic similarity (HNSW vector index) with full-text search (FTS4) for the best of both worlds
- **On-device OCR** — ML Kit Text Recognition extracts text from every screenshot automatically
- **Vector embeddings** — `bge-small-en-v1.5` ONNX model generates 384-dimensional sentence embeddings for semantic understanding
- **Fully offline & private** — no network calls for search or indexing; the AI model is downloaded once over Wi-Fi and then never needs the internet again
- **Timeline view** — home gallery groups screenshots by Today / Yesterday / This Week / This Month
- **Filter chips** — quickly narrow to All, Recent, By App, or Summarized screenshots
- **Detail screen** — view extracted text, edit it, copy it, share the screenshot, or delete it
- **Dark / Light / System theme** — full Material 3 dynamic colour support
- **Background indexing** — `WorkManager` workers run OCR and embedding generation in the background without blocking the UI
- **Memory-aware** — automatically selects quantized (34 MB) or full FP32 (133 MB) model based on device RAM

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 1.9.22 |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture (Data / Domain / Presentation) |
| DI | Hilt 2.50 |
| Database | Room 2.6.1 (FTS4, version 5) |
| Background work | WorkManager 2.9.0 |
| OCR | ML Kit Text Recognition 16.0.0 |
| AI Embeddings | ONNX Runtime 1.16.0 (`bge-small-en-v1.5`) |
| Image loading | Coil 2.6.0 |
| Preferences | Jetpack DataStore |
| Networking | OkHttp 4.12.0 (model download only) |
| Navigation | Navigation Compose 2.7.6 |
| Min SDK | 26 (Android 8.0 Oreo) |
| Target SDK | 34 (Android 14) |

---

## Architecture

```
app/src/main/java/com/recall/app/
├── data/
│   ├── di/           Hilt modules (Database, DataStore, OCR, Repository)
│   ├── local/        Room database, DAOs, entities, migrations, UserPreferences
│   ├── nlp/          ONNX embedding generator, vector index, tokenizer, model selector
│   ├── ocr/          ML Kit OCR processor
│   ├── repository/   Repository implementations
│   ├── service/      ScreenshotContentObserver (real-time detection)
│   └── worker/       WorkManager workers (OCR, scan, model download)
├── domain/
│   ├── model/        Pure Kotlin domain models (Screenshot, ProcessingState, …)
│   ├── repository/   Repository interfaces
│   └── usecase/      Business logic (SearchScreenshotsUseCase, GetAllScreenshotsUseCase, …)
└── presentation/
    └── ui/
        ├── components/   Shared composables
        ├── detail/       Detail screen + ViewModel
        ├── home/         Home screen + ViewModel + timeline utilities
        ├── navigation/   NavGraph
        ├── permissions/  Onboarding / permission screen
        ├── search/       Search screen
        ├── settings/     Settings screen + ViewModel
        └── theme/        RecallTheme, colours, typography
```

### Screenshot indexing pipeline

```
New screenshot saved
        │
        ▼
ScreenshotContentObserver          (real-time, 1 s debounce per URI)
        │
        ▼
ScreenshotProcessingWorker         (enqueued immediately, KEEP policy)
        │
        ├─► MlKitOcrProcessor      → extract text
        └─► OnnxEmbeddingGenerator → 384-dim float vector
                │
                ▼
        Room DB (ScreenshotEntity) + FTS4 index + VectorIndexOptimized

On cold launch:  ScanExistingWorker → BackgroundOcrWorker  (catches up missed files)
Every 6 hours:   BackgroundOcrWorker  (two-pass: OCR-pending → embedding-pending)
```

### Search pipeline

```
User query
    │
    ▼
SearchScreenshotsUseCase
    ├─ async ──► EmbeddingGenerator.generate(query)
    │                └─► VectorIndexOptimized.search()   (HNSW, threshold 0.3, timeout 1.5 s)
    └─ async ──► ScreenshotDao.searchFts()               (FTS4 + JOIN)
                        │
                        ▼
               Merge: AI results first, then FTS-only, deduplicated by ID
```

---

## Getting Started

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17** — the project pins Java 17 via `.java-version`
- **Android device or emulator** running API 26+

### Clone & build

```bash
git clone https://github.com/mux032/Recall.git
cd Recall
./gradlew assembleDebug
```

Install on a connected device/emulator:

```bash
./gradlew installDebug
```

### First launch

1. Grant **storage / media images** permission when prompted.
2. The app will begin scanning existing screenshots automatically.
3. Go to **Settings → AI Model** and tap **Download** to get the embedding model (Wi-Fi + battery-not-low required). Without it, search falls back to keyword-only mode.

### Build variants

| Command | Output |
|---------|--------|
| `./gradlew assembleDebug` | Debug APK (battery constraint skipped) |
| `./gradlew assembleRelease` | Release APK (requires signing config) |
| `./gradlew clean build` | Full clean build |

---

## Development Guide

### Branching strategy

| Branch | Purpose |
|--------|---------|
| `main` | Stable, always-releasable |
| `fix/<issue-number>-<short-description>` | Bug fixes |
| `feat/<issue-number>-<short-description>` | New features |

Example: `feat/107-processing-status-banner`

### Making a change

1. **Find or open a GitHub Issue** for the work. Every branch should map to an issue.
2. **Branch off `main`:**
   ```bash
   git checkout main && git pull
   git checkout -b fix/123-your-description
   ```
3. **Make your changes.** Follow the existing layer boundaries:
   - Data concerns (DB, workers, network) → `data/`
   - Business rules → `domain/`
   - UI / ViewModel → `presentation/ui/`
4. **Add or update tests** (see [Testing](#testing) below).
5. **Open a PR** targeting `main` and link the issue with `Closes #NNN`.

### Database changes

Room uses **destructive migration** (`fallbackToDestructiveMigration`) during development. Before any production release:

- Add a proper `Migration` object in `DatabaseMigrations.kt`
- Bump `version` in `RecallDatabase.kt`
- Update the version comment in the `@Database` annotation

Current DB version: **5**

### Adding a new Worker

1. Create a `HiltWorker` subclass in `data/worker/`.
2. Tag it with `RecallApplication.INDEXING_TAG` if it performs indexing — this makes it visible to the pause/cancel controls.
3. Register the `HiltWorkerFactory` binding is already global via `RecallApplication`; no extra setup needed.

### Adding a new screen

1. Add a route object to `Screen` sealed class in `NavGraph.kt`.
2. Add a `composable(Screen.YourScreen.route)` block in `RecallNavGraph`.
3. Create `YourScreen.kt` and `YourViewModel.kt` under `presentation/ui/yourscreen/`.

---

## Testing

### Unit tests

Run all unit tests:

```bash
./gradlew test
# or for the debug variant specifically:
./gradlew :app:testDebugUnitTest
```

Key test files and what they cover:

| Test file | Coverage |
|-----------|---------|
| `SearchScreenshotsUseCaseTest` | Hybrid search merge logic, AI timeout fallback |
| `VectorIndexTest` / `VectorIndexOptimizedTest` | In-memory HNSW search, LRU eviction |
| `WordPieceTokenizerTest` | Trie-based tokenizer, edge cases |
| `OnnxEmbeddingGeneratorTest` | ONNX session lifecycle, null-model fallback |
| `BackgroundOcrWorkerConstantsTest` | Two-pass slot allocation, retry limits |
| `BackgroundOcrWorkerPass2Test` | Embedding-only retry path |
| `ModelDownloadWorkerTest` | SHA-256 verification, OkHttp streaming (MockWebServer) |
| `HomeViewModelTest` | Pagination, reactive DB count refresh |
| `ModelSelectorTest` | RAM-based model selection |
| `DeviceProfilerTest` | Memory class detection |

### Instrumented tests (requires device/emulator)

```bash
./gradlew connectedAndroidTest
```

Key instrumented tests:

| Test file | Coverage |
|-----------|---------|
| `ScreenshotDaoTest` | Room DAO operations, FTS JOIN correctness |
| `DetailScreenTest` | Compose UI — share, delete, OCR edit |
| `DarkModeThemeTest` | Theme switching |
| `ExtractedTextSectionTest` | OCR text section composable |

### Writing a new test

- **Unit test** → `app/src/test/java/com/recall/app/`  
  Use JUnit 4 + Mockito-Kotlin. For `@AndroidEntryPoint` / Android API surface, extend with Robolectric (`@RunWith(RobolectricTestRunner::class)`).
- **Instrumented test** → `app/src/androidTest/java/com/recall/app/`  
  Use Compose test rules (`createComposeRule()`) or Room in-memory DB (`Room.inMemoryDatabaseBuilder`).

---

## Permissions

| Permission | Why |
|-----------|-----|
| `READ_MEDIA_IMAGES` (API 33+) | Read screenshots from MediaStore |
| `READ_EXTERNAL_STORAGE` (API ≤ 32) | Read screenshots on older devices |
| `INTERNET` | Download the ONNX model from HuggingFace (one-time) |
| `POST_NOTIFICATIONS` | Worker progress notifications |
| `FOREGROUND_SERVICE_DATA_SYNC` | Reserved for future foreground indexing service |

---

## AI Model

Recall uses **[BAAI/bge-small-en-v1.5](https://huggingface.co/BAAI/bge-small-en-v1.5)** exported to ONNX.

| Variant | Size | Used when |
|---------|------|-----------|
| Quantized INT8 | ~34 MB | Device RAM < 4 GB |
| Full FP32 | ~133 MB | Device RAM ≥ 4 GB |

The model is **not bundled** in the APK. It is downloaded once from HuggingFace via Settings when the user is on Wi-Fi with sufficient battery. SHA-256 integrity is verified after download.

---

## Contributing

1. Check the [open issues](https://github.com/mux032/Recall/issues) — issues are labelled by `phase`, `layer`, `type`, and `priority`.
2. Comment on the issue you want to work on so we don't duplicate effort.
3. Follow the [Making a change](#making-a-change) workflow above.
4. Keep PRs focused: one issue per PR.
5. All new code must include tests.

### Issue labels

| Label | Meaning |
|-------|---------|
| `layer: data` | Data layer (DB, workers, NLP) |
| `layer: domain` | Domain models and use cases |
| `layer: presentation` | UI and ViewModels |
| `type: feature` | New functionality |
| `type: bug` | Something broken |
| `type: refactor` | Code quality, no behaviour change |
| `priority: high` | Blocking or critical |
| `priority: medium` | Important but not blocking |
| `phase: N` | Development phase grouping |

---

## Roadmap

Active development is tracked in the [Recall Roadmap](https://github.com/mux032/Recall/projects/1) project board under milestone **v1.0 — AI Search**.

Current phase: **Phase 11 — UI & UX Improvements**

---

## License

Copyright (C) 2026 mux032

This program is free software: you can redistribute it and/or modify it under
the terms of the **GNU General Public License v3.0** as published by the Free
Software Foundation, either version 3 of the License, or (at your option) any
later version.

This program is distributed in the hope that it will be useful, but **without
any warranty**; without even the implied warranty of merchantability or fitness
for a particular purpose. See the [LICENSE](LICENSE) file for the full terms.

---

> **Privacy note:** Recall processes all data on-device. No screenshots, OCR text, or embeddings are ever transmitted to any server.
