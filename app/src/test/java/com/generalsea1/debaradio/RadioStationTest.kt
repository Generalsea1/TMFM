package com.generalsea1.tmfm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStationTest {
    @Test
    fun quranStationIsAlwaysBlocked() {
        val station = RadioStation(
            id = "blocked",
            name = "Quran Radio",
            countryCode = "EG",
            countryName = "مصر",
            city = "Cairo",
            frequencyMhz = 98.2,
            band = "FM",
            streamUrl = "https://example.com/a",
            streamType = "MP3",
            officialUrl = null,
            logoUrl = null,
            language = "Arabic",
            category = "Quran",
            stationType = "FM",
            isHardware = true,
            isOnline = true,
            isVerified = true
        )
        assertFalse(RadioCatalogPolicy.allow(station))
    }

    @Test
    fun explicitIslamicFlagIsAlwaysBlocked() {
        val station = RadioStation(
            id = "blocked",
            name = "Religious Radio",
            countryCode = "EG",
            countryName = "مصر",
            city = "Cairo",
            frequencyMhz = null,
            band = null,
            streamUrl = "https://example.com/a",
            streamType = "MP3",
            officialUrl = null,
            logoUrl = null,
            language = "Arabic",
            category = "Religious",
            stationType = "Internet",
            isHardware = false,
            isOnline = true,
            isVerified = true,
            isIslamic = true
        )
        assertFalse(RadioCatalogPolicy.allow(station))
    }

    @Test
    fun christianStationIsAllowed() {
        val station = RadioStation(
            id = "christian",
            name = "Christian Radio",
            countryCode = "EG",
            countryName = "مصر",
            city = "Cairo",
            frequencyMhz = null,
            band = null,
            streamUrl = "https://example.com/a",
            streamType = "MP3",
            officialUrl = null,
            logoUrl = null,
            language = "Arabic",
            category = "Christian",
            stationType = "Internet",
            isHardware = false,
            isOnline = true,
            isVerified = true,
            isChristian = true
        )
        assertTrue(RadioCatalogPolicy.allow(station))
    }
}
