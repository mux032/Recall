package com.recall.app.data.di

import android.content.Context
import android.os.Build
import com.recall.app.util.MemoryClass
import com.recall.app.util.MemoryInfoHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class DeviceProfilerTest {

    private lateinit var context: Context
    private lateinit var memoryInfoHelper: MemoryInfoHelper
    private lateinit var profiler: DeviceProfiler

    @Before
    fun setup() {
        context = mock()
        memoryInfoHelper = mock()
    }

    // -----------------------------------------------------------------------
    // totalRamBytes — delegated to MemoryInfoHelper.getTotalMemory()
    // -----------------------------------------------------------------------

    @Test
    fun `getProfile returns correct totalRamBytes from MemoryInfoHelper`() {
        val expectedRam = 8L * 1024 * 1024 * 1024 // 8 GB
        whenever(memoryInfoHelper.getTotalMemory()).thenReturn(expectedRam)
        whenever(memoryInfoHelper.getMemoryClass()).thenReturn(MemoryClass.HIGH)

        profiler = DeviceProfiler(context, memoryInfoHelper)
        val profile = profiler.getProfile()

        assertEquals(expectedRam, profile.totalRamBytes)
    }

    @Test
    fun `getProfile reflects low RAM correctly`() {
        whenever(memoryInfoHelper.getTotalMemory()).thenReturn(2L * 1024 * 1024 * 1024)
        whenever(memoryInfoHelper.getMemoryClass()).thenReturn(MemoryClass.LOW)

        profiler = DeviceProfiler(context, memoryInfoHelper)
        assertEquals(MemoryClass.LOW, profiler.getProfile().memoryClass)
    }

    @Test
    fun `getProfile reflects medium RAM correctly`() {
        whenever(memoryInfoHelper.getTotalMemory()).thenReturn(6L * 1024 * 1024 * 1024)
        whenever(memoryInfoHelper.getMemoryClass()).thenReturn(MemoryClass.MEDIUM)

        profiler = DeviceProfiler(context, memoryInfoHelper)
        assertEquals(MemoryClass.MEDIUM, profiler.getProfile().memoryClass)
    }

    @Test
    fun `getProfile reflects high RAM correctly`() {
        whenever(memoryInfoHelper.getTotalMemory()).thenReturn(12L * 1024 * 1024 * 1024)
        whenever(memoryInfoHelper.getMemoryClass()).thenReturn(MemoryClass.HIGH)

        profiler = DeviceProfiler(context, memoryInfoHelper)
        assertEquals(MemoryClass.HIGH, profiler.getProfile().memoryClass)
    }

    @Test
    fun `getProfile reflects very high RAM correctly`() {
        whenever(memoryInfoHelper.getTotalMemory()).thenReturn(16L * 1024 * 1024 * 1024)
        whenever(memoryInfoHelper.getMemoryClass()).thenReturn(MemoryClass.VERY_HIGH)

        profiler = DeviceProfiler(context, memoryInfoHelper)
        assertEquals(MemoryClass.VERY_HIGH, profiler.getProfile().memoryClass)
    }

    // -----------------------------------------------------------------------
    // availableCores — from Runtime.getRuntime().availableProcessors()
    // -----------------------------------------------------------------------

    @Test
    fun `getProfile returns positive availableCores`() {
        whenever(memoryInfoHelper.getTotalMemory()).thenReturn(4L * 1024 * 1024 * 1024)
        whenever(memoryInfoHelper.getMemoryClass()).thenReturn(MemoryClass.MEDIUM)

        profiler = DeviceProfiler(context, memoryInfoHelper)
        val cores = profiler.getProfile().availableCores

        assertTrue("availableCores must be positive, got $cores", cores > 0)
    }

    @Test
    fun `getProfile availableCores matches Runtime`() {
        whenever(memoryInfoHelper.getTotalMemory()).thenReturn(4L * 1024 * 1024 * 1024)
        whenever(memoryInfoHelper.getMemoryClass()).thenReturn(MemoryClass.MEDIUM)

        profiler = DeviceProfiler(context, memoryInfoHelper)
        assertEquals(
            Runtime.getRuntime().availableProcessors(),
            profiler.getProfile().availableCores
        )
    }

    // -----------------------------------------------------------------------
    // supportedAbis — from Build.SUPPORTED_ABIS
    // -----------------------------------------------------------------------

    @Test
    fun `getProfile returns non-empty supportedAbis`() {
        whenever(memoryInfoHelper.getTotalMemory()).thenReturn(4L * 1024 * 1024 * 1024)
        whenever(memoryInfoHelper.getMemoryClass()).thenReturn(MemoryClass.MEDIUM)

        profiler = DeviceProfiler(context, memoryInfoHelper)
        val abis = profiler.getProfile().supportedAbis

        assertNotNull(abis)
        assertTrue("supportedAbis must not be empty", abis.isNotEmpty())
    }

    @Test
    fun `getProfile supportedAbis matches Build_SUPPORTED_ABIS`() {
        whenever(memoryInfoHelper.getTotalMemory()).thenReturn(4L * 1024 * 1024 * 1024)
        whenever(memoryInfoHelper.getMemoryClass()).thenReturn(MemoryClass.MEDIUM)

        profiler = DeviceProfiler(context, memoryInfoHelper)
        assertEquals(Build.SUPPORTED_ABIS.toList(), profiler.getProfile().supportedAbis)
    }

    // -----------------------------------------------------------------------
    // Caching — getProfile() must return the same instance on every call
    // -----------------------------------------------------------------------

    @Test
    fun `getProfile returns same cached instance on repeated calls`() {
        whenever(memoryInfoHelper.getTotalMemory()).thenReturn(4L * 1024 * 1024 * 1024)
        whenever(memoryInfoHelper.getMemoryClass()).thenReturn(MemoryClass.MEDIUM)

        profiler = DeviceProfiler(context, memoryInfoHelper)
        val first = profiler.getProfile()
        val second = profiler.getProfile()

        assertTrue("getProfile() must return the exact same cached instance", first === second)
    }

    // -----------------------------------------------------------------------
    // DeviceProfile data class
    // -----------------------------------------------------------------------

    @Test
    fun `DeviceProfile equality holds for identical values`() {
        val a = DeviceProfile(
            totalRamBytes = 8L * 1024 * 1024 * 1024,
            availableCores = 8,
            supportedAbis = listOf("arm64-v8a"),
            memoryClass = MemoryClass.HIGH
        )
        val b = DeviceProfile(
            totalRamBytes = 8L * 1024 * 1024 * 1024,
            availableCores = 8,
            supportedAbis = listOf("arm64-v8a"),
            memoryClass = MemoryClass.HIGH
        )
        assertEquals(a, b)
    }

    @Test
    fun `DeviceProfile copy produces correct modified instance`() {
        val original = DeviceProfile(
            totalRamBytes = 4L * 1024 * 1024 * 1024,
            availableCores = 4,
            supportedAbis = listOf("arm64-v8a"),
            memoryClass = MemoryClass.MEDIUM
        )
        val modified = original.copy(availableCores = 8)

        assertEquals(8, modified.availableCores)
        assertEquals(original.totalRamBytes, modified.totalRamBytes)
        assertEquals(original.memoryClass, modified.memoryClass)
    }
}
