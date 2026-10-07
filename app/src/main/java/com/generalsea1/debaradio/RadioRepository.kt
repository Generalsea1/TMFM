package com.generalsea1.debaradio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI

class RadioRepository {

    suspend fun fetchStations(): List<RadioStation> = withContext(Dispatchers.IO) {
        val uri = URI.create(
            SupabaseConfig.BASE_URL +
                "/rest/v1/radio_stations" +
                "?select=id,name,country_code,country_name,city,frequency_mhz,band,stream_url,stream_type,official_url,language,category,is_hardware,is_online,is_verified,verification_status,last_verified" +
                "&is_verified=eq.true&is_online=eq.true&order=sort_order.asc,name.asc"
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
            parseStations(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun parseStations(raw: String): List<RadioStation> {
        val json = JSONArray(raw)
        return buildList(json.length()) {
            for (i in 0 until json.length()) {
                val o = json.getJSONObject(i)
                add(
                    RadioStation(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        countryCode = o.getString("country_code"),
                        countryName = o.getString("country_name"),
                        city = o.optString("city").takeIf { it.isNotBlank() },
                        frequencyMhz = if (o.isNull("frequency_mhz")) null else o.getDouble("frequency_mhz"),
                        band = o.optString("band").takeIf { it.isNotBlank() },
                        streamUrl = o.optString("stream_url").takeIf { it.isNotBlank() },
                        streamType = o.optString("stream_type").takeIf { it.isNotBlank() },
                        officialUrl = o.optString("official_url").takeIf { it.isNotBlank() },
                        language = o.optString("language").takeIf { it.isNotBlank() },
                        category = o.optString("category").takeIf { it.isNotBlank() },
                        isHardware = o.optBoolean("is_hardware", false),
                        isOnline = o.optBoolean("is_online", true),
                        isVerified = o.optBoolean("is_verified", false),
                        verificationStatus = o.optString("verification_status", "unverified"),
                        lastVerified = o.optString("last_verified").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }
}
