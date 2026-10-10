package com.lumina.player.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.East
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumina.player.R
import com.lumina.player.core.MediaKind
import com.lumina.player.core.PlayMode
import com.lumina.player.core.TimeFormat
import com.lumina.player.data.AppSettings
import com.lumina.player.playback.PlayerEngine
import com.lumina.player.playback.PlayerEvent
import com.lumina.player.playback.PlayerState
import com.lumina.player.ui.components.MediaThumbnail
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs
import kotlin.math.roundToInt

class PlayerCallbacks(
    val onBack: () -> Unit,
    val onPickSubtitle: () -> Unit,
    val onScreenshot: () -> Unit,
    val onEnterPip: () -> Unit,
    val onRotate: () -> Unit,
    val onSavePlaylist: () -> Unit,
    val setBrightness: (Float) -> Unit,
    val getBrightness: () -> Float,
    val onVideoLayout: (VLCVideoLayout?) -> Unit,
)

enum class PlayerSheet { NONE, TRACKS, SPEED, EQUALIZER, QUEUE }

private enum class DragMode { NONE, SEEK, VOLUME, BRIGHTNESS }

@Composable
fun PlayerScreen(engine: PlayerEngine, settings: AppSettings, inPip: Boolean, callbacks: PlayerCallbacks) {
    val state by engine.state.collectAsStateWithLifecycle()
    val current by rememberUpdatedState(state)
    val context = LocalContext.current

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(PlayerSheet.NONE) }
    var osd by remember { mutableStateOf<String?>(null) }
    var osdTick by remember { mutableIntStateOf(0) }
    var interaction by remember { mutableIntStateOf(0) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }

    fun showOsd(text: String) {
        osd = text
        osdTick++
    }

    // Kontrolleri otomatik gizle
    LaunchedEffect(controlsVisible, state.isPlaying, sheet, interaction, locked) {
        if (controlsVisible && state.isPlaying && sheet == PlayerSheet.NONE) {
            delay(if (locked) 1_500 else 3_500)
            controlsVisible = false
        }
    }
    LaunchedEffect(osdTick) {
        if (osd != null) {
            delay(900)
            osd = null
        }
    }
    LaunchedEffect(state.ended) { if (state.ended) controlsVisible = true }

    // Motor olayları → bildirim çubuğu
    LaunchedEffect(engine) {
        engine.events.collect { event ->
            when (event) {
                is PlayerEvent.Message -> snackbar.showSnackbar(event.text, duration = SnackbarDuration.Short)
                is PlayerEvent.Resumed -> {
                    val result = snackbar.showSnackbar(
                        message = context.getString(R.string.msg_resumed, TimeFormat.format(event.positionMs)),
                        actionLabel = context.getString(R.string.action_start_over),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) engine.seekTo(0)
                }
                PlayerEvent.QueueFinished -> Unit
            }
        }
    }

    val step = settings.seekStepSec * 1000L
    val seekForwardText = stringResource(R.string.osd_seek_forward, settings.seekStepSec)
    val seekBackText = stringResource(R.string.osd_seek_back, settings.seekStepSec)
    val volumeFmt = stringResource(R.string.osd_volume)
    val brightnessFmt = stringResource(R.string.osd_brightness)
    val pausedText = stringResource(R.string.osd_paused)
    val seekDragFmt = stringResource(R.string.osd_seek_drag)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val item = state.current
        val showVideo = state.hasVideo || item?.kind == MediaKind.VIDEO
        if (showVideo) {
            VideoSurface(engine, callbacks)
        } else if (item != null) {
            AudioArtwork(state, compact = inPip)
        }

        if (!inPip) {
            // ---------------------------------------------------- jest katmanı
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(locked) {
                            detectTapGestures(
                                onTap = {
                                    controlsVisible = !controlsVisible
                                    interaction++
                                },
                                onDoubleTap = { offset ->
                                    if (locked) return@detectTapGestures
                                    when {
                                        offset.x < widthPx / 3 -> { engine.seekBy(-step); showOsd(seekBackText) }
                                        offset.x > widthPx * 2 / 3 -> { engine.seekBy(step); showOsd(seekForwardText) }
                                        else -> {
                                            val wasPlaying = current.isPlaying
                                            engine.togglePlayPause()
                                            if (wasPlaying) showOsd(pausedText)
                                        }
                                    }
                                },
                            )
                        }
                        .pointerInput(locked, settings.gestures) {
                            if (locked || !settings.gestures) return@pointerInput
                            var mode = DragMode.NONE
                            var start = Offset.Zero
                            var total = Offset.Zero
                            var startPos = 0L
                            var startVolume = 100
                            var startBrightness = 0.5f
                            var target = 0L
                            detectDragGestures(
                                onDragStart = { offset ->
                                    mode = DragMode.NONE
                                    start = offset
                                    total = Offset.Zero
                                    startPos = current.positionMs
                                    startVolume = current.volume
                                    startBrightness = callbacks.getBrightness()
                                },
                                onDragEnd = {
                                    if (mode == DragMode.SEEK) engine.seekTo(target)
                                    seekPreview = null
                                    mode = DragMode.NONE
                                },
                                onDragCancel = {
                                    seekPreview = null
                                    mode = DragMode.NONE
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    total += drag
                                    if (mode == DragMode.NONE) {
                                        val threshold = 24.dp.toPx()
                                        mode = when {
                                            abs(total.x) > threshold && abs(total.x) > abs(total.y) * 1.5f ->
                                                if (current.seekable && current.durationMs > 0) DragMode.SEEK else DragMode.NONE
                                            abs(total.y) > threshold && abs(total.y) > abs(total.x) ->
                                                if (start.x < widthPx / 2) DragMode.BRIGHTNESS else DragMode.VOLUME
                                            else -> DragMode.NONE
                                        }
                                    }
                                    when (mode) {
                                        DragMode.SEEK -> {
                                            // Ekran genişliği boyunca 2 dakika (kısa videolarda süre kadar)
                                            val span = minOf(current.durationMs, 120_000L)
                                            target = (startPos + (total.x / widthPx * span).toLong()).coerceIn(0, current.durationMs)
                                            seekPreview = target
                                            val delta = (target - startPos) / 1000
                                            showOsd(seekDragFmt.format(if (delta >= 0) "+" else "−", abs(delta), TimeFormat.format(target)))
                                        }
                                        DragMode.VOLUME -> {
                                            val v = (startVolume - total.y / heightPx * 200).roundToInt().coerceIn(0, PlayerEngine.MAX_VOLUME)
                                            engine.setVolume(v)
                                            showOsd(volumeFmt.format(v))
                                        }
                                        DragMode.BRIGHTNESS -> {
                                            val b = (startBrightness - total.y / heightPx).coerceIn(0.01f, 1f)
                                            callbacks.setBrightness(b)
                                            showOsd(brightnessFmt.format((b * 100).roundToInt()))
                                        }
                                        DragMode.NONE -> Unit
                                    }
                                },
                            )
                        }
                )
            }

            // ---------------------------------------------------- kontroller
            AnimatedVisibility(
                visible = controlsVisible && !locked,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ControlsOverlay(
                    state = state,
                    seekPreview = seekPreview,
                    seekStepSec = settings.seekStepSec,
                    onSeekPreview = { seekPreview = it; interaction++ },
                    onSeekCommit = { pos -> engine.seekTo(pos); seekPreview = null },
                    engine = engine,
                    callbacks = callbacks,
                    onLock = { locked = true; controlsVisible = true },
                    onSheet = { sheet = it },
                    onOsd = ::showOsd,
                    onInteraction = { interaction++ },
                )
            }
            if (locked && controlsVisible) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    IconButton(
                        onClick = { locked = false; interaction++ },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                    ) {
                        Icon(Icons.Rounded.Lock, stringResource(R.string.action_unlock), tint = Color.White)
                    }
                }
            }

            if (state.isBuffering && !state.isPlaying) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = Color.White)
            }

            osd?.let { text ->
                Text(
                    text,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }

            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 96.dp),
            )
        }
    }

    when (sheet) {
        PlayerSheet.NONE -> Unit
        PlayerSheet.TRACKS -> TracksSheet(state, engine, callbacks.onPickSubtitle) { sheet = PlayerSheet.NONE }
        PlayerSheet.SPEED -> SpeedSheet(state.rate, engine::setRate) { sheet = PlayerSheet.NONE }
        PlayerSheet.EQUALIZER -> EqualizerSheet(engine, settings) { sheet = PlayerSheet.NONE }
        PlayerSheet.QUEUE -> QueueSheet(state, engine, callbacks.onSavePlaylist) { sheet = PlayerSheet.NONE }
    }
}

