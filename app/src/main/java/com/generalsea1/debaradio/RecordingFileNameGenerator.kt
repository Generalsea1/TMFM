package com.generalsea1.tmfm

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordingFileNameGenerator {
    fun generate(stationName: String, timestampMillis: Long, extension: String = "m4a"): String {
        val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(timestampMillis))
        val safeName = stationName
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { "Station" }
            .take(80)
        val safeExtension = extension
            .trim()
            .lowercase(Locale.ROOT)
            .removePrefix(".")
            .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
            ?: "bin"
        return "TMFM_" + safeName + "_" + date + "." + safeExtension
    }
}
