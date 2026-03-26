# GitHub Issues for Recall App - Code Review Follow-up

## Issue #1: [CRITICAL] O(n²) Brute-Force Vector Search Doesn't Scale

**Labels:** `bug`, `performance`, `critical`, `search`

**Description:**
The current vector search implementation in `VectorIndex.kt` uses brute-force iteration through all vectors, computing cosine similarity for each one. This O(n²) approach will cause search performance to degrade linearly as the screenshot collection grows.

**Current Implementation:**
```kotlin
fun search(queryVector: FloatArray, limit: Int = 10): List<Pair<String, Float>> {
    for ((id, vector) in index) {  // O(n) iteration
        val score = cosineSimilarity(queryVector, vector)  // O(d) where d=384
        scores.add(Pair(id, score))
    }
    scores.sortByDescending { it.second }  // O(n log n) sort
    return scores.take(limit)
}
```

**Performance Impact:**
- 100 screenshots: ~5-10ms ✅ Acceptable
- 1,000 screenshots: ~50-100ms ⚠️ Noticeable
- 10,000 screenshots: ~500ms+ ❌ Unacceptable for UI search

**Acceptance Criteria:**
- [ ] Search with 1,000 vectors completes in under 100ms
- [ ] Search with 10,000 vectors completes in under 200ms
- [ ] Implement caching for repeated queries
- [ ] Add performance tests to verify search latency

**Suggested Fix:**

1. **Immediate:** Add LRU cache for repeated queries
```kotlin
private val queryCache = LruCache<String, List<Pair<String, Float>>>(50)

fun search(queryVector: FloatArray, limit: Int = 10): List<Pair<String, Float>> {
    val cacheKey = queryVector.joinToString(",") { it.toString() }
    val cached = queryCache.get(cacheKey)
    if (cached != null) return cached
    
    // ... existing search logic ...
    
    queryCache.put(cacheKey, results)
    return results
}
```

2. **Long-term:** Implement approximate nearest neighbor (ANN) index using FAISS or LSH

**Files to Modify:**
- `app/src/main/java/com/recall/app/data/nlp/VectorIndex.kt`

**References:**
- Code Review Section: Performance & Efficiency - Issue #1
- Related: Issue #6 (Embedding Generation Caching)

---

## Issue #2: [CRITICAL] Memory Leak Risk: ONNX Session Never Closed

**Labels:** `bug`, `memory-leak`, `critical`, `onnx`

**Description:**
The `OrtSession` in `OnnxEmbeddingGenerator` holds native memory resources that are never released. While the app lifecycle may eventually clean this up, it's a potential memory leak that can cause OOM crashes over time.

**Current Implementation:**
```kotlin
init {
    val bytes = context.assets.open("model.onnx").readBytes()
    val sessionOptions = OrtSession.SessionOptions().apply {
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        setIntraOpNumThreads(2)
    }
    session = env.createSession(bytes, sessionOptions)  // Never closed!
}
```

**Impact:**
- Native memory leaks are not caught by GC
- Can cause OOM crashes over extended usage
- Particularly problematic for long-running app sessions

**Acceptance Criteria:**
- [ ] Implement `Closeable` interface in `OnnxEmbeddingGenerator`
- [ ] Close ONNX session and environment in `close()` method
- [ ] Call `close()` in `RecallApplication.onTerminate()` or via lifecycle-aware component
- [ ] Verify no native memory leaks with profiling tools

**Suggested Fix:**
```kotlin
@Singleton
class OnnxEmbeddingGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) : EmbeddingGenerator, Closeable {

    // ... existing code ...

    override fun close() {
        try {
            session.close()
            env.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close ONNX resources", e)
        }
    }
}
```

**Files to Modify:**
- `app/src/main/java/com/recall/app/data/nlp/OnnxEmbeddingGenerator.kt`
- `app/src/main/java/com/recall/app/RecallApplication.kt`

