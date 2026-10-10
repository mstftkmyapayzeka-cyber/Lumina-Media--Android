package com.lumina.player.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumina.player.R
import com.lumina.player.core.Formats
import com.lumina.player.core.MediaItem
import com.lumina.player.core.MediaKind
import com.lumina.player.core.TimeFormat
import com.lumina.player.playback.PlayerState
import com.lumina.player.ui.components.MediaThumbnail

data class RowAction(val label: String, val onClick: () -> Unit)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaRow(
    item: MediaItem,
    progress: Float,
    onClick: () -> Unit,
    actions: List<RowAction>,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onClick, onLongClick = { menu = true })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isVideo = item.kind == MediaKind.VIDEO
        MediaThumbnail(
            item = item,
            modifier = if (isVideo) Modifier.width(112.dp).aspectRatio(16f / 9f) else Modifier.size(56.dp),
            progress = progress,
            badge = item.resolutionLabel,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (progress >= 1f) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitleOf(item),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (actions.isNotEmpty()) {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    actions.forEach { a ->
                        DropdownMenuItem(text = { Text(a.label) }, onClick = { menu = false; a.onClick() })
                    }
                }
            }
        }
    }
}

@Composable
private fun subtitleOf(item: MediaItem): String {
    val parts = mutableListOf<String>()
    if (item.kind == MediaKind.AUDIO) parts += item.artist ?: stringResource(R.string.unknown_artist)
    if (item.durationMs > 0) parts += TimeFormat.format(item.durationMs)
    if (item.sizeBytes > 0) parts += TimeFormat.size(item.sizeBytes)
    if (item.isStream) parts += stringResource(R.string.stream)
    if (item.kind == MediaKind.VIDEO && item.subtitles.isNotEmpty()) parts += stringResource(R.string.has_subtitles)
    return parts.joinToString("  •  ")
}

@Composable
fun MiniPlayer(state: PlayerState, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    val item = state.current ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen),
    ) {
        Column {
            Row(
                Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaThumbnail(item, Modifier.size(44.dp), corner = 8.dp, sizePx = 160)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(
                        if (state.durationMs > 0) "${TimeFormat.format(state.positionMs)} / ${TimeFormat.format(state.durationMs)}"
                        else item.artist ?: stringResource(R.string.app_name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.action_play_pause),
                    )
                }
                IconButton(onClick = onNext, enabled = state.hasNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.action_next))
                }
            }
            if (state.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
fun OpenUrlDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    fun submit() {
        if (Formats.isStreamUrl(text)) onOpen(text.trim()) else error = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_open_url_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dialog_open_url_label), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    singleLine = true,
                    placeholder = { Text("https://…") },
                    isError = error,
                    supportingText = if (error) {
                        { Text(stringResource(R.string.dialog_open_url_invalid)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                )
            }
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text(stringResource(R.string.dialog_open)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
