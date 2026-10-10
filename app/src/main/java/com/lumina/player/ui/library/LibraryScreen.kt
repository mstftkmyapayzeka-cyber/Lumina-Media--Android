package com.lumina.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumina.player.R
import com.lumina.player.core.MediaItem
import com.lumina.player.core.MediaKind
import com.lumina.player.data.MediaFolder
import com.lumina.player.playback.PlayerState
import com.lumina.player.ui.components.MediaThumbnail

/** Kütüphane ekranlarının dış dünyaya (Activity) bağlandığı eylemler. */
class LibraryActions(
    val play: (List<MediaItem>, Int) -> Unit,
    val shufflePlay: (List<MediaItem>) -> Unit,
    val enqueue: (List<MediaItem>) -> Unit,
    val playNext: (List<MediaItem>) -> Unit,
    val openFiles: () -> Unit,
    val addFolder: () -> Unit,
    val openUrl: (String) -> Unit,
    val openPlaylist: () -> Unit,
    val openPlayer: () -> Unit,
    val togglePlay: () -> Unit,
    val next: () -> Unit,
    val openSettings: () -> Unit,
    val openFolder: (String) -> Unit,
    val requestPermission: () -> Unit,
)

private enum class HomeTab(val title: Int, val icon: ImageVector) {
    VIDEOS(R.string.tab_videos, Icons.Rounded.Movie),
    MUSIC(R.string.tab_music, Icons.Rounded.MusicNote),
    FOLDERS(R.string.tab_folders, Icons.Rounded.Folder),
    HISTORY(R.string.tab_history, Icons.Rounded.History),
}

@Composable
fun HomeScreen(vm: LibraryViewModel, playerState: PlayerState, actions: LibraryActions) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var openMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var showUrl by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (searching) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                placeholder = { Text(stringResource(R.string.search_hint)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            searching = !searching
                            if (!searching) query = ""
                        }) {
                            Icon(
                                if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.search),
                            )
                        }
                        Box {
                            IconButton(onClick = { sortMenu = true }) {
                                Icon(Icons.Rounded.Sort, contentDescription = stringResource(R.string.sort))
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(order.label())) },
                                        onClick = { sortMenu = false; vm.setSort(order) },
                                        trailingIcon = if (order == state.sort) {
                                            { Text("✓", color = MaterialTheme.colorScheme.primary) }
                                        } else null,
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { openMenu = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.menu_open))
                            }
                            DropdownMenu(expanded = openMenu, onDismissRequest = { openMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_open_file)) },
                                    leadingIcon = { Icon(Icons.Rounded.InsertDriveFile, null) },
                                    onClick = { openMenu = false; actions.openFiles() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_open_folder)) },
                                    leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                                    onClick = { openMenu = false; actions.addFolder() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_open_url)) },
                                    leadingIcon = { Icon(Icons.Rounded.Link, null) },
                                    onClick = { openMenu = false; showUrl = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_open_playlist)) },
                                    leadingIcon = { Icon(Icons.Rounded.QueueMusic, null) },
                                    onClick = { openMenu = false; actions.openPlaylist() },
                                )
                            }
                        }
                        IconButton(onClick = actions.openSettings) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    HomeTab.entries.forEachIndexed { i, t ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = { Text(stringResource(t.title), maxLines = 1) },
                            icon = { Icon(t.icon, contentDescription = null) },
                        )
                    }
                }
            }
        },
        bottomBar = {
            Box(Modifier.navigationBarsPadding()) {
                MiniPlayer(playerState, onOpen = actions.openPlayer, onToggle = actions.togglePlay, onNext = actions.next)
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.hasPermission,
            onRefresh = { vm.refresh() },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val q = query.trim()
            fun List<MediaItem>.filtered() = if (q.isEmpty()) this else filter { it.title.contains(q, ignoreCase = true) || it.artist?.contains(q, true) == true }
            when (HomeTab.entries[tab]) {
                HomeTab.VIDEOS -> MediaListTab(
                    items = state.videos.filtered(),
                    progress = state.progress,
                    currentUri = playerState.current?.uri,
                    needsPermission = !state.hasPermission,
                    loading = state.loading,
                    emptyIcon = Icons.Rounded.Movie,
                    emptyText = R.string.empty_videos,
                    actions = actions,
                    header = {
                        val continueItems = state.recent
                            .filter { it.kind == MediaKind.VIDEO && !it.watched && it.progress > 0.01f }
                            .take(12)
                        if (continueItems.isNotEmpty() && q.isEmpty()) {
                            item(key = "continue") {
                                ContinueRow(continueItems.map { it.toMediaItem() }, state.progress, actions)
                            }
                        }
                    },
                )
                HomeTab.MUSIC -> MediaListTab(
                    items = state.audio.filtered(),
                    progress = emptyMap(),
                    currentUri = playerState.current?.uri,
                    needsPermission = !state.hasPermission,
                    loading = state.loading,
                    emptyIcon = Icons.Rounded.MusicNote,
                    emptyText = R.string.empty_music,
                    actions = actions,
                    header = {},
                )
                HomeTab.FOLDERS -> FoldersTab(state, q, vm, actions)
                HomeTab.HISTORY -> HistoryTab(state, q, vm, actions)
            }
        }
    }

    if (showUrl) {
        OpenUrlDialog(onDismiss = { showUrl = false }, onOpen = { showUrl = false; actions.openUrl(it) })
    }
}