**References:**
- Code Review Section: Performance & Efficiency - Issue #2

---

## Issue #3: [CRITICAL] No Pagination: Loading All Screenshots Into Memory

**Labels:** `bug`, `performance`, `critical`, `database`, `oom`

**Description:**
The `getAllScreenshots()` query loads EVERY screenshot into memory at once. For users with 1,000+ screenshots, this causes excessive memory usage and potential OOM crashes.

**Current Implementation:**
```kotlin
@Query("SELECT * FROM screenshots ORDER BY dateCreated DESC")
fun getAllScreenshots(): Flow<List<ScreenshotEntity>>
```

**Memory Impact:**
- Each `ScreenshotEntity` with embedding: ~2KB
- 1,000 screenshots = ~2MB just for entities
- Plus bitmap caching in UI = potential OOM on low-RAM devices

**Acceptance Criteria:**
- [ ] Implement pagination with LIMIT/OFFSET or keyset pagination
- [ ] Memory usage stays under 100MB for 1,000 screenshots
- [ ] UI remains responsive with 10,000+ screenshots in database
- [ ] Add memory profiling tests

**Suggested Fix:**
```kotlin
// Option 1: Simple pagination
@Query("SELECT * FROM screenshots ORDER BY dateCreated DESC LIMIT :limit OFFSET :offset")
suspend fun getScreenshotsPaged(offset: Int, limit: Int = 50): List<ScreenshotEntity>

// Option 2: Paging 3 library with LoadState support
@Query("SELECT * FROM screenshots ORDER BY dateCreated DESC")
fun getAllScreenshotsPaging(): PagingSource<Int, ScreenshotEntity>
```

**Files to Modify:**
- `app/src/main/java/com/recall/app/data/local/dao/ScreenshotDao.kt`
- `app/src/main/java/com/recall/app/presentation/ui/home/HomeViewModel.kt`
- `app/src/main/java/com/recall/app/presentation/ui/home/HomeScreen.kt`

**References:**
- Code Review Section: Performance & Efficiency - Issue #5

---

## Issue #4: [HIGH] Unnecessary Re-renders: HomeScreen Recomposition Bomb

**Labels:** `performance`, `compose`, `high-priority`, `ui`

**Description:**
The timeline transformation in `HomeScreen.kt` runs every time the `screenshots` StateFlow emits, even for single item changes. This causes unnecessary recompositions, wasting CPU cycles and draining battery.

**Acceptance Criteria:**
- [ ] Move timeline transformation to ViewModel
- [ ] Use `derivedStateOf` or `StateFlow.map` with proper caching
- [ ] Recomposition only occurs for changed items
- [ ] Add recomposition counting tests

**Suggested Fix:**
```kotlin
// In HomeViewModel.kt
val timelineSections: StateFlow<List<TimelineSection>> = screenshots
    .map { list -> list.toTimelineSections() }  // Transform in ViewModel
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

// Extension function
fun List<Screenshot>.toTimelineSections(): List<TimelineSection> {
    // ... transformation logic ...
}
```

**Files to Modify:**
- `app/src/main/java/com/recall/app/presentation/ui/home/HomeViewModel.kt`
- `app/src/main/java/com/recall/app/presentation/ui/home/HomeScreen.kt`

**References:**
- Code Review Section: Performance & Efficiency - Issue #3

---

## Issue #5: [HIGH] Compose LazyColumn Item Keys Not Stable

**Labels:** `bug`, `compose`, `high-priority`, `ui`

**Description:**
Using `rowIndex` as part of the item key in `HomeScreen.kt` means every item key changes when the list is modified. This forces Compose to recreate all items instead of just inserting the new one.

**Acceptance Criteria:**
- [ ] Use stable keys based on screenshot IDs
- [ ] Verify items maintain state when list is modified
- [ ] Add Compose stability tests

