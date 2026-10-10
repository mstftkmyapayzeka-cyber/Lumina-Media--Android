package com.lumina.player.playback

import androidx.annotation.StringRes
import com.lumina.player.R
import com.lumina.player.core.MediaItem
import com.lumina.player.core.PlayMode
import org.videolan.libvlc.MediaPlayer

data class Track(val id: Int, val name: String)

enum class VideoScale(val vlc: MediaPlayer.ScaleType, @StringRes val label: Int) {
    BEST_FIT(MediaPlayer.ScaleType.SURFACE_BEST_FIT, R.string.scale_best_fit),
    FIT_SCREEN(MediaPlayer.ScaleType.SURFACE_FIT_SCREEN, R.string.scale_fit_screen),
    FILL(MediaPlayer.ScaleType.SURFACE_FILL, R.string.scale_fill),
    RATIO_16_9(MediaPlayer.ScaleType.SURFACE_16_9, R.string.scale_16_9),
    RATIO_4_3(MediaPlayer.ScaleType.SURFACE_4_3, R.string.scale_4_3),
    ORIGINAL(MediaPlayer.ScaleType.SURFACE_ORIGINAL, R.string.scale_original);

    fun next(): VideoScale = entries[(ordinal + 1) % entries.size]
}

data class PlayerState(
    val queue: List<MediaItem> = emptyList(),
    val index: Int = -1,
    val current: MediaItem? = null,
    /** Medya libVLC'ye yüklendi mi (oturum geri yüklendiğinde false olur). */
    val prepared: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val ended: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val seekable: Boolean = true,
    val hasVideo: Boolean = false,
    val rate: Float = 1f,
    val volume: Int = 100,
    val playMode: PlayMode = PlayMode.SEQUENTIAL,
    val audioTracks: List<Track> = emptyList(),
    val audioTrack: Int = -1,
    val subtitleTracks: List<Track> = emptyList(),
    val subtitleTrack: Int = -1,
    val subtitleDelayMs: Long = 0,
    val audioDelayMs: Long = 0,
    val scale: VideoScale = VideoScale.BEST_FIT,
) {
    val hasNext: Boolean get() = index in 0 until queue.lastIndex || (queue.isNotEmpty() && playMode in setOf(PlayMode.REPEAT_ALL, PlayMode.SHUFFLE))
    val hasPrevious: Boolean get() = queue.size > 1
}

sealed interface PlayerEvent {
    data class Message(val text: String) : PlayerEvent
    data class Resumed(val positionMs: Long) : PlayerEvent
    data object QueueFinished : PlayerEvent
}
