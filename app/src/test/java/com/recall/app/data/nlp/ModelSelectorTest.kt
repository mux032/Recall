package com.recall.app.data.nlp

import com.recall.app.data.di.DeviceProfile
import com.recall.app.data.di.DeviceProfiler
import com.recall.app.util.MemoryClass
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
@Config(manifest = Config.NONE)
class ModelSelectorTest {

    private lateinit var deviceProfiler: DeviceProfiler
    private lateinit var selector: ModelSelector

    @Before
    fun setup() {
        deviceProfiler = mock()
    }

    // -----------------------------------------------------------------------
    // Helper — build selector with a specific RAM class
    // -----------------------------------------------------------------------

    private fun selectorFor(memoryClass: MemoryClass): ModelSelector {
        whenever(deviceProfiler.getProfile()).thenReturn(
            DeviceProfile(
                totalRamBytes = ramForClass(memoryClass),
                availableCores = 8,
                supportedAbis = listOf("arm64-v8a"),
                memoryClass = memoryClass
            )
        )
        return ModelSelector(deviceProfiler)
    }

    private fun ramForClass(memoryClass: MemoryClass): Long = when (memoryClass) {
        MemoryClass.LOW -> 2L * 1024 * 1024 * 1024       // 2 GB
        MemoryClass.MEDIUM -> 6L * 1024 * 1024 * 1024    // 6 GB
        MemoryClass.HIGH -> 12L * 1024 * 1024 * 1024     // 12 GB
        MemoryClass.VERY_HIGH -> 16L * 1024 * 1024 * 1024 // 16 GB
    }

    // -----------------------------------------------------------------------
    // selectModel() — RAM class → model mapping
    // -----------------------------------------------------------------------

    @Test
    fun `selectModel returns quantized model for LOW RAM`() {
        val config = selectorFor(MemoryClass.LOW).selectModel()
        assertEquals(ModelSelector.QUANTIZED_MODEL_FILENAME, config.fileName)
    }

    @Test
    fun `selectModel returns full model for MEDIUM RAM`() {
        val config = selectorFor(MemoryClass.MEDIUM).selectModel()
        assertEquals(ModelSelector.FULL_MODEL_FILENAME, config.fileName)
    }

    @Test
    fun `selectModel returns full model for HIGH RAM`() {
        val config = selectorFor(MemoryClass.HIGH).selectModel()
        assertEquals(ModelSelector.FULL_MODEL_FILENAME, config.fileName)
    }

    @Test
    fun `selectModel returns full model for VERY_HIGH RAM`() {
        val config = selectorFor(MemoryClass.VERY_HIGH).selectModel()
        assertEquals(ModelSelector.FULL_MODEL_FILENAME, config.fileName)
    }

    // -----------------------------------------------------------------------
    // selectModel() — URL validation
    // -----------------------------------------------------------------------

    @Test
    fun `selectModel LOW RAM URL starts with huggingface`() {
        val config = selectorFor(MemoryClass.LOW).selectModel()
        assertTrue(
            "URL must start with https://huggingface.co, got: ${config.url}",
            config.url.startsWith("https://huggingface.co")
        )
    }

    @Test
    fun `selectModel MEDIUM RAM URL starts with huggingface`() {
        val config = selectorFor(MemoryClass.MEDIUM).selectModel()
        assertTrue(
            "URL must start with https://huggingface.co, got: ${config.url}",
            config.url.startsWith("https://huggingface.co")
        )
    }

    // -----------------------------------------------------------------------
    // selectModel() — SHA-256 validation
    // -----------------------------------------------------------------------

    @Test
    fun `selectModel quantized model sha256 is 64 hex chars`() {
        val config = selectorFor(MemoryClass.LOW).selectModel()
        assertTrue(
            "SHA-256 must be 64 hex chars, got length ${config.sha256.length}",
            config.sha256.length == 64
        )
        assertTrue(
            "SHA-256 must be hex only",
            config.sha256.all { it.isDigit() || it in 'a'..'f' }
        )
    }

    @Test
    fun `selectModel full model sha256 is 64 hex chars`() {
        val config = selectorFor(MemoryClass.MEDIUM).selectModel()
        assertTrue(
            "SHA-256 must be 64 hex chars, got length ${config.sha256.length}",
            config.sha256.length == 64
        )
        assertTrue(
            "SHA-256 must be hex only",
            config.sha256.all { it.isDigit() || it in 'a'..'f' }
        )
    }

