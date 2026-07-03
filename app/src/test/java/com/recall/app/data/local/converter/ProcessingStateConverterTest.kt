package com.recall.app.data.local.converter

import com.recall.app.domain.model.ProcessingState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ProcessingStateConverter].
 *
 * Verifies that the TypeConverter correctly serialises [ProcessingState] to its String
 * database representation and deserialises it back — including the safe fallback for
 * unknown values and legacy migration from old state names.
 */
class ProcessingStateConverterTest {

    private lateinit var converter: ProcessingStateConverter

    @Before
    fun setup() {
        converter = ProcessingStateConverter()
    }

    // -----------------------------------------------------------------------
    // fromProcessingState — enum → String
    // -----------------------------------------------------------------------

    @Test
    fun `fromProcessingState OcrPending returns OCR_PENDING`() {
        assertEquals("OCR_PENDING", converter.fromProcessingState(ProcessingState.OcrPending))
    }

    @Test
    fun `fromProcessingState OcrCompleted returns OCR_COMPLETED`() {
        assertEquals("OCR_COMPLETED", converter.fromProcessingState(ProcessingState.OcrCompleted))
    }

    @Test
    fun `fromProcessingState OcrEmbCompleted returns OCR_EMB_COMPLETED`() {
        assertEquals("OCR_EMB_COMPLETED", converter.fromProcessingState(ProcessingState.OcrEmbCompleted))
    }

    @Test
    fun `fromProcessingState Failed returns FAILED`() {
        assertEquals("FAILED", converter.fromProcessingState(ProcessingState.Failed))
    }

    // -----------------------------------------------------------------------
    // toProcessingState — String → enum (current values)
    // -----------------------------------------------------------------------

    @Test
    fun `toProcessingState OCR_PENDING returns OcrPending`() {
        assertEquals(ProcessingState.OcrPending, converter.toProcessingState("OCR_PENDING"))
    }

    @Test
    fun `toProcessingState OCR_COMPLETED returns OcrCompleted`() {
        assertEquals(ProcessingState.OcrCompleted, converter.toProcessingState("OCR_COMPLETED"))
    }

    @Test
    fun `toProcessingState OCR_EMB_COMPLETED returns OcrEmbCompleted`() {
        assertEquals(ProcessingState.OcrEmbCompleted, converter.toProcessingState("OCR_EMB_COMPLETED"))
    }

    @Test
    fun `toProcessingState FAILED returns Failed`() {
        assertEquals(ProcessingState.Failed, converter.toProcessingState("FAILED"))
    }

    // -----------------------------------------------------------------------
    // Legacy value migration
    // -----------------------------------------------------------------------

    @Test
    fun `toProcessingState legacy PENDING migrates to OcrPending`() {
        assertEquals(ProcessingState.OcrPending, converter.toProcessingState("PENDING"))
    }

    @Test
    fun `toProcessingState legacy DONE migrates to OcrEmbCompleted`() {
        assertEquals(ProcessingState.OcrEmbCompleted, converter.toProcessingState("DONE"))
    }

    @Test
    fun `toProcessingState legacy PROCESSING migrates to OcrPending`() {
        assertEquals(ProcessingState.OcrPending, converter.toProcessingState("PROCESSING"))
    }

    @Test
    fun `toProcessingState unknown value falls back to OcrPending`() {
        // Unknown values (e.g. from a future schema or a typo) must not crash
        assertEquals(ProcessingState.OcrPending, converter.toProcessingState("UNKNOWN"))
        assertEquals(ProcessingState.OcrPending, converter.toProcessingState(""))
        assertEquals(ProcessingState.OcrPending, converter.toProcessingState("ocr_pending")) // case-sensitive
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
    // fromValue direct tests (legacy migration regression guard)
    // -----------------------------------------------------------------------

    @Test
    fun `fromValue PENDING returns OcrPending`() {
        assertEquals(ProcessingState.OcrPending, ProcessingState.fromValue("PENDING"))
    }

    @Test
    fun `fromValue DONE returns OcrEmbCompleted`() {
        assertEquals(ProcessingState.OcrEmbCompleted, ProcessingState.fromValue("DONE"))
    }

    @Test
    fun `fromValue PROCESSING returns OcrPending`() {
        assertEquals(ProcessingState.OcrPending, ProcessingState.fromValue("PROCESSING"))
    }
}
