package com.generalsea1.tmfm

enum class BroadcastType {
    HARDWARE_FM,
    INTERNET,
    HYBRID,
    DIRECTORY_ONLY
}

enum class StationClassification {
    PLAYABLE_INTERNET,
    VERIFIED_FREQUENCY,
    DIRECTORY_REFERENCE,
    UNVERIFIED,
    OFFLINE
}

fun RadioStation.classification(): StationClassification = when {
    !isOnline && streamUrl != null -> StationClassification.OFFLINE
    internetPlayable -> StationClassification.PLAYABLE_INTERNET
    frequencyMhz != null && frequencyVerified -> StationClassification.VERIFIED_FREQUENCY
    streamUrl != null -> StationClassification.UNVERIFIED
    frequencyMhz != null && source != null -> StationClassification.DIRECTORY_REFERENCE
    else -> StationClassification.UNVERIFIED
}

fun RadioStation.hardwarePlayableOn(status: HardwareTunerStatus): Boolean =
    frequencyMhz != null &&
        (broadcastType == BroadcastType.HARDWARE_FM || broadcastType == BroadcastType.HYBRID) &&
        status.accessState == HardwareAccessState.AVAILABLE &&
        status.tunerSessionVerified
