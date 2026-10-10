package com.lumina.player.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import com.lumina.player.core.MediaItem
import com.lumina.player.core.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/** Video küçük resimleri ve albüm kapakları; bellek önbellekli, sınırlı eşzamanlılıkla. */
object Thumbnails {

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 12).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val missing = HashSet<String>()
    private val permits = Semaphore(3)

    fun cached(item: MediaItem, sizePx: Int): Bitmap? = cache.get(key(item, sizePx))

    suspend fun load(context: Context, item: MediaItem, sizePx: Int): Bitmap? {
        if (item.isStream) return null
        val key = key(item, sizePx)
        cache.get(key)?.let { return it }
        synchronized(missing) { if (key in missing) return null }
        return permits.withPermit {
            withContext(Dispatchers.IO) {
                cache.get(key) ?: decode(context, item, sizePx).also { bmp ->
                    if (bmp != null) cache.put(key, bmp) else synchronized(missing) { missing += key }
                }
            }
        }
    }

    private fun key(item: MediaItem, sizePx: Int) = "${item.uri}@$sizePx"

    private fun decode(context: Context, item: MediaItem, sizePx: Int): Bitmap? {
        val uri = Uri.parse(item.uri)
        if (Build.VERSION.SDK_INT >= 29 && uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching { context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null) }
                .getOrNull()?.let { return it }
        }
        return runCatching { retrieve(context, item, uri, sizePx) }.getOrNull()
    }

    private fun retrieve(context: Context, item: MediaItem, uri: Uri, sizePx: Int): Bitmap? {
        val mmr = MediaMetadataRetriever()
        try {
            val path = item.path
            if (path != null && File(path).canRead()) mmr.setDataSource(path) else mmr.setDataSource(context, uri)
            mmr.embeddedPicture?.let { bytes ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            if (item.kind == MediaKind.AUDIO) return null
            val frame = if (Build.VERSION.SDK_INT >= 27) {
                mmr.getScaledFrameAtTime(3_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, sizePx, sizePx)
            } else {
                mmr.getFrameAtTime(3_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: return null
            if (frame.width <= sizePx && frame.height <= sizePx) return frame
            val scale = sizePx.toFloat() / maxOf(frame.width, frame.height)
            return Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true)
        } finally {
            runCatching { mmr.release() }
        }
    }
}
