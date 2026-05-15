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
 * unknown values.
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
    fun `fromProcessingState Pending returns PENDING`() {
        assertEquals("PENDING", converter.fromProcessingState(ProcessingState.Pending))
    }

    @Test
    fun `fromProcessingState Done returns DONE`() {
        assertEquals("DONE", converter.fromProcessingState(ProcessingState.Done))
    }

    @Test
    fun `fromProcessingState Failed returns FAILED`() {
        assertEquals("FAILED", converter.fromProcessingState(ProcessingState.Failed))
    }

    @Test
    fun `fromProcessingState Processing returns PROCESSING`() {
        assertEquals("PROCESSING", converter.fromProcessingState(ProcessingState.Processing))
    }

    // -----------------------------------------------------------------------
    // toProcessingState — String → enum
    // -----------------------------------------------------------------------

    @Test
    fun `toProcessingState PENDING returns Pending`() {
        assertEquals(ProcessingState.Pending, converter.toProcessingState("PENDING"))
    }

    @Test
    fun `toProcessingState DONE returns Done`() {
        assertEquals(ProcessingState.Done, converter.toProcessingState("DONE"))
    }

    @Test
    fun `toProcessingState FAILED returns Failed`() {
        assertEquals(ProcessingState.Failed, converter.toProcessingState("FAILED"))
    }

    @Test
    fun `toProcessingState PROCESSING returns Processing`() {
        assertEquals(ProcessingState.Processing, converter.toProcessingState("PROCESSING"))
    }

    @Test
    fun `toProcessingState unknown value falls back to Pending`() {
        // Unknown values (e.g. from a future schema or a typo) must not crash
        assertEquals(ProcessingState.Pending, converter.toProcessingState("UNKNOWN"))
        assertEquals(ProcessingState.Pending, converter.toProcessingState(""))
        assertEquals(ProcessingState.Pending, converter.toProcessingState("pending")) // case-sensitive
    }

    // -----------------------------------------------------------------------
    // Round-trip
    // -----------------------------------------------------------------------

    @Test
    fun `round-trip Pending`() {
        val original = ProcessingState.Pending
        assertEquals(original, converter.toProcessingState(converter.fromProcessingState(original)))
    }

    @Test
    fun `round-trip Done`() {
        val original = ProcessingState.Done
        assertEquals(original, converter.toProcessingState(converter.fromProcessingState(original)))
    }

    @Test
    fun `round-trip Failed`() {
        val original = ProcessingState.Failed
        assertEquals(original, converter.toProcessingState(converter.fromProcessingState(original)))
    }

    @Test
    fun `round-trip Processing`() {
        val original = ProcessingState.Processing
        assertEquals(original, converter.toProcessingState(converter.fromProcessingState(original)))
    }
}
