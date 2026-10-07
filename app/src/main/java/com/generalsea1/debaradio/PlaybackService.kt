package com.generalsea1.tmfm

import android.app.PendingIntent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var retryRunnable: Runnable? = null
    private var retryCount = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    retryCount = 0
                    retryRunnable?.let(handler::removeCallbacks)
                    retryRunnable = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val item = player.currentMediaItem ?: return
                if (!player.playWhenReady || retryCount >= MAX_RETRIES) return

                val delayMs = RETRY_DELAYS_MS[retryCount]
                retryCount += 1
                retryRunnable?.let(handler::removeCallbacks)

                val task = Runnable {
                    retryRunnable = null
                    player.setMediaItem(item, false)
                    player.prepare()
                    player.play()
                }
                retryRunnable = task
                handler.postDelayed(task, delayMs)
            }
        })

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val sessionActivity = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                100,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val builder = MediaSession.Builder(this, player)
        if (sessionActivity != null) builder.setSessionActivity(sessionActivity)
        mediaSession = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(
        intent: android.content.Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_SET_SLEEP_TIMER ->
                scheduleSleep(intent.getIntExtra(EXTRA_SLEEP_MINUTES, 0))
            ACTION_CANCEL_SLEEP_TIMER ->
                scheduleSleep(0)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun scheduleSleep(minutes: Int) {
        sleepRunnable?.let(handler::removeCallbacks)
        sleepRunnable = null
        if (minutes <= 0) return

        val task = Runnable {
            startService(
                android.content.Intent(this, RecordingService::class.java)
                    .setAction(RecordingService.ACTION_STOP)
            )
            mediaSession?.player?.stop()
            mediaSession?.player?.clearMediaItems()
            sleepRunnable = null
        }
        sleepRunnable = task
        handler.postDelayed(task, minutes.toLong() * 60_000L)
    }

    override fun onDestroy() {
        retryRunnable?.let(handler::removeCallbacks)
        sleepRunnable?.let(handler::removeCallbacks)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private var sleepRunnable: Runnable? = null

    companion object {
        const val ACTION_SET_SLEEP_TIMER = "com.generalsea1.tmfm.action.SET_SLEEP_TIMER"
        const val ACTION_CANCEL_SLEEP_TIMER = "com.generalsea1.tmfm.action.CANCEL_SLEEP_TIMER"
        const val EXTRA_SLEEP_MINUTES = "sleep_minutes"

        private val RETRY_DELAYS_MS = longArrayOf(1_000L, 2_000L, 4_000L)
        private const val MAX_RETRIES = 3
    }
}