private fun SortOrder.label(): Int = when (this) {
    SortOrder.NAME -> R.string.sort_name
    SortOrder.DATE -> R.string.sort_date
    SortOrder.SIZE -> R.string.sort_size
    SortOrder.DURATION -> R.string.sort_duration
}

@Composable
private fun itemActions(list: List<MediaItem>, index: Int, actions: LibraryActions): List<RowAction> {
    val item = list[index]
    return listOf(
        RowAction(stringResource(R.string.action_play)) { actions.play(list, index) },
        RowAction(stringResource(R.string.action_play_next)) { actions.playNext(listOf(item)) },
        RowAction(stringResource(R.string.action_enqueue)) { actions.enqueue(listOf(item)) },
    )
}

@Composable
private fun MediaListTab(
    items: List<MediaItem>,
    progress: Map<String, Float>,
    currentUri: String?,
    needsPermission: Boolean,
    loading: Boolean,
    emptyIcon: ImageVector,
    emptyText: Int,
    actions: LibraryActions,
    header: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    when {
        needsPermission -> PermissionState(actions)
        items.isEmpty() && !loading -> EmptyState(emptyIcon, stringResource(emptyText), actions)
        else -> LazyColumn(
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            header()
            if (items.isNotEmpty()) {
                item(key = "play_all") { PlayAllRow(items, actions) }
            }
            items(items.size, key = { items[it].uri }) { i ->
                val item = items[i]
                MediaRow(
                    item = item,
                    progress = progress[item.uri] ?: 0f,
                    onClick = { actions.play(items, i) },
                    actions = itemActions(items, i, actions),
                    highlighted = item.uri == currentUri,
                )
            }
        }
    }
}

