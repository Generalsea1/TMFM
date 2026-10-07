package com.generalsea1.tmfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingFileNameGeneratorTest {
    @Test
    fun generatedNameIsSafeAndDeterministic() {
        val name = RecordingFileNameGenerator.generate("محطة/اختبار", 0L)
        assertEquals("TMFM_محطة_اختبار_1970-01-01_00-00-00.m4a", name)
    }

    @Test
    fun generatedNameStartsWithTmfm() {
        val name = RecordingFileNameGenerator.generate("Station", 0L)
        assertTrue(name.startsWith("TMFM_"))
        assertTrue(name.endsWith(".m4a"))
    }
}
