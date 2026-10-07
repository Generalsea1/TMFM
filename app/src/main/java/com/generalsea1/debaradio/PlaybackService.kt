package com.generalsea1.tmfm

import android.app.PendingIntent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .build()

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val sessionActivity = launchIntent?.let {
            PendingIntent.getActivity(
                this, 100, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val builder = MediaSession.Builder(this, player)
        if (sessionActivity != null) builder.setSessionActivity(sessionActivity)
        mediaSession = builder.build()
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_SLEEP_TIMER -> scheduleSleep(intent.getIntExtra(EXTRA_SLEEP_MINUTES, 0))
            ACTION_CANCEL_SLEEP_TIMER -> scheduleSleep(0)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun scheduleSleep(minutes: Int) {
        sleepRunnable?.let(handler::removeCallbacks)
        sleepRunnable = null
        if (minutes <= 0) return
        val task = Runnable {
            mediaSession?.player?.stop()
            mediaSession?.player?.clearMediaItems()
            sleepRunnable = null
        }
        sleepRunnable = task
        handler.postDelayed(task, minutes.toLong() * 60_000L)
    }

    override fun onDestroy() {
        sleepRunnable?.let(handler::removeCallbacks)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_SET_SLEEP_TIMER = "com.generalsea1.tmfm.action.SET_SLEEP_TIMER"
        const val ACTION_CANCEL_SLEEP_TIMER = "com.generalsea1.tmfm.action.CANCEL_SLEEP_TIMER"
        const val EXTRA_SLEEP_MINUTES = "sleep_minutes"
    }
}
