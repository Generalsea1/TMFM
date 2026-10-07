package com.generalsea1.tmfm

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

enum class HardwareRadioAvailability {
    SYSTEM_ONLY,
    NO_TUNER,
    UNKNOWN
}

data class HardwareRadioStatus(
    val availability: HardwareRadioAvailability,
    val featurePresent: Boolean,
    val accessGranted: Boolean,
    val fm: Boolean,
    val am: Boolean,
    val controlApiAvailable: Boolean,
    val detail: String
)

object HardwareRadioProbe {
    fun detect(context: Context): HardwareRadioStatus {
        val pm = context.packageManager
        val featurePresent = pm.hasSystemFeature("android.hardware.broadcastradio")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return HardwareRadioStatus(
                HardwareRadioAvailability.NO_TUNER,
                featurePresent,
                false,
                false,
                false,
                false,
                "لا توجد واجهة Broadcast Radio العامة المطلوبة على إصدار Android هذا."
            )
        }

        val accessGranted = context.checkSelfPermission(
            "android.permission.ACCESS_BROADCAST_RADIO"
        ) == PackageManager.PERMISSION_GRANTED

        if (!featurePresent) {
            return HardwareRadioStatus(
                HardwareRadioAvailability.NO_TUNER,
                false,
                accessGranted,
                false,
                false,
                false,
                "لا يعلن Android عن Broadcast Radio متاح للتطبيق؛ وجود FM داخل الشريحة غير مُثبت من هذه الواجهة."
            )
        }

        if (!accessGranted) {
            return HardwareRadioStatus(
                HardwareRadioAvailability.SYSTEM_ONLY,
                true,
                false,
                false,
                false,
                false,
                "يوجد Broadcast Radio معلن، لكن التحكم به محمي بصلاحية نظام. لا يدّعي TMFM وجود tuner قابل للتحكم من APK عادي."
            )
        }

        // Permission alone is not proof of a tuner session. M0 requires a real open/tune callback.
        return HardwareRadioStatus(
            HardwareRadioAvailability.UNKNOWN,
            true,
            true,
            false,
            false,
            false,
            "الصلاحية متاحة، لكن لا توجد داخل هذه الفئة أدلة على فتح tuner session أو نجاح tune callback؛ الحالة تبقى NOT VERIFIED."
        )
    }

    fun unavailableEngine(context: Context): UnavailableEngine {
        val status = detect(context)
        val accessState = when (status.availability) {
            HardwareRadioAvailability.SYSTEM_ONLY -> HardwareAccessState.SYSTEM_ONLY
            HardwareRadioAvailability.NO_TUNER -> HardwareAccessState.NO_TUNER
            HardwareRadioAvailability.UNKNOWN -> HardwareAccessState.UNKNOWN
        }
        return UnavailableEngine(accessState, status.detail)
    }
}
