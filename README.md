# Recall - AI-Powered Screenshot Memory

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Platform](https://img.shields.io/badge/platform-Android-green)]()
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.22-purple)]()
[![API](https://img.shields.io/badge/API-26%2B-blue)]()

**Your AI-powered second brain for screenshots** - Search your screenshots using natural language with 100% on-device processing for complete privacy.

---

## 🎯 About

Screenshot Recall uses on-device AI (OCR, vision models) to understand your screenshots' content, enabling instant semantic search without compromising privacy. All processing happens locally - nothing is uploaded to the cloud.

---

## ✨ Features

### Currently Implemented

- **Smart Search** - Integrated search bar with dynamic suggestions and real-time results
- **Screenshot Detection** - Automatic detection and background processing of new screenshots
- **OCR Processing** - ML Kit text extraction from screenshots
- **Timeline View** - Date-grouped browsing (Today, Yesterday, Last Week)
- **Smart Categories** - AI-powered categorization (Shopping, Travel, Code, Food, etc.)
- **Screenshot Detail** - View extracted text, tags, and metadata
- **Material Design 3** - Modern UI with light/dark theme support

### Coming Soon

- **Vision Model** - AI-generated captions for screenshots (Phase 6)
- **Semantic Search** - Vector similarity search with embeddings (Phase 7)
- **Advanced Categories** - Smart tagging and filtering (Phase 10)

---

## 📱 Screenshots

```
Bottom Navigation: Home | Categories | Settings

Home Screen:
├─ Search Bar (always visible)
├─ Dynamic Suggested Searches
├─ Smart Categories (horizontal chips)
└─ Timeline (date-grouped screenshots)

Screenshot Detail:
├─ Full image (fit-to-screen)
├─ Extracted OCR text (scrollable)
├─ AI summary (placeholder)
├─ Tags and metadata
└─ Share & Delete buttons
```

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 1.9.22 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt 2.50 |
| Database | Room 2.6.1 |
| Navigation | Navigation Component 2.7.6 |
| Image Loading | Glide 4.16.0 |
| OCR | ML Kit 16.0.0 |
| Background Work | WorkManager 2.9.0 |
| UI | Material Design 3 |

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio:** Hedgehog (2023.1.1) or later
- **JDK:** 17 or higher
- **Android SDK:** API 34 (Android 14)
- **Minimum Android:** API 26 (Android 8.0)

### Installation & Build

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd recall
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Open the project folder
   - Wait for Gradle sync to complete

3. **Build and Run**
   - Connect an Android device or start an emulator (API 26+)
   - Click the Run button (▶️) or press `Shift+F10`
   - App will install and launch

### Build Commands

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run lint checks
./gradlew lint
```

### Build Output

- **Debug APK:** `app/build/outputs/apk/debug/app-debug.apk`
- **Build Time:** ~30-40s (clean), ~10-15s (incremental)

---

## 📦 Project Structure

```
recall/
├── app/src/main/java/com/recall/app/
│   ├── data/           # Data layer (Database, Repositories, Workers)
│   ├── domain/         # Domain models and interfaces
│   ├── di/             # Hilt dependency injection modules
│   └── presentation/   # UI layer (Fragments, ViewModels)
├── app/src/main/res/   # Resources (layouts, drawables, values)
├── app/src/test/       # Unit tests
├── README.md           # This file
└── TODO.md             # Implementation roadmap
```

---

## 🧪 Testing

```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.recall.app.data.local.dao.ScreenshotDaoTest"

# Generate coverage report
./gradlew jacocoTestReport
```

**Test Coverage:** 70 tests, ~53 passing (76%)

---

## 📊 Current Status

### Progress

- ✅ Phase 1-3: Foundation & UI Shell
- ✅ Phase 4: Screenshot Detection
- ✅ Phase 5: OCR Processing
- ⏳ Phase 6: Vision Model Integration (Next)
- ⏳ Phase 7: Embedding & Vector Search

---

## 📱 Requirements

### Minimum Requirements

- **Android:** 8.0 (API 26) or higher
- **RAM:** 4GB recommended
- **Storage:** ~50MB for app + database

### Recommended

- **Android:** 11.0 (API 30) or higher
- **RAM:** 6GB+ for better performance
- **Storage:** 100MB+ for indexing

### Permissions

- **Android 13+:** `READ_MEDIA_IMAGES`, `POST_NOTIFICATIONS`
- **Android 10-12:** No permissions required (uses MediaStore)
- **Android 9 and below:** `READ_EXTERNAL_STORAGE`

---

## 🔍 How It Works

### Screenshot Processing Flow

1. **Detection** - MediaStore ContentObserver detects new screenshot
2. **Queue** - WorkManager schedules background processing
3. **OCR** - ML Kit extracts text from image
4. **Store** - Results saved to Room database
5. **Search** - Text immediately searchable

### Search Flow

1. **Type** - User types in search bar
2. **Debounce** - 200ms delay for performance
3. **Query** - Search in OCR text and summaries
4. **Display** - Results shown in grid

---

## 📄 Documentation

| File | Description |
|------|-------------|
| `README.md` | Project overview (this file) |
| `status.md` | Current project status and metrics |
| `TODO.md` | Implementation roadmap |
| `guide/` | Product documentation (PRD, TRD, Architecture) |

---

## ⚠️ Known Issues

1. **Share Button Error** - FileProvider not configured yet
   - Workaround: Use system share from file manager

2. **AI Summary Placeholder** - Vision model not integrated yet
   - Coming in Phase 6

---

## 🗺️ Roadmap

- **Phase 6 (Next):** Vision model integration (MobileCLIP)
- **Phase 7:** Embedding generation and semantic search
- **Phase 8-10:** UI polish, advanced categories
- **Phase 11-14:** Performance, testing, release prep

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/name`)
3. Commit changes (`git commit -m 'Add feature'`)
4. Push and open a Pull Request

Please ensure tests pass and follow Kotlin conventions.

---

## 📄 License

MIT License - see [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- ML Kit for OCR
- Material Design for UI components
- Android Jetpack libraries

---

**Built with ❤️ using Kotlin and Material Design 3**

**Last Updated:** March 16, 2026
