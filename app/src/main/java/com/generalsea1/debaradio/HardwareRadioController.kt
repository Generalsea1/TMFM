package com.generalsea1.debaradio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.radio.ProgramSelector
import android.hardware.radio.RadioManager
import android.hardware.radio.RadioMetadata
import android.hardware.radio.RadioTuner
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.roundToInt

data class HardwareRadioSnapshot(
    val tuned: Boolean,
    val band: String,
    val frequencyMhz: Double?,
    val signalStrength: Int?,
    val stationName: String?,
    val radioText: String?,
    val stereo: Boolean,
    val digital: Boolean,
    val antennaConnected: Boolean?
)

@RequiresApi(28)
class HardwareRadioController(
    context: Context,
    private val listener: Listener
) : AutoCloseable {

    interface Listener {
        fun onProgramChanged(snapshot: HardwareRadioSnapshot)
        fun onAntennaChanged(connected: Boolean)
        fun onControlChanged(control: Boolean)
        fun onTuneFailed(message: String)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val manager: RadioManager? =
        appContext.getSystemService(RadioManager::class.java)

    private val callbackHandlerThread = HandlerThread("TMFM-HardwareRadio").apply {
        start()
    }
    private val callbackHandler = Handler(callbackHandlerThread.looper)

    private var tuner: RadioTuner? = null
    private var currentBand: Int = RadioManager.BAND_INVALID
    private var antennaConnected: Boolean? = null

    private val tunerCallback = object : RadioTuner.Callback() {
        override fun onProgramInfoChanged(info: RadioManager.ProgramInfo) {
            listener.onProgramChanged(toSnapshot(info))
        }

        override fun onAntennaState(connected: Boolean) {
            antennaConnected = connected
            listener.onAntennaChanged(connected)
        }

        override fun onControlChanged(control: Boolean) {
            listener.onControlChanged(control)
        }

        override fun onTuneFailed(result: Int, selector: ProgramSelector?) {
            listener.onTuneFailed("فشل ضبط/بحث محطة الراديو. النتيجة: $result")
        }

        override fun onError(status: Int) {
            listener.onError("حدث خطأ في الراديو الهوائي. النتيجة: $status")
        }
    }

    fun open(): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_BROADCAST_RADIO
            ) != PackageManager.PERMISSION_GRANTED
        ) return false

        if (tuner != null) return true

        val radioManager = manager ?: return false
        return runCatching {
            val modules = ArrayList<RadioManager.ModuleProperties>()
            if (radioManager.listModules(modules) != RadioManager.STATUS_OK) return false

            val module = modules.firstOrNull { props ->
                props.getBands().any { band ->
                    band.type == RadioManager.BAND_FM || band.type == RadioManager.BAND_AM
                }
            } ?: return false

            val preferredBand = module.getBands().firstOrNull {
                it.type == RadioManager.BAND_FM
            }?.type
                ?: module.getBands().firstOrNull {
                    it.type == RadioManager.BAND_AM
                }?.type
                ?: RadioManager.BAND_INVALID

            currentBand = preferredBand
            tuner = radioManager.openTuner(
                module.id,
                null,
                true,
                tunerCallback,
                callbackHandler
            )
            tuner != null
        }.getOrElse {
            tuner = null
            listener.onError(it.message ?: "تعذر فتح موالف الراديو الهوائي.")
            false
        }
    }

    fun tuneFm(frequencyMhz: Double): Boolean {
        if (frequencyMhz <= 0.0) return false
        currentBand = RadioManager.BAND_FM
        val frequencyKhz = (frequencyMhz * 1000.0).roundToInt()
        return tune(ProgramSelector.createAmFmSelector(RadioManager.BAND_FM, frequencyKhz))
    }

    fun tuneAm(frequencyKhz: Int): Boolean {
        if (frequencyKhz <= 0) return false
        currentBand = RadioManager.BAND_AM
        return tune(ProgramSelector.createAmFmSelector(RadioManager.BAND_AM, frequencyKhz))
    }

    fun stepUp(): Boolean = step(RadioTuner.DIRECTION_UP)
    fun stepDown(): Boolean = step(RadioTuner.DIRECTION_DOWN)

    fun seekUp(): Boolean = scan(RadioTuner.DIRECTION_UP)
    fun seekDown(): Boolean = scan(RadioTuner.DIRECTION_DOWN)

    fun setMuted(muted: Boolean): Boolean =
        runCatching {
            tuner?.setMute(muted) == RadioManager.STATUS_OK
        }.getOrDefault(false)

    fun cancel(): Boolean =
        runCatching {
            tuner?.cancel() == RadioManager.STATUS_OK
        }.getOrDefault(false)

    private fun tune(selector: ProgramSelector): Boolean =
        runCatching {
            tuner?.tune(selector)
            true
        }.onFailure {
            listener.onTuneFailed(it.message ?: "تعذر ضبط التردد.")
        }.getOrDefault(false)

    private fun step(direction: Int): Boolean =
        runCatching {
            tuner?.step(direction, false) == RadioManager.STATUS_OK
        }.onFailure {
            listener.onTuneFailed(it.message ?: "تعذر تغيير التردد.")
        }.getOrDefault(false)

    private fun scan(direction: Int): Boolean =
        runCatching {
            val target = tuner ?: return@runCatching false
            if (Build.VERSION.SDK_INT >= 34) {
                target.seek(direction, false) == RadioManager.STATUS_OK
            } else {
                target.scan(direction, false) == RadioManager.STATUS_OK
            }
        }.onFailure {
            listener.onTuneFailed(it.message ?: "تعذر البحث عن المحطة التالية.")
        }.getOrDefault(false)

    private fun toSnapshot(info: RadioManager.ProgramInfo): HardwareRadioSnapshot {
        val selector = info.selector
        val frequencyKhz = runCatching {
            selector.getFirstId(ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY)
        }.getOrNull()

        val band = when {
            selector.programType == ProgramSelector.PROGRAM_TYPE_FM ||
                selector.programType == ProgramSelector.PROGRAM_TYPE_FM_HD -> "FM"
            selector.programType == ProgramSelector.PROGRAM_TYPE_AM ||
                selector.programType == ProgramSelector.PROGRAM_TYPE_AM_HD -> "AM"
            currentBand == RadioManager.BAND_AM -> "AM"
            else -> "FM"
        }

        val metadata: RadioMetadata? = info.metadata
        val stationName = listOf(
            metadata?.getString(RadioMetadata.METADATA_KEY_PROGRAM_NAME),
            metadata?.getString(RadioMetadata.METADATA_KEY_RDS_PS)
        ).firstOrNull { !it.isNullOrBlank() }?.trim()

        val radioText = metadata?.getString(RadioMetadata.METADATA_KEY_RDS_RT)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return HardwareRadioSnapshot(
            tuned = info.isTuned,
            band = band,
            frequencyMhz = if (frequencyKhz != null && frequencyKhz > 0) {
                frequencyKhz / 1000.0
            } else null,
            signalStrength = info.signalStrength.coerceIn(0, 100),
            stationName = stationName,
            radioText = radioText,
            stereo = info.isStereo,
            digital = info.isDigital,
            antennaConnected = antennaConnected
        )
    }

    override fun close() {
        runCatching { tuner?.close() }
        tuner = null
        callbackHandlerThread.quitSafely()
    }
}
