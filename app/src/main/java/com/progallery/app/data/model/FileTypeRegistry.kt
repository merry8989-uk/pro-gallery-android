package com.progallery.app.data.model

/**
 * Classifies a file into a browsable kind.
 *
 * Resolution order: MIME type first (it is authoritative when the provider
 * gives one), then the file extension. Anything unrecognised is [MediaType.OTHER]
 * rather than being dropped, so no file is invisible to the scanner.
 *
 * The extension tables cover the formats the app advertises — photos, videos,
 * audio, documents and archives — including the professional and vendor formats
 * (RAW/PSD/AI/EPS/INDD/DWG, HEIF, TIFF, WebP, …).
 *
 * This object is pure Kotlin on purpose so it can be unit tested on the JVM.
 */
object FileTypeRegistry {

    private val byExtension: Map<String, MediaType> = buildMap {
        fun put(kind: MediaType, vararg extensions: String) {
            extensions.forEach { put(it, kind) }
        }

        // ---- Images -------------------------------------------------------
        put(
            MediaType.IMAGE,
            // web + everyday
            "jpg", "jpeg", "jpe", "jfif", "pjpeg", "png", "apng", "gif", "webp",
            "bmp", "dib", "ico", "cur", "wbmp",
            // modern + high efficiency
            "heic", "heif", "avif", "jxl", "jp2", "j2k", "jpf", "jpx", "jpm",
            // print / scan
            "tif", "tiff", "tga", "pcx", "ppm", "pgm", "pbm", "pnm", "pam",
            // professional / layered
            "psd", "psb", "xcf", "sketch", "ai", "eps", "epsf", "indd", "indt",
            "cdr", "cdt", "svg", "svgz",
            // camera raw
            "raw", "dng", "cr2", "cr3", "crw", "nef", "nrw", "arw", "srf", "sr2",
            "orf", "raf", "rw2", "rwl", "pef", "ptx", "srw", "x3f", "3fr", "fff",
            "mrw", "erf", "kdc", "dcr", "mos", "iiq",
            // hdr / scientific
            "exr", "hdr", "rgbe", "dds", "ktx", "astc",
        )

        // ---- Video --------------------------------------------------------
        put(
            MediaType.VIDEO,
            "mp4", "m4v", "mpeg4", "mov", "qt", "mkv", "webm", "avi", "divx",
            "wmv", "asf", "flv", "f4v", "swf", "mpg", "mpeg", "mpe", "m2v",
            "mpv", "ts", "m2ts", "mts", "tp", "trp",
            "3gp", "3g2", "3gpp", "ogv", "ogg", "rm", "rmvb", "vob", "ifo",
            "amv", "svi", "mxf", "dv", "roq", "y4m", "nut", "bik",
        )

        // ---- Audio --------------------------------------------------------
        put(
            MediaType.AUDIO,
            "mp3", "mp2", "m4a", "m4b", "m4p", "m4r", "aac", "wav", "wave",
            "flac", "ogg", "oga", "opus", "spx", "wma", "amr", "awb",
            "mid", "midi", "rmi", "aif", "aiff", "aifc", "ac3", "eac3", "dts",
            "ape", "au", "snd", "ra", "ram", "mka", "3ga", "caf", "dsf", "dff",
            "wv", "tta", "gsm", "voc", "8svx", "s3m", "xm", "it", "mod",
        )

        // ---- Documents ----------------------------------------------------
        put(
            MediaType.DOCUMENT,
            // pdf + office
            "pdf", "doc", "docx", "docm", "dot", "dotx", "dotm",
            "xls", "xlsx", "xlsm", "xlsb", "xlt", "xltx", "xltm",
            "ppt", "pptx", "pptm", "pps", "ppsx", "pot", "potx", "potm",
            "odt", "ott", "ods", "ots", "odp", "otp", "odg", "odf", "odc",
            "pages", "numbers", "key", "wpd", "wps", "abw", "sxw",
            // text + markup + data
            "txt", "text", "rtf", "md", "markdown", "mdx", "rst", "tex", "latex",
            "bib", "log", "ini", "cfg", "conf", "properties", "env", "toml",
            "yaml", "yml", "json", "jsonl", "ndjson", "xml", "xsd", "xsl",
            "html", "htm", "xhtml", "mht", "mhtml", "css", "scss", "less",
            "js", "mjs", "cjs", "ts", "tsx", "jsx", "kt", "kts", "java", "py",
            "c", "h", "cpp", "hpp", "cs", "go", "rs", "rb", "php", "swift",
            "sh", "bat", "ps1", "sql", "gradle", "pro",
            "csv", "tsv", "psv", "dif", "sylk",
            // ebooks + subtitles + notes
            "epub", "mobi", "azw", "azw3", "fb2", "djvu", "cbr", "cbz",
            "srt", "vtt", "ass", "ssa", "sub", "sbv",
            "one", "onenote", "xps", "oxps",
            // CAD / technical drawing (DWG and friends)
            "dwg", "dws", "dwt", "dxf", "dwf", "dgn", "stl", "step", "stp",
            "iges", "igs", "3ds", "obj", "fbx", "blend", "skp", "ifc",
        )

        // ---- Archives / packages -----------------------------------------
        put(
            MediaType.ARCHIVE,
            "zip", "zipx", "rar", "r00", "7z", "tar", "gz", "tgz", "gzip",
            "bz2", "tbz", "tbz2", "xz", "txz", "lz", "lzma", "lzo", "zst", "tzst",
            "z", "cab", "arj", "lzh", "lha", "ace", "arc", "cpio", "wim",
            "iso", "img", "dmg", "vhd", "vmdk", "vdi",
            "jar", "war", "ear", "apk", "aab", "apks", "xapk", "ipa",
            "deb", "rpm", "pkg", "msi", "appx", "snap", "flatpak",
        )
    }

