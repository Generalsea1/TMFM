package com.generalsea1.tmfm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
            ACTION_START -> startRecording(intent, startId)
        }
        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent, startId: Int) {
        val stationName = intent.getStringExtra(EXTRA_STATION_NAME).orEmpty().ifBlank { "Station" }
        val stationId = intent.getStringExtra(EXTRA_STATION_ID).orEmpty().ifBlank { "unknown" }
        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL).orEmpty()
        val streamType = intent.getStringExtra(EXTRA_STREAM_TYPE)
        val frequency = if (intent.hasExtra(EXTRA_FREQUENCY)) intent.getDoubleExtra(EXTRA_FREQUENCY, Double.NaN).takeUnless { it.isNaN() } else null

        if (streamUrl.isBlank() || streamType.isNullOrBlank()) {
            broadcastFailure("بيانات البث اللازمة للتسجيل غير متاحة.")
            return
        }
        if (!running.compareAndSet(false, true)) return

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(stationName),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        scope.launch {
            try {
                val result = DirectStreamRecorder(this@RecordingService).record(
                    stationName = stationName,
                    streamUrl = streamUrl,
                    declaredStreamType = streamType,
                    shouldContinue = { running.get() }
                )
                AppDatabase.get(this@RecordingService).recordingDao().insert(
                    RecordingEntity(
                        stationId = stationId,
                        stationName = stationName,
                        title = stationName,
                        frequencyMhz = frequency,
                        filePath = result.file.absolutePath,
                        createdAtMillis = result.file.lastModified(),
                        durationMillis = result.durationMillis,
                        fileSizeBytes = result.bytesWritten,
                        source = "internet-direct-stream"
                    )
                )
                broadcastFinished(result.file.absolutePath)
            } catch (t: Throwable) {
                broadcastFailure(t.message ?: "تعذر إكمال التسجيل.")
            } finally {
                running.set(false)
                stopSelf(startId)
            }
        }
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
                PendingIntent.getService(
                    this,
                    402,
                    Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "تسجيلات TMFM", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun broadcastFinished(path: String) {
        sendBroadcast(
            Intent(ACTION_FINISHED).setPackage(packageName).putExtra(EXTRA_PATH, path)
        )
    }

    private fun broadcastFailure(message: String) {
        sendBroadcast(
            Intent(ACTION_FAILED).setPackage(packageName).putExtra(EXTRA_ERROR, message)
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