package com.lumina.player.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumina.player.LuminaApp
import com.lumina.player.core.MediaItem
import com.lumina.player.core.NaturalSort
import com.lumina.player.data.HistoryEntry
import com.lumina.player.data.MediaFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOrder { NAME, DATE, SIZE, DURATION }

data class LibraryState(
    val loading: Boolean = true,
    val hasPermission: Boolean = false,
    val videos: List<MediaItem> = emptyList(),
    val audio: List<MediaItem> = emptyList(),
    val folders: List<MediaFolder> = emptyList(),
    val treeFolders: List<MediaFolder> = emptyList(),
    val scanningTrees: Boolean = false,
    val recent: List<HistoryEntry> = emptyList(),
    val progress: Map<String, Float> = emptyMap(),
    val sort: SortOrder = SortOrder.NAME,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val lumina = app as LuminaApp
    private val repo = lumina.repository
    private val history = lumina.history
    private val settings = lumina.settings

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            history.version.collect { reloadHistory() }
        }
        viewModelScope.launch {
            settings.state.map { it.folders }.distinctUntilChanged().drop(1).collect { scanTrees() }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val granted = repo.hasMediaPermission()
            _state.update { it.copy(loading = true, hasPermission = granted) }
            if (granted) {
                val videos = async { repo.loadVideos() }
                val audio = async { repo.loadAudio() }
                val v = videos.await()
                val a = audio.await()
                _state.update {
                    it.copy(
                        videos = sorted(v, it.sort),
                        audio = sorted(a, it.sort),
                        folders = repo.groupByFolder(v + a),
                    )
                }
            }
            _state.update { it.copy(loading = false) }
            scanTrees()
        }
    }

    private suspend fun scanTrees() {
        val uris = settings.value.folders
        _state.update { it.copy(scanningTrees = uris.isNotEmpty()) }
        val trees = uris.map { u ->
            viewModelScope.async { runCatching { repo.scanTree(Uri.parse(u)) }.getOrNull() }
        }.awaitAll().filterNotNull()
        _state.update { it.copy(treeFolders = trees.sortedWith { a, b -> NaturalSort.compare(a.name, b.name) }, scanningTrees = false) }
    }

    private suspend fun reloadHistory() {
        val (recent, progress) = withContext(Dispatchers.IO) {
            runCatching { history.recent(200) to history.progressMap() }.getOrDefault(emptyList<HistoryEntry>() to emptyMap<String, Float>())
        }
        _state.update { it.copy(recent = recent, progress = progress) }
    }

    fun setSort(order: SortOrder) {
        _state.update { it.copy(sort = order, videos = sorted(it.videos, order), audio = sorted(it.audio, order)) }
    }

    fun addTreeFolder(uri: Uri) = settings.addFolder(uri.toString())

    fun removeTreeFolder(id: String) {
        settings.removeFolder(id)
        runCatching {
            lumina.contentResolver.releasePersistableUriPermission(
                Uri.parse(id), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun removeFromHistory(uri: String) {
        viewModelScope.launch(Dispatchers.IO) { history.remove(uri) }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) { history.clear() }
    }

    fun folder(id: String): MediaFolder? = _state.value.let { s ->
        s.folders.firstOrNull { it.id == id } ?: s.treeFolders.firstOrNull { it.id == id }
    }

    companion object {
        fun sorted(items: List<MediaItem>, order: SortOrder): List<MediaItem> = when (order) {
            SortOrder.NAME -> items.sortedWith { a, b -> NaturalSort.compare(a.title, b.title) }
            SortOrder.DATE -> items.sortedByDescending { it.dateModified }
            SortOrder.SIZE -> items.sortedByDescending { it.sizeBytes }
            SortOrder.DURATION -> items.sortedByDescending { it.durationMs }
        }
    }
}
