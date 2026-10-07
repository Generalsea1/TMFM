package com.generalsea1.debaradio

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordingFileNameGenerator {
    private val formatter = SimpleDateFormat("yyyy-MM-dd — HH-mm-ss", Locale.US)

    fun generate(stationName: String, timestampMillis: Long = System.currentTimeMillis()): String {
        val safeStation = stationName
            .trim()
            .replace(Regex("[\\/:*?"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .ifBlank { "Unknown Station" }

        return safeStation + " — " + formatter.format(Date(timestampMillis)) + ".m4a"
    }
}
