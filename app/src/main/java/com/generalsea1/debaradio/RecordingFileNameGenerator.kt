package com.generalsea1.tmfm

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordingFileNameGenerator {
    fun generate(stationName: String, timestampMillis: Long): String {
        val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(timestampMillis))
        val safeName = stationName
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { "Station" }
            .take(80)
        return "TMFM_" + safeName + "_" + date + ".m4a"
    }
}