**Suggested Fix:**
```kotlin
// Better: Use the first screenshot ID in each row as the key
screenshotRows.forEach { rowScreenshots ->
    val firstScreenshotId = rowScreenshots.firstOrNull()?.id ?: return@forEach
    item(key = "row-$firstScreenshotId") {
        Row(...) {
            rowScreenshots.forEach { screenshot ->
                ScreenshotItem(
                    screenshot = screenshot,
                    onClick = { onScreenshotClick(screenshot.id) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
```

**Files to Modify:**
- `app/src/main/java/com/recall/app/presentation/ui/home/HomeScreen.kt`

**References:**
- Code Review Section: Performance & Efficiency - Issue #8

---

## Issue #6: [HIGH] Missing Caching for Repeated Embedding Generation

**Labels:** `performance`, `caching`, `high-priority`, `onnx`

**Description:**
Every call to `generate(text)` runs the full ONNX inference pipeline, even for identical text. If the same screenshot is searched multiple times, embeddings are regenerated each time.

**Impact:**
- ONNX inference is expensive (~50-100ms per call)
- Redundant computations waste CPU and battery
- Search feels slower than necessary

**Acceptance Criteria:**
- [ ] Add LRU cache for text → embedding mappings
- [ ] Cache hit returns in under 1ms
- [ ] Cache size limited to 100 entries
- [ ] Add cache hit/miss metrics

**Suggested Fix:**
```kotlin
private val embeddingCache = LruCache<String, FloatArray>(100)

override suspend fun generate(text: String): FloatArray? = withContext(Dispatchers.Default) {
    if (text.isBlank()) return@withContext null
    
    // Check cache first
    val cached = embeddingCache.get(text)
    if (cached != null) return@withContext cached
    
    // ... existing ONNX inference logic ...
    
    val result = l2Normalize(pooledOutput)
    embeddingCache.put(text, result)  // Cache the result
    return@withContext result
}
```

**Files to Modify:**
- `app/src/main/java/com/recall/app/data/nlp/OnnxEmbeddingGenerator.kt`

**References:**
- Code Review Section: Performance & Efficiency - Issue #7
- Related: Issue #1 (Vector Search Caching)

---

## Issue #7: [MEDIUM] O(n × m) Nested Loop in Search UseCase

**Labels:** `performance`, `search`, `medium-priority`

**Description:**
The search result sorting uses `vectorMatches.mapNotNull { screenshots.find { ... } }`, creating an O(n × m) nested loop where n = matches and m = fetched screenshots.

**Acceptance Criteria:**
- [ ] Convert to map for O(1) lookup
- [ ] Search result sorting completes in under 10ms
- [ ] Add performance benchmark

**Suggested Fix:**
```kotlin
val screenshotsById = screenshotRepository.getScreenshotsByIds(idsToFetch)
    .associateBy { it.id }  // O(m) to build map

val sortedScreenshots = vectorMatches.mapNotNull { (id, score) ->
    screenshotsById[id]  // O(1) lookup
}
```

**Files to Modify:**
- `app/src/main/java/com/recall/app/domain/usecase/SearchScreenshotsUseCase.kt`

**References:**
- Code Review Section: Performance & Efficiency - Issue #4

---

## Issue #8: [MEDIUM] Inefficient Tokenization: Longest-Match Search is O(n²)

**Labels:** `performance`, `nlp`, `medium-priority`, `tokenizer`

**Description:**
The WordPiece tokenizer tries every possible substring length from longest to shortest for each word. For a 20-character word, that's 20 HashMap lookups in the worst case.

**Acceptance Criteria:**
- [ ] Implement Trie-based tokenization for O(m) complexity
- [ ] Tokenization of 100 words completes in under 50ms
- [ ] Add tokenization performance tests

**Suggested Fix:**
Implement a Trie (prefix tree) for O(m) tokenization instead of O(n²) substring search.