@Composable
private fun VideoSurface(engine: PlayerEngine, callbacks: PlayerCallbacks) {
    val context = LocalContext.current
    val layout = remember { VLCVideoLayout(context) }
    AndroidView(factory = { layout }, modifier = Modifier.fillMaxSize())
    DisposableEffect(layout) {
        engine.attachVideo(layout)
        callbacks.onVideoLayout(layout)
        onDispose {
            engine.detachVideo(layout)
            callbacks.onVideoLayout(null)
        }
    }
}

@Composable
private fun AudioArtwork(state: PlayerState, compact: Boolean) {
    val item = state.current ?: return
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), Color.Black)
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            MediaThumbnail(
                item,
                Modifier.size(if (compact) 96.dp else 240.dp),
                sizePx = 720,
                corner = 20.dp,
            )
            if (!compact) {
                Spacer(Modifier.height(24.dp))
                Text(
                    item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.artist ?: stringResource(R.string.unknown_artist),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun ControlsOverlay(
    state: PlayerState,
    seekPreview: Long?,
    seekStepSec: Int,
    onSeekPreview: (Long) -> Unit,
    onSeekCommit: (Long) -> Unit,
    engine: PlayerEngine,
    callbacks: PlayerCallbacks,
    onLock: () -> Unit,
    onSheet: (PlayerSheet) -> Unit,
    onOsd: (String) -> Unit,
    onInteraction: () -> Unit,
) {
    val scaleLabels = com.lumina.player.playback.VideoScale.entries.associateWith { stringResource(it.label) }
    val modeLabels = PlayMode.entries.associateWith { stringResource(playModeLabel(it)) }
    Box(Modifier.fillMaxSize()) {
        // Üst ve alt karartma
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        )

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            // ------------------------------------------------ üst çubuk
            Row(verticalAlignment = Alignment.CenterVertically) {
                OverlayButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onClick = callbacks.onBack)
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        state.current?.title.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.queue.size > 1) {
                        Text(
                            "${state.index + 1} / ${state.queue.size}",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                OverlayButton(Icons.Rounded.Subtitles, stringResource(R.string.tracks)) { onSheet(PlayerSheet.TRACKS) }
                if (state.hasVideo) {
                    OverlayButton(Icons.Rounded.PhotoCamera, stringResource(R.string.action_screenshot)) {
                        callbacks.onScreenshot(); onInteraction()
                    }
                }
                OverlayButton(Icons.Rounded.PlaylistPlay, stringResource(R.string.queue)) { onSheet(PlayerSheet.QUEUE) }
            }

            Spacer(Modifier.weight(1f))

            // ------------------------------------------------ orta kontroller
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OverlayButton(Icons.Rounded.SkipPrevious, stringResource(R.string.action_previous), size = 52) {
                    engine.previous(); onInteraction()
                }
                OverlayButton(Icons.Rounded.Replay10, stringResource(R.string.action_seek_back, seekStepSec), size = 52) {
                    engine.seekBy(-seekStepSec * 1000L); onInteraction()
                }
                IconButton(
                    onClick = { engine.togglePlayPause(); onInteraction() },
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        when {
                            state.ended -> Icons.Rounded.Replay
                            state.isPlaying -> Icons.Rounded.Pause
                            else -> Icons.Rounded.PlayArrow
                        },
                        contentDescription = stringResource(R.string.action_play_pause),
                        tint = Color.White,
                        modifier = Modifier.size(44.dp),
                    )
                }
                OverlayButton(Icons.Rounded.Forward10, stringResource(R.string.action_seek_forward, seekStepSec), size = 52) {
                    engine.seekBy(seekStepSec * 1000L); onInteraction()
                }
                OverlayButton(Icons.Rounded.SkipNext, stringResource(R.string.action_next), size = 52, enabled = state.hasNext) {
                    engine.next(); onInteraction()
                }
            }

            Spacer(Modifier.weight(1f))

            // ------------------------------------------------ zaman çubuğu
            val duration = state.durationMs
            val position = seekPreview ?: state.positionMs
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(TimeFormat.format(position), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                if (duration > 0) {
                    var dragValue by remember { mutableStateOf<Float?>(null) }
                    Slider(
                        value = dragValue ?: (position.toFloat() / duration).coerceIn(0f, 1f),
                        onValueChange = {
                            dragValue = it
                            onSeekPreview((it * duration).toLong())
                        },
                        onValueChangeFinished = {
                            dragValue?.let { onSeekCommit((it * duration).toLong()) }
                            dragValue = null
                        },
                        enabled = state.seekable,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )
                    Text(TimeFormat.format(duration), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Spacer(Modifier.weight(1f))
                    if (state.current?.isStream == true) {
                        Text(
                            stringResource(R.string.time_live),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFEF4444))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // ------------------------------------------------ araç çubuğu
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OverlayButton(Icons.Rounded.LockOpen, stringResource(R.string.action_lock), onClick = onLock)
                OverlayButton(playModeIcon(state.playMode), stringResource(R.string.play_mode)) {
                    val next = PlayMode.entries[(state.playMode.ordinal + 1) % PlayMode.entries.size]
                    engine.setPlayMode(next)
                    onOsd(modeLabels.getValue(next))
                    onInteraction()
                }
                if (state.hasVideo) {
                    OverlayButton(Icons.Rounded.AspectRatio, stringResource(R.string.scale)) {
                        val next = state.scale.next()
                        engine.setScale(next)
                        onOsd(scaleLabels.getValue(next))
                        onInteraction()
                    }
                }
                OverlayButton(Icons.Rounded.Speed, stringResource(R.string.speed)) { onSheet(PlayerSheet.SPEED) }
                OverlayButton(Icons.Rounded.Equalizer, stringResource(R.string.equalizer)) { onSheet(PlayerSheet.EQUALIZER) }
                if (state.hasVideo) {
                    OverlayButton(Icons.Rounded.ScreenRotation, stringResource(R.string.rotate)) {
                        callbacks.onRotate(); onInteraction()
                    }
                    OverlayButton(Icons.Rounded.PictureInPictureAlt, stringResource(R.string.pip), onClick = callbacks.onEnterPip)
                }
            }
            if (state.rate != 1f) {
                Text(
                    stringResource(R.string.osd_speed, formatRate(state.rate)),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun OverlayButton(
    icon: ImageVector,
    description: String,
    size: Int = 44,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size((size * 0.58f).dp),
        )
    }
}

fun playModeLabel(mode: PlayMode): Int = when (mode) {
    PlayMode.SEQUENTIAL -> R.string.mode_sequential
    PlayMode.SHUFFLE -> R.string.mode_shuffle
    PlayMode.REPEAT_ALL -> R.string.mode_repeat_all
    PlayMode.REPEAT_ONE -> R.string.mode_repeat_one
    PlayMode.STOP_AFTER -> R.string.mode_stop_after
}

fun playModeIcon(mode: PlayMode): ImageVector = when (mode) {
    PlayMode.SEQUENTIAL -> Icons.Rounded.East
    PlayMode.SHUFFLE -> Icons.Rounded.Shuffle
    PlayMode.REPEAT_ALL -> Icons.Rounded.Repeat
    PlayMode.REPEAT_ONE -> Icons.Rounded.RepeatOne
    PlayMode.STOP_AFTER -> Icons.Rounded.StopCircle
}

fun formatRate(rate: Float): String =
    if (rate == rate.toInt().toFloat()) rate.toInt().toString() else "%.2f".format(java.util.Locale.US, rate).trimEnd('0')

