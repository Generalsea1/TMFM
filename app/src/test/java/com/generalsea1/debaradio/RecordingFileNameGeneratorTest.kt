package com.generalsea1.debaradio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingFileNameGeneratorTest {
    @Test
    fun generatesRequiredNameAndSanitizesInvalidCharacters() {
        val name = RecordingFileNameGenerator.generate(
            stationName = "Test:/FM?",
            timestampMillis = 0L
        )

        assertEquals("Test__FM_ — 1970-01-01 — 00-00-00.m4a", name)
        assertFalse(name.contains(":"))
        assertFalse(name.contains("/"))
        assertFalse(name.contains("?"))
    }

    @Test
    fun fallsBackWhenStationNameIsBlank() {
        val name = RecordingFileNameGenerator.generate(
            stationName = "   ",
            timestampMillis = 0L
        )

        assertTrue(name.startsWith("Unknown Station — 1970-01-01"))
        assertTrue(name.endsWith(".m4a"))
    }
}
