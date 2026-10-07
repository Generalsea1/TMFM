package com.generalsea1.tmfm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

class DirectStreamRecorder(private val context: android.content.Context) {
    data class Result(
        val file: File,
        val durationMillis: Long,
        val bytesWritten: Long,
        val extension: String,
        val contentType: String
    )

    suspend fun record(
        stationName: String,
        streamUrl: String,
        declaredStreamType: String?,
        shouldContinue: () -> Boolean
    ): Result = withContext(Dispatchers.IO) {
        require(streamUrl.startsWith("https://", ignoreCase = true)) {
            "يجب أن يكون مسار التسجيل HTTPS."
        }
        require(!streamUrl.contains(".m3u8", ignoreCase = true)) {
            "تسجيل HLS غير متاح حاليًا؛ التشغيل يظل متاحًا."
        }
        val declaredExtension = extensionFor(declaredStreamType)
        require(declaredExtension != null) {
            "نوع البث غير مدعوم للتسجيل المباشر."
        }

        val dir = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                ?: error("مساحة التخزين الخاصة بالتطبيق غير متاحة."),
            "TMFM/Recordings"
        ).apply { mkdirs() }

        val timestamp = System.currentTimeMillis()
        val temp = File(dir, "TMFM_recording_" + timestamp + ".part")
        var target: File? = null
        var total = 0L
        var firstContentType = ""
        var attempt = 0
        var lastError: Throwable? = null
        val startedAt = System.currentTimeMillis()

        try {
            while (shouldContinue() && attempt < 3) {
                attempt++
                var connection: HttpURLConnection? = null
                try {
                    connection = (URI.create(streamUrl).toURL().openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 8_000
                        readTimeout = 12_000
                        instanceFollowRedirects = true
                        setRequestProperty("Accept", "audio/mpeg,audio/aac;q=0.9,*/*;q=0.1")
                        setRequestProperty("Icy-MetaData", "0")
                        setRequestProperty("User-Agent", "TMFM/1.0 Android")
                    }

                    if (connection.responseCode !in 200..299) error("HTTP " + connection.responseCode)
                    require(connection.url.protocol.equals("https", ignoreCase = true)) {
                        "تم تحويل رابط البث إلى اتصال غير آمن."
                    }

                    val contentType = connection.contentType.orEmpty()
                        .substringBefore(";")
                        .trim()
                        .lowercase(Locale.US)
                    if (contentType.contains("mpegurl") || contentType.contains("x-mpegurl")) {
                        error("تسجيل HLS غير متاح حاليًا؛ التشغيل يظل متاحًا.")
                    }
                    if (firstContentType.isBlank()) firstContentType = contentType

                    val extension = extensionForContentType(contentType) ?: declaredExtension
                    target = target ?: File(
                        dir,
                        RecordingFileNameGenerator.generate(stationName, timestamp, extension)
                    )

                    BufferedInputStream(connection.inputStream, 16 * 1024).use { input ->
                        FileOutputStream(temp, total > 0).use { output ->
                            total += copyAudio(
                                input = input,
                                output = output,
                                metaInt = connection.getHeaderField("icy-metaint")?.toIntOrNull() ?: -1,
                                shouldContinue = shouldContinue
                            )
                            output.flush()
                            output.fd.sync()
                        }
                    }

                    if (!shouldContinue()) break
                    // Clean EOF is a valid end of a progressive stream.
                    break
                } catch (t: Throwable) {
                    lastError = t
                    if (t.message?.contains("تسجيل HLS غير متاح") == true) throw t
                    if (!shouldContinue() || attempt >= 3) break
                    delay((attempt * 1000L).coerceAtMost(3000L))
                } finally {
                    connection?.disconnect()
                }
            }

            if (total < 4096L) {
                temp.delete()
                throw IllegalStateException(
                    lastError?.message ?: "لم يصل صوت كافٍ لتكوين ملف تسجيل صالح."
                )
            }

            val finalFile = requireNotNull(target)
            if (finalFile.exists()) finalFile.delete()
            check(temp.renameTo(finalFile)) { "تعذر حفظ ملف التسجيل النهائي." }

            Result(
                file = finalFile,
                durationMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L),
                bytesWritten = total,
                extension = finalFile.extension.lowercase(Locale.US),
                contentType = firstContentType.ifBlank { "unknown" }
            )
        } finally {
            if (temp.exists() && total < 4096L) temp.delete()
        }
    }

    private fun copyAudio(
        input: BufferedInputStream,
        output: FileOutputStream,
        metaInt: Int,
        shouldContinue: () -> Boolean
    ): Long {
        var total = 0L
        var audioRemaining = metaInt
        val buffer = ByteArray(16 * 1024)

        while (shouldContinue()) {
            if (metaInt > 0 && audioRemaining == 0) {
                val lengthUnits = input.read()
                if (lengthUnits < 0) break
                skipFully(input, lengthUnits * 16)
                audioRemaining = metaInt
                continue
            }

            val requested = if (metaInt > 0) minOf(buffer.size, audioRemaining) else buffer.size
            val read = input.read(buffer, 0, requested)
            if (read < 0) break
            if (read == 0) continue
            output.write(buffer, 0, read)
            total += read
            if (metaInt > 0) audioRemaining -= read
        }
        return total
    }

    private fun skipFully(input: BufferedInputStream, count: Int) {
        var remaining = count.toLong()
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) remaining -= skipped
            else if (input.read() >= 0) remaining-- else break
        }
    }

    private fun extensionFor(declared: String?): String? = when (declared?.uppercase(Locale.US)) {
        "MP3" -> "mp3"
        "AAC" -> "aac"
        else -> null
    }

    private fun extensionForContentType(contentType: String): String? = when {
        contentType == "audio/mpeg" || contentType == "audio/mp3" -> "mp3"
        contentType == "audio/aac" || contentType == "audio/aacp" || contentType == "audio/x-aac" -> "aac"
        else -> null
    }
}