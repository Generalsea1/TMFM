package com.generalsea1.tmfm

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

enum class HardwareAccessState {
    UNKNOWN,
    NO_TUNER,
    SYSTEM_ONLY,
    AVAILABLE
}

enum class HardwareEngineStatus {
    UNAVAILABLE,
    AVAILABLE
}

data class HardwareTunerStatus(
    val accessState: HardwareAccessState,
    val featurePresent: Boolean,
    val tunerSessionVerified: Boolean,
    val systemRadioPackage: String?,
    val detail: String
)

interface HardwareRadioEngine {
    val status: HardwareEngineStatus
    suspend fun tune(frequencyMhz: Double): Result<Unit>
    suspend fun seek(directionUp: Boolean): Result<Double>
    suspend fun scan(): Result<List<Double>>
    fun stop()
}

class UnavailableEngine(
    private val reason: String
) : HardwareRadioEngine {
    override val status: HardwareEngineStatus = HardwareEngineStatus.UNAVAILABLE

    override suspend fun tune(frequencyMhz: Double): Result<Unit> =
        Result.failure(UnsupportedOperationException(reason))

    override suspend fun seek(directionUp: Boolean): Result<Double> =
        Result.failure(UnsupportedOperationException(reason))

    override suspend fun scan(): Result<List<Double>> =
        Result.failure(UnsupportedOperationException(reason))

    override fun stop() = Unit
}

object SystemRadioLauncher {
    data class Target(
        val packageName: String,
        val label: String,
        val intent: Intent
    )

    fun findSystemRadioApp(context: Context): Target? {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = pm.queryIntentActivities(query, PackageManager.MATCH_ALL)

        val fmTokens = listOf(
            "fm radio", "radio fm", "fm", "radio", "راديو", "راديو fm"
        )

        return candidates
            .asSequence()
            .filter { resolve ->
                resolve.activityInfo.packageName != context.packageName &&
                    isSystemPackage(resolve.activityInfo.applicationInfo)
            }
            .mapNotNull { resolve ->
                val label = resolve.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isBlank()) return@mapNotNull null
                val normalized = label.lowercase()
                if (fmTokens.any { normalized.contains(it) }) {
                    Target(
                        packageName = resolve.activityInfo.packageName,
                        label = label,
                        intent = Intent(query)
                            .setClassName(
                                resolve.activityInfo.packageName,
                                resolve.activityInfo.name
                            )
                    )
                } else null
            }
            .sortedWith(compareBy<Target> { !it.label.lowercase().contains("fm") }.thenBy { it.label })
            .firstOrNull()
    }

    fun open(context: Context): Result<String> {
        val target = findSystemRadioApp(context)
            ?: return Result.failure(
                IllegalStateException("لا يوجد تطبيق راديو FM نظامي قابل للفتح على هذا الجهاز.")
            )

        return runCatching {
            context.startActivity(target.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            target.label
        }
    }

    private fun isSystemPackage(info: ApplicationInfo): Boolean =
        (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}
