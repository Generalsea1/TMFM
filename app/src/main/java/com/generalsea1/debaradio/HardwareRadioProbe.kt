package com.generalsea1.debaradio

import android.content.Context
import android.os.Build

data class HardwareRadioStatus(
    val accessible: Boolean,
    val fm: Boolean,
    val am: Boolean,
    val detail: String
)

object HardwareRadioProbe {

    fun detect(context: Context): HardwareRadioStatus {
        if (Build.VERSION.SDK_INT < 24) {
            return unavailable("Android version below the Broadcast Radio API baseline.")
        }

        return try {
            val service = context.getSystemService("broadcastradio")
                ?: return unavailable("لا يوجد Broadcast Radio service متاح للتطبيق.")

            val managerClass = Class.forName("android.hardware.radio.RadioManager")
            val listModules = managerClass.getMethod("listModules", List::class.java)
            val modules = mutableListOf<Any>()
            val result = listModules.invoke(service, modules) as? Int ?: -1

            if (result != 0 || modules.isEmpty()) {
                unavailable("لا توجد وحدة tuner قابلة للوصول لتطبيق طرف ثالث.")
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
                            1 -> fm = true
                            0 -> am = true
                        }
                    }
                }

                HardwareRadioStatus(
                    accessible = fm || am,
                    fm = fm,
                    am = am,
                    detail = "تم الوصول إلى Broadcast Radio في النظام."
                )
            }
        } catch (_: SecurityException) {
            unavailable("الجهاز يمنع تطبيقات الطرف الثالث من الوصول إلى tuner الهوائي.")
        } catch (_: ReflectiveOperationException) {
            unavailable("لا توجد واجهة Broadcast Radio قابلة للاستخدام من التطبيق.")
        } catch (_: Throwable) {
            unavailable("تعذر تحديد قدرات الراديو الهوائي بأمان.")
        }
    }

    private fun unavailable(detail: String) =
        HardwareRadioStatus(false, false, false, detail)
}
