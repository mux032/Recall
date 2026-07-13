package com.recall.app.data.local.converter

import com.recall.app.domain.model.ProcessingState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ProcessingStateConverter].
 *
 * Verifies that the TypeConverter correctly serialises [ProcessingState] to its compact
 * integer database representation and deserialises it back — including the safe fallback
 * for unknown values and legacy String migration via [ProcessingState.fromValue].
 */
class ProcessingStateConverterTest {

    private lateinit var converter: ProcessingStateConverter

    @Before
    fun setup() {
        converter = ProcessingStateConverter()
    }

    // -----------------------------------------------------------------------
    // fromProcessingState — sealed class → Int
    // -----------------------------------------------------------------------

    @Test
    fun `fromProcessingState OcrPending returns 0`() {
        assertEquals(0, converter.fromProcessingState(ProcessingState.OcrPending))
    }

    @Test
    fun `fromProcessingState OcrCompleted returns 1`() {
        assertEquals(1, converter.fromProcessingState(ProcessingState.OcrCompleted))
    }

    @Test
    fun `fromProcessingState OcrEmbCompleted returns 2`() {
        assertEquals(2, converter.fromProcessingState(ProcessingState.OcrEmbCompleted))
    }

    @Test
    fun `fromProcessingState Failed returns 3`() {
        assertEquals(3, converter.fromProcessingState(ProcessingState.Failed))
    }

    // -----------------------------------------------------------------------
    // toProcessingState — Int → sealed class (current values)
    // -----------------------------------------------------------------------

    @Test
    fun `toProcessingState 0 returns OcrPending`() {
        assertEquals(ProcessingState.OcrPending, converter.toProcessingState(0))
    }

    @Test
    fun `toProcessingState 1 returns OcrCompleted`() {
        assertEquals(ProcessingState.OcrCompleted, converter.toProcessingState(1))
    }

    @Test
    fun `toProcessingState 2 returns OcrEmbCompleted`() {
        assertEquals(ProcessingState.OcrEmbCompleted, converter.toProcessingState(2))
    }

    @Test
    fun `toProcessingState 3 returns Failed`() {
        assertEquals(ProcessingState.Failed, converter.toProcessingState(3))
    }

    @Test
    fun `toProcessingState unknown value falls back to OcrPending`() {
        // Unknown int values must not crash — safe fallback to OcrPending
        assertEquals(ProcessingState.OcrPending, converter.toProcessingState(99))
        assertEquals(ProcessingState.OcrPending, converter.toProcessingState(-1))
    }

    // -----------------------------------------------------------------------
    // Named constants are stable
    // -----------------------------------------------------------------------

    @Test
    fun `VAL constants match sealed object values`() {
        assertEquals(ProcessingState.VAL_OCR_PENDING,       ProcessingState.OcrPending.value)
        assertEquals(ProcessingState.VAL_OCR_COMPLETED,     ProcessingState.OcrCompleted.value)
        assertEquals(ProcessingState.VAL_OCR_EMB_COMPLETED, ProcessingState.OcrEmbCompleted.value)
        assertEquals(ProcessingState.VAL_FAILED,            ProcessingState.Failed.value)
    }

    // -----------------------------------------------------------------------
    // Round-trip
    // -----------------------------------------------------------------------

    @Test
    fun `round-trip OcrPending`() {
        val original = ProcessingState.OcrPending
        assertEquals(original, converter.toProcessingState(converter.fromProcessingState(original)))
    }

    @Test
    fun `round-trip OcrCompleted`() {
        val original = ProcessingState.OcrCompleted
        assertEquals(original, converter.toProcessingState(converter.fromProcessingState(original)))
    }

    @Test
    fun `round-trip OcrEmbCompleted`() {
        val original = ProcessingState.OcrEmbCompleted
        assertEquals(original, converter.toProcessingState(converter.fromProcessingState(original)))
    }

    @Test
    fun `round-trip Failed`() {
        val original = ProcessingState.Failed
        assertEquals(original, converter.toProcessingState(converter.fromProcessingState(original)))
    }

    // -----------------------------------------------------------------------
    // Legacy String migration regression guard (ProcessingState.fromValue(String))
    // -----------------------------------------------------------------------

    @Test
    fun `fromValue String PENDING returns OcrPending`() {
        assertEquals(ProcessingState.OcrPending, ProcessingState.fromValue("PENDING"))
    }

    @Test
    fun `fromValue String DONE returns OcrEmbCompleted`() {
        assertEquals(ProcessingState.OcrEmbCompleted, ProcessingState.fromValue("DONE"))
    }

    @Test
    fun `fromValue String PROCESSING returns OcrPending`() {
        assertEquals(ProcessingState.OcrPending, ProcessingState.fromValue("PROCESSING"))
    }

    @Test
    fun `fromValue String OCR_PENDING returns OcrPending`() {
        assertEquals(ProcessingState.OcrPending, ProcessingState.fromValue("OCR_PENDING"))
    }

    @Test
    fun `fromValue String OCR_COMPLETED returns OcrCompleted`() {
        assertEquals(ProcessingState.OcrCompleted, ProcessingState.fromValue("OCR_COMPLETED"))
    }

    @Test
    fun `fromValue String OCR_EMB_COMPLETED returns OcrEmbCompleted`() {
        assertEquals(ProcessingState.OcrEmbCompleted, ProcessingState.fromValue("OCR_EMB_COMPLETED"))
    }

    @Test
    fun `fromValue String unknown falls back to OcrPending`() {
        assertEquals(ProcessingState.OcrPending, ProcessingState.fromValue("UNKNOWN_GARBAGE"))
    }
}
