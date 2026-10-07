package com.generalsea1.tmfm

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object HardwareRadioProbe {
    fun detect(context: Context): HardwareTunerStatus {
        val pm = context.packageManager
        val featurePresent = pm.hasSystemFeature("android.hardware.broadcastradio")
        val systemRadio = SystemRadioLauncher.findSystemRadioApp(context)

        if (Build.VERSION.SDK_INT < 28) {
            return HardwareTunerStatus(
                accessState = HardwareAccessState.NO_TUNER,
                featurePresent = featurePresent,
                tunerSessionVerified = false,
                systemRadioPackage = systemRadio?.packageName,
                detail = "إصدار Android هذا لا يثبت وجود واجهة Broadcast Radio قابلة للاستخدام من TMFM."
            )
        }

        if (!featurePresent) {
            return HardwareTunerStatus(
                accessState = HardwareAccessState.NO_TUNER,
                featurePresent = false,
                tunerSessionVerified = false,
                systemRadioPackage = systemRadio?.packageName,
                detail = if (systemRadio == null) {
                    "لم يعلن النظام عن Broadcast Radio، ولم يُعثر على تطبيق FM نظامي."
                } else {
                    "لم يعلن النظام عن Broadcast Radio، لكن يوجد تطبيق FM نظامي: " +
                        systemRadio.label + "."
                }
            )
        }

        val accessGranted = runCatching {
            context.checkSelfPermission("android.permission.ACCESS_BROADCAST_RADIO") ==
                PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        return if (accessGranted) {
            HardwareTunerStatus(
                accessState = HardwareAccessState.AVAILABLE,
                featurePresent = true,
                tunerSessionVerified = false,
                systemRadioPackage = systemRadio?.packageName,
                detail = "الوصول مُعلن، لكن جلسة tuner مع callback حقيقي لم تثبت بعد؛ لا يُعلن TMFM عن FM قابل للتحكم."
            )
        } else {
            HardwareTunerStatus(
                accessState = HardwareAccessState.SYSTEM_ONLY,
                featurePresent = true,
                tunerSessionVerified = false,
                systemRadioPackage = systemRadio?.packageName,
                detail = if (systemRadio == null) {
                    "الجهاز يعلن عن Broadcast Radio، لكن التحكم في الـtuner غير متاح لتطبيق TMFM العادي."
                } else {
                    "الجهاز يعلن عن Broadcast Radio، لكن التحكم في الـtuner غير متاح لـTMFM العادي. يمكن تجربة تطبيق FM النظامي: " +
                        systemRadio.label + "."
                }
            )
        }
    }

    fun engine(context: Context): HardwareRadioEngine =
        UnavailableEngine(
            when (detect(context).accessState) {
                HardwareAccessState.NO_TUNER ->
                    "الراديو الهوائي غير متاح على هذا الجهاز."
                HardwareAccessState.SYSTEM_ONLY ->
                    "FM موجود أو معلن من النظام، لكن التحكم الداخلي محجوز لمكوّن النظام/OEM."
                HardwareAccessState.AVAILABLE ->
                    "الوصول مُعلن، لكن جلسة tuner فعلية لم تُثبت بعد."
                HardwareAccessState.UNKNOWN ->
                    "حالة FM غير معروفة."
            }
        )
}
