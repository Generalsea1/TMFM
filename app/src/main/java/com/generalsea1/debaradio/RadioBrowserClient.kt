package com.generalsea1.tmfm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

data class RadioCountry(
    val code: String,
    val name: String,
    val stationCount: Int
)

class RadioBrowserClient {
    private val cachedBases = AtomicReference<List<String>>(emptyList())

    suspend fun fetchPopularByCountry(countryCode: String, limit: Int = 200): List<RadioStation> =
        withContext(Dispatchers.IO) {
            fetchJsonFromMirrors(
                "/json/stations/bycountrycodeexact/" + encode(countryCode.uppercase(Locale.ROOT)),
                "hidebroken=true&limit=" + limit + "&order=votes&reverse=true"
            ).let(::parseStations)
        }

    suspend fun fetchCountries(limit: Int = 150): List<RadioCountry> =
        withContext(Dispatchers.IO) {
            fetchJsonFromMirrors(
                "/json/countries",
                "hidebroken=true&limit=" + limit + "&order=stationcount&reverse=true"
            ).let(::parseCountries)
        }

    suspend fun search(query: String, countryCode: String? = null, limit: Int = 150): List<RadioStation> =
        coroutineScope {
            val normalized = query.trim()
            if (normalized.isBlank()) return@coroutineScope emptyList()

            listOf("name", "language", "tag", "country").map { field ->
                async(Dispatchers.IO) {
                    val params = buildList {
                        add(field + "=" + encode(normalized))
                        countryCode?.takeIf { it.isNotBlank() }?.let {
                            add("countrycode=" + encode(it.uppercase(Locale.ROOT)))
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
            }.awaitAll().flatten().distinctBy { it.id }.take(limit)
        }

    suspend fun countClick(stationUuid: String) = withContext(Dispatchers.IO) {
        val uuid = stationUuid.removePrefix("rb-")
        runCatching {
            requestText(resolveBase() + "/json/url/" + encode(uuid), "GET")
        }
    }

    private suspend fun fetchJsonFromMirrors(path: String, query: String): String {
        var lastError: Throwable? = null
        for (base in bases()) {
            runCatching {
                return requestText(base + path + "?" + query, "GET")
            }.onFailure { lastError = it }
        }
        throw IllegalStateException(
            "دليل الراديو الخارجي غير متاح حاليًا" +
                (lastError?.message?.let { ": " + it } ?: "")
        )
    }

    private suspend fun bases(): List<String> {
        val existing = cachedBases.get()
        if (existing.isNotEmpty()) return existing

        val discovered = runCatching {
            requestText(
                "https://all.api.radio-browser.info/json/servers",
                "GET"
            ).let(::parseServers).map { "https://" + it }
        }.getOrElse { emptyList() }

        return (
            discovered + listOf(
                "https://de1.api.radio-browser.info",
                "https://nl1.api.radio-browser.info"
            )
        ).distinct().also { cachedBases.compareAndSet(emptyList(), it) }
    }

    private suspend fun resolveBase(): String = bases().first()

    private fun parseServers(raw: String): List<String> {
        val json = JSONArray(raw)
        return buildList(json.length()) {
            for (i in 0 until json.length()) {
                json.optJSONObject(i)?.optString("name")
                    ?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun parseCountries(raw: String): List<RadioCountry> {
        val json = JSONArray(raw)
        return buildList(json.length()) {
            for (i in 0 until json.length()) {
                val o = json.optJSONObject(i) ?: continue
                val code = o.optString("iso_3166_1").trim().uppercase(Locale.ROOT)
                val name = o.optString("name").trim()
                if (code.isBlank() || name.isBlank()) continue
                add(RadioCountry(code, name, o.optInt("stationcount", 0)))
            }
        }.sortedByDescending { it.stationCount }
    }

    private fun parseStations(raw: String): List<RadioStation> {
        val json = JSONArray(raw)
        return buildList(json.length()) {
            for (i in 0 until json.length()) {
                val o = json.optJSONObject(i) ?: continue
                val uuid = o.optString("stationuuid").takeIf { it.isNotBlank() } ?: continue

                val streamUrl = listOf(
                    o.optString("url_resolved"),
                    o.optString("url")
                ).firstOrNull { it.startsWith("https://", true) } ?: continue

                if (o.optInt("lastcheckok", 1) != 1) continue

                val codec = o.optString("codec").uppercase(Locale.US)
                val hls = o.optInt("hls", 0) == 1
                val station = RadioStation(
                    id = "rb-" + uuid,
                    name = o.optString("name").trim().ifBlank { "محطة غير مسماة" },
                    nameEnglish = o.optString("name").trim().ifBlank { null },
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
                    officialUrl = o.optString("homepage").takeIf { it.startsWith("https://", true) },
                    logoUrl = o.optString("favicon").takeIf { it.startsWith("https://", true) },
                    language = o.optString("language").takeIf { it.isNotBlank() },
                    category = o.optString("tags").takeIf { it.isNotBlank() },
                    broadcastType = BroadcastType.INTERNET,
                    frequencyVerified = false,
                    streamVerified = false,
                    hardwareAccessState = HardwareAccessState.UNKNOWN,
                    isOnline = true,
                    isVerified = false,
                    verificationStatus = "radio_browser_unverified",
                    lastVerified = o.optString("lastchecktime_iso8601").takeIf { it.isNotBlank() },
                    source = "Radio Browser",
                    notes = "اكتشاف خارجي غير موثق من TMFM."
                )
                if (RadioCatalogPolicy.allow(station)) add(station)
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
                "TMFM/0.3 Android; contact=https://github.com/Generalsea1/TMFM"
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
