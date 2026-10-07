package com.generalsea1.tmfm

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcelable
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
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
        if (Build.VERSION.SDK_INT < 29) {
            broadcastFailure("تسجيل تشغيل الراديو يحتاج Android 10 أو أحدث.")
            stopSelf()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            broadcastFailure("لا توجد صلاحية تسجيل الصوت.")
            stopSelf()
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = intent.parcelableExtraCompat<Intent>(EXTRA_RESULT_DATA)
        if (resultCode != RESULT_OK || resultData == null) {
            broadcastFailure("لم يتم منح إذن التقاط تشغيل TMFM.")
            stopSelf()
            return
        }

        val stationName = intent.getStringExtra(EXTRA_STATION_NAME) ?: "Station"
        val stationId = intent.getStringExtra(EXTRA_STATION_ID) ?: "unknown"
        val frequency = intent.getDoubleExtra(EXTRA_FREQUENCY, Double.NaN).takeUnless { it.isNaN() }

        if (!running.compareAndSet(false, true)) return

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(stationName),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )

        scope.launch {
            recordPlayback(
                stationId = stationId,
                stationName = stationName,
                frequencyMhz = frequency,
                resultCode = resultCode,
                resultData = resultData
            )
            stopSelf()
        }
    }

    @RequiresApi(29)
    private suspend fun recordPlayback(
        stationId: String,
        stationName: String,
        frequencyMhz: Double?,
        resultCode: Int,
        resultData: Intent
    ) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = manager.getMediaProjection(resultCode, resultData)
            ?: run {
                broadcastFailure("تعذر إنشاء جلسة التسجيل.")
                return
            }

        val timestamp = System.currentTimeMillis()
        val dir = File(
            getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC),
            "TMFM/Recordings"
        ).apply { mkdirs() }
        val output = File(dir, RecordingFileNameGenerator.generate(stationName, timestamp))

        var audioRecord: AudioRecord? = null
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1
        var totalPcmBytes = 0L
        var sampleRate = 44_100
        var channels = 2

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                running.set(false)
            }
        }
        projection.registerCallback(callback, Handler(Looper.getMainLooper()))

        try {
            val configured = createAudioRecord(projection)
            audioRecord = configured.first
            sampleRate = configured.second
            channels = configured.third

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channels
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            audioRecord.startRecording()

            val pcm = ByteBuffer.allocateDirect(32_768)
            val bufferInfo = MediaCodec.BufferInfo()

            while (running.get() && !Thread.currentThread().isInterrupted) {
                pcm.clear()
                val bytesRead = audioRecord.read(pcm, pcm.capacity(), AudioRecord.READ_BLOCKING)
                if (bytesRead > 0) {
                    queuePcm(codec, pcm, bytesRead, totalPcmBytes, sampleRate, channels)
                    totalPcmBytes += bytesRead
                }
                drainEncoder(codec, muxer, bufferInfo) { track ->
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    trackIndex
                }
            }

            if (running.get().not()) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    codec.getInputBuffer(inputIndex)?.clear()
                    val samples = totalPcmBytes / (2L * channels)
                    val ptsUs = samples * 1_000_000L / sampleRate
                    codec.queueInputBuffer(inputIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
            }

            drainEncoderUntilEos(codec, muxer, bufferInfo) {
                if (!muxerStarted) {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                trackIndex
            }

            runCatching { audioRecord.stop() }
            if (muxerStarted) {
                muxer.stop()
                muxerStarted = false
            }

            val duration = (totalPcmBytes / (2L * channels).coerceAtLeast(1L))
                .times(1_000L)
                .div(sampleRate.toLong().coerceAtLeast(1L))

            if (output.exists() && output.length() > 0L && duration > 0L) {
                AppDatabase.get(this).recordingDao().insert(
                    RecordingEntity(
                        stationId = stationId,
                        stationName = stationName,
                        frequencyMhz = frequencyMhz,
                        filePath = output.absolutePath,
                        createdAtMillis = timestamp,
                        durationMillis = duration,
                        fileSizeBytes = output.length(),
                        source = "TMFM playback capture"
                    )
                )
                broadcastFinished(output.absolutePath)
            } else {
                output.delete()
                broadcastFailure("لم ينتج التسجيل ملفًا صوتيًا صالحًا.")
            }
        } catch (t: Throwable) {
            output.delete()
            broadcastFailure("تعذر إكمال التسجيل: " + (t.message ?: "خطأ غير معروف"))
        } finally {
            running.set(false)
            runCatching { audioRecord?.release() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { projection.unregisterCallback(callback) }
            runCatching { projection.stop() }
        }
    }

    private fun queuePcm(
        codec: MediaCodec,
        pcm: ByteBuffer,
        bytesRead: Int,
        totalPcmBytes: Long,
        sampleRate: Int,
        channels: Int
    ) {
        var queued = false
        while (!queued && running.get()) {
            val inputIndex = codec.dequeueInputBuffer(10_000)
            if (inputIndex < 0) continue
            val input = codec.getInputBuffer(inputIndex) ?: return
            input.clear()
            pcm.position(0)
            pcm.limit(bytesRead)
            input.put(pcm)
            val samples = totalPcmBytes / (2L * channels)
            val ptsUs = samples * 1_000_000L / sampleRate
            codec.queueInputBuffer(inputIndex, 0, bytesRead, ptsUs, 0)
            queued = true
        }
    }

    @RequiresApi(29)
    @SuppressLint("MissingPermission")
    private fun createAudioRecord(
        projection: MediaProjection
    ): Triple<AudioRecord, Int, Int> {
        val attempts = listOf(
            Triple(48_000, AudioFormat.CHANNEL_IN_STEREO, 2),
            Triple(44_100, AudioFormat.CHANNEL_IN_STEREO, 2),
            Triple(48_000, AudioFormat.CHANNEL_IN_MONO, 1),
            Triple(44_100, AudioFormat.CHANNEL_IN_MONO, 1)
        )

        for ((rate, channelMask, channelCount) in attempts) {
            val minBuffer = AudioRecord.getMinBufferSize(
                rate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) continue

            val captureConfig = android.media.AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUid(applicationInfo.uid)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()

            val record = runCatching {
                AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(channelMask)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuffer * 2, 16_384))
                    .setAudioPlaybackCaptureConfig(captureConfig)
                    .build()
            }.getOrNull()

            if (record?.state == AudioRecord.STATE_INITIALIZED) {
                return Triple(record, rate, channelCount)
            }
            runCatching { record?.release() }
        }

        error("لا يستطيع الجهاز تهيئة التقاط تشغيل TMFM.")
    }

    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        info: MediaCodec.BufferInfo,
        ensureTrack: (Int) -> Int
    ) {
        while (true) {
            when (val index = codec.dequeueOutputBuffer(info, 0)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> ensureTrack(-1)
                else -> if (index >= 0) {
                    val out = codec.getOutputBuffer(index)
                    if (out != null && info.size > 0) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        muxer.writeSampleData(ensureTrack(index), out, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                }
            }
        }
    }

    private fun drainEncoderUntilEos(
        codec: MediaCodec,
        muxer: MediaMuxer,
        info: MediaCodec.BufferInfo,
        ensureTrack: () -> Int
    ) {
        var eos = false
        while (!eos) {
            when (val index = codec.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> ensureTrack()
                else -> if (index >= 0) {
                    val out = codec.getOutputBuffer(index)
                    if (out != null && info.size > 0) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        muxer.writeSampleData(ensureTrack(), out, info)
                    }
                    eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(index, false)
                }
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
            Intent(ACTION_FINISHED).setPackage(packageName)
                .putExtra(EXTRA_PATH, path)
        )
    }

    private fun broadcastFailure(message: String) {
        sendBroadcast(
            Intent(ACTION_FAILED).setPackage(packageName)
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
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_STATION_NAME = "station_name"
        const val EXTRA_STATION_ID = "station_id"
        const val EXTRA_FREQUENCY = "frequency"
        const val EXTRA_PATH = "path"
        const val EXTRA_ERROR = "error"
        const val RESULT_OK = -1
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

private inline fun <reified T : Parcelable> Intent.parcelableExtraCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java)
    else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
