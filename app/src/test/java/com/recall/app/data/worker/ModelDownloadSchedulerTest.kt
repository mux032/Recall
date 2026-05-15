package com.recall.app.data.worker

import android.os.Build
import androidx.work.Constraints
import androidx.work.NetworkType
import com.recall.app.data.nlp.ModelConfig
import com.recall.app.data.nlp.ModelSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ModelDownloadScheduler].
 *
 * Tests the constraint configuration and input data constants without spinning up
 * WorkManager (which requires an instrumented test environment). The constraint
 * values and work name are the critical correctness guarantees — if they're wrong,
 * the download runs on metered networks or without charging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class ModelDownloadSchedulerTest {

    // -----------------------------------------------------------------------
    // DOWNLOAD_CONSTRAINTS — critical correctness
    // -----------------------------------------------------------------------

    @Test
    fun `DOWNLOAD_CONSTRAINTS requires UNMETERED network`() {
        val constraints = ModelDownloadScheduler.DOWNLOAD_CONSTRAINTS
        assertEquals(
            "Must require UNMETERED to prevent downloading over mobile data",
            NetworkType.UNMETERED,
            constraints.requiredNetworkType
        )
    }

    @Test
    fun `DOWNLOAD_CONSTRAINTS does NOT require charging`() {
        // Charging is intentionally NOT required — a user at 80% on Wi-Fi should not
        // have to plug in to download a 32–127MB file. requiresBatteryNotLow is the
        // appropriate safety net instead.
        val constraints = ModelDownloadScheduler.DOWNLOAD_CONSTRAINTS
        assertEquals(false, constraints.requiresCharging())
    }

    @Test
    fun `DOWNLOAD_CONSTRAINTS requires battery not low`() {
        // Safety net: don't download when device is critically low on power (~15%)
        val constraints = ModelDownloadScheduler.DOWNLOAD_CONSTRAINTS
        assertTrue(
            "Must require battery not low as a safety net for low-power devices",
            constraints.requiresBatteryNotLow()
        )
    }

    @Test
    fun `DOWNLOAD_CONSTRAINTS does not require device idle`() {
        // requiresDeviceIdle (Doze mode) would delay the download too aggressively
        val constraints = ModelDownloadScheduler.DOWNLOAD_CONSTRAINTS
        assertEquals(false, constraints.requiresDeviceIdle())
    }

    // -----------------------------------------------------------------------
    // WORK_NAME — deduplication key
    // -----------------------------------------------------------------------

    @Test
    fun `WORK_NAME is non-blank`() {
        assertTrue(ModelDownloadScheduler.WORK_NAME.isNotBlank())
    }

    @Test
    fun `WORK_NAME has expected value`() {
        assertEquals("model_download", ModelDownloadScheduler.WORK_NAME)
    }

    // -----------------------------------------------------------------------
    // Input data keys — must match ModelDownloadWorker constants
    // -----------------------------------------------------------------------

    @Test
    fun `KEY_MODEL_URL in ModelDownloadWorker matches expected value`() {
        assertEquals("model_url", ModelDownloadWorker.KEY_MODEL_URL)
    }

    @Test
    fun `KEY_MODEL_SHA256 in ModelDownloadWorker matches expected value`() {
        assertEquals("model_sha256", ModelDownloadWorker.KEY_MODEL_SHA256)
    }

    @Test
    fun `KEY_MODEL_FILENAME in ModelDownloadWorker matches expected value`() {
        assertEquals("model_filename", ModelDownloadWorker.KEY_MODEL_FILENAME)
    }

    // -----------------------------------------------------------------------
    // ModelConfig fields used as input data
    // -----------------------------------------------------------------------

    @Test
    fun `QUANTIZED_MODEL config provides all required fields for input data`() {
        val config = ModelSelector.QUANTIZED_MODEL
        assertTrue(config.url.isNotBlank())
        assertTrue(config.sha256.isNotBlank())
        assertTrue(config.fileName.isNotBlank())
    }

    @Test
    fun `FULL_MODEL config provides all required fields for input data`() {
        val config = ModelSelector.FULL_MODEL
        assertTrue(config.url.isNotBlank())
        assertTrue(config.sha256.isNotBlank())
        assertTrue(config.fileName.isNotBlank())
    }

    @Test
    fun `all ModelConfig fields are non-null and correctly typed`() {
        listOf(ModelSelector.QUANTIZED_MODEL, ModelSelector.FULL_MODEL).forEach { config ->
            assertNotNull(config.url)
            assertNotNull(config.sha256)
            assertNotNull(config.fileName)
            assertTrue(config.sizeBytes > 0)
        }
    }

    // -----------------------------------------------------------------------
    // Constraints — builder API verification
    // -----------------------------------------------------------------------

    @Test
    fun `manually built constraints with UNMETERED and battery-not-low match DOWNLOAD_CONSTRAINTS`() {
        val manual = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()

        assertEquals(
            ModelDownloadScheduler.DOWNLOAD_CONSTRAINTS.requiredNetworkType,
            manual.requiredNetworkType
        )
        assertEquals(
            ModelDownloadScheduler.DOWNLOAD_CONSTRAINTS.requiresBatteryNotLow(),
            manual.requiresBatteryNotLow()
        )
        assertEquals(
            ModelDownloadScheduler.DOWNLOAD_CONSTRAINTS.requiresCharging(),
            manual.requiresCharging()
        )
    }

    @Test
    fun `CONNECTED network type is less restrictive than UNMETERED`() {
        // Verify we chose the right NetworkType — CONNECTED allows metered networks,
        // UNMETERED restricts to Wi-Fi/ethernet only
        assertTrue(
            "UNMETERED is more restrictive than CONNECTED",
            NetworkType.UNMETERED != NetworkType.CONNECTED
        )
        assertEquals(NetworkType.UNMETERED, ModelDownloadScheduler.DOWNLOAD_CONSTRAINTS.requiredNetworkType)
    }
}
