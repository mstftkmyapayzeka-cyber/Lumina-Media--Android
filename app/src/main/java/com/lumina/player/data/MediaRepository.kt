package com.lumina.player.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.lumina.player.core.Formats
import com.lumina.player.core.M3u
import com.lumina.player.core.MediaItem
import com.lumina.player.core.MediaKind
import com.lumina.player.core.NaturalSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Klasör görünümünde bir grup (MediaStore klasörü ya da kullanıcının eklediği SAF klasörü). */
data class MediaFolder(
    val id: String,
    val name: String,
    val path: String?,
    val items: List<MediaItem>,
    val isTree: Boolean = false,
) {
    val videoCount: Int get() = items.count { it.kind == MediaKind.VIDEO }
    val audioCount: Int get() = items.count { it.kind == MediaKind.AUDIO }
}

/** Cihazdaki medyayı bulur: MediaStore sorguları, SAF klasör tarama, tekil URI çözümleme. */
class MediaRepository(private val context: Context, private val settings: SettingsStore) {

    private val resolver: ContentResolver get() = context.contentResolver

    // ------------------------------------------------------------------ MediaStore
    suspend fun loadVideos(): List<MediaItem> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        @Suppress("DEPRECATION")
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        )
        query(collection, projection, null) { c ->
            val id = c.getLong(0)
            val name = c.getString(1) ?: "video_$id"
            val path = c.getString(7)
            MediaItem(
                uri = ContentUris.withAppendedId(collection, id).toString(),
                title = Formats.stemOf(name),
                kind = MediaKind.VIDEO,
                path = path,
                durationMs = c.getLong(2),
                sizeBytes = c.getLong(3),
                dateModified = c.getLong(4) * 1000,
                width = c.getInt(5),
                height = c.getInt(6),
                folder = path?.let { File(it).parent } ?: c.getString(8),
            )
        }
    }

    suspend fun loadAudio(): List<MediaItem> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        @Suppress("DEPRECATION")
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA,
        )
        // Zil sesi, bildirim ve alarm sesleri müzik listesine girmez
        val selection = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0"
        query(collection, projection, selection) { c ->
            val id = c.getLong(0)
            val name = c.getString(1) ?: "audio_$id"
            val path = c.getString(8)
            val artist = c.getString(6)?.takeUnless { it == MediaStore.UNKNOWN_STRING }
            MediaItem(
                uri = ContentUris.withAppendedId(collection, id).toString(),
                title = c.getString(2)?.takeIf { it.isNotBlank() } ?: Formats.stemOf(name),
                kind = MediaKind.AUDIO,
                path = path,
                durationMs = c.getLong(3),
                sizeBytes = c.getLong(4),
                dateModified = c.getLong(5) * 1000,
                artist = artist,
                album = c.getString(7),
                folder = path?.let { File(it).parent },
            )
        }
    }

    private inline fun query(
        collection: Uri,
        projection: Array<String>,
        selection: String?,
        map: (Cursor) -> MediaItem,
    ): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        runCatching {
            resolver.query(collection, projection, selection, null, null)?.use { c ->
                while (c.moveToNext()) {
                    runCatching { map(c) }.getOrNull()?.let(out::add)
                }
            }
        }
        return out.sortedWith { a, b -> NaturalSort.compare(a.title, b.title) }
    }

    /** MediaStore öğelerini bulundukları klasöre göre gruplar. */
    fun groupByFolder(items: List<MediaItem>): List<MediaFolder> =
        items.filter { it.folder != null }
            .groupBy { it.folder!! }
            .map { (folder, list) ->
                MediaFolder(
                    id = folder,
                    name = folder.substringAfterLast('/').ifEmpty { folder },
                    path = folder,
                    items = list.sortedWith { a, b -> NaturalSort.compare(a.title, b.title) },
                )
            }
            .sortedWith { a, b -> NaturalSort.compare(a.name, b.name) }

    // ------------------------------------------------------------------ SAF klasör tarama
    /**
     * Kullanıcının seçtiği klasörü (alt klasörlerle) tarar. MediaStore'un tanımadığı
     * biçimler ve uzantısı yanlış dosyalar (içerik analiziyle) de bulunur.
     */
    suspend fun scanTree(treeUri: Uri): MediaFolder = withContext(Dispatchers.IO) {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val items = ArrayList<MediaItem>()
        val detect = settings.value.detectByContent
        scanDirectory(treeUri, rootId, items, detect, depth = 0)
        val name = runCatching {
            resolver.query(
                DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId),
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: rootId.substringAfterLast(':').substringAfterLast('/').ifEmpty { rootId }
        MediaFolder(
            id = treeUri.toString(),
            name = name,
            path = null,
            items = items.sortedWith { a, b -> NaturalSort.compare(a.title, b.title) },
            isTree = true,
        )
    }

    private fun scanDirectory(treeUri: Uri, dirId: String, out: MutableList<MediaItem>, detect: Boolean, depth: Int) {
        if (depth > 12) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        data class Child(val id: String, val name: String, val mime: String?, val size: Long, val modified: Long)
        val files = ArrayList<Child>()
        val dirs = ArrayList<String>()
        runCatching {
            resolver.query(children, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    if (name.startsWith(".")) continue
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) dirs += id
                    else files += Child(id, name, mime, c.getLong(3), c.getLong(4))
                }
            }
        }
        val subtitles = files.filter { Formats.isSubtitle(it.name) }
        for (f in files) {
            if (Formats.isSubtitle(f.name)) continue
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, f.id)
            val kind = Formats.kindByName(f.name)
                ?: Formats.kindByMime(f.mime)?.takeIf { Formats.shouldSniff(f.name) }
                ?: if (detect && Formats.shouldSniff(f.name) && f.size > 64 * 1024) sniff(uri) else null
            kind ?: continue
            val stem = Formats.stemOf(f.name).lowercase()
            val subs = if (kind == MediaKind.VIDEO) {
                subtitles.filter { Formats.stemOf(it.name).lowercase().startsWith(stem) }
                    .map { DocumentsContract.buildDocumentUriUsingTree(treeUri, it.id).toString() }
            } else emptyList()
            out += MediaItem(
                uri = uri.toString(),
                title = Formats.stemOf(f.name),
                kind = kind,
                sizeBytes = f.size,
                dateModified = f.modified,
                folder = dirId,
                subtitles = subs,
            )
        }
        for (d in dirs) scanDirectory(treeUri, d, out, detect, depth + 1)
    }

    private fun sniff(uri: Uri): MediaKind? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(4096)
            var read = 0
            while (read < buf.size) {
                val n = input.read(buf, read, buf.size - read)
                if (n <= 0) break
                read += n
            }
            Formats.sniff(buf.copyOf(read))
        }
    }.getOrNull()

    // ------------------------------------------------------------------ Tekil URI
    /** Dışarıdan gelen ya da seçilen bir URI'yi oynatılabilir öğeye çevirir. */
    suspend fun itemFromUri(uri: Uri, mimeHint: String? = null): MediaItem? = withContext(Dispatchers.IO) {
        val text = uri.toString()
        if (Formats.isStreamUrl(text)) {
            return@withContext MediaItem(uri = text, title = Formats.displayName(text), kind = MediaKind.VIDEO)
        }
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> {
                val path = uri.path ?: return@withContext null
                val file = File(path)
                val kind = Formats.kindByName(file.name) ?: Formats.kindByMime(mimeHint)
                    ?: sniff(uri) ?: MediaKind.VIDEO
                MediaItem(
                    uri = text, title = Formats.stemOf(file.name), kind = kind, path = path,
                    sizeBytes = file.length(), dateModified = file.lastModified(),
                )
            }
            ContentResolver.SCHEME_CONTENT -> {
                var name: String? = null
                var size = 0L
                runCatching {
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                        ?.use { c ->
                            if (c.moveToFirst()) {
                                name = c.getString(0)
                                size = if (c.isNull(1)) 0 else c.getLong(1)
                            }
                        }
                }
                val display = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "media"
                val mime = mimeHint ?: runCatching { resolver.getType(uri) }.getOrNull()
                val kind = Formats.kindByName(display) ?: Formats.kindByMime(mime) ?: sniff(uri) ?: MediaKind.VIDEO
                MediaItem(uri = text, title = Formats.stemOf(display), kind = kind, sizeBytes = size)
            }
            else -> MediaItem(uri = text, title = Formats.displayName(text), kind = MediaKind.VIDEO)
        }
    }

    /** M3U/M3U8 dosyasını okur. Göreli girdiler yalnızca dosya yolu biliniyorsa çözülür. */
    suspend fun loadPlaylist(uri: Uri): List<MediaItem> = withContext(Dispatchers.IO) {
        val text = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull() ?: return@withContext emptyList()
        val baseDir = if (uri.scheme == ContentResolver.SCHEME_FILE) uri.path?.let { File(it).parentFile } else null
        M3u.parse(text) { rel -> baseDir?.let { File(it, rel).absolutePath } }.map { e ->
            val loc = if (e.location.startsWith("/")) Uri.fromFile(File(e.location)).toString() else e.location
            val name = Uri.parse(loc).lastPathSegment ?: loc
            MediaItem(
                uri = loc,
                title = e.title ?: Formats.displayName(name),
                kind = Formats.kindByName(name) ?: MediaKind.VIDEO,
                path = if (e.location.startsWith("/")) e.location else null,
                durationMs = e.durationMs,
            )
        }
    }

    /**
     * Gerçek dosya yolu bilinen videolar için aynı klasördeki altyazıları bulur.
     * (Android 11+ kapsamlı depolamada başka uygulamaların .srt dosyaları görünmeyebilir.)
     */
    fun findSiblingSubtitles(item: MediaItem): List<String> {
        val path = item.path ?: return emptyList()
        return runCatching {
            val file = File(path)
            val stem = Formats.stemOf(file.name).lowercase()
            file.parentFile?.listFiles()
                ?.filter { it.isFile && Formats.isSubtitle(it.name) && Formats.stemOf(it.name).lowercase().startsWith(stem) }
                ?.sortedBy { it.name.length }
                ?.map { Uri.fromFile(it).toString() }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    fun hasMediaPermission(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= 33) {
            listOf(android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return perms.any {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}
