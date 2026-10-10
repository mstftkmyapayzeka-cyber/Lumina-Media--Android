package com.lumina.player.data

import android.content.Context
import com.lumina.player.core.PlayMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, DARK, LIGHT }

enum class Accent(val argb: Long) {
    BLUE(0xFF3B82F6),
    PURPLE(0xFF8B5CF6),
    RED(0xFFEF4444),
    GREEN(0xFF22C55E),
    ORANGE(0xFFF97316),
}

/** VLC göreli altyazı boyutu: küçük sayı = büyük yazı. */
enum class SubtitleSize(val vlcRelSize: Int) { SMALL(20), NORMAL(16), LARGE(12), HUGE(8) }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: Accent = Accent.BLUE,
    val resumePlayback: Boolean = true,
    val restoreSession: Boolean = true,
    val backgroundPlayback: Boolean = false,
    val autoPip: Boolean = true,
    val hwDecoding: Boolean = true,
    val detectByContent: Boolean = true,
    val autoLoadSubtitles: Boolean = true,
    val autoSkipOnError: Boolean = true,
    val seekStepSec: Int = 10,
    val gestures: Boolean = true,
    val subtitleSize: SubtitleSize = SubtitleSize.NORMAL,
    val volume: Int = 100,
    val rate: Float = 1f,
    val playMode: PlayMode = PlayMode.SEQUENTIAL,
    val eqEnabled: Boolean = false,
    val eqPreset: Int = -1,
    val eqPreamp: Float = 0f,
    val eqBands: List<Float> = List(10) { 0f },
    val folders: List<String> = emptyList(),
)

/** SharedPreferences tabanlı ayarlar; değişiklikler [state] akışıyla yayınlanır. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val value: AppSettings get() = _state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        save(next)
    }

    fun addFolder(uri: String) = update { s -> if (uri in s.folders) s else s.copy(folders = s.folders + uri) }

    fun removeFolder(uri: String) = update { s -> s.copy(folders = s.folders - uri) }

    private inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = enumOf(prefs.getString("theme", null), d.theme),
            accent = enumOf(prefs.getString("accent", null), d.accent),
            resumePlayback = prefs.getBoolean("resume_playback", d.resumePlayback),
            restoreSession = prefs.getBoolean("restore_session", d.restoreSession),
            backgroundPlayback = prefs.getBoolean("background_playback", d.backgroundPlayback),
            autoPip = prefs.getBoolean("auto_pip", d.autoPip),
            hwDecoding = prefs.getBoolean("hw_decoding", d.hwDecoding),
            detectByContent = prefs.getBoolean("detect_by_content", d.detectByContent),
            autoLoadSubtitles = prefs.getBoolean("auto_load_subtitles", d.autoLoadSubtitles),
            autoSkipOnError = prefs.getBoolean("auto_skip_on_error", d.autoSkipOnError),
            seekStepSec = prefs.getInt("seek_step", d.seekStepSec),
            gestures = prefs.getBoolean("gestures", d.gestures),
            subtitleSize = enumOf(prefs.getString("subtitle_size", null), d.subtitleSize),
            volume = prefs.getInt("volume", d.volume),
            rate = prefs.getFloat("rate", d.rate),
            playMode = enumOf(prefs.getString("play_mode", null), d.playMode),
            eqEnabled = prefs.getBoolean("eq_enabled", d.eqEnabled),
            eqPreset = prefs.getInt("eq_preset", d.eqPreset),
            eqPreamp = prefs.getFloat("eq_preamp", d.eqPreamp),
            eqBands = prefs.getString("eq_bands", null)
                ?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 10 } ?: d.eqBands,
            folders = prefs.getString("folders", null)
                ?.split('\n')?.filter { it.isNotBlank() } ?: d.folders,
        )
    }

    private fun save(s: AppSettings) {
        prefs.edit()
            .putString("theme", s.theme.name)
            .putString("accent", s.accent.name)
            .putBoolean("resume_playback", s.resumePlayback)
            .putBoolean("restore_session", s.restoreSession)
            .putBoolean("background_playback", s.backgroundPlayback)
            .putBoolean("auto_pip", s.autoPip)
            .putBoolean("hw_decoding", s.hwDecoding)
            .putBoolean("detect_by_content", s.detectByContent)
            .putBoolean("auto_load_subtitles", s.autoLoadSubtitles)
            .putBoolean("auto_skip_on_error", s.autoSkipOnError)
            .putInt("seek_step", s.seekStepSec)
            .putBoolean("gestures", s.gestures)
            .putString("subtitle_size", s.subtitleSize.name)
            .putInt("volume", s.volume)
            .putFloat("rate", s.rate)
            .putString("play_mode", s.playMode.name)
            .putBoolean("eq_enabled", s.eqEnabled)
            .putInt("eq_preset", s.eqPreset)
            .putFloat("eq_preamp", s.eqPreamp)
            .putString("eq_bands", s.eqBands.joinToString(","))
            .putString("folders", s.folders.joinToString("\n"))
            .apply()
    }
}
