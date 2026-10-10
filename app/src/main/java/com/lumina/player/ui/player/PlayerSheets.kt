package com.lumina.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumina.player.R
import com.lumina.player.core.PlayMode
import com.lumina.player.core.TimeFormat
import com.lumina.player.data.AppSettings
import com.lumina.player.playback.PlayerEngine
import com.lumina.player.playback.PlayerState
import kotlin.math.roundToInt

@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) { content() }
    }
}

@Composable
private fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

// ====================================================================== Ses / altyazı izleri
@Composable
fun TracksSheet(state: PlayerState, engine: PlayerEngine, onPickSubtitle: () -> Unit, onDismiss: () -> Unit) {
    Sheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetTitle(stringResource(R.string.menu_subtitle_track))
            if (state.subtitleTracks.isEmpty()) {
                Hint(stringResource(R.string.no_subtitles))
            }
            state.subtitleTracks.forEach { t ->
                RadioRow(
                    label = if (t.id == -1) stringResource(R.string.menu_subtitle_off) else t.name,
                    selected = t.id == state.subtitleTrack,
                ) { engine.selectSubtitleTrack(t.id) }
            }
            OutlinedButton(
                onClick = { onPickSubtitle(); onDismiss() },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            ) { Text(stringResource(R.string.menu_subtitle_load)) }
            DelayRow(stringResource(R.string.subtitle_delay), state.subtitleDelayMs, engine::setSubtitleDelay)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SheetTitle(stringResource(R.string.menu_audio_track))
            if (state.audioTracks.isEmpty()) Hint(stringResource(R.string.no_audio_tracks))
            state.audioTracks.forEach { t ->
                RadioRow(
                    label = if (t.id == -1) stringResource(R.string.menu_subtitle_off) else t.name,
                    selected = t.id == state.audioTrack,
                ) { engine.selectAudioTrack(t.id) }
            }
            DelayRow(stringResource(R.string.audio_delay), state.audioDelayMs, engine::setAudioDelay)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DelayRow(label: String, valueMs: Long, onChange: (Long) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(valueMs - 100) }) { Text("−100") }
        Text(
            stringResource(R.string.delay_ms, valueMs),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onChange(0) }.padding(horizontal = 4.dp),
        )
        TextButton(onClick = { onChange(valueMs + 100) }) { Text("+100") }
    }
}

// ====================================================================== Hız
@Composable
fun SpeedSheet(rate: Float, onRate: (Float) -> Unit, onDismiss: () -> Unit) {
    val presets = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f)
    Sheet(onDismiss) {
        SheetTitle(stringResource(R.string.menu_speed) + "  •  ${formatRate(rate)}×")
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { p ->
                FilterChip(
                    selected = abs(rate - p) < 0.01f,
                    onClick = { onRate(p) },
                    label = { Text(if (p == 1f) stringResource(R.string.menu_speed_normal) else "${formatRate(p)}×") },
                )
            }
        }
        Slider(
            value = rate,
            onValueChange = { onRate((it * 20).roundToInt() / 20f) },
            valueRange = PlayerEngine.MIN_RATE..PlayerEngine.MAX_RATE,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

private fun abs(v: Float) = kotlin.math.abs(v)

// ====================================================================== Ekolayzer
@Composable
fun EqualizerSheet(engine: PlayerEngine, settings: AppSettings, onDismiss: () -> Unit) {
    var enabled by remember { mutableStateOf(settings.eqEnabled) }
    var preset by remember { mutableIntStateOf(settings.eqPreset) }
    var preamp by remember { mutableFloatStateOf(settings.eqPreamp) }
    val frequencies = remember { engine.equalizerFrequencies }
    val bands = remember {
        mutableStateListOf<Float>().apply {
            addAll(settings.eqBands.take(frequencies.size))
            while (size < frequencies.size) add(0f)
        }
    }
    fun commit() = engine.updateEqualizer(enabled, preset, preamp, bands.toList())

    Sheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp))
                SheetTitle(stringResource(R.string.equalizer), Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it; commit() })
            }
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = preset == -1, onClick = { preset = -1; commit() }, label = { Text(stringResource(R.string.eq_custom)) })
                engine.equalizerPresets.forEachIndexed { i, name ->
                    FilterChip(
                        selected = preset == i,
                        onClick = {
                            val (p, b) = engine.presetValues(i)
                            preset = i
                            preamp = p
                            b.forEachIndexed { idx, v -> if (idx < bands.size) bands[idx] = v }
                            enabled = true
                            commit()
                        },
                        label = { Text(name) },
                    )
                }
            }
            BandSlider(stringResource(R.string.eq_preamp), preamp, onChange = { preamp = it; preset = -1 }, onDone = { commit() })
            HorizontalDivider(Modifier.padding(vertical = 4.dp, horizontal = 20.dp))
            frequencies.forEachIndexed { i, f ->
                BandSlider(
                    label = if (f >= 1000f) "${formatRate(f / 1000f)} kHz" else "${f.roundToInt()} Hz",
                    value = bands[i],
                    onChange = { bands[i] = it; preset = -1 },
                    onDone = { commit() },
                )
            }
            TextButton(
                onClick = {
                    preamp = 0f
                    for (i in bands.indices) bands[i] = 0f
                    preset = -1
                    commit()
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            ) { Text(stringResource(R.string.eq_reset)) }
        }
    }
}

@Composable
private fun BandSlider(label: String, value: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = -20f..20f,
            modifier = Modifier.weight(1f),
        )
        Text(
            "%+.0f dB".format(value),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(48.dp).padding(start = 8.dp),
        )
    }
}

// ====================================================================== Çalma sırası
@Composable
fun QueueSheet(state: PlayerState, engine: PlayerEngine, onSave: () -> Unit, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (state.index > 2) listState.scrollToItem(state.index - 2) }
    Sheet(onDismiss) {
        SheetTitle(stringResource(R.string.queue) + "  •  ${state.queue.size}")
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = { engine.cyclePlayMode() },
                label = { Text(stringResource(playModeLabel(state.playMode))) },
                leadingIcon = { Icon(playModeIcon(state.playMode), null, Modifier.size(18.dp)) },
            )
            AssistChip(onClick = { engine.sortQueueByName() }, label = { Text(stringResource(R.string.sort_name)) })
            AssistChip(onClick = onSave, label = { Text(stringResource(R.string.save_playlist)) })
            AssistChip(onClick = { engine.clearQueue(); onDismiss() }, label = { Text(stringResource(R.string.clear_queue)) })
        }
        Spacer(Modifier.size(8.dp))
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = 520.dp)) {
            items(state.queue.size, key = { "$it:${state.queue[it].uri}" }) { i ->
                val item = state.queue[i]
                val isCurrent = i == state.index
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable { engine.playAt(i) }
                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${i + 1}",
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(32.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (item.durationMs > 0 || item.artist != null) {
                            Text(
                                listOfNotNull(item.artist, item.durationMs.takeIf { it > 0 }?.let(TimeFormat::format)).joinToString("  •  "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    IconButton(onClick = { engine.removeAt(i) }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.remove))
                    }
                }
            }
        }
    }
}