**Files to Modify:**
- `app/src/main/java/com/recall/app/data/nlp/WordPieceTokenizer.kt`

**References:**
- Code Review Section: Performance & Efficiency - Issue #6

---

## Issue #9: [MEDIUM] No Debouncing on MediaStore Scans

**Labels:** `bug`, `performance`, `medium-priority`, `content-observer`

**Description:**
`ContentObserver.onChange()` can fire multiple times rapidly when a file is created. Without debouncing, this could trigger multiple `ScreenshotProcessingWorker` instances.

**Acceptance Criteria:**
- [ ] Implement 1-second debouncing in ContentObserver
- [ ] Only one worker triggered per screenshot
- [ ] Add test for rapid onChange events

**Suggested Fix:**
```kotlin
class ScreenshotContentObserver @Inject constructor(
    private val workManager: WorkManager
) : ContentObserver(Handler(Looper.getMainLooper())) {
    
    private val debounceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var debounceJob: Job? = null
    
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        debounceJob?.cancel()
        debounceJob = debounceScope.launch {
            delay(1000)  // 1 second debounce
            // Enqueue worker
        }
    }
}
```

**Files to Modify:**
- `app/src/main/java/com/recall/app/data/service/ScreenshotContentObserver.kt`

**References:**
- Code Review Section: Performance & Efficiency - Issue #9

---

## Issue #10: [LOW] Magic Numbers in Worker Delays

**Labels:** `code-quality`, `low-priority`, `refactoring`

**Description:**
Hardcoded delay values (500ms, 2000ms) and batch sizes (5) in `BackgroundOcrWorker.kt` should be extracted to named constants with documentation.

**Acceptance Criteria:**
- [ ] Extract all magic numbers to named constants
- [ ] Add KDoc explaining rationale for each value
- [ ] Make configurable via BuildConfig or remote config

**Suggested Fix:**
```kotlin
companion object {
    /// Number of items to process before taking a short delay
    private const val ITEMS_PER_SHORT_DELAY = 3
    
    /// Short delay between item batches (milliseconds)
    private const val DELAY_BETWEEN_ITEMS_MS = 500L
    
    /// Longer delay between major batches to prevent overheating
    private const val DELAY_BETWEEN_BATCHES_MS = 2000L
    
    /// Maximum screenshots to process per worker run
    const val MAX_SCREENSHOTS_PER_RUN = 20
}
```

**Files to Modify:**
- `app/src/main/java/com/recall/app/data/worker/BackgroundOcrWorker.kt`

**References:**
- Code Review Section: Performance & Efficiency - Issue #11

---

## Issue #11: [LOW] Improve Accessibility Content Descriptions

**Labels:** `accessibility`, `low-priority`, `ui`

**Description:**
Content descriptions in AsyncImage and other UI elements could be more descriptive for accessibility purposes.

**Acceptance Criteria:**
- [ ] Use meaningful descriptions including date and source app
- [ ] All images have proper content descriptions
- [ ] Test with TalkBack enabled

**Suggested Fix:**
```kotlin
contentDescription = "Screenshot captured on ${formatDate(screenshot.dateCreated)} from ${screenshot.appName}"
```

**Files to Modify:**
- `app/src/main/java/com/recall/app/presentation/ui/home/HomeScreen.kt`
- `app/src/main/java/com/recall/app/presentation/ui/detail/DetailScreen.kt`

**References:**
- Code Review Section: Performance & Efficiency - Issue #12

---

## Issue #12: [LOW] Use Room TypeConverter for ProcessingState

**Labels:** `code-quality`, `low-priority`, `refactoring`, `room`

**Description:**
Manual conversion between String and ProcessingState in entities is error-prone. Using Room TypeConverter would be cleaner and more type-safe.

**Acceptance Criteria:**
- [ ] Create TypeConverter for ProcessingState
- [ ] Update entity to use ProcessingState enum directly
- [ ] Add migration if needed
- [ ] All existing code continues to work

