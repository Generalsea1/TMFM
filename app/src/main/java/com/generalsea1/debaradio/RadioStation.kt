package com.generalsea1.tmfm

data class RadioStation(
    val id: String,
    val name: String,
    val nameArabic: String? = null,
    val nameEnglish: String? = null,
    val countryCode: String,
    val countryName: String,
    val city: String?,
    val frequencyMhz: Double?,
    val band: String?,
    val streamUrl: String?,
    val streamType: String?,
    val officialUrl: String?,
    val logoUrl: String?,
    val language: String?,
    val category: String?,
    val stationType: String?,
    val isHardware: Boolean,
    val isOnline: Boolean,
    val isVerified: Boolean,
    val isIslamic: Boolean = false,
    val isChristian: Boolean = false,
    val verificationStatus: String = "unverified",
    val lastVerified: String? = null,
    val source: String? = null,
    val notes: String? = null,
    val regionalAvailability: String? = null
) {
    val isUserVisible: Boolean
        get() = isVerified || verificationStatus.equals("frequency_reference", ignoreCase = true)

    val internetPlayable: Boolean
        get() = isVerified && isOnline && !streamUrl.isNullOrBlank()

    val directStreamRecordable: Boolean
        get() = internetPlayable &&
            streamType?.uppercase() in setOf("MP3", "AAC") &&
            !streamUrl.orEmpty().contains(".m3u8", ignoreCase = true)

    val broadcastType: String
        get() = when {
            frequencyMhz != null && internetPlayable -> "HYBRID"
            frequencyMhz != null -> "HARDWARE_FM"
            internetPlayable -> "INTERNET"
            else -> "DIRECTORY_ONLY"
        }
}