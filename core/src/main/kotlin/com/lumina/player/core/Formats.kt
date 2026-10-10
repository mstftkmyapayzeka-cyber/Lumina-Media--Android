package com.lumina.player.core

/** Desteklenen biçimler ve içerik (sihirli bayt) tabanlı medya tespiti. */
object Formats {

    val VIDEO_EXTENSIONS: Set<String> = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "mpg", "mpeg", "mpe", "m1v", "m2v",
        "ts", "m2ts", "mts", "m2t", "vob", "3gp", "3g2", "ogv", "ogm", "m4v", "divx", "xvid",
        "rmvb", "rm", "asf", "f4v", "mxf", "dv", "evo", "nsv", "tod", "trp", "wtv", "dvr-ms",
        "y4m", "h264", "h265", "hevc", "264", "265", "ivf", "amv", "bik", "mk3d",
    )

    val AUDIO_EXTENSIONS: Set<String> = setOf(
        "mp3", "flac", "wav", "aac", "m4a", "ogg", "oga", "opus", "wma", "aiff", "aif", "aifc",
        "ape", "alac", "mka", "ac3", "eac3", "dts", "dtshd", "thd", "mlp", "mp2", "mpa", "m4b",
        "wv", "tta", "mpc", "spx", "amr", "au", "snd", "caf", "mid", "midi", "mod", "xm",
        "it", "s3m", "w64", "dsf", "dff", "tak", "ra", "voc", "weba",
    )

    val SUBTITLE_EXTENSIONS: Set<String> = setOf(
        "srt", "ass", "ssa", "vtt", "sub", "idx", "smi", "sami", "txt", "usf", "mpl", "jss",
        "psb", "rt", "ttml", "dfxp", "stl", "sbv",
    )

    val PLAYLIST_EXTENSIONS: Set<String> = setOf("m3u", "m3u8", "pls", "xspf")

    /** Kesinlikle medya olmayan yaygın uzantılar (içerik analizi bunlarda yapılmaz). */
    val NON_MEDIA_EXTENSIONS: Set<String> = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff", "ico", "svg", "heic",
        "nfo", "txt", "log", "ini", "json", "xml", "html", "htm", "css", "js", "py", "md",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip", "rar", "7z", "tar", "gz",
        "exe", "dll", "msi", "bat", "cmd", "ps1", "lnk", "url", "db", "sqlite", "torrent",
        "part", "crdownload", "tmp", "sfv", "md5", "sha1", "cue", "iso", "bin", "img",
        "apk", "nomedia", "thumbnails",
    ) + SUBTITLE_EXTENSIONS + PLAYLIST_EXTENSIONS

    val STREAM_SCHEMES: List<String> = listOf(
        "http://", "https://", "rtsp://", "rtmp://", "rtp://", "udp://", "mms://", "mmsh://",
        "ftp://", "srt://", "smb://",
    )

    /** Uzantıyı noktasız ve küçük harfle döndürür ("Film.MKV" → "mkv"). */
    fun extOf(name: String): String {
        val clean = name.substringBefore('?').substringAfterLast('/')
        val dot = clean.lastIndexOf('.')
        return if (dot <= 0 || dot == clean.length - 1) "" else clean.substring(dot + 1).lowercase()
    }

    fun stemOf(name: String): String {
        val clean = name.substringAfterLast('/')
        val dot = clean.lastIndexOf('.')
        return if (dot <= 0) clean else clean.substring(0, dot)
    }

    fun isStreamUrl(text: String): Boolean {
        val t = text.trim().lowercase()
        return STREAM_SCHEMES.any { t.startsWith(it) }
    }

    fun isSubtitle(name: String): Boolean {
        // .txt altyazı da olabilir ama otomatik olarak altyazı sayılmaz (yanlış pozitif riski)
        val ext = extOf(name)
        return ext != "txt" && ext in SUBTITLE_EXTENSIONS
    }

    fun isPlaylist(name: String): Boolean = extOf(name) in PLAYLIST_EXTENSIONS

    /** Uzantıya göre sınıflandırır; bilinmiyorsa null. */
    fun kindByName(name: String): MediaKind? = when (extOf(name)) {
        in VIDEO_EXTENSIONS -> MediaKind.VIDEO
        in AUDIO_EXTENSIONS -> MediaKind.AUDIO
        else -> null
    }

    /** MIME türüne göre sınıflandırır ("video/x-matroska" → VIDEO). */
    fun kindByMime(mime: String?): MediaKind? = when {
        mime == null -> null
        mime.startsWith("video/") -> MediaKind.VIDEO
        mime.startsWith("audio/") -> MediaKind.AUDIO
        else -> null
    }

    /** Uzantısı bilinmeyen dosyalarda içerik analizi yapılmalı mı? */
    fun shouldSniff(name: String): Boolean {
        val ext = extOf(name)
        return ext !in NON_MEDIA_EXTENSIONS && ext !in VIDEO_EXTENSIONS && ext !in AUDIO_EXTENSIONS
    }

    /**
     * Dosyanın ilk baytlarına bakarak medya türünü tahmin eder (en az 12, ideali 4096 bayt).
     * Uzantısı yanlış veya olmayan dosyalar için kullanılır.
     */
    fun sniff(head: ByteArray): MediaKind? {
        if (head.size < 12) return null
        fun u(i: Int): Int = head[i].toInt() and 0xFF
        fun ascii(from: Int, len: Int): String =
            if (from + len > head.size) "" else String(head, from, len, Charsets.ISO_8859_1)
        fun startsWith(vararg bytes: Int): Boolean =
            bytes.size <= head.size && bytes.indices.all { u(it) == bytes[it] }

        // ISO BMFF (MP4/MOV/M4A/3GP): 4 bayt boyut + 'ftyp'
        val box = ascii(4, 4)
        if (box == "ftyp") {
            return if (ascii(8, 4) in setOf("M4A ", "M4B ", "M4P ", "F4A ", "F4B ")) MediaKind.AUDIO
            else MediaKind.VIDEO
        }
        if (box in setOf("moov", "mdat", "free", "wide", "skip")) return MediaKind.VIDEO
        // Matroska / WebM (EBML)
        if (startsWith(0x1A, 0x45, 0xDF, 0xA3)) return MediaKind.VIDEO
        val magic4 = ascii(0, 4)
        // RIFF: AVI / WAV
        if (magic4 == "RIFF") {
            return when (ascii(8, 4)) {
                "AVI ", "AVIX" -> MediaKind.VIDEO
                "WAVE", "RMP3" -> MediaKind.AUDIO
                else -> null
            }
        }
        // ASF / WMV / WMA
        if (startsWith(0x30, 0x26, 0xB2, 0x75, 0x8E, 0x66, 0xCF, 0x11, 0xA6, 0xD9, 0x00, 0xAA, 0x00, 0x62, 0xCE, 0x6C)) {
            return MediaKind.VIDEO
        }
        if (ascii(0, 3) == "FLV") return MediaKind.VIDEO
        if (magic4 == ".RMF") return MediaKind.VIDEO
        // MPEG-PS / VOB ve MPEG elementer video
        if (startsWith(0x00, 0x00, 0x01, 0xBA) || startsWith(0x00, 0x00, 0x01, 0xB3)) return MediaKind.VIDEO
        // MPEG-TS (188 baytlık paketler) / M2TS (192 bayt)
        if (head.size >= 188 * 3 && u(0) == 0x47 && u(188) == 0x47 && u(376) == 0x47) return MediaKind.VIDEO
        if (head.size >= 192 * 3 && u(4) == 0x47 && u(196) == 0x47 && u(388) == 0x47) return MediaKind.VIDEO
        // Ogg: Theora ise video, değilse ses
        if (magic4 == "OggS") {
            val window = String(head, 0, minOf(512, head.size), Charsets.ISO_8859_1)
            return if ("theora" in window) MediaKind.VIDEO else MediaKind.AUDIO
        }
        if (magic4 in setOf("fLaC", "MAC ", "wvpk", "MPCK", "MThd", "caff", ".snd")) return MediaKind.AUDIO
        if (ascii(0, 3) == "MP+") return MediaKind.AUDIO
        if (magic4 == "FORM" && ascii(8, 4) in setOf("AIFF", "AIFC")) return MediaKind.AUDIO
        if (ascii(0, 6) == "#!AMR\n") return MediaKind.AUDIO
        // MP3: ID3 etiketi veya MPEG ses çerçeve senkronu
        if (ascii(0, 3) == "ID3") return MediaKind.AUDIO
        if (u(0) == 0xFF && (u(1) and 0xE0) == 0xE0 && (u(1) and 0x06) != 0) return MediaKind.AUDIO
        // AAC ADTS
        if (u(0) == 0xFF && (u(1) and 0xF6) == 0xF0) return MediaKind.AUDIO
        // AC-3 / E-AC-3
        if (startsWith(0x0B, 0x77)) return MediaKind.AUDIO
        // DTS
        if (startsWith(0x7F, 0xFE, 0x80, 0x01)) return MediaKind.AUDIO
        // Ham H.264 / HEVC Annex-B
        if (startsWith(0x00, 0x00, 0x00, 0x01)) {
            val nal = u(4)
            if ((nal and 0x1F) in setOf(7, 9)) return MediaKind.VIDEO
            if (((nal shr 1) and 0x3F) in setOf(32, 33, 35)) return MediaKind.VIDEO
        }
        return null
    }

    /** Akış adresi veya dosya adından okunabilir başlık üretir. */
    fun displayName(pathOrUrl: String): String {
        if (isStreamUrl(pathOrUrl)) {
            val tail = pathOrUrl.trimEnd('/').substringAfterLast('/').substringBefore('?')
            return tail.ifEmpty { pathOrUrl }
        }
        val name = pathOrUrl.substringAfterLast('/')
        return stemOf(name).ifEmpty { name }
    }
}
