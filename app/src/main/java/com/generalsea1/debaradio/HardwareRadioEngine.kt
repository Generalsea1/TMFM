package com.generalsea1.tmfm

enum class HardwareAccessState {
    UNKNOWN,
    NO_TUNER,
    SYSTEM_ONLY,
    AVAILABLE
}

/**
 * Hardware radio is deliberately isolated from the Internet radio engine.
 * Availability is evidence-driven: AVAILABLE is reserved for a proven tuner session.
 */
interface HardwareRadioEngine {
    val accessState: HardwareAccessState
    val reason: String
    fun close()
}

/** Used on ordinary APK installs until a real, supported tuner session is proven. */
class UnavailableEngine(
    override val accessState: HardwareAccessState,
    override val reason: String
) : HardwareRadioEngine {
    override fun close() = Unit
}
