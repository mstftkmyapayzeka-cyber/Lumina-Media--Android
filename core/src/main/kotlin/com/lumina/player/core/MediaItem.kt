package com.lumina.player.core

/**
 * Çalma listesi ve kütüphane için medya öğesi.
 *
 * @property uri Oynatılacak kaynak: `content://`, `file://` ya da akış adresi.
 * @property path Varsa gerçek dosya yolu (libVLC'ye doğrudan verilir).
 * @property subtitles Aynı klasörde bulunan harici altyazıların URI'leri.
 */
data class MediaItem(
    val uri: String,
    val title: String,
    val kind: MediaKind = MediaKind.VIDEO,
    val path: String? = null,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val dateModified: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val artist: String? = null,
    val album: String? = null,
    val folder: String? = null,
    val subtitles: List<String> = emptyList(),
) {
    val isStream: Boolean get() = Formats.isStreamUrl(uri)

    /** "4K", "1080p", "720p" … rozeti. */
    val resolutionLabel: String?
        get() {
            val h = minOf(width, height).takeIf { it > 0 } ?: return null
            return when {
                h >= 2000 -> "4K"
                h >= 1400 -> "1440p"
                h >= 1000 -> "1080p"
                h >= 700 -> "720p"
                h >= 560 -> "576p"
                h >= 460 -> "480p"
                else -> "${h}p"
            }
        }
}
