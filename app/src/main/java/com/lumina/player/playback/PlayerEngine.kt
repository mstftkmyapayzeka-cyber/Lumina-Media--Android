package com.lumina.player.playback

import android.app.Application
import android.content.ContentResolver
import android.media.AudioManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.lumina.player.LuminaApp
import com.lumina.player.R
import com.lumina.player.core.Formats
import com.lumina.player.core.MediaItem
import com.lumina.player.core.MediaKind
import com.lumina.player.core.PlayMode
import com.lumina.player.core.Playlist
import com.lumina.player.core.SubtitleDecoder
import com.lumina.player.data.HistoryStore
import com.lumina.player.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.security.MessageDigest

/**
 * libVLC MediaPlayer sarmalayıcısı. Arayüzden bağımsızdır: durumunu [state] akışıyla
 * yayınlar, metotlarla kontrol edilir. Tüm çağrılar ana iş parçacığından yapılmalıdır;
 * libVLC Android olayları da ana iş parçacığına iletilir.
 */
class PlayerEngine(
    private val app: Application,
    private val settings: SettingsStore,
    private val history: HistoryStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val libVlc: LibVLC
    private val player: MediaPlayer
    private val playlist = Playlist()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private var openFd: ParcelFileDescriptor? = null
    private var openJob: Job? = null
    private var attachedLayout: VLCVideoLayout? = null
    private var lastProgressSave = 0L
    private var pendingStartPaused = false

    private val audioManager = app.getSystemService(AudioManager::class.java)
    private var pausedByFocus = false
    private var ducked = false
    private val focusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributesCompat.Builder()
                .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                .setContentType(AudioAttributesCompat.CONTENT_TYPE_MOVIE)
                .build()
        )
        .setOnAudioFocusChangeListener { change -> onAudioFocusChange(change) }
        .build()

    init {
        val s = settings.value
        val args = arrayListOf(
            "--audio-time-stretch",          // hız değişiminde ses perdesi korunur
            "--no-sub-autodetect-file",      // altyazıyı biz buluyor ve kodlamasını düzeltiyoruz
            "--no-stats",
            "--network-caching=1500",
            "--freetype-rel-fontsize=${s.subtitleSize.vlcRelSize}",
            "--freetype-outline-thickness=4",
        )
        libVlc = LibVLC(app, args)
        player = MediaPlayer(libVlc)
        player.setEventListener(object : MediaPlayer.EventListener {
            override fun onEvent(event: MediaPlayer.Event) = onVlcEvent(event)
        })
        player.setVolume(s.volume.coerceIn(0, MAX_VOLUME))
        playlist.mode = s.playMode
        _state.update { it.copy(volume = s.volume, rate = s.rate, playMode = s.playMode) }
        applyEqualizer()
        if (s.restoreSession) restoreSession()
    }

    // ================================================================== Çalma listesi
    fun playItems(items: List<MediaItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        saveProgressNow()
        playlist.setItems(items, startIndex)
        publishQueue()
        openCurrent()
    }

    fun playAt(index: Int) {
        if (index == playlist.currentIndex && _state.value.prepared) {
            play()
            return
        }
        saveProgressNow()
        if (playlist.select(index) != null) {
            publishQueue()
            openCurrent()
        }
    }

    fun enqueue(items: List<MediaItem>) {
        val wasEmpty = playlist.isEmpty
        playlist.add(items)
        publishQueue()
        if (wasEmpty) openCurrent()
    }

    fun playNext(items: List<MediaItem>) {
        val wasEmpty = playlist.isEmpty
        playlist.insertNext(items)
        publishQueue()
        if (wasEmpty) openCurrent()
    }

    fun removeAt(index: Int) {
        val removingCurrent = index == playlist.currentIndex
        playlist.remove(index)
        publishQueue()
        if (removingCurrent) {
            if (playlist.isEmpty) stop() else openCurrent()
        }
    }

    fun move(from: Int, to: Int) {
        playlist.move(from, to)
        publishQueue()
    }

    fun sortQueueByName() {
        playlist.sortByName()
        publishQueue()
    }

    fun clearQueue() {
        stop()
        playlist.clear()
        publishQueue()
    }

    fun next() {
        saveProgressNow()
        if (playlist.next(auto = false) != null) {
            publishQueue()
            openCurrent()
        }
    }

    fun previous() {
        if (_state.value.positionMs > 3_000 && _state.value.seekable) {
            seekTo(0)
            return
        }
        saveProgressNow()
        if (playlist.previous() != null) {
            publishQueue()
            openCurrent()
        }
    }

    fun setPlayMode(mode: PlayMode) {
        playlist.mode = mode
        settings.update { it.copy(playMode = mode) }
        _state.update { it.copy(playMode = mode) }
    }

    fun cyclePlayMode() = setPlayMode(PlayMode.entries[(playlist.mode.ordinal + 1) % PlayMode.entries.size])

    // ================================================================== Oynatma
    fun play() {
        val st = _state.value
        when {
            st.current == null -> return
            !st.prepared -> openCurrent()
            st.ended -> openCurrent(fromStart = true)
            else -> {
                requestFocus()
                player.play()
            }
        }
    }

    fun pause() {
        if (player.isPlaying) player.pause()
        saveProgressNow()
    }

    fun togglePlayPause() = if (player.isPlaying) pause() else play()

    fun stop() {
        saveProgressNow()
        openJob?.cancel()
        player.stop()
        closeFd()
        abandonFocus()
        _state.update { it.copy(isPlaying = false, isBuffering = false, prepared = false, positionMs = 0) }
    }

    fun seekTo(ms: Long) {
        val st = _state.value
        if (!st.prepared || !st.seekable) return
        val target = if (st.durationMs > 0) ms.coerceIn(0, st.durationMs) else ms.coerceAtLeast(0)
        player.setTime(target)
        _state.update { it.copy(positionMs = target, ended = false) }
    }

    fun seekBy(deltaMs: Long) = seekTo(_state.value.positionMs + deltaMs)

    fun setRate(rate: Float) {
        val r = rate.coerceIn(MIN_RATE, MAX_RATE)
        player.setRate(r)
        settings.update { it.copy(rate = r) }
        _state.update { it.copy(rate = r) }
    }

    fun setVolume(volume: Int) {
        val v = volume.coerceIn(0, MAX_VOLUME)
        player.setVolume(if (ducked) v / 3 else v)
        settings.update { it.copy(volume = v) }
        _state.update { it.copy(volume = v) }
    }

    // ================================================================== İzler / altyazı
    fun selectAudioTrack(id: Int) {
        player.setAudioTrack(id)
        refreshTracks()
    }

    fun selectSubtitleTrack(id: Int) {
        player.setSpuTrack(id)
        refreshTracks()
    }

    fun setSubtitleDelay(ms: Long) {
        player.setSpuDelay(ms * 1000)
        _state.update { it.copy(subtitleDelayMs = ms) }
    }

    fun setAudioDelay(ms: Long) {
        player.setAudioDelay(ms * 1000)
        _state.update { it.copy(audioDelayMs = ms) }
    }

    /** Harici altyazı dosyası yükler (kodlaması UTF-8'e çevrilir). */
    fun addSubtitle(uri: Uri) {
        scope.launch {
            val file = withContext(Dispatchers.IO) { prepareSubtitle(uri.toString()) }
            if (file == null) {
                emit(app.getString(R.string.msg_subtitle_failed))
                return@launch
            }
            player.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(file), true)
            emit(app.getString(R.string.msg_subtitle_loaded, file.name.substringAfter('_')))
        }
    }

    // ================================================================== Video
    fun attachVideo(layout: VLCVideoLayout) {
        if (attachedLayout === layout) return
        if (attachedLayout != null) player.detachViews()
        player.attachViews(layout, null, true, false)
        attachedLayout = layout
        player.setVideoScale(_state.value.scale.vlc)
    }

    fun detachVideo(layout: VLCVideoLayout) {
        if (attachedLayout !== layout) return
        player.detachViews()
        attachedLayout = null
    }

    /** Arka planda video izi kapatılarak yalnızca ses çalınır. */
    fun setVideoEnabled(enabled: Boolean) {
        if (_state.value.prepared) player.setVideoTrackEnabled(enabled)
    }

    fun setScale(scale: VideoScale) {
        player.setVideoScale(scale.vlc)
        _state.update { it.copy(scale = scale) }
    }

    // ================================================================== Ekolayzer
    val equalizerPresets: List<String> by lazy {
        (0 until MediaPlayer.Equalizer.getPresetCount()).map { MediaPlayer.Equalizer.getPresetName(it) }
    }

    val equalizerFrequencies: List<Float> by lazy {
        (0 until MediaPlayer.Equalizer.getBandCount()).map { MediaPlayer.Equalizer.getBandFrequency(it) }
    }

    /** Hazır ayarın ön yükseltme ve bant değerleri. */
    fun presetValues(preset: Int): Pair<Float, List<Float>> {
        val eq = MediaPlayer.Equalizer.createFromPreset(preset)
        return eq.getPreAmp() to equalizerFrequencies.indices.map { eq.getAmp(it) }
    }

    fun updateEqualizer(enabled: Boolean, preset: Int, preamp: Float, bands: List<Float>) {
        settings.update { it.copy(eqEnabled = enabled, eqPreset = preset, eqPreamp = preamp, eqBands = bands) }
        applyEqualizer()
    }

    private fun applyEqualizer() {
        val s = settings.value
        if (!s.eqEnabled) {
            player.setEqualizer(null)
            return
        }
        val eq = MediaPlayer.Equalizer.create()
        eq.setPreAmp(s.eqPreamp)
        val bandCount = MediaPlayer.Equalizer.getBandCount()
        s.eqBands.forEachIndexed { i, v -> if (i < bandCount) eq.setAmp(i, v) }
        player.setEqualizer(eq)
    }

    // ================================================================== Medya açma
    private fun openCurrent(fromStart: Boolean = false, startPaused: Boolean = false) {
        val item = playlist.current ?: return
        openJob?.cancel()
        _state.update {
            it.copy(
                current = item, prepared = false, isBuffering = true, ended = false,
                positionMs = 0, durationMs = item.durationMs, hasVideo = item.kind == MediaKind.VIDEO,
                audioTracks = emptyList(), subtitleTracks = emptyList(), audioTrack = -1, subtitleTrack = -1,
                subtitleDelayMs = 0, audioDelayMs = 0,
            )
        }
        openJob = scope.launch {
            val s = settings.value
            val resumeAt = if (!fromStart && s.resumePlayback && !item.isStream) {
                withContext(Dispatchers.IO) { runCatching { history.resumePosition(item.uri) }.getOrNull() }
            } else null
            val subtitleFiles = if (item.kind == MediaKind.VIDEO && s.autoLoadSubtitles) {
                withContext(Dispatchers.IO) {
                    val candidates = item.subtitles.ifEmpty { LuminaApp.instance.repository.findSiblingSubtitles(item) }
                    candidates.take(4).mapNotNull { prepareSubtitle(it) }
                }
            } else emptyList()

            val media = buildMedia(item)
            if (media == null) {
                onOpenFailed(item)
                return@launch
            }
            media.setHWDecoderEnabled(s.hwDecoding, false)
            if (resumeAt != null) media.addOption(":start-time=${resumeAt / 1000.0}")
            player.setMedia(media)
            media.release()
            subtitleFiles.forEachIndexed { i, f ->
                player.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(f), i == 0)
            }
            pendingStartPaused = startPaused
            requestFocus()
            player.play()
            _state.update { it.copy(prepared = true) }
            if (resumeAt != null) _events.tryEmit(PlayerEvent.Resumed(resumeAt))
            PlaybackService.start(app)
        }
    }

    private fun buildMedia(item: MediaItem): Media? {
        closeFd()
        val uri = Uri.parse(item.uri)
        return try {
            val path = item.path
            when {
                item.isStream -> Media(libVlc, uri)
                path != null && File(path).canRead() -> Media(libVlc, path)
                uri.scheme == ContentResolver.SCHEME_FILE && uri.path != null -> Media(libVlc, uri.path!!)
                uri.scheme == ContentResolver.SCHEME_CONTENT -> {
                    val pfd = app.contentResolver.openFileDescriptor(uri, "r") ?: return null
                    openFd = pfd
                    Media(libVlc, pfd.fileDescriptor)
                }
                else -> Media(libVlc, uri)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Medya açılamadı: ${item.uri}", e)
            null
        }
    }

    private fun closeFd() {
        runCatching { openFd?.close() }
        openFd = null
    }

    private fun onOpenFailed(item: MediaItem) {
        _state.update { it.copy(isBuffering = false, isPlaying = false, prepared = false) }
        val msg = if (item.isStream) app.getString(R.string.error_stream_failed, item.uri)
        else app.getString(R.string.error_cannot_open, item.title)
        emit(msg)
        skipAfterError()
    }

    private fun skipAfterError() {
        if (!settings.value.autoSkipOnError) return
        val before = playlist.currentIndex
        if (playlist.mode != PlayMode.REPEAT_ONE && playlist.hasNext() && playlist.size > 1) {
            playlist.next(auto = false)
            if (playlist.currentIndex != before) {
                publishQueue()
                emit(app.getString(R.string.error_skipping))
                openCurrent()
            }
        }
    }

    /**
     * Altyazıyı önbelleğe kopyalar; metin tabanlıysa Türkçe kodlama tespitiyle UTF-8'e çevirir.
     * libVLC `content://` adreslerini doğrudan açamadığı için her durumda yerel dosya döner.
     */
    private fun prepareSubtitle(uriString: String): File? = runCatching {
        val uri = Uri.parse(uriString)
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "subtitle.srt"
        val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                total += n
                if (total > MAX_SUBTITLE_BYTES) return@runCatching null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: return@runCatching null
        val dir = File(app.cacheDir, "subtitles").apply { mkdirs() }
        val hash = MessageDigest.getInstance("MD5").digest(uriString.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(10)
        val isText = SubtitleDecoder.looksLikeText(bytes)
        val ext = Formats.extOf(name).let { if (it.isEmpty() || it == "txt") "srt" else it }
        val file = File(dir, "${hash}_${Formats.stemOf(name)}.$ext")
        if (isText) {
            file.writeText(SubtitleDecoder.decode(bytes).text, Charsets.UTF_8)
        } else {
            file.writeBytes(bytes)
        }
        file
    }.onFailure { Log.w(TAG, "Altyazı hazırlanamadı: $uriString", it) }.getOrNull()

    // ================================================================== libVLC olayları
    private fun onVlcEvent(e: MediaPlayer.Event) {
        when (e.type) {
            MediaPlayer.Event.Opening -> _state.update { it.copy(isBuffering = true) }
            MediaPlayer.Event.Buffering -> _state.update { it.copy(isBuffering = e.buffering < 100f) }
            MediaPlayer.Event.Playing -> {
                _state.update { it.copy(isPlaying = true, isBuffering = false, ended = false) }
                val rate = settings.value.rate
                if (player.getRate() != rate) player.setRate(rate)
                if (pendingStartPaused) {
                    pendingStartPaused = false
                    player.pause()
                }
                refreshTracks()
            }
            MediaPlayer.Event.Paused -> {
                _state.update { it.copy(isPlaying = false) }
                saveProgressNow()
            }
            MediaPlayer.Event.Stopped -> _state.update { it.copy(isPlaying = false, isBuffering = false) }
            MediaPlayer.Event.EndReached -> onEndReached()
            MediaPlayer.Event.EncounteredError -> {
                val item = _state.value.current
                _state.update { it.copy(isPlaying = false, isBuffering = false, prepared = false) }
                if (item != null) {
                    emit(
                        if (item.isStream) app.getString(R.string.error_stream_failed, item.uri)
                        else app.getString(R.string.error_playback_failed, item.title)
                    )
                }
                skipAfterError()
            }
            MediaPlayer.Event.TimeChanged -> {
                _state.update { it.copy(positionMs = e.timeChanged) }
                maybeSaveProgress()
            }
            MediaPlayer.Event.LengthChanged -> _state.update { it.copy(durationMs = e.lengthChanged) }
            MediaPlayer.Event.SeekableChanged -> _state.update { it.copy(seekable = e.seekable) }
            MediaPlayer.Event.Vout -> if (e.voutCount > 0) _state.update { it.copy(hasVideo = true) }
            MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected -> refreshTracks()
        }
    }

    private fun onEndReached() {
        val st = _state.value
        st.current?.let { item ->
            val dur = if (st.durationMs > 0) st.durationMs else st.positionMs
            persistProgress(item, dur, dur)
        }
        _state.update { it.copy(isPlaying = false, positionMs = it.durationMs) }
        if (playlist.next(auto = true) != null) {
            publishQueue()
            openCurrent(fromStart = true)
        } else {
            _state.update { it.copy(ended = true) }
            abandonFocus()
            _events.tryEmit(PlayerEvent.QueueFinished)
        }
    }

    private fun refreshTracks() {
        val audio = player.getAudioTracks()?.map { Track(it.id, it.name ?: "#${it.id}") }.orEmpty()
        val spu = player.getSpuTracks()?.map { Track(it.id, it.name ?: "#${it.id}") }.orEmpty()
        _state.update {
            it.copy(
                audioTracks = audio,
                audioTrack = player.getAudioTrack(),
                subtitleTracks = spu,
                subtitleTrack = player.getSpuTrack(),
                hasVideo = it.hasVideo || player.getVideoTracksCount() > 0,
            )
        }
    }

    // ================================================================== Kalıcılık
    private fun maybeSaveProgress() {
        val now = System.currentTimeMillis()
        if (now - lastProgressSave > 5_000) saveProgressNow()
    }

    fun saveProgressNow() {
        val st = _state.value
        val item = st.current ?: return
        if (!st.prepared) return
        lastProgressSave = System.currentTimeMillis()
        persistProgress(item, st.positionMs, st.durationMs)
    }

    private fun persistProgress(item: MediaItem, position: Long, duration: Long) {
        scope.launch(Dispatchers.IO) { runCatching { history.save(item, position, duration) } }
    }

    private fun publishQueue() {
        _state.update {
            it.copy(queue = playlist.items, index = playlist.currentIndex, current = playlist.current ?: it.current.takeIf { playlist.size > 0 })
        }
        saveSession()
    }

    private fun sessionFile() = File(app.filesDir, "session.json")

    private fun saveSession() {
        val items = playlist.items
        val index = playlist.currentIndex
        scope.launch(Dispatchers.IO) {
            runCatching {
                val arr = JSONArray()
                items.take(5_000).forEach { item ->
                    arr.put(
                        JSONObject()
                            .put("uri", item.uri).put("title", item.title).put("kind", item.kind.name)
                            .put("path", item.path ?: "").put("duration", item.durationMs)
                            .put("artist", item.artist ?: "")
                            .put("subs", JSONArray(item.subtitles))
                    )
                }
                sessionFile().writeText(JSONObject().put("index", index).put("items", arr).toString())
            }
        }
    }

    private fun restoreSession() {
        val json = runCatching { JSONObject(sessionFile().readText()) }.getOrNull() ?: return
        val arr = json.optJSONArray("items") ?: return
        val items = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val subs = o.optJSONArray("subs")
            MediaItem(
                uri = o.optString("uri").ifEmpty { return@mapNotNull null },
                title = o.optString("title"),
                kind = runCatching { MediaKind.valueOf(o.optString("kind")) }.getOrDefault(MediaKind.VIDEO),
                path = o.optString("path").ifEmpty { null },
                durationMs = o.optLong("duration"),
                artist = o.optString("artist").ifEmpty { null },
                subtitles = subs?.let { s -> (0 until s.length()).map { s.optString(it) } }.orEmpty(),
            )
        }
        if (items.isEmpty()) return
        playlist.setItems(items, json.optInt("index", 0))
        _state.update {
            it.copy(
                queue = playlist.items, index = playlist.currentIndex, current = playlist.current,
                durationMs = playlist.current?.durationMs ?: 0, hasVideo = playlist.current?.kind == MediaKind.VIDEO,
            )
        }
    }

    // ================================================================== Ses odağı
    private fun requestFocus() {
        AudioManagerCompat.requestAudioFocus(audioManager, focusRequest)
    }

    private fun abandonFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest)
    }

    private fun onAudioFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByFocus = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausedByFocus = player.isPlaying
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                ducked = true
                player.setVolume(_state.value.volume / 3)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (ducked) {
                    ducked = false
                    player.setVolume(_state.value.volume)
                }
                if (pausedByFocus) {
                    pausedByFocus = false
                    player.play()
                }
            }
        }
    }

    private fun emit(text: String) {
        _events.tryEmit(PlayerEvent.Message(text))
    }

    companion object {
        private const val TAG = "LuminaEngine"
        const val MIN_RATE = 0.25f
        const val MAX_RATE = 4f
        const val MAX_VOLUME = 200
        private const val MAX_SUBTITLE_BYTES = 8 * 1024 * 1024
    }
}
