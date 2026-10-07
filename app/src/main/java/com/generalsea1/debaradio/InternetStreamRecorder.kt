package com.generalsea1.tmfm

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

internal data class RecordingResult(
    val file: File,
    val durationMillis: Long
)

internal class InternetStreamRecorder(
    private val context: Context,
    private val shouldStop: () -> Boolean
) {
    fun record(
        stationName: String,
        stationId: String,
        frequencyMhz: Double?,
        streamUrl: String,
        streamType: String?
    ): RecordingResult {
        val timestamp = System.currentTimeMillis()
        val dir = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC),
            "TMFM/Recordings"
        ).apply { mkdirs() }

        val lowerUrl = streamUrl.lowercase(Locale.ROOT)
        val lowerType = streamType.orEmpty().lowercase(Locale.ROOT)

        return if (lowerType == "hls" || lowerUrl.contains(".m3u8")) {
            HlsStreamRecorder(shouldStop).record(stationName, timestamp, streamUrl, dir)
        } else {
            ProgressiveStreamRecorder(shouldStop).record(
                stationName = stationName,
                streamUrl = streamUrl,
                streamType = streamType,
                dir = dir,
                timestamp = timestamp
            )
        }
    }
}

private class ProgressiveStreamRecorder(
    private val shouldStop: () -> Boolean
) {
    fun record(
        stationName: String,
        streamUrl: String,
        streamType: String?,
        dir: File,
        timestamp: Long
    ): RecordingResult {
        val connection = (URI.create(streamUrl).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "TMFM/1.0 Android")
            setRequestProperty("Icy-MetaData", "0")
        }

        try {
            if (connection.responseCode !in 200..299) {
                error("HTTP " + connection.responseCode)
            }

            val contentType = connection.contentType.orEmpty().lowercase(Locale.ROOT)
            val extension = when {
                contentType.contains("audio/mpeg") ||
                    contentType.contains("audio/mp3") ||
                    streamType.equals("MP3", true) -> "mp3"
                contentType.contains("audio/aac") ||
                    contentType.contains("aac") ||
                    streamType.equals("AAC", true) -> "aac"
                else -> error("نوع البث الصوتي غير معروف ولا يمكن حفظه بأمان.")
            }

            val output = File(
                dir,
                RecordingFileNameGenerator.generate(stationName, timestamp, extension)
            )
            val started = System.currentTimeMillis()
            val metaInterval = connection.getHeaderFieldInt("icy-metaint", -1)

            BufferedInputStream(connection.inputStream, 64 * 1024).use { input ->
                BufferedOutputStream(output.outputStream(), 64 * 1024).use { out ->
                    if (metaInterval > 0) copyWithoutIcyMetadata(input, out, metaInterval)
                    else {
                        val buffer = ByteArray(64 * 1024)
                        while (!shouldStop()) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read > 0) out.write(buffer, 0, read)
                        }
                    }
                    out.flush()
                }
            }

            val duration = (System.currentTimeMillis() - started).coerceAtLeast(1L)
            if (!output.exists() || output.length() < 1024L) {
                output.delete()
                error("لم يصل صوت كافٍ من البث لإنشاء تسجيل صالح.")
            }

            return RecordingResult(output, duration)
        } finally {
            connection.disconnect()
        }
    }

    private fun copyWithoutIcyMetadata(
        input: BufferedInputStream,
        out: BufferedOutputStream,
        metaInterval: Int
    ) {
        val buffer = ByteArray(64 * 1024)
        var remainingAudio = metaInterval

        while (!shouldStop()) {
            while (remainingAudio > 0 && !shouldStop()) {
                val toRead = minOf(remainingAudio, buffer.size)
                val read = input.read(buffer, 0, toRead)
                if (read < 0) return
                if (read > 0) {
                    out.write(buffer, 0, read)
                    remainingAudio -= read
                }
            }

            if (shouldStop()) return

            val lengthByte = input.read()
            if (lengthByte < 0) return
            val metadataBytes = lengthByte * 16
            if (metadataBytes > 0) skipFully(input, metadataBytes)
            remainingAudio = metaInterval
        }
    }

    private fun skipFully(input: BufferedInputStream, count: Int) {
        var left = count
        while (left > 0 && !shouldStop()) {
            val skipped = input.skip(left.toLong()).toInt()
            if (skipped > 0) left -= skipped
            else {
                if (input.read() < 0) return
                left--
            }
        }
    }
}

