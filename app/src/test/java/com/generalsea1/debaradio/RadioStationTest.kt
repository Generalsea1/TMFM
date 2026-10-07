package com.generalsea1.debaradio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStationTest {
    @Test
    fun stationMetadataIsPreserved() {
        val station = RadioStation(
            id = "test",
            name = "Test FM",
            countryCode = "EG",
            countryName = "مصر",
            city = "Cairo",
            frequencyMhz = 90.9,
            band = "FM",
            streamUrl = "https://example.com/live",
            streamType = "MP3",
            officialUrl = "https://example.com",
            language = "العربية",
            category = "Music",
            isHardware = false,
            isOnline = true,
            isVerified = true,
            verificationStatus = "verified",
            lastVerified = "2026-10-07T00:00:00Z"
        )

        assertEquals("Test FM", station.name)
        assertEquals(90.9, station.frequencyMhz!!, 0.001)
        assertTrue(station.isVerified)
    }
}
