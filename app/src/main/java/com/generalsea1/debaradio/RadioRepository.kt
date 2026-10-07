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
            val bundled = if (code == "EG") {
                BundledCatalog.egypt
            } else {
                BundledCatalog.globalBaseline.filter { it.countryCode == code }
            }
            val verified = runCatching { fetchSupabaseStations(code) }.getOrElse { emptyList() }
            val remote = runCatching {
                radioBrowser.fetchPopularByCountry(code, 200)
            }.getOrElse { emptyList() }

            mergeStations(
                mergeStations(bundled, verified),
                remote
            ).also {
                if (it.isEmpty()) error("تعذر تحميل دليل المحطات حاليًا.")
            }
        }

    suspend fun fetchCountries(): List<RadioCountry> = withContext(Dispatchers.IO) {
        radioBrowser.fetchCountries()
    }

    suspend fun searchStations(query: String): List<RadioStation> {
        val normalized = query.trim()
        val bundled = BundledCatalog.egypt + BundledCatalog.globalBaseline
        val verified = runCatching { fetchSupabaseStations(null) }.getOrElse { emptyList() }
        val remote = runCatching {
            radioBrowser.search(normalized, limit = 150)
        }.getOrElse { emptyList() }

        return mergeStations(
            mergeStations(bundled, verified),
            remote
        ).filter { station ->
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
                haystack.contains(normalized, ignoreCase = true)
        }
    }

    suspend fun markRadioBrowserClick(stationId: String) {
        if (stationId.startsWith("rb-")) {
            radioBrowser.countClick(stationId)
        }
    }

    private suspend fun fetchSupabaseStations(countryCode: String?): List<RadioStation> =
        withContext(Dispatchers.IO) {
            val params = buildList {
                add("select=id,name,name_ar,name_en,country_code,country_name,city,frequency_mhz,band,stream_url,stream_type,official_url,logo_url,language,category,station_type,is_hardware,is_online,is_verified,is_islamic,is_christian,verification_status,last_verified,source,notes,regional_availability")
                add("is_verified=eq.true")
                add("is_online=eq.true")
                add("is_islamic=eq.false")
                countryCode?.let {
                    add("country_code=eq." + Uri.encode(it.uppercase(Locale.ROOT)))
                }
                add("order=sort_order.asc,name.asc")
            }

            val uri = URI.create(
                SupabaseConfig.BASE_URL +
                    "/rest/v1/radio_stations?" +
                    params.joinToString("&")
            )

            val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 10_000
                setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                setRequestProperty("Accept", "application/json")
            }

            try {
                if (connection.responseCode !in 200..299) {
                    error("Catalog HTTP " + connection.responseCode)
                }
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
                add(
                    RadioStation(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        nameArabic = o.optString("name_ar").takeIf { it.isNotBlank() },
                        nameEnglish = o.optString("name_en").takeIf { it.isNotBlank() },
                        countryCode = o.getString("country_code"),
                        countryName = o.getString("country_name"),
                        city = o.optString("city").takeIf { it.isNotBlank() },
                        frequencyMhz = if (o.isNull("frequency_mhz")) null else o.getDouble("frequency_mhz"),
                        band = o.optString("band").takeIf { it.isNotBlank() },
                        streamUrl = o.optString("stream_url").takeIf { it.startsWith("https://", true) },
                        streamType = o.optString("stream_type").takeIf { it.isNotBlank() },
                        officialUrl = o.optString("official_url").takeIf { it.startsWith("https://", true) },
                        logoUrl = o.optString("logo_url").takeIf { it.startsWith("https://", true) },
                        language = o.optString("language").takeIf { it.isNotBlank() },
                        category = o.optString("category").takeIf { it.isNotBlank() },
                        stationType = o.optString("station_type").takeIf { it.isNotBlank() },
                        isHardware = o.optBoolean("is_hardware", false),
                        isOnline = o.optBoolean("is_online", true),
                        isVerified = o.optBoolean("is_verified", false),
                        isIslamic = o.optBoolean("is_islamic", false),
                        isChristian = o.optBoolean("is_christian", false),
                        verificationStatus = o.optString("verification_status", "unverified"),
                        lastVerified = o.optString("last_verified").takeIf { it.isNotBlank() },
                        source = o.optString("source").takeIf { it.isNotBlank() },
                        notes = o.optString("notes").takeIf { it.isNotBlank() },
                        regionalAvailability = o.optString("regional_availability").takeIf { it.isNotBlank() }
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
            val freq = station.frequencyMhz?.let {
                String.format(Locale.US, "%.1f", it)
            }.orEmpty()
            val name = station.name.trim().lowercase(Locale.ROOT)
            return station.countryCode + "|" + freq + "|" + name
        }

        fun put(station: RadioStation) {
            if (!RadioCatalogPolicy.allow(station)) return
            val key = key(station)
            val old = merged[key]
            merged[key] = if (old == null) {
                station
            } else {
                old.copy(
                    streamUrl = old.streamUrl ?: station.streamUrl,
                    streamType = old.streamType ?: station.streamType,
                    officialUrl = old.officialUrl ?: station.officialUrl,
                    logoUrl = old.logoUrl ?: station.logoUrl,
                    isOnline = old.isOnline || station.isOnline,
                    isVerified = old.isVerified || station.isVerified,
                    verificationStatus =
                        if (old.isVerified) old.verificationStatus else station.verificationStatus
                )
            }
        }

        first.forEach(::put)
        second.forEach(::put)
        return merged.values.toList()
    }
}
