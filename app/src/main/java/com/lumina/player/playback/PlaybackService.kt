package com.lumina.player.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.lumina.player.LuminaApp
import com.lumina.player.PlayerActivity
import com.lumina.player.R
import com.lumina.player.data.Thumbnails
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Arka planda oynatmayı sürdüren ön plan hizmeti: medya bildirimi, kilit ekranı
 * kontrolleri, kulaklık/Bluetooth tuşları (MediaSession) ve kulaklık çıkarılınca duraklatma.
 */
class PlaybackService : LifecycleService() {

    private val engine: PlayerEngine get() = (application as LuminaApp).engine
    private lateinit var session: MediaSessionCompat
    private var isForeground = false
    private var artworkUri: String? = null
    private var artwork: Bitmap? = null
    private var artworkJob: Job? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) engine.pause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSessionCompat(this, "LuminaPlayer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = engine.play()
                override fun onPause() = engine.pause()
                override fun onSkipToNext() = engine.next()
                override fun onSkipToPrevious() = engine.previous()
                override fun onSeekTo(pos: Long) = engine.seekTo(pos)
                override fun onStop() = engine.stop()
                override fun onFastForward() = engine.seekBy(10_000)
                override fun onRewind() = engine.seekBy(-10_000)
            })
            setSessionActivity(openPlayerIntent())
            isActive = true
        }
        ContextCompat.registerReceiver(
            this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // Oynatma konumu (ucuz): saniyede bir
        lifecycleScope.launch {
            engine.state
                .map { Triple(it.isPlaying, it.rate, it.positionMs / 1000) }
                .distinctUntilChanged()
                .collect { updatePlaybackState() }
        }
        // Bildirim ve meta veri (pahalı): yalnızca anlamlı değişikliklerde
        lifecycleScope.launch {
            engine.state
                .map { NotificationKey(it.current?.uri, it.current?.title, it.isPlaying, it.durationMs, it.hasNext, it.ended) }
                .distinctUntilChanged()
                .collect { render() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> engine.togglePlayPause()
            ACTION_NEXT -> engine.next()
            ACTION_PREVIOUS -> engine.previous()
            ACTION_STOP -> {
                engine.stop()
                stopEverything()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!engine.state.value.isPlaying) stopEverything()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(noisyReceiver) }
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    private data class NotificationKey(
        val uri: String?, val title: String?, val playing: Boolean, val duration: Long,
        val hasNext: Boolean, val ended: Boolean,
    )

    private fun updatePlaybackState() {
        val st = engine.state.value
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_FAST_FORWARD or
            PlaybackStateCompat.ACTION_REWIND
        val state = when {
            st.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            st.isBuffering -> PlaybackStateCompat.STATE_BUFFERING
            st.current != null -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, st.positionMs, if (st.isPlaying) st.rate else 0f)
                .build()
        )
    }

    private fun render() {
        val st = engine.state.value
        val item = st.current
        if (item == null) {
            stopEverything()
            return
        }
        if (artworkUri != item.uri) {
            artworkUri = item.uri
            artwork = null
            artworkJob?.cancel()
            artworkJob = lifecycleScope.launch {
                artwork = Thumbnails.load(this@PlaybackService, item, 512)
                if (artwork != null && artworkUri == item.uri) render()
            }
        }

        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, item.artist ?: getString(R.string.app_name))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, item.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, st.durationMs)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
                .build()
        )
        updatePlaybackState()

        val playPause = if (st.isPlaying) {
            NotificationCompat.Action(R.drawable.ic_pause, getString(R.string.action_pause), actionIntent(ACTION_PLAY_PAUSE))
        } else {
            NotificationCompat.Action(R.drawable.ic_play, getString(R.string.action_play), actionIntent(ACTION_PLAY_PAUSE))
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.title)
            .setContentText(item.artist ?: getString(R.string.app_name))
            .setLargeIcon(artwork)
            .setContentIntent(openPlayerIntent())
            .setDeleteIntent(actionIntent(ACTION_STOP))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(st.isPlaying)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(R.drawable.ic_skip_previous, getString(R.string.action_previous), actionIntent(ACTION_PREVIOUS))
            .addAction(playPause)
            .addAction(R.drawable.ic_skip_next, getString(R.string.action_next), actionIntent(ACTION_NEXT))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        if (st.isPlaying) {
            try {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
                )
                isForeground = true
            } catch (e: Exception) {
                // Android 12+: arka plandan ön plan hizmeti başlatılamazsa bildirimi yine de göster
                notify(notification)
            }
        } else {
            if (isForeground) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                isForeground = false
            }
            notify(notification)
        }
    }

    private fun notify(notification: android.app.Notification) {
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
    }

    private fun stopEverything() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        isForeground = false
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun openPlayerIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, PlayerActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(), Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_PLAY_PAUSE = "com.lumina.player.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.lumina.player.NEXT"
        private const val ACTION_PREVIOUS = "com.lumina.player.PREVIOUS"
        private const val ACTION_STOP = "com.lumina.player.STOP"

        fun start(context: Context) {
            runCatching { context.startService(Intent(context, PlaybackService::class.java)) }
        }
    }
}
