package com.playzongx.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationParserTest {
    @Test
    fun `parses minutes and seconds`() = assertEquals(225_000L, parseDurationText("3:45"))

    @Test
    fun `parses hours, minutes, seconds`() = assertEquals(3_723_000L, parseDurationText("1:02:03"))

    @Test
    fun `rejects null and malformed values`() {
        assertNull(parseDurationText(null))
        assertNull(parseDurationText("abc"))
        assertNull(parseDurationText("3:75"))
        assertNull(parseDurationText(""))
        assertNull(parseDurationText("  "))
        assertNull(parseDurationText("1:2:3:4"))
        assertNull(parseDurationText("3:"))
        assertNull(parseDurationText(":45"))
        assertNull(parseDurationText("1::03"))
        assertNull(parseDurationText("1:2a:03"))
    }

    @Test
    fun `formats millis back to display text`() {
        assertEquals("--:--", formatDuration(null))
        assertEquals("3:45", formatDuration(225_000L))
        assertEquals("1:02:03", formatDuration(3_723_000L))
    }

    @Test
    fun `formats edge cases and truncates milliseconds`() {
        assertEquals("--:--", formatDuration(-1L))
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:07", formatDuration(7_999L))
        assertEquals("59:59", formatDuration(3_599_999L))
        assertEquals("1:00:00", formatDuration(3_600_000L))
    }
}
