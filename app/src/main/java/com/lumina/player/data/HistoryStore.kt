package com.lumina.player.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.lumina.player.core.MediaItem
import com.lumina.player.core.MediaKind
import com.lumina.player.core.ResumePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HistoryEntry(
    val uri: String,
    val title: String,
    val kind: MediaKind,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val lastPlayed: Long,
) {
    val progress: Float get() = if (watched) 1f else ResumePolicy.progress(positionMs, durationMs)

    fun toMediaItem() = MediaItem(uri = uri, title = title, kind = kind, durationMs = durationMs)
}

/**
 * İzleme geçmişi ve kaldığı yeri hatırlama (SQLite).
 * Tüm metotlar G/Ç yapar; ana iş parçacığı dışından çağrılmalıdır.
 */
class HistoryStore(context: Context) : SQLiteOpenHelper(context, "history.db", null, 1) {

    private val _version = MutableStateFlow(0)
    /** Her yazmada artar; arayüz listeleri yenilemek için dinler. */
    val version: StateFlow<Int> = _version.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE playback_progress (
                uri TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                kind TEXT NOT NULL,
                position_ms INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                is_watched INTEGER NOT NULL,
                last_played INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_last_played ON playback_progress(last_played DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun save(item: MediaItem, positionMs: Long, durationMs: Long) {
        if (item.uri.isBlank()) return
        val values = ContentValues().apply {
            put("uri", item.uri)
            put("title", item.title)
            put("kind", item.kind.name)
            put("position_ms", positionMs.coerceAtLeast(0))
            put("duration_ms", durationMs.coerceAtLeast(0))
            put("is_watched", if (ResumePolicy.isWatched(positionMs, durationMs)) 1 else 0)
            put("last_played", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("playback_progress", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        _version.value++
    }

    fun get(uri: String): HistoryEntry? =
        readableDatabase.query(
            "playback_progress", null, "uri = ?", arrayOf(uri), null, null, null
        ).use { c -> if (c.moveToFirst()) c.toEntry() else null }

    /** Kaldığı yerden devam konumu (masaüstü sürümle aynı kurallar). */
    fun resumePosition(uri: String): Long? {
        val e = get(uri) ?: return null
        if (e.watched) return null
        return ResumePolicy.resumePosition(e.positionMs, e.durationMs)
    }

    fun recent(limit: Int = 100): List<HistoryEntry> =
        readableDatabase.query(
            "playback_progress", null, null, null, null, null, "last_played DESC", limit.toString()
        ).use { c ->
            buildList { while (c.moveToNext()) add(c.toEntry()) }
        }

    /** Liste görünümündeki ilerleme çubukları için uri → ilerleme (0..1). */
    fun progressMap(): Map<String, Float> =
        readableDatabase.rawQuery(
            "SELECT uri, position_ms, duration_ms, is_watched FROM playback_progress", null
        ).use { c ->
            buildMap {
                while (c.moveToNext()) {
                    val p = if (c.getInt(3) == 1) 1f else ResumePolicy.progress(c.getLong(1), c.getLong(2))
                    put(c.getString(0), p)
                }
            }
        }

    fun remove(uri: String) {
        writableDatabase.delete("playback_progress", "uri = ?", arrayOf(uri))
        _version.value++
    }

    fun clear() {
        writableDatabase.delete("playback_progress", null, null)
        _version.value++
    }

    private fun android.database.Cursor.toEntry() = HistoryEntry(
        uri = getString(getColumnIndexOrThrow("uri")),
        title = getString(getColumnIndexOrThrow("title")),
        kind = runCatching { MediaKind.valueOf(getString(getColumnIndexOrThrow("kind"))) }.getOrDefault(MediaKind.VIDEO),
        positionMs = getLong(getColumnIndexOrThrow("position_ms")),
        durationMs = getLong(getColumnIndexOrThrow("duration_ms")),
        watched = getInt(getColumnIndexOrThrow("is_watched")) == 1,
        lastPlayed = getLong(getColumnIndexOrThrow("last_played")),
    )
}
