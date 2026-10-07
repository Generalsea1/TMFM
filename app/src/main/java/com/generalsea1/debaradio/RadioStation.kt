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
    val broadcastType: BroadcastType = if (streamUrl != null) BroadcastType.INTERNET else BroadcastType.DIRECTORY_ONLY,
    val frequencyVerified: Boolean = false,
    val streamVerified: Boolean = false,
    val streamVerifiedAt: String? = null,
    val hardwareAccessState: HardwareAccessState = HardwareAccessState.UNKNOWN,
    val isOnline: Boolean = true,
    val isVerified: Boolean = false,
    val isIslamic: Boolean = false,
    val isChristian: Boolean = false,
    val verificationStatus: String = "unverified",
    val lastVerified: String? = null,
    val source: String? = null,
    val notes: String? = null,
    val regionalAvailability: String? = null,
    val streamCodec: String? = null,
    val streamBitrateKbps: Int? = null,
    val streamConnectMs: Long? = null,
    val streamVerificationReason: String? = null,
    val streamConsecutiveFailures: Int = 0
) {
    val internetPlayable: Boolean
        get() = isOnline && !streamUrl.isNullOrBlank() && streamVerified
}
