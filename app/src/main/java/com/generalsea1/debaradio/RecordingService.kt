package com.generalsea1.debaradio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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
import android.os.IBinder
import android.os.Handler
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            ACTION_START -> {
                if (Build.VERSION.SDK_INT < 29) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                val stationName = intent.getStringExtra(EXTRA_STATION_NAME) ?: "Unknown Station"
                val stationId = intent.getStringExtra(EXTRA_STATION_ID) ?: "unknown"
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.parcelableExtraCompat<Intent>(EXTRA_RESULT_DATA)

                if (resultCode != RESULT_OK || resultData == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (running.compareAndSet(false, true)) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildNotification(stationName),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )

                    serviceScope.launch {
                        runCatching {
                            recordPlayback(
                                stationId = stationId,
                                stationName = stationName,
                                resultCode = resultCode,
                                resultData = resultData
                            )
                        }
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun stopRecording() {
        running.set(false)
    }

    @RequiresApi(29)
    private suspend fun recordPlayback(
        stationId: String,
        stationName: String,
        resultCode: Int,
        resultData: Intent
    ) {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
            ?: run {
                broadcastFailure("تعذر الحصول على جلسة تسجيل الصوت.")
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
        var actualSampleRate = 44_100
        var actualChannels = 2
        val projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                running.set(false)
            }
        }
        projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

        try {
            val configured = createAudioRecord(projection)
            audioRecord = configured.first
            actualSampleRate = configured.second
            actualChannels = configured.third

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                actualSampleRate,
                actualChannels
            ).apply {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )
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
                val bytesRead = audioRecord.read(
                    pcm,
                    pcm.capacity(),
                    AudioRecord.READ_BLOCKING
                )

                if (bytesRead > 0) {
                    var queued = false
                    while (!queued && running.get()) {
                        val inputIndex = codec.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val input = codec.getInputBuffer(inputIndex) ?: break
                            input.clear()
                            pcm.position(0)
                            pcm.limit(bytesRead)
                            input.put(pcm)
                            val samples = totalPcmBytes / (2L * actualChannels)
                            val ptsUs = samples * 1_000_000L / actualSampleRate
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                bytesRead,
                                ptsUs,
                                0
                            )
                            totalPcmBytes += bytesRead
                            queued = true
                        }
                    }
                }

                drainEncoder(codec, muxer!!, bufferInfo) { mode ->
                    if (!muxerStarted) {
                        trackIndex = muxer!!.addTrack(codec.outputFormat)
                        muxer!!.start()
                        muxerStarted = true
                    }
                    trackIndex
                }
            }

            codec.dequeueInputBuffer(10_000).takeIf { it >= 0 }?.let { inputIndex ->
                codec.getInputBuffer(inputIndex)?.clear()
                val samples = totalPcmBytes / (2L * actualChannels)
                val ptsUs = samples * 1_000_000L / actualSampleRate
                codec.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    ptsUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }

            drainEncoderUntilEos(
                codec = codec,
                muxer = muxer!!,
                bufferInfo = bufferInfo,
                ensureTrack = {
                    if (!muxerStarted) {
                        trackIndex = muxer!!.addTrack(codec.outputFormat)
                        muxer!!.start()
                        muxerStarted = true
                    }
                    trackIndex
                }
            )

            audioRecord.stop()

            if (muxerStarted) {
                muxer.stop()
                muxerStarted = false
            }

            val duration = totalPcmBytes
                .div((2L * actualChannels).coerceAtLeast(1))
                .times(1_000L)
                .div(actualSampleRate.toLong().coerceAtLeast(1))

            if (output.exists() && output.length() > 0L) {
                AppDatabase.get(this).recordingDao().insert(
                    RecordingEntity(
                        stationId = stationId,
                        stationName = stationName,
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
                broadcastFailure("لم ينتج التسجيل ملفًا صالحًا.")
            }
        } catch (t: Throwable) {
            output.delete()
            broadcastFailure(t.message ?: "فشل التسجيل.")
        } finally {
            running.set(false)
            runCatching { audioRecord?.release() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { projection.unregisterCallback(projectionCallback) }
            runCatching { projection.stop() }
        }
    }

    @RequiresApi(29)
    private fun createAudioRecord(projection: MediaProjection): Triple<AudioRecord, Int, Int> {
        val attempts = listOf(
            Triple(44_100, AudioFormat.CHANNEL_IN_STEREO, 2),
            Triple(48_000, AudioFormat.CHANNEL_IN_STEREO, 2),
            Triple(44_100, AudioFormat.CHANNEL_IN_MONO, 1),
            Triple(48_000, AudioFormat.CHANNEL_IN_MONO, 1)
        )

        for ((sampleRate, channelMask, channels) in attempts) {
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) continue

            val config = android.media.AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUid(applicationInfo.uid)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()

            val record = runCatching {
                AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelMask)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuffer * 2, 16_384))
                    .setAudioPlaybackCaptureConfig(config)
                    .build()
            }.getOrNull()

            if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
                return Triple(record, sampleRate, channels)
            }
            runCatching { record?.release() }
        }

        error("لا يستطيع الجهاز تهيئة AudioPlaybackCapture.")
    }

    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        ensureTrack: (Int) -> Int
    ) {
        while (true) {
            when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> ensureTrack(-1)
                else -> if (outputIndex >= 0) {
                    val output = codec.getOutputBuffer(outputIndex)
                    if (output != null && bufferInfo.size > 0) {
                        output.position(bufferInfo.offset)
                        output.limit(bufferInfo.offset + bufferInfo.size)
                        val track = ensureTrack(outputIndex)
                        muxer.writeSampleData(track, output, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
    }

    private fun drainEncoderUntilEos(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        ensureTrack: (Int) -> Int
    ) {
        var eos = false
        while (!eos) {
            when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> ensureTrack(-1)
                else -> if (outputIndex >= 0) {
                    val output = codec.getOutputBuffer(outputIndex)
                    if (output != null && bufferInfo.size > 0) {
                        output.position(bufferInfo.offset)
                        output.limit(bufferInfo.offset + bufferInfo.size)
                        val track = ensureTrack(outputIndex)
                        muxer.writeSampleData(track, output, bufferInfo)
                    }
                    eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
    }

    private fun buildNotification(stationName: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radio)
            .setContentTitle("TMFM Radio — تسجيل")
            .setContentText(stationName)
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
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "TMFM Recordings",
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
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.generalsea1.debaradio.action.START_RECORDING"
        const val ACTION_STOP = "com.generalsea1.debaradio.action.STOP_RECORDING"
        const val ACTION_FINISHED = "com.generalsea1.debaradio.action.RECORDING_FINISHED"
        const val ACTION_FAILED = "com.generalsea1.debaradio.action.RECORDING_FAILED"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_STATION_NAME = "station_name"
        const val EXTRA_STATION_ID = "station_id"
        const val EXTRA_PATH = "path"
        const val EXTRA_ERROR = "error"
        private const val CHANNEL_ID = "tmfm-recordings"
        private const val NOTIFICATION_ID = 401
        const val RESULT_OK = -1
    }
}

private object PendingIntentFactory {
    fun stop(context: Context) = android.app.PendingIntent.getService(
        context,
        402,
        Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )
}

private inline fun <reified T : Parcelable> Intent.parcelableExtraCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