**Suggested Fix:**
```kotlin
class Converters {
    @TypeConverter
    fun fromProcessingState(value: ProcessingState): String = value.value
    
    @TypeConverter
    fun toProcessingState(value: String): ProcessingState = ProcessingState.fromValue(value)
}

// Then in entity:
val processingState: ProcessingState  // Direct enum storage
```

**Files to Modify:**
- `app/src/main/java/com/recall/app/data/local/Converters.kt` (NEW)
- `app/src/main/java/com/recall/app/data/local/entity/ScreenshotEntity.kt`
- `app/src/main/java/com/recall/app/domain/model/Screenshot.kt`

**References:**
- Code Review Section: Performance & Efficiency - Issue #14

---

## Issue #13: [LOW] Add Retry Logic for Vector Index Bootstrap Failure

**Labels:** `reliability`, `low-priority`, `vector-index`

**Description:**
The vector index bootstrap has no retry mechanism if it fails. A transient error during bootstrap would leave the search functionality broken until the next app restart.

**Acceptance Criteria:**
- [ ] Add retry logic via WorkManager
- [ ] Schedule retry with exponential backoff
- [ ] Log bootstrap failures for debugging
- [ ] Add user notification if bootstrap fails repeatedly

**Suggested Fix:**
```kotlin
} catch (e: Exception) {
    Log.e(TAG, "Failed to bootstrap Vector Index", e)
    // Schedule a retry via WorkManager
    val retryWork = OneTimeWorkRequestBuilder<VectorIndexBootstrapWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5000, TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(context).enqueue(retryWork)
}
```

**Files to Modify:**
- `app/src/main/java/com/recall/app/data/nlp/VectorIndexBootstrapper.kt`

**References:**
- Code Review Section: Performance & Efficiency - Issue #15

---

## Issue #14: [TESTING] Add Performance Tests for Critical Paths

**Labels:** `testing`, `performance`, `high-priority`

**Description:**
The codebase lacks performance tests for critical paths like vector search, embedding generation, and UI recomposition. This makes it difficult to catch performance regressions.

**Missing Tests:**
1. VectorIndex search performance with 100, 1000, 10000 vectors
2. OnnxEmbeddingGenerator caching effectiveness
3. HomeScreen recomposition count on state changes
4. Memory usage with large screenshot collections
5. ONNX session memory leak detection

**Acceptance Criteria:**
- [ ] Add vector search performance benchmarks
- [ ] Add embedding generation caching tests
- [ ] Add Compose recomposition counting tests
- [ ] Add memory profiling tests
- [ ] Add performance regression detection in CI

**Files to Create:**
- `app/src/test/java/com/recall/app/data/nlp/VectorIndexPerformanceTest.kt`
- `app/src/test/java/com/recall/app/data/nlp/OnnxEmbeddingGeneratorTest.kt`
- `app/src/test/java/com/recall/app/presentation/ui/home/HomeScreenRecompositionTest.kt`

**References:**
- Code Review Section: Testing Gaps (all reviews)

---

## Summary

**Total Issues:** 14

**By Priority:**
- 🔴 Critical: 3 issues (#1, #2, #3)
- 🟠 High: 3 issues (#4, #5, #6)
- 🟡 Medium: 3 issues (#7, #8, #9)
- 🟢 Low: 4 issues (#10, #11, #12, #13)
- 🧪 Testing: 1 issue (#14)

**By Category:**
- Performance: 7 issues
- Code Quality: 3 issues
- Reliability: 2 issues
- Testing: 1 issue
- Accessibility: 1 issue

**Recommended Milestones:**
1. **Performance Optimization** (Issues #1-7) - Fix before 1,000+ users
2. **Code Quality** (Issues #10-12) - Next sprint
3. **Reliability** (Issues #9, #13) - Next sprint
4. **Test Coverage** (Issue #14) - Ongoing