@Composable
private fun PlayAllRow(items: List<MediaItem>, actions: LibraryActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(onClick = { actions.play(items, 0) }) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.play_all))
        }
        OutlinedButton(onClick = { actions.shufflePlay(items) }) {
            Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.shuffle))
        }
        Spacer(Modifier.weight(1f))
        Text(
            pluralStringResource(R.plurals.item_count, items.size, items.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContinueRow(items: List<MediaItem>, progress: Map<String, Float>, actions: LibraryActions) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.continue_watching),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items.size, key = { "c_" + items[it].uri }) { i ->
                val item = items[i]
                Column(
                    Modifier
                        .width(180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { actions.play(items, i) }
                ) {
                    MediaThumbnail(
                        item,
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        progress = progress[item.uri] ?: 0f,
                    )
                    Text(
                        item.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun FoldersTab(state: LibraryState, q: String, vm: LibraryViewModel, actions: LibraryActions) {
    fun List<MediaFolder>.filtered() = if (q.isEmpty()) this else filter { it.name.contains(q, ignoreCase = true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
        item(key = "my_header") {
            SectionHeader(stringResource(R.string.my_folders)) {
                IconButton(onClick = actions.addFolder) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = stringResource(R.string.menu_open_folder))
                }
            }
        }
        if (state.treeFolders.isEmpty() && !state.scanningTrees) {
            item(key = "my_empty") {
                Text(
                    stringResource(R.string.my_folders_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        if (state.scanningTrees) {
            item(key = "scanning") {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.scanning), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(state.treeFolders.filtered(), key = { "t_" + it.id }) { f ->
            FolderRow(f, onClick = { actions.openFolder(f.id) }, onRemove = { vm.removeTreeFolder(f.id) })
        }
        item(key = "device_header") { SectionHeader(stringResource(R.string.device_folders)) {} }
        if (!state.hasPermission) {
            item(key = "perm") { PermissionState(actions) }
        }
        items(state.folders.filtered(), key = { "f_" + it.id }) { f ->
            FolderRow(f, onClick = { actions.openFolder(f.id) }, onRemove = null)
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun FolderRow(folder: MediaFolder, onClick: () -> Unit, onRemove: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (folder.isTree) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val parts = buildList {
                if (folder.videoCount > 0) add(pluralStringResource(R.plurals.video_count, folder.videoCount, folder.videoCount))
                if (folder.audioCount > 0) add(pluralStringResource(R.plurals.audio_count, folder.audioCount, folder.audioCount))
            }
            Text(
                parts.joinToString("  •  ").ifEmpty { stringResource(R.string.folder_empty) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onRemove != null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.action_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.remove_folder)) }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(state: LibraryState, q: String, vm: LibraryViewModel, actions: LibraryActions) {
    val entries = state.recent.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }
    if (entries.isEmpty()) {
        EmptyState(Icons.Rounded.History, stringResource(R.string.empty_history), actions)
        return
    }
    val items = entries.map { it.toMediaItem() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(4.dp)) {
        item(key = "clear") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { vm.clearHistory() }) { Text(stringResource(R.string.clear_history)) }
            }
        }
        items(items.size, key = { "h_" + items[it].uri }) { i ->
            val item = items[i]
            MediaRow(
                item = item,
                progress = entries[i].progress,
                onClick = { actions.play(items, i) },
                actions = listOf(
                    RowAction(stringResource(R.string.action_play)) { actions.play(items, i) },
                    RowAction(stringResource(R.string.action_enqueue)) { actions.enqueue(listOf(item)) },
                    RowAction(stringResource(R.string.remove_from_history)) { vm.removeFromHistory(item.uri) },
                ),
            )
        }
    }
}

@Composable
private fun PermissionState(actions: LibraryActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.LockOpen, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.permission_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = actions.requestPermission) { Text(stringResource(R.string.permission_grant)) }
        OutlinedButton(onClick = actions.addFolder) { Text(stringResource(R.string.menu_open_folder)) }
    }
}

/** Masaüstü "hoş geldin" görünümünün karşılığı. */
@Composable
private fun EmptyState(icon: ImageVector, text: String, actions: LibraryActions) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions.openFiles) { Text(stringResource(R.string.welcome_open_file)) }
            OutlinedButton(onClick = actions.addFolder) { Text(stringResource(R.string.welcome_open_folder)) }
        }
    }
}

// ====================================================================== Klasör ayrıntısı
@Composable
fun FolderScreen(
    folder: MediaFolder?,
    progress: Map<String, Float>,
    playerState: PlayerState,
    actions: LibraryActions,
    onBack: () -> Unit,
) {
    var sort by rememberSaveable { mutableStateOf(SortOrder.NAME) }
    var sortMenu by remember { mutableStateOf(false) }
    val items = remember(folder, sort) { LibraryViewModel.sorted(folder?.items.orEmpty(), sort) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folder?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.Sort, stringResource(R.string.sort)) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(order.label())) },
                                    onClick = { sortMenu = false; sort = order },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Box(Modifier.navigationBarsPadding()) {
                MiniPlayer(playerState, onOpen = actions.openPlayer, onToggle = actions.togglePlay, onNext = actions.next)
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.folder_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(4.dp)) {
                item(key = "play_all") { PlayAllRow(items, actions) }
                items(items.size, key = { items[it].uri }) { i ->
                    val item = items[i]
                    MediaRow(
                        item = item,
                        progress = progress[item.uri] ?: 0f,
                        onClick = { actions.play(items, i) },
                        actions = itemActions(items, i, actions),
                        highlighted = item.uri == playerState.current?.uri,
                    )
                }
            }
        }
    }
}