    private val byMimePrefix: List<Pair<String, MediaType>> = listOf(
        "image/" to MediaType.IMAGE,
        "video/" to MediaType.VIDEO,
        "audio/" to MediaType.AUDIO,
        "text/" to MediaType.DOCUMENT,
        "font/" to MediaType.OTHER,
        "model/" to MediaType.OTHER,
    )

    private val byMimeExact: Map<String, MediaType> = mapOf(
        "application/pdf" to MediaType.DOCUMENT,
        "application/rtf" to MediaType.DOCUMENT,
        "application/json" to MediaType.DOCUMENT,
        "application/ld+json" to MediaType.DOCUMENT,
        "application/xml" to MediaType.DOCUMENT,
        "application/xhtml+xml" to MediaType.DOCUMENT,
        "application/msword" to MediaType.DOCUMENT,
        "application/vnd.ms-excel" to MediaType.DOCUMENT,
        "application/vnd.ms-powerpoint" to MediaType.DOCUMENT,
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to MediaType.DOCUMENT,
        "application/vnd.openxmlformats-officedocument.wordprocessingml.template" to MediaType.DOCUMENT,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to MediaType.DOCUMENT,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.template" to MediaType.DOCUMENT,
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" to MediaType.DOCUMENT,
        "application/vnd.openxmlformats-officedocument.presentationml.slideshow" to MediaType.DOCUMENT,
        "application/vnd.openxmlformats-officedocument.presentationml.template" to MediaType.DOCUMENT,
        "application/vnd.oasis.opendocument.text" to MediaType.DOCUMENT,
        "application/vnd.oasis.opendocument.spreadsheet" to MediaType.DOCUMENT,
        "application/vnd.oasis.opendocument.presentation" to MediaType.DOCUMENT,
        "application/epub+zip" to MediaType.DOCUMENT,
        "application/x-mobipocket-ebook" to MediaType.DOCUMENT,
        "application/zip" to MediaType.ARCHIVE,
        "application/x-zip-compressed" to MediaType.ARCHIVE,
        "application/x-rar-compressed" to MediaType.ARCHIVE,
        "application/vnd.rar" to MediaType.ARCHIVE,
        "application/x-7z-compressed" to MediaType.ARCHIVE,
        "application/gzip" to MediaType.ARCHIVE,
        "application/x-gzip" to MediaType.ARCHIVE,
        "application/x-tar" to MediaType.ARCHIVE,
        "application/x-bzip2" to MediaType.ARCHIVE,
        "application/x-xz" to MediaType.ARCHIVE,
        "application/x-iso9660-image" to MediaType.ARCHIVE,
        "application/vnd.android.package-archive" to MediaType.ARCHIVE,
    )

    /** Lowercased extension without the dot, or "" when there is none. */
    fun extensionOf(fileName: String?): String {
        val name = fileName?.trim().orEmpty()
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase()
    }

    /**
     * The kind for a file. Never throws and never returns null: unknown files
     * are [MediaType.OTHER] so they still appear in the scanner.
     */
    fun kindFor(mimeType: String?, fileName: String? = null): MediaType {
        val mime = mimeType?.trim()?.lowercase()?.substringBefore(';')?.trim().orEmpty()

        if (mime.isNotEmpty() && mime != "application/octet-stream") {
            byMimeExact[mime]?.let { return it }
            byMimePrefix.firstOrNull { mime.startsWith(it.first) }?.let { return it.second }
        }

        val extension = extensionOf(fileName)
        if (extension.isNotEmpty()) {
            byExtension[extension]?.let { return it }
        }

        // A generic MIME plus a known extension is handled above; a generic MIME
        // with an unknown extension stays OTHER.
        return MediaType.OTHER
    }

    /** True when the app can render a preview for this kind. */
    fun isPreviewable(kind: MediaType): Boolean =
        kind == MediaType.IMAGE || kind == MediaType.VIDEO

    /** Every extension the registry knows, for documentation and tests. */
    fun knownExtensions(): Set<String> = byExtension.keys

    /** Extensions grouped by kind. */
    fun extensionsFor(kind: MediaType): List<String> =
        byExtension.filterValues { it == kind }.keys.sorted()
}
