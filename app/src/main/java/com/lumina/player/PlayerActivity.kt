package com.lumina.player

import android.app.PictureInPictureParams
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Rational
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lumina.player.core.M3u
import com.lumina.player.core.MediaKind
import com.lumina.player.playback.PlayerEngine
import com.lumina.player.ui.player.PlayerCallbacks
import com.lumina.player.ui.player.PlayerScreen
import com.lumina.player.ui.theme.LuminaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tam ekran oynatıcı: video yüzeyi, jestler, resim içinde resim (PiP), klavye kısayolları. */
class PlayerActivity : ComponentActivity() {

    private val app get() = application as LuminaApp
    private val engine: PlayerEngine get() = app.engine
    private val inPip = mutableStateOf(false)
    private var videoLayout: VLCVideoLayout? = null
    private var videoDisabledForBackground = false

    private val subtitleLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) engine.addSubtitle(uri)
    }

    private val savePlaylistLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) {
            val text = M3u.export(engine.state.value.queue)
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null }.getOrDefault(false)
                }
                Toast.makeText(this@PlayerActivity, if (ok) R.string.msg_playlist_saved else R.string.msg_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()
        if (savedInstanceState == null) handleIntent(intent)

        val callbacks = PlayerCallbacks(
            onBack = { finish() },
            onPickSubtitle = { subtitleLauncher.launch(arrayOf("*/*")) },
            onScreenshot = ::takeScreenshot,
            onEnterPip = ::enterPip,
            onRotate = ::cycleOrientation,
            onSavePlaylist = { savePlaylistLauncher.launch("Lumina.m3u") },
            setBrightness = ::setBrightness,
            getBrightness = ::currentBrightness,
            onVideoLayout = { layout -> videoLayout = layout },
        )

        setContent {
            val settings by app.settings.state.collectAsStateWithLifecycle()
            val pip by inPip
            LuminaTheme(themeMode = settings.theme, accent = settings.accent, forceDark = true) {
                PlayerScreen(engine = engine, settings = settings, inPip = pip, callbacks = callbacks)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                engine.state
                    .map { Triple(it.isPlaying, it.hasVideo, it.current?.uri) }
                    .distinctUntilChanged()
                    .collect { (playing, hasVideo, _) ->
                        if (playing && hasVideo) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        updatePipParams()
                    }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (intent?.action == Intent.ACTION_VIEW && uri != null) {
            lifecycleScope.launch {
                val item = app.repository.itemFromUri(uri, intent.type)
                if (item != null) engine.playItems(listOf(item), 0)
                else Toast.makeText(this@PlayerActivity, R.string.error_open_generic, Toast.LENGTH_LONG).show()
            }
        } else if (engine.state.value.current == null) {
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        if (videoDisabledForBackground) {
            engine.setVideoEnabled(true)
            videoDisabledForBackground = false
        }
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        val st = engine.state.value
        if (st.hasVideo && st.current?.kind != MediaKind.AUDIO) {
            if (app.settings.value.backgroundPlayback && st.isPlaying) {
                engine.setVideoEnabled(false)
                videoDisabledForBackground = true
            } else {
                engine.pause()
            }
        }
        engine.saveProgressNow()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ------------------------------------------------------------------ Resim içinde resim
    private fun pipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < 26) return null
        val item = engine.state.value.current
        val ratio = if (item != null && item.width > 0 && item.height > 0) {
            Rational(item.width, item.height)
        } else Rational(16, 9)
        val clamped = when {
            ratio.toFloat() > 2.39f -> Rational(239, 100)
            ratio.toFloat() < 0.42f -> Rational(42, 100)
            else -> ratio
        }
        val builder = PictureInPictureParams.Builder().setAspectRatio(clamped)
        if (Build.VERSION.SDK_INT >= 31) {
            val st = engine.state.value
            builder.setAutoEnterEnabled(app.settings.value.autoPip && st.isPlaying && st.hasVideo)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature("android.software.picture_in_picture")) {
            pipParams()?.let { runCatching { setPictureInPictureParams(it) } }
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < 26 || !packageManager.hasSystemFeature("android.software.picture_in_picture")) {
            Toast.makeText(this, R.string.msg_pip_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        pipParams()?.let { runCatching { enterPictureInPictureMode(it) } }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val st = engine.state.value
        if (Build.VERSION.SDK_INT in 26..30 && app.settings.value.autoPip && st.isPlaying && st.hasVideo) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    // ------------------------------------------------------------------ Ekran / parlaklık
    private fun cycleOrientation() {
        val (next, label) = when (requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT to R.string.orientation_portrait
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED to R.string.orientation_auto
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE to R.string.orientation_landscape
        }
        requestedOrientation = next
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
    }

    private fun currentBrightness(): Float {
        val b = window.attributes.screenBrightness
        if (b >= 0f) return b
        return runCatching {
            android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f)
    }

    private fun setBrightness(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value.coerceIn(0.01f, 1f) }
    }

    // ------------------------------------------------------------------ Ekran görüntüsü
    private fun takeScreenshot() {
        val surface = videoLayout?.let { findSurface(it) }
        if (surface == null || Build.VERSION.SDK_INT < 24 || surface.width == 0 || surface.height == 0 || !surface.holder.surface.isValid) {
            Toast.makeText(this, R.string.msg_screenshot_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(surface, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) { saveBitmap(bitmap) }
                    Toast.makeText(
                        this@PlayerActivity,
                        if (ok) R.string.msg_screenshot_saved else R.string.msg_screenshot_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } else {
                Toast.makeText(this, R.string.msg_screenshot_failed, Toast.LENGTH_SHORT).show()
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun findSurface(view: View): SurfaceView? {
        if (view is SurfaceView && view.visibility == View.VISIBLE) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findSurface(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun saveBitmap(bitmap: Bitmap): Boolean {
        return runCatching { writeBitmap(bitmap) }.getOrDefault(false)
    }

    private fun writeBitmap(bitmap: Bitmap): Boolean {
        val name = "Lumina_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Lumina Player")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        } else {
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Lumina Player").apply { mkdirs() }
            File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return true
    }

    // ------------------------------------------------------------------ Klavye (masaüstü kısayolları)
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val step = app.settings.value.seekStepSec * 1000L
        val handled = when (keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { engine.togglePlayPause(); true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                engine.seekBy(-(if (event.isShiftPressed) 30_000 else if (event.isCtrlPressed) 60_000 else step)); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                engine.seekBy(if (event.isShiftPressed) 30_000 else if (event.isCtrlPressed) 60_000 else step); true
            }
            KeyEvent.KEYCODE_DPAD_UP -> if ((event.source and android.view.InputDevice.SOURCE_KEYBOARD) != 0) {
                engine.setVolume(engine.state.value.volume + 5); true
            } else false
            KeyEvent.KEYCODE_DPAD_DOWN -> if ((event.source and android.view.InputDevice.SOURCE_KEYBOARD) != 0) {
                engine.setVolume(engine.state.value.volume - 5); true
            } else false
            KeyEvent.KEYCODE_M -> { engine.setVolume(if (engine.state.value.volume > 0) 0 else 100); true }
            KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_MEDIA_NEXT -> { engine.next(); true }
            KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { engine.previous(); true }
            KeyEvent.KEYCODE_LEFT_BRACKET -> { engine.setRate(engine.state.value.rate - 0.25f); true }
            KeyEvent.KEYCODE_RIGHT_BRACKET -> { engine.setRate(engine.state.value.rate + 0.25f); true }
            KeyEvent.KEYCODE_S -> { takeScreenshot(); true }
            KeyEvent.KEYCODE_ESCAPE -> { finish(); true }
            else -> false
        }
        return handled || super.onKeyDown(keyCode, event)
    }
}
