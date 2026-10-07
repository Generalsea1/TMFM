package com.generalsea1.tmfm

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingRecordStation: RadioStation? = null

    private val recordPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchProjectionPermission()
            else {
                pendingRecordStation = null
                toast("يلزم السماح بتسجيل الصوت حتى يستطيع TMFM تسجيل تشغيله الفعلي.")
            }
        }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val station = pendingRecordStation
            pendingRecordStation = null
            if (result.resultCode != Activity.RESULT_OK || result.data == null || station == null) return@registerForActivityResult

            val intent = Intent(this, RecordingService::class.java)
                .setAction(RecordingService.ACTION_START)
                .putExtra(RecordingService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(RecordingService.EXTRA_RESULT_DATA, result.data)
                .putExtra(RecordingService.EXTRA_STATION_ID, station.id)
                .putExtra(RecordingService.EXTRA_STATION_NAME, station.name)
                .putExtra(RecordingService.EXTRA_FREQUENCY, station.frequencyMhz)

            ContextCompat.startForegroundService(this, intent)
            toast("بدأ تسجيل TMFM: " + station.name)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener(
            { controller = runCatching { controllerFuture?.get() }.getOrNull() },
            ContextCompat.getMainExecutor(this)
        )

        setContent {
            TmfmRadioApp(
                controllerProvider = { controller },
                onStationPlayed = ::play,
                onRecordRequested = ::requestRecording,
                onPlayRecording = ::playRecording,
                onShareRecording = ::shareRecording,
                onSleepRequested = ::setSleepTimer
            )
        }
    }

    private fun play(station: RadioStation) {
        val c = controller ?: run {
            toast("مشغل TMFM لم يجهز بعد.")
            return
        }
        val url = station.streamUrl
        if (url.isNullOrBlank()) {
            toast("هذا السجل تردد/دليل فقط ولا يملك بث إنترنت موثقًا داخل TMFM.")
            return
        }

        val item = MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.nameArabic ?: station.name)
                    .setArtist(station.countryName)
                    .setAlbumTitle("TMFM")
                    .build()
            )
            .build()

        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    private fun requestRecording(station: RadioStation) {
        if (Build.VERSION.SDK_INT < 29) {
            toast("تسجيل تشغيل الإنترنت يحتاج Android 10 أو أحدث.")
            return
        }
        if (station.streamUrl.isNullOrBlank()) {
            toast("لا يمكن تسجيل محطة لا تملك مسار بث إنترنت متاحًا.")
            return
        }

        play(station)
        pendingRecordStation = station
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            launchProjectionPermission()
        }
    }

    private fun launchProjectionPermission() {
        if (pendingRecordStation == null) return
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        if (Build.VERSION.SDK_INT >= 29) {
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        } else {
            pendingRecordStation = null
            toast("هذه الوظيفة غير مدعومة على هذا الإصدار.")
        }
    }

    private fun playRecording(recording: RecordingEntity) {
        val file = File(recording.filePath)
        if (!file.exists()) {
            toast("ملف التسجيل غير موجود.")
            return
        }
        val c = controller ?: run {
            toast("مشغل TMFM لم يجهز بعد.")
            return
        }

        c.setMediaItem(
            MediaItem.Builder()
                .setUri(Uri.fromFile(file))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(recording.title ?: recording.stationName)
                        .setArtist("TMFM")
                        .build()
                )
                .build()
        )
        c.prepare()
        c.play()
    }

    private fun shareRecording(recording: RecordingEntity) {
        val file = File(recording.filePath)
        if (!file.exists()) {
            toast("ملف التسجيل غير موجود.")
            return
        }

        val uri = FileProvider.getUriForFile(this, "com.generalsea1.tmfm.fileprovider", file)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("audio/mp4")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "مشاركة تسجيل TMFM"
            )
        )
    }

    private fun setSleepTimer(minutes: Int) {
        val value = minutes.coerceIn(0, 24 * 60)
        startService(
            Intent(this, PlaybackService::class.java)
                .setAction(
                    if (value == 0) PlaybackService.ACTION_CANCEL_SLEEP_TIMER
                    else PlaybackService.ACTION_SET_SLEEP_TIMER
                )
                .putExtra(PlaybackService.EXTRA_SLEEP_MINUTES, value)
        )
        toast(if (value == 0) "تم إلغاء مؤقت النوم." else "سيُوقف تشغيل TMFM بعد $value دقيقة.")
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        controllerFuture = null
        super.onDestroy()
    }
}
