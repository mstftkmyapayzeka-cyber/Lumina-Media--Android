package com.lumina.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumina.player.BuildConfig
import com.lumina.player.R
import com.lumina.player.data.Accent
import com.lumina.player.data.AppSettings
import com.lumina.player.data.SettingsStore
import com.lumina.player.data.SubtitleSize
import com.lumina.player.data.ThemeMode
import org.videolan.libvlc.LibVLC

@Composable
fun SettingsScreen(store: SettingsStore, onClearHistory: () -> Unit, onBack: () -> Unit) {
    val s by store.state.collectAsStateWithLifecycle()
    fun set(t: (AppSettings) -> AppSettings) = store.update(t)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Section(R.string.settings_appearance)
            Label(R.string.settings_theme)
            ChipRow {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = s.theme == mode,
                        onClick = { set { it.copy(theme = mode) } },
                        label = {
                            Text(
                                stringResource(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> R.string.theme_system
                                        ThemeMode.DARK -> R.string.theme_dark
                                        ThemeMode.LIGHT -> R.string.theme_light
                                    }
                                )
                            )
                        },
                    )
                }
            }
            Label(R.string.settings_accent)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Accent.entries.forEach { accent ->
                    val color = Color(accent.argb)
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(2.dp, if (s.accent == accent) MaterialTheme.colorScheme.onBackground else Color.Transparent, CircleShape)
                            .clickable { set { it.copy(accent = accent) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (s.accent == accent) Icon(Icons.Rounded.Check, null, tint = Color.White)
                    }
                }
            }

            Section(R.string.settings_playback)
            Toggle(R.string.settings_resume, R.string.settings_resume_desc, s.resumePlayback) { v -> set { it.copy(resumePlayback = v) } }
            Toggle(R.string.settings_restore_session, R.string.settings_restore_session_desc, s.restoreSession) { v -> set { it.copy(restoreSession = v) } }
            Toggle(R.string.settings_background, R.string.settings_background_desc, s.backgroundPlayback) { v -> set { it.copy(backgroundPlayback = v) } }
            Toggle(R.string.settings_pip, R.string.settings_pip_desc, s.autoPip) { v -> set { it.copy(autoPip = v) } }
            Toggle(R.string.settings_hw, R.string.settings_hw_desc, s.hwDecoding) { v -> set { it.copy(hwDecoding = v) } }
            Toggle(R.string.settings_skip_error, R.string.settings_skip_error_desc, s.autoSkipOnError) { v -> set { it.copy(autoSkipOnError = v) } }
            Toggle(R.string.settings_gestures, R.string.settings_gestures_desc, s.gestures) { v -> set { it.copy(gestures = v) } }
            Label(R.string.settings_seek_step)
            ChipRow {
                listOf(5, 10, 15, 30).forEach { sec ->
                    FilterChip(
                        selected = s.seekStepSec == sec,
                        onClick = { set { it.copy(seekStepSec = sec) } },
                        label = { Text(stringResource(R.string.seconds_short, sec)) },
                    )
                }
            }

            Section(R.string.settings_subtitles)
            Toggle(R.string.settings_auto_subs, R.string.settings_auto_subs_desc, s.autoLoadSubtitles) { v -> set { it.copy(autoLoadSubtitles = v) } }
            Label(R.string.settings_subtitle_size)
            ChipRow {
                SubtitleSize.entries.forEach { size ->
                    FilterChip(
                        selected = s.subtitleSize == size,
                        onClick = { set { it.copy(subtitleSize = size) } },
                        label = {
                            Text(
                                stringResource(
                                    when (size) {
                                        SubtitleSize.SMALL -> R.string.size_small
                                        SubtitleSize.NORMAL -> R.string.size_normal
                                        SubtitleSize.LARGE -> R.string.size_large
                                        SubtitleSize.HUGE -> R.string.size_huge
                                    }
                                )
                            )
                        },
                    )
                }
            }
            Hint(R.string.settings_restart_hint)

            Section(R.string.settings_library)
            Toggle(R.string.settings_detect_content, R.string.settings_detect_content_desc, s.detectByContent) { v -> set { it.copy(detectByContent = v) } }
            TextButton(onClick = onClearHistory, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.clear_history))
            }

            Section(R.string.settings_about)
            Text(
                stringResource(R.string.about_text, BuildConfig.VERSION_NAME, runCatching { LibVLC.version() }.getOrDefault("3.x")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Section(title: Int) {
    HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outline)
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Label(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
}

@Composable
private fun Hint(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Toggle(title: Int, desc: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