private class HlsStreamRecorder(
    private val shouldStop: () -> Boolean
) {
    fun record(
        stationName: String,
        timestamp: Long,
        playlistUrl: String,
        dir: File
    ): RecordingResult {
        var targetDurationMillis = 4_000L
        var output: File? = null
        var started = System.currentTimeMillis()
        val downloadedSequences = HashSet<Long>()

        while (!shouldStop()) {
            var effectivePlaylistUrl = playlistUrl
            var playlist = fetchText(effectivePlaylistUrl)

            if (playlist.contains("#EXT-X-STREAM-INF:")) {
                val lines = playlist.lineSequence()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .toList()
                var variant: String? = null
                for (i in lines.indices) {
                    if (lines[i].startsWith("#EXT-X-STREAM-INF:")) {
                        variant = lines.drop(i + 1).firstOrNull { !it.startsWith("#") }
                        if (variant != null) break
                    }
                }
                if (variant == null) error("تعذر اختيار مسار HLS صالح.")
                effectivePlaylistUrl = URI.create(playlistUrl).resolve(variant).toString()
                playlist = fetchText(effectivePlaylistUrl)
            }

            if (playlist.contains("#EXT-X-KEY:") && !playlist.contains("METHOD=NONE")) {
                error("هذا HLS يستخدم تشفيرًا أو حماية تمنع TMFM من تسجيله بأمان.")
            }

            val lines = playlist.lineSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .toList()

            val base = URI.create(effectivePlaylistUrl)
            var mediaSequence = 0L
            var mapUrl: String? = null
            var sequence = 0L
            var endList = false

            for (line in lines) {
                when {
                    line.startsWith("#EXT-X-MEDIA-SEQUENCE:") ->
                        mediaSequence = line.substringAfter(":").trim().toLongOrNull() ?: 0L
                    line.startsWith("#EXT-X-TARGETDURATION:") ->
                        targetDurationMillis =
                            ((line.substringAfter(":").trim().toLongOrNull() ?: 4L)
                                .coerceIn(1L, 30L) * 1000L)
                    line.startsWith("#EXT-X-MAP:") ->
                        Regex("""URI="([^"]+)"""").find(line)?.groupValues?.getOrNull(1)?.let {
                            mapUrl = base.resolve(it).toString()
                        }
                    line == "#EXT-X-ENDLIST" -> endList = true
                    !line.startsWith("#") -> {
                        val currentSequence = mediaSequence + sequence
                        sequence++
                        if (currentSequence in downloadedSequences) continue

                        val bytes = fetchBytes(base.resolve(line).toString())
                        if (output == null) {
                            val isTs = bytes.size > 376 &&
                                bytes[0].toInt() == 0x47 &&
                                bytes[188].toInt() == 0x47
                            val extension = if (isTs) "ts" else "mp4"
                            output = File(
                                dir,
                                RecordingFileNameGenerator.generate(
                                    stationName,
                                    timestamp,
                                    extension
                                )
                            )
                            if (extension == "mp4" && mapUrl != null) {
                                FileOutputStream(output!!, false).use { out ->
                                    out.write(fetchBytes(mapUrl!!))
                                }
                            }
                        }

                        FileOutputStream(output!!, true).use { out ->
                            out.write(bytes)
                        }
                        downloadedSequences += currentSequence
                    }
                }
            }

            if (endList) break
            if (!shouldStop()) Thread.sleep(targetDurationMillis / 2L)
        }

        val file = output ?: error("لم تصل أي مقاطع HLS صالحة.")
        if (!file.exists() || file.length() < 4096L) {
            file.delete()
            error("ملف HLS الناتج غير صالح.")
        }

        return RecordingResult(
            file = file,
            durationMillis = (System.currentTimeMillis() - started).coerceAtLeast(1L)
        )
    }

    private fun fetchText(url: String): String {
        val connection = fetchConnection(url)
        return try {
            if (connection.responseCode !in 200..299) {
                error("HLS HTTP " + connection.responseCode)
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchBytes(url: String): ByteArray {
        val connection = fetchConnection(url)
        return try {
            if (connection.responseCode !in 200..299) {
                error("HLS segment HTTP " + connection.responseCode)
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchConnection(url: String): HttpURLConnection =
        (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "TMFM/1.0 Android")
        }
}
