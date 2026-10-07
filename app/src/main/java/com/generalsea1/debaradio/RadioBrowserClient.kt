package com.generalsea1.debaradio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URI
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

class RadioBrowserClient {

    private val cachedBases = AtomicReference<List<String>>(emptyList())

    suspend fun fetchPopularByCountry(
        countryCode: String,
        limit: Int = 120
    ): List<RadioStation> = withContext(Dispatchers.IO) {
        fetchJsonFromMirrors(
            path = "/json/stations/bycountrycodeexact/" + encode(countryCode),
            query = "hidebroken=true&limit=" + limit + "&order=votes&reverse=true"
        ).let(::parseStations)
    }

    suspend fun search(
        query: String,
        countryCode: String? = null,
        limit: Int = 100
    ): List<RadioStation> = coroutineScope {
        val normalized = query.trim()
        if (normalized.isBlank()) return@coroutineScope emptyList()

        val queries = listOf("name", "language", "tag", "country").map { field ->
            async(Dispatchers.IO) {
                val params = buildList {
                    add(field + "=" + encode(normalized))
                    countryCode?.takeIf { it.isNotBlank() }?.let {
                        add("countrycode=" + encode(it))
                    }
                    add("hidebroken=true")
                    add("limit=" + limit)
                    add("order=votes")
                    add("reverse=true")
                }.joinToString("&")

                runCatching {
                    fetchJsonFromMirrors("/json/stations/search", params).let(::parseStations)
                }.getOrElse { emptyList() }
            }
        }

        queries.awaitAll()
            .flatten()
            .distinctBy { it.id }
            .take(limit)
    }

    suspend fun countClick(stationUuid: String) = withContext(Dispatchers.IO) {
        val uuid = stationUuid.removePrefix("rb-")
        runCatching {
            val base = resolveBase()
            requestText(base + "/json/url/" + encode(uuid), "GET")
        }
    }

    private suspend fun fetchJsonFromMirrors(path: String, query: String): String {
        var lastError: Throwable? = null

        for (base in bases()) {
            try {
                return requestText(base + path + "?" + query, "GET")
            } catch (t: Throwable) {
                lastError = t
            }
        }

        throw IllegalStateException(
            "Radio Browser غير متاح حاليًا" +
                (lastError?.message?.let { ": " + it } ?: "")
        )
    }

    private suspend fun bases(): List<String> {
        val existing = cachedBases.get()
        if (existing.isNotEmpty()) return existing

        val discovered = runCatching {
            requestText("https://all.api.radio-browser.info/json/servers", "GET")
                .let(::parseServers)
                .map { "https://" + it }
        }.getOrElse { emptyList() }

        val fallback = listOf(
            "https://de1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info"
        )

        return (discovered + fallback)
            .distinct()
            .also { cachedBases.compareAndSet(emptyList(), it) }
    }

    private suspend fun resolveBase(): String = bases().first()

    private fun parseServers(raw: String): List<String> {
        val json = JSONArray(raw)
        return buildList(json.length()) {
            for (i in 0 until json.length()) {
                json.optJSONObject(i)?.optString("name")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }
    }

    private fun parseStations(raw: String): List<RadioStation> {
        val json = JSONArray(raw)
        return buildList(json.length()) {
            for (i in 0 until json.length()) {
                val o = json.getJSONObject(i)
                val uuid = o.optString("stationuuid").takeIf { it.isNotBlank() } ?: continue
                val streamUrl = o.optString("url_resolved").takeIf { it.isNotBlank() }
                    ?: o.optString("url").takeIf { it.isNotBlank() }
                    ?: continue
                if (o.optInt("lastcheckok", 1) != 1) continue

                val codec = o.optString("codec").uppercase(Locale.US)
                val hls = o.optInt("hls", 0) == 1
                add(
                    RadioStation(
                        id = "rb-" + uuid,
                        name = o.optString("name").trim().ifBlank { "Radio Browser Station" },
                        countryCode = o.optString("countrycode").trim().uppercase(Locale.US),
                        countryName = o.optString("country").trim().ifBlank { "Unknown" },
                        city = o.optString("state").takeIf { it.isNotBlank() },
                        frequencyMhz = null,
                        band = null,
                        streamUrl = streamUrl,
                        streamType = if (hls) "HLS" else when (codec) {
                            "MP3" -> "MP3"
                            "AAC", "AAC+" -> "AAC"
                            else -> "OTHER"
                        },
                        officialUrl = o.optString("homepage").takeIf { it.isNotBlank() },
                        logoUrl = o.optString("favicon").takeIf { it.isNotBlank() },
                        language = o.optString("language").takeIf { it.isNotBlank() },
                        category = o.optString("tags").takeIf { it.isNotBlank() },
                        isHardware = false,
                        isOnline = true,
                        isVerified = false,
                        verificationStatus = "radio_browser",
                        lastVerified = o.optString("lastchecktime_iso8601").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    private fun requestText(url: String, method: String): String {
        val connection = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                "TMFM-Radio/0.2 Android; contact=https://github.com/Generalsea1/TMFM"
            )
        }

        try {
            if (connection.responseCode !in 200..299) {
                error("HTTP " + connection.responseCode)
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
