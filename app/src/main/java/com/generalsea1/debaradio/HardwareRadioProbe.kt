package com.generalsea1.tmfm

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

enum class HardwareRadioAvailability {
    HARDWARE_PRESENT_ACCESS_DENIED,
    HARDWARE_AND_ACCESSIBLE,
    NO_HARDWARE,
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

        if (Build.VERSION.SDK_INT < 28) {
            return HardwareRadioStatus(
                HardwareRadioAvailability.NO_HARDWARE, featurePresent, false,
                false, false, false,
                "إصدار Android هذا لا يوفّر مسار Broadcast Radio المطلوب."
            )
        }

        val accessGranted =
            context.checkSelfPermission("android.permission.ACCESS_BROADCAST_RADIO") ==
                PackageManager.PERMISSION_GRANTED

        if (!featurePresent) {
            return HardwareRadioStatus(
                HardwareRadioAvailability.NO_HARDWARE, false, accessGranted,
                false, false, false,
                "لم يعلن النظام عن عتاد Broadcast Radio في هذا الجهاز."
            )
        }

        if (!accessGranted) {
            return HardwareRadioStatus(
                HardwareRadioAvailability.HARDWARE_PRESENT_ACCESS_DENIED, true, false,
                true, false, false,
                "الجهاز يعلن عن Broadcast Radio، لكن Android لا يمنح TMFM صلاحية التحكم في الـtuner؛ هذه صلاحية System/Privileged وليست صلاحية مستخدم عادية."
            )
        }

        return HardwareRadioStatus(
            HardwareRadioAvailability.HARDWARE_AND_ACCESSIBLE, true, true,
            true, false, false,
            "الطبقة موجودة ومصرح بها، لكن التحكم الكامل يحتاج تكامل OEM/System API مصرحًا به. لا يتم تشغيل ماسح وهمي."
        )
    }
}
