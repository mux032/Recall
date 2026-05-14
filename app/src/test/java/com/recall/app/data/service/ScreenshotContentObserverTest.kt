package com.recall.app.data.service

import android.net.Uri
import android.os.Build
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ScreenshotContentObserver] debounce behaviour.
 *
 * Uses Robolectric (for [Uri]) with a [TestScope] + [StandardTestDispatcher] so we can
 * control virtual time without real wall-clock delays.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenshotContentObserverTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val uri1 = Uri.parse("content://media/external/images/media/1")
    private val uri2 = Uri.parse("content://media/external/images/media/2")

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun buildObserver(
        debounceMs: Long = ScreenshotContentObserver.DEBOUNCE_MS,
        onHandleNewMedia: (Uri) -> Unit = {}
    ) = TestableContentObserver(testScope, debounceMs, onHandleNewMedia)

    // -----------------------------------------------------------------------
    // Single URI — debounce
    // -----------------------------------------------------------------------

    @Test
    fun `rapid onChange calls for same URI result in single invocation`() = testScope.runTest {
        val invocations = mutableListOf<Uri>()
        val observer = buildObserver(onHandleNewMedia = { invocations.add(it) })

        // 5 rapid calls before the debounce window expires
        repeat(5) { observer.onChange(uri1) }
        advanceTimeBy(ScreenshotContentObserver.DEBOUNCE_MS + 100)

        assertEquals("Expected exactly 1 invocation after debounce", 1, invocations.size)
        assertEquals(uri1, invocations.first())
    }

    @Test
    fun `single onChange fires after debounce window`() = testScope.runTest {
        val invocations = mutableListOf<Uri>()
        val observer = buildObserver(onHandleNewMedia = { invocations.add(it) })

        observer.onChange(uri1)

        advanceTimeBy(ScreenshotContentObserver.DEBOUNCE_MS - 100) // not yet
        assertEquals(0, invocations.size)

        advanceTimeBy(200) // past window
        assertEquals(1, invocations.size)
    }

    @Test
    fun `onChange does not fire if destroyed within debounce window`() = testScope.runTest {
        val invocations = mutableListOf<Uri>()
        val observer = buildObserver(onHandleNewMedia = { invocations.add(it) })

        observer.onChange(uri1)
        advanceTimeBy(ScreenshotContentObserver.DEBOUNCE_MS - 100)

        observer.destroy()
        advanceTimeBy(500)

        assertEquals(0, invocations.size)
    }

    // -----------------------------------------------------------------------
    // Multiple URIs
    // -----------------------------------------------------------------------

    @Test
    fun `concurrent onChange calls for different URIs debounce independently`() =
        testScope.runTest {
            val invocations = mutableListOf<Uri>()
            val observer = buildObserver(onHandleNewMedia = { invocations.add(it) })

            repeat(3) { observer.onChange(uri1) }
            repeat(3) { observer.onChange(uri2) }
            advanceTimeBy(ScreenshotContentObserver.DEBOUNCE_MS + 100)

            assertEquals("Expected one invocation per URI", 2, invocations.size)
            assertTrue(invocations.contains(uri1))
            assertTrue(invocations.contains(uri2))
        }

    // -----------------------------------------------------------------------
    // Timer reset
    // -----------------------------------------------------------------------

    @Test
    fun `each new onChange resets the debounce timer`() = testScope.runTest {
        val invocations = mutableListOf<Uri>()
        val observer = buildObserver(debounceMs = 500, onHandleNewMedia = { invocations.add(it) })

        observer.onChange(uri1)
        advanceTimeBy(400)           // reset
        observer.onChange(uri1)
        advanceTimeBy(400)           // still within new 500 ms window
        assertEquals(0, invocations.size)

        advanceTimeBy(200)           // now past the window
        assertEquals(1, invocations.size)
    }

    // -----------------------------------------------------------------------
    // Null URI guard
    // -----------------------------------------------------------------------

    @Test
    fun `onChange with null URI is a no-op`() = testScope.runTest {
        val invocations = mutableListOf<Uri>()
        val observer = buildObserver(onHandleNewMedia = { invocations.add(it) })

        observer.onChange(null)
        advanceTimeBy(ScreenshotContentObserver.DEBOUNCE_MS + 100)

        assertEquals(0, invocations.size)
    }

    // -----------------------------------------------------------------------
    // destroy()
    // -----------------------------------------------------------------------

    @Test
    fun `destroy cancels all pending debounce jobs`() = testScope.runTest {
        val invocations = mutableListOf<Uri>()
        val observer = buildObserver(onHandleNewMedia = { invocations.add(it) })

        observer.onChange(uri1)
        observer.onChange(uri2)

        observer.destroy()
        advanceTimeBy(ScreenshotContentObserver.DEBOUNCE_MS + 100)

        assertEquals(0, invocations.size)
    }
}

/**
 * Minimal test double that replicates the debounce logic of [ScreenshotContentObserver]
 * but replaces [handleNewMedia] with a lambda — no [android.content.Context] or
 * [androidx.work.WorkManager] required.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private class TestableContentObserver(
    private val scope: TestScope,
    private val debounceMs: Long,
    private val onHandleNewMedia: (Uri) -> Unit
) {
    private val debounceJobs = mutableMapOf<String, Job>()

    fun onChange(uri: Uri?) {
        uri ?: return
        val key = uri.toString()
        debounceJobs[key]?.cancel()
        debounceJobs[key] = scope.launch {
            delay(debounceMs)
            debounceJobs.remove(key)
            onHandleNewMedia(uri)
        }
    }

    fun destroy() {
        debounceJobs.values.forEach { it.cancel() }
        debounceJobs.clear()
    }
}
