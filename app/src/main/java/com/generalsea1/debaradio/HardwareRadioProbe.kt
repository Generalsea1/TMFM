package com.generalsea1.debaradio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

enum class HardwareRadioAvailability {
    AVAILABLE,
    NOT_SUPPORTED,
    NOT_AUTHORIZED,
    FAILED
}

data class HardwareRadioStatus(
    val availability: HardwareRadioAvailability,
    val fm: Boolean,
    val am: Boolean,
    val detail: String
) {
    val accessible: Boolean
        get() = availability == HardwareRadioAvailability.AVAILABLE
}

object HardwareRadioProbe {

    fun detect(context: Context): HardwareRadioStatus {
        if (Build.VERSION.SDK_INT < 28) {
            return unavailable(
                HardwareRadioAvailability.NOT_SUPPORTED,
                "Broadcast Radio API غير متاح على إصدار Android هذا."
            )
        }

        val permissionState = context.checkSelfPermission(Manifest.permission.ACCESS_BROADCAST_RADIO)
        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            return unavailable(
                HardwareRadioAvailability.NOT_AUTHORIZED,
                "هذا الجهاز/التثبيت لا يمنح التطبيق صلاحية الوصول إلى tuner الهوائي."
            )
        }

        return try {
            val service = context.getSystemService("broadcastradio")
                ?: return unavailable(
                    HardwareRadioAvailability.NOT_SUPPORTED,
                    "لا يوجد Broadcast Radio service متاح."
                )

            val managerClass = Class.forName("android.hardware.radio.RadioManager")
            val listModules = managerClass.getMethod("listModules", List::class.java)
            val modules = mutableListOf<Any>()
            val result = listModules.invoke(service, modules) as? Int ?: -1

            if (result != 0 || modules.isEmpty()) {
                unavailable(
                    HardwareRadioAvailability.NOT_SUPPORTED,
                    "لا توجد وحدة tuner قابلة للوصول."
                )
            } else {
                var fm = false
                var am = false

                modules.forEach { module ->
                    val bands = module.javaClass
                        .getMethod("getBands")
                        .invoke(module) as? Array<*>
                        ?: emptyArray<Any?>()

                    bands.forEach { band ->
                        val type = band?.javaClass?.getMethod("getType")?.invoke(band) as? Int
                        when (type) {
                            0 -> am = true
                            1 -> fm = true
                        }
                    }
                }

                if (!fm && !am) {
                    unavailable(
                        HardwareRadioAvailability.NOT_SUPPORTED,
                        "تم العثور على خدمة Broadcast Radio دون نطاق FM/AM قابل للاستخدام."
                    )
                } else {
                    HardwareRadioStatus(
                        availability = HardwareRadioAvailability.AVAILABLE,
                        fm = fm,
                        am = am,
                        detail = "تم اكتشاف tuner حقيقي متاح للنظام والتطبيق."
                    )
                }
            }
        } catch (_: SecurityException) {
            unavailable(
                HardwareRadioAvailability.NOT_AUTHORIZED,
                "النظام يمنع تطبيقات الطرف الثالث من الوصول إلى tuner الهوائي."
            )
        } catch (_: ReflectiveOperationException) {
            unavailable(
                HardwareRadioAvailability.FAILED,
                "تعذر استخدام واجهة Broadcast Radio على هذا الجهاز."
            )
        } catch (_: Throwable) {
            unavailable(
                HardwareRadioAvailability.FAILED,
                "تعذر تحديد قدرات الراديو الهوائي بأمان."
            )
        }
    }

    private fun unavailable(
        availability: HardwareRadioAvailability,
        detail: String
    ) = HardwareRadioStatus(
        availability = availability,
        fm = false,
        am = false,
        detail = detail
    )
}
