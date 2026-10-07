package com.generalsea1.tmfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioAvailabilityTest {
    private fun station(
        frequency: Double? = null,
        stream: String? = null,
        frequencyVerified: Boolean = false,
        streamVerified: Boolean = false,
        isOnline: Boolean = true,
        source: String? = "test"
    ) = RadioStation(
        id = "test",
        name = "Test",
        countryCode = "EG",
        countryName = "Egypt",
        city = "Cairo",
        frequencyMhz = frequency,
        band = frequency?.let { "FM" },
        streamUrl = stream,
        streamType = "MP3",
        officialUrl = null,
        logoUrl = null,
        language = "en",
        category = "music",
        broadcastType = when {
            stream != null && frequency != null -> BroadcastType.HYBRID
            stream != null -> BroadcastType.INTERNET
            frequency != null -> BroadcastType.HARDWARE_FM
            else -> BroadcastType.DIRECTORY_ONLY
        },
        frequencyVerified = frequencyVerified,
        streamVerified = streamVerified,
        isOnline = isOnline,
        source = source
    )

    @Test fun verifiedInternetIsPlayable() {
        val station = station(stream = "https://example.com/live", streamVerified = true)
        assertTrue(station.internetPlayable)
        assertEquals(StationClassification.PLAYABLE_INTERNET, station.classification())
    }

    @Test fun unverifiedStreamCannotPlay() {
        val station = station(stream = "https://example.com/live")
        assertFalse(station.internetPlayable)
        assertEquals(StationClassification.UNVERIFIED, station.classification())
    }

    @Test fun verifiedFrequencyIsNotAnInternetStream() {
        val station = station(frequency = 90.9, frequencyVerified = true)
        assertFalse(station.internetPlayable)
        assertEquals(StationClassification.VERIFIED_FREQUENCY, station.classification())
    }

    @Test fun offlineStreamCannotPlay() {
        val station = station(
            stream = "https://example.com/live",
            streamVerified = true,
            isOnline = false
        )
        assertFalse(station.internetPlayable)
        assertEquals(StationClassification.OFFLINE, station.classification())
    }
}

class RadioCatalogPolicyTest {
    private fun station(name: String, isIslamic: Boolean = false, isChristian: Boolean = false) =
        RadioStation(
            id = name,
            name = name,
            countryCode = "XX",
            countryName = "Test",
            city = null,
            frequencyMhz = null,
            band = null,
            streamUrl = null,
            streamType = null,
            officialUrl = null,
            logoUrl = null,
            category = null,
            broadcastType = BroadcastType.DIRECTORY_ONLY,
            isIslamic = isIslamic,
            isChristian = isChristian
        )

    @Test fun databaseFlagBlocks() {
        assertFalse(RadioCatalogPolicy.allow(station("Anything", isIslamic = true)))
    }

    @Test fun forbiddenSearchTextBlocks() {
        assertFalse(RadioCatalogPolicy.allow(station("Quran Radio")))
        assertFalse(RadioCatalogPolicy.allow(station("إذاعة القرآن الكريم")))
    }

    @Test fun christianStationAllowed() {
        assertTrue(RadioCatalogPolicy.allow(station("Christian Radio", isChristian = true)))
    }

    @Test fun normalStationAllowed() {
        assertTrue(RadioCatalogPolicy.allow(station("Jazz FM")))
    }
}