    // -----------------------------------------------------------------------
    // selectModel() — sizeBytes
    // -----------------------------------------------------------------------

    @Test
    fun `quantized model is smaller than full model`() {
        val quantized = selectorFor(MemoryClass.LOW).selectModel()
        val full = selectorFor(MemoryClass.MEDIUM).selectModel()
        assertTrue(
            "Quantized model (${quantized.sizeBytes}) must be smaller than full model (${full.sizeBytes})",
            quantized.sizeBytes < full.sizeBytes
        )
    }

    @Test
    fun `selectModel sizeBytes is positive for all RAM classes`() {
        MemoryClass.entries.forEach { memoryClass ->
            val config = selectorFor(memoryClass).selectModel()
            assertTrue(
                "sizeBytes must be positive for $memoryClass, got ${config.sizeBytes}",
                config.sizeBytes > 0
            )
        }
    }

    // -----------------------------------------------------------------------
    // getAllModels()
    // -----------------------------------------------------------------------

    @Test
    fun `getAllModels returns at least 2 variants`() {
        selector = selectorFor(MemoryClass.MEDIUM)
        assertTrue(selector.getAllModels().size >= 2)
    }

    @Test
    fun `getAllModels contains both quantized and full variants`() {
        selector = selectorFor(MemoryClass.MEDIUM)
        val models = selector.getAllModels()
        val fileNames = models.map { it.fileName }
        assertTrue(fileNames.contains(ModelSelector.QUANTIZED_MODEL_FILENAME))
        assertTrue(fileNames.contains(ModelSelector.FULL_MODEL_FILENAME))
    }

    @Test
    fun `getAllModels ordered smallest first`() {
        selector = selectorFor(MemoryClass.MEDIUM)
        val models = selector.getAllModels()
        for (i in 0 until models.size - 1) {
            assertTrue(
                "Models must be ordered smallest to largest",
                models[i].sizeBytes <= models[i + 1].sizeBytes
            )
        }
    }

    @Test
    fun `getAllModels all URLs start with https huggingface`() {
        selector = selectorFor(MemoryClass.MEDIUM)
        selector.getAllModels().forEach { config ->
            assertTrue(
                "URL must start with https://huggingface.co: ${config.url}",
                config.url.startsWith("https://huggingface.co")
            )
        }
    }

    @Test
    fun `getAllModels all sha256 values are valid`() {
        selector = selectorFor(MemoryClass.MEDIUM)
        selector.getAllModels().forEach { config ->
            assertEquals("SHA-256 must be 64 chars for ${config.fileName}", 64, config.sha256.length)
        }
    }

    // -----------------------------------------------------------------------
    // Constants — regression guard
    // -----------------------------------------------------------------------

    @Test
    fun `FULL_MODEL_URL constant is correct HuggingFace path`() {
        assertTrue(ModelSelector.FULL_MODEL_URL.contains("bge-small-en-v1.5"))
        assertTrue(ModelSelector.FULL_MODEL_URL.contains("model.onnx"))
        assertTrue(ModelSelector.FULL_MODEL_URL.startsWith("https://huggingface.co"))
    }

    @Test
    fun `QUANTIZED_MODEL_URL constant is correct HuggingFace path`() {
        assertTrue(ModelSelector.QUANTIZED_MODEL_URL.contains("bge-small-en-v1.5"))
        assertTrue(ModelSelector.QUANTIZED_MODEL_URL.contains("model_quantized.onnx"))
        assertTrue(ModelSelector.QUANTIZED_MODEL_URL.startsWith("https://huggingface.co"))
    }

    @Test
    fun `ModelConfig equality holds for identical values`() {
        val a = ModelSelector.FULL_MODEL.copy()
        val b = ModelSelector.FULL_MODEL.copy()
        assertEquals(a, b)
    }

    @Test
    fun `all ModelConfig fields are non-blank`() {
        listOf(ModelSelector.FULL_MODEL, ModelSelector.QUANTIZED_MODEL).forEach { config ->
            assertNotNull(config.url)
            assertTrue(config.url.isNotBlank())
            assertTrue(config.sha256.isNotBlank())
            assertTrue(config.fileName.isNotBlank())
            assertTrue(config.displayName.isNotBlank())
        }
    }
}
