package com.lumina.player

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lumina.player.core.MediaItem
import com.lumina.player.core.PlayMode
import com.lumina.player.ui.library.FolderScreen
import com.lumina.player.ui.library.HomeScreen
import com.lumina.player.ui.library.LibraryActions
import com.lumina.player.ui.library.LibraryViewModel
import com.lumina.player.ui.settings.SettingsScreen
import com.lumina.player.ui.theme.LuminaTheme
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private val app get() = application as LuminaApp
    private val vm: LibraryViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refresh()
    }

    private val openFilesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) openUris(uris)
    }

    private val openTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.addTreeFolder(uri)
            Toast.makeText(this, R.string.msg_folder_added, Toast.LENGTH_SHORT).show()
        }
    }

    private val openPlaylistLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val items = app.repository.loadPlaylist(uri)
                if (items.isEmpty()) {
                    Toast.makeText(this@MainActivity, R.string.msg_playlist_empty, Toast.LENGTH_SHORT).show()
                } else {
                    play(items, 0)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && !app.repository.hasMediaPermission()) requestPermissions()

        val actions = LibraryActions(
            play = ::play,
            shufflePlay = { items ->
                app.engine.setPlayMode(PlayMode.SHUFFLE)
                play(items, Random.nextInt(items.size))
            },
            enqueue = { items ->
                app.engine.enqueue(items)
                Toast.makeText(this, R.string.msg_enqueued, Toast.LENGTH_SHORT).show()
            },
            playNext = { items ->
                app.engine.playNext(items)
                Toast.makeText(this, R.string.msg_play_next, Toast.LENGTH_SHORT).show()
            },
            openFiles = { openFilesLauncher.launch(arrayOf("video/*", "audio/*", "application/octet-stream", "*/*")) },
            addFolder = { openTreeLauncher.launch(null) },
            openUrl = { url -> openUris(listOf(Uri.parse(url))) },
            openPlaylist = { openPlaylistLauncher.launch(arrayOf("audio/x-mpegurl", "application/x-mpegurl", "application/vnd.apple.mpegurl", "*/*")) },
            openPlayer = ::openPlayer,
            togglePlay = { app.engine.togglePlayPause() },
            next = { app.engine.next() },
            openSettings = {},
            openFolder = {},
            requestPermission = ::requestPermissions,
        )

        setContent {
            val settings by app.settings.state.collectAsStateWithLifecycle()
            val playerState by app.engine.state.collectAsStateWithLifecycle()
            LuminaTheme(themeMode = settings.theme, accent = settings.accent) {
                val nav = rememberNavController()
                val navActions = LibraryActions(
                    play = actions.play, shufflePlay = actions.shufflePlay, enqueue = actions.enqueue,
                    playNext = actions.playNext, openFiles = actions.openFiles, addFolder = actions.addFolder,
                    openUrl = actions.openUrl, openPlaylist = actions.openPlaylist, openPlayer = actions.openPlayer,
                    togglePlay = actions.togglePlay, next = actions.next,
                    openSettings = { nav.navigate("settings") },
                    openFolder = { id -> nav.navigate("folder/" + Uri.encode(id)) },
                    requestPermission = actions.requestPermission,
                )
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(vm, playerState, navActions)
                    }
                    composable(
                        "folder/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    ) { entry ->
                        val id = entry.arguments?.getString("id")?.let(Uri::decode).orEmpty()
                        val libState by vm.state.collectAsStateWithLifecycle()
                        val folder = (libState.folders + libState.treeFolders).firstOrNull { it.id == id }
                        FolderScreen(folder, libState.progress, playerState, navActions, onBack = { nav.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(app.settings, onClearHistory = { vm.clearHistory() }, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // İzin ayarlardan verilmiş olabilir
        if (!vm.state.value.hasPermission && app.repository.hasMediaPermission()) vm.refresh()
    }

    private fun requestPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(perms)
    }

    private fun openUris(uris: List<Uri>) {
        lifecycleScope.launch {
            val items = uris.mapNotNull { uri ->
                if (uri.scheme == "content") {
                    runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                app.repository.itemFromUri(uri)
            }
            if (items.isNotEmpty()) play(items, 0)
        }
    }

    private fun play(items: List<MediaItem>, index: Int) {
        app.engine.playItems(items, index)
        openPlayer()
    }

    private fun openPlayer() {
        startActivity(Intent(this, PlayerActivity::class.java))
    }
}
