package com.generalsea1.debaradio

data class RadioStation(
    val id: String,
    val name: String,
    val countryCode: String,
    val countryName: String,
    val city: String?,
    val frequencyMhz: Double?,
    val band: String?,
    val streamUrl: String?,
    val streamType: String?,
    val officialUrl: String?,
    val language: String?,
    val category: String?,
    val isHardware: Boolean,
    val isOnline: Boolean,
    val isVerified: Boolean,
    val verificationStatus: String,
    val lastVerified: String?,
)
