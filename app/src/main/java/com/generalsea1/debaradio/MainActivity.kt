package com.generalsea1.tmfm

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        if (!station.internetPlayable) {
            toast("هذه محطة ترددية/دليل فقط ولا يوجد بث إنترنت موثق متاح للتشغيل.")
            return
        }
        val url = requireNotNull(station.streamUrl)
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
        if (!station.directStreamRecordable) {
            toast("التسجيل متاح فقط لبث MP3/AAC موثق ومباشر؛ HLS غير مدعوم حاليًا.")
            return
        }
        val intent = Intent(this, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_STATION_ID, station.id)
            .putExtra(RecordingService.EXTRA_STATION_NAME, station.name)
            .putExtra(RecordingService.EXTRA_FREQUENCY, station.frequencyMhz)
            .putExtra(RecordingService.EXTRA_STREAM_URL, station.streamUrl)
            .putExtra(RecordingService.EXTRA_STREAM_TYPE, station.streamType)
        ContextCompat.startForegroundService(this, intent)
        toast("بدأ تسجيل TMFM: " + (station.nameArabic ?: station.name))
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
        val type = if (file.extension.equals("aac", true)) "audio/aac" else "audio/mpeg"
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType(type)
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
        toast(if (value == 0) "تم إلغاء مؤقت النوم." else "سيُوقف تشغيل TMFM بعد " + value + " دقيقة.")
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