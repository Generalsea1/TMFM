package com.generalsea1.tmfm

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

class RadioRepository(
    private val radioBrowser: RadioBrowserClient = RadioBrowserClient()
) {
    suspend fun fetchEgyptStations(): List<RadioStation> = fetchCountryStations("EG")

    suspend fun fetchCountryStations(countryCode: String): List<RadioStation> =
        withContext(Dispatchers.IO) {
            val code = countryCode.trim().uppercase(Locale.ROOT)
            val bundled = if (code == "EG") BundledCatalog.egypt
            else BundledCatalog.globalBaseline.filter { it.countryCode == code }

            val curated = runCatching { fetchSupabaseStations(code) }.getOrElse { emptyList() }
            val discovered = runCatching {
                radioBrowser.fetchPopularByCountry(code, 200)
            }.getOrElse { emptyList() }

            mergeStations(mergeStations(bundled, curated), discovered)
                .also { if (it.isEmpty()) error("تعذر تحميل دليل المحطات حاليًا.") }
        }

    suspend fun fetchCountries(): List<RadioCountry> =
        withContext(Dispatchers.IO) { radioBrowser.fetchCountries() }

    suspend fun searchStations(query: String): List<RadioStation> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()

        val bundled = BundledCatalog.egypt + BundledCatalog.globalBaseline
        val curated = runCatching { fetchSupabaseStations(null) }.getOrElse { emptyList() }
        val discovered = runCatching {
            radioBrowser.search(normalized, limit = 150)
        }.getOrElse { emptyList() }

        return mergeStations(mergeStations(bundled, curated), discovered).filter { station ->
            val haystack = listOfNotNull(
                station.name,
                station.nameArabic,
                station.nameEnglish,
                station.countryName,
                station.city,
                station.frequencyMhz?.toString(),
                station.language,
                station.category
            ).joinToString(" ")
            RadioCatalogPolicy.allow(station) &&
                station.classification() != StationClassification.UNVERIFIED &&
                haystack.contains(normalized, ignoreCase = true)
        }
    }

    suspend fun markRadioBrowserClick(stationId: String) {
        if (stationId.startsWith("rb-")) radioBrowser.countClick(stationId)
    }

    private suspend fun fetchSupabaseStations(countryCode: String?): List<RadioStation> =
        withContext(Dispatchers.IO) {
            val params = buildList {
                add("select=*")
                add("is_islamic=eq.false")
                countryCode?.let { add("country_code=eq." + Uri.encode(it.uppercase(Locale.ROOT))) }
                add("order=sort_order.asc,name.asc")
            }

            val uri = URI.create(
                SupabaseConfig.BASE_URL + "/rest/v1/radio_stations?" + params.joinToString("&")
            )

            val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                setRequestProperty("Accept", "application/json")
            }

            try {
                if (connection.responseCode !in 200..299) error("Catalog HTTP " + connection.responseCode)
                parseSupabaseStations(
                    connection.inputStream.bufferedReader().use { it.readText() }
                )
            } finally {
                connection.disconnect()
            }
        }

    private fun parseSupabaseStations(raw: String): List<RadioStation> {
        val json = JSONArray(raw)
        return buildList(json.length()) {
            for (i in 0 until json.length()) {
                val o = json.getJSONObject(i)
                val streamUrl = o.optString("stream_url")
                    .takeIf { it.startsWith("https://", true) }
                val frequency = if (o.isNull("frequency_mhz")) null
                else o.optDouble("frequency_mhz", Double.NaN).takeUnless { it.isNaN() }

                val oldVerified = o.optBoolean("is_verified", false)
                val frequencyVerified = if (o.has("frequency_verified")) {
                    o.optBoolean("frequency_verified", false)
                } else {
                    oldVerified && frequency != null && streamUrl == null
                }
                val streamVerified = o.optBoolean("stream_verified", false)

                val explicitType = o.optString("broadcast_type").takeIf { it.isNotBlank() }
                val broadcastType = explicitType?.let {
                    runCatching { BroadcastType.valueOf(it) }.getOrNull()
                } ?: when {
                    streamUrl != null && frequency != null -> BroadcastType.HYBRID
                    streamUrl != null -> BroadcastType.INTERNET
                    frequency != null -> BroadcastType.HARDWARE_FM
                    else -> BroadcastType.DIRECTORY_ONLY
                }

                val status = runCatching {
                    HardwareAccessState.valueOf(o.optString("hardware_access_state", "UNKNOWN"))
                }.getOrDefault(HardwareAccessState.UNKNOWN)

                add(
                    RadioStation(
                        id = o.optString("id"),
                        name = o.optString("name").ifBlank { "محطة غير مسماة" },
                        nameArabic = o.optString("name_ar").takeIf { it.isNotBlank() },
                        nameEnglish = o.optString("name_en").takeIf { it.isNotBlank() },
                        countryCode = o.optString("country_code").uppercase(Locale.ROOT),
                        countryName = o.optString("country_name").ifBlank { "غير متاح" },
                        city = o.optString("city").takeIf { it.isNotBlank() },
                        frequencyMhz = frequency,
                        band = o.optString("band").takeIf { it.isNotBlank() },
                        streamUrl = streamUrl,
                        streamType = o.optString("stream_type").takeIf { it.isNotBlank() },
                        officialUrl = o.optString("official_url").takeIf { it.startsWith("https://", true) },
                        logoUrl = o.optString("logo_url").takeIf { it.startsWith("https://", true) },
                        language = o.optString("language").takeIf { it.isNotBlank() },
                        category = o.optString("category").takeIf { it.isNotBlank() },
                        broadcastType = broadcastType,
                        frequencyVerified = frequencyVerified,
                        streamVerified = streamVerified,
                        streamVerifiedAt = o.optString("stream_verified_at").takeIf { it.isNotBlank() },
                        hardwareAccessState = status,
                        isOnline = o.optBoolean("is_online", true),
                        isVerified = oldVerified,
                        isIslamic = o.optBoolean("is_islamic", false),
                        isChristian = o.optBoolean("is_christian", false),
                        verificationStatus = o.optString("verification_status", "unverified"),
                        lastVerified = o.optString("last_verified").takeIf { it.isNotBlank() },
                        source = o.optString("source").takeIf { it.isNotBlank() },
                        notes = o.optString("notes").takeIf { it.isNotBlank() },
                        regionalAvailability = o.optString("regional_availability").takeIf { it.isNotBlank() },
                        streamCodec = o.optString("stream_codec").takeIf { it.isNotBlank() },
                        streamBitrateKbps = if (o.isNull("stream_bitrate_kbps")) null else o.optInt("stream_bitrate_kbps"),
                        streamConnectMs = if (o.isNull("stream_connect_ms")) null else o.optLong("stream_connect_ms"),
                        streamVerificationReason = o.optString("stream_verification_reason").takeIf { it.isNotBlank() },
                        streamConsecutiveFailures = o.optInt("stream_consecutive_failures", 0)
                    )
                )
            }
        }.filter(RadioCatalogPolicy::allow)
    }

    private fun mergeStations(
        first: List<RadioStation>,
        second: List<RadioStation>
    ): List<RadioStation> {
        val merged = LinkedHashMap<String, RadioStation>()

        fun key(station: RadioStation): String {
            val freq = station.frequencyMhz?.let { String.format(Locale.US, "%.1f", it) }.orEmpty()
            return station.countryCode + "|" + freq + "|" + station.name.trim().lowercase(Locale.ROOT)
        }

        fun put(station: RadioStation) {
            if (!RadioCatalogPolicy.allow(station)) return
            val k = key(station)
            val old = merged[k]
            merged[k] = if (old == null) station else old.copy(
                streamUrl = old.streamUrl ?: station.streamUrl,
                streamType = old.streamType ?: station.streamType,
                officialUrl = old.officialUrl ?: station.officialUrl,
                logoUrl = old.logoUrl ?: station.logoUrl,
                isOnline = old.isOnline || station.isOnline,
                isVerified = old.isVerified || station.isVerified,
                frequencyVerified = old.frequencyVerified || station.frequencyVerified,
                streamVerified = old.streamVerified || station.streamVerified,
                streamVerifiedAt = old.streamVerifiedAt ?: station.streamVerifiedAt,
                streamCodec = old.streamCodec ?: station.streamCodec,
                streamBitrateKbps = old.streamBitrateKbps ?: station.streamBitrateKbps,
                streamConnectMs = old.streamConnectMs ?: station.streamConnectMs,
                streamVerificationReason = old.streamVerificationReason ?: station.streamVerificationReason,
                streamConsecutiveFailures =
                    maxOf(old.streamConsecutiveFailures, station.streamConsecutiveFailures),
                verificationStatus = when {
                    old.streamVerified -> old.verificationStatus
                    station.streamVerified -> station.verificationStatus
                    old.frequencyVerified -> old.verificationStatus
                    else -> station.verificationStatus
                }
            )
        }

        first.forEach(::put)
        second.forEach(::put)
        return merged.values.toList()
    }
}
