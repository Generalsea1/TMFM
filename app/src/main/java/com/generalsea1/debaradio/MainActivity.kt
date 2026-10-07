package com.generalsea1.debaradio

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingRecordStation: RadioStation? = null

    private val recordPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchProjectionPermission()
            } else {
                pendingRecordStation = null
                toast("يلزم السماح بتسجيل الصوت لتسجيل تشغيل TMFM.")
            }
        }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val station = pendingRecordStation
            pendingRecordStation = null

            if (result.resultCode != Activity.RESULT_OK || result.data == null || station == null) {
                return@registerForActivityResult
            }

            val serviceIntent = Intent(this, RecordingService::class.java)
                .setAction(RecordingService.ACTION_START)
                .putExtra(RecordingService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(RecordingService.EXTRA_RESULT_DATA, result.data)
                .putExtra(RecordingService.EXTRA_STATION_ID, station.id)
                .putExtra(RecordingService.EXTRA_STATION_NAME, station.name)

            ContextCompat.startForegroundService(this, serviceIntent)
            toast("بدأ تسجيل: " + station.name)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener(
            {
                controller = runCatching { controllerFuture?.get() }.getOrNull()
            },
            ContextCompat.getMainExecutor(this)
        )

        setContent {
            androidx.compose.material3.MaterialTheme(
                colorScheme = TmfmColors
            ) {
                TmfmRadioApp(
                    controllerProvider = { controller },
                    onStationPlayed = ::play,
                    onRecordRequested = ::requestRecording,
                    onPlayRecording = ::playRecording,
                    onShareRecording = ::shareRecording
                )
            }
        }
    }

    private fun play(station: RadioStation) {
        val c = controller
        val url = station.streamUrl

        if (c == null) {
            toast("المشغل لم يجهز بعد.")
            return
        }
        if (url.isNullOrBlank()) {
            toast("هذه المحطة لا تملك بثًا صالحًا حاليًا.")
            return
        }

        val item = MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist(station.countryName)
                    .setAlbumTitle("TMFM Radio")
                    .build()
            )
            .build()

        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    private fun requestRecording(station: RadioStation) {
        if (Build.VERSION.SDK_INT < 29) {
            toast("تسجيل تشغيل الراديو يتطلب Android 10 (API 29) أو أحدث.")
            return
        }

        if (station.streamUrl.isNullOrBlank()) {
            toast("المحطة لا تملك بثًا صالحًا للتسجيل.")
            return
        }

        // Recording captures TMFM's own media playback, so ensure the station is playing first.
        play(station)
        pendingRecordStation = station
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            launchProjectionPermission()
        }
    }

    private fun launchProjectionPermission() {
        if (pendingRecordStation == null) return

        val manager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun playRecording(recording: RecordingEntity) {
        val file = File(recording.filePath)
        if (!file.exists()) {
            toast("ملف التسجيل غير موجود.")
            return
        }

        val c = controller ?: run {
            toast("المشغل لم يجهز بعد.")
            return
        }

        c.setMediaItem(
            MediaItem.Builder()
                .setUri(Uri.fromFile(file))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(recording.stationName)
                        .setArtist("TMFM Recording")
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

        val uri = FileProvider.getUriForFile(
            this,
            packageName + ".fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND)
            .setType("audio/mp4")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        startActivity(Intent.createChooser(intent, "مشاركة تسجيل TMFM"))
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

@androidx.compose.runtime.Composable
private fun TmfmRadioApp(
    controllerProvider: () -> MediaController?,
    onStationPlayed: (RadioStation) -> Unit,
    onRecordRequested: (RadioStation) -> Unit,
    onPlayRecording: (RecordingEntity) -> Unit,
    onShareRecording: (RecordingEntity) -> Unit,
    vm: MainViewModel = viewModel()
) {
    TmfmRadioScreen(
        vm = vm,
        controllerProvider = controllerProvider,
        onStationPlayed = onStationPlayed,
        onRecordRequested = onRecordRequested,
        onPlayRecording = onPlayRecording,
        onShareRecording = onShareRecording
    )
}
