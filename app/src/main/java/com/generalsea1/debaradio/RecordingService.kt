package com.generalsea1.tmfm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> running.set(false)
            ACTION_START -> startRecording(intent)
        }
        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent) {
        val url = intent.getStringExtra(EXTRA_STREAM_URL)
        if (url.isNullOrBlank()) {
            broadcastFailure("لا يوجد رابط بث موثّق للتسجيل.")
            stopSelf()
            return
        }
        if (!running.compareAndSet(false, true)) return

        val stationName = intent.getStringExtra(EXTRA_STATION_NAME) ?: "Station"
        val stationId = intent.getStringExtra(EXTRA_STATION_ID) ?: "unknown"
        val frequency = intent.getDoubleExtra(EXTRA_FREQUENCY, Double.NaN).takeUnless { it.isNaN() }
        val streamType = intent.getStringExtra(EXTRA_STREAM_TYPE)

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(stationName),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        scope.launch {
            try {
                val result = InternetStreamRecorder(this@RecordingService, running::get).record(
                    stationName = stationName,
                    stationId = stationId,
                    frequencyMhz = frequency,
                    streamUrl = url,
                    streamType = streamType
                )
                if (running.get().not() || result.file.exists()) {
                    saveRecording(
                        stationId = stationId,
                        stationName = stationName,
                        frequencyMhz = frequency,
                        result = result
                    )
                }
            } catch (t: Throwable) {
                broadcastFailure("تعذر إكمال التسجيل: " + (t.message ?: "خطأ غير معروف"))
            } finally {
                running.set(false)
                stopSelf()
            }
        }
    }

    private fun saveRecording(
        stationId: String,
        stationName: String,
        frequencyMhz: Double?,
        result: RecordingResult
    ) {
        val file = result.file
        if (!file.exists() || file.length() < 1024L) {
            file.delete()
            broadcastFailure("لم ينتج التسجيل ملفًا صوتيًا صالحًا.")
            return
        }

        AppDatabase.get(this).recordingDao().insert(
            RecordingEntity(
                stationId = stationId,
                stationName = stationName,
                frequencyMhz = frequencyMhz,
                filePath = file.absolutePath,
                createdAtMillis = System.currentTimeMillis(),
                durationMillis = result.durationMillis,
                fileSizeBytes = file.length(),
                source = "TMFM direct stream recording"
            )
        )
        broadcastFinished(file.absolutePath)
    }

    private fun buildNotification(stationName: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radio)
            .setContentTitle("TMFM")
            .setContentText("تسجيل: " + stationName)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_radio,
                "إيقاف",
                PendingIntentFactory.stop(this)
            )
            .build()

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "تسجيلات TMFM",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun broadcastFinished(path: String) {
        sendBroadcast(
            Intent(ACTION_FINISHED)
                .setPackage(packageName)
                .putExtra(EXTRA_PATH, path)
        )
    }

    private fun broadcastFailure(message: String) {
        sendBroadcast(
            Intent(ACTION_FAILED)
                .setPackage(packageName)
                .putExtra(EXTRA_ERROR, message)
        )
    }

    override fun onDestroy() {
        running.set(false)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.generalsea1.tmfm.action.START_RECORDING"
        const val ACTION_STOP = "com.generalsea1.tmfm.action.STOP_RECORDING"
        const val ACTION_FINISHED = "com.generalsea1.tmfm.action.RECORDING_FINISHED"
        const val ACTION_FAILED = "com.generalsea1.tmfm.action.RECORDING_FAILED"
        const val EXTRA_STATION_NAME = "station_name"
        const val EXTRA_STATION_ID = "station_id"
        const val EXTRA_FREQUENCY = "frequency"
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_STREAM_TYPE = "stream_type"
        const val EXTRA_PATH = "path"
        const val EXTRA_ERROR = "error"
        private const val CHANNEL_ID = "tmfm-recordings"
        private const val NOTIFICATION_ID = 401
    }
}

private object PendingIntentFactory {
    fun stop(context: Context) = android.app.PendingIntent.getService(
        context,
        402,
        Intent(context, RecordingService::class.java)
            .setAction(RecordingService.ACTION_STOP),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )
}
