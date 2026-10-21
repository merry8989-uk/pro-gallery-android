package com.progallery.app.data.text

/** What kind of text this is, which decides how it is shown. */
enum class TextKind(val label: String) {
    JSON("JSON"),
    XML("XML"),
    CSV("Delimited"),
    CODE("Code"),
    MARKUP("Markup"),
    PLAIN("Text"),
}

/**
 * Which documents the app can read as text.
 *
 * The format registry already knows these are documents — it lists `.json`, `.csv`, `.log` and
 * a hundred code extensions — but knowing what a file *is* is not the same as being able to
 * show it. Without a reader they went to "open with another app" and often found nothing,
 * which is what the Documents screen looked like.
 *
 * A whitelist rather than "anything not binary", because a `.docx` is a zip archive and
 * dumping it as text would be worse than saying nothing.
 */
object TextFormats {

    private val JSON = setOf("json", "jsonl", "ndjson", "geojson", "ipynb")
    private val XML = setOf("xml", "xsd", "xsl", "xslt", "svg", "plist", "kml", "gpx", "rss", "atom")
    private val CSV = setOf("csv", "tsv", "psv", "dif", "sylk")
    private val MARKUP = setOf("html", "htm", "xhtml", "mht", "mhtml", "css", "scss", "less", "md", "markdown", "mdx", "rst")
    private val CODE = setOf(
        "js", "mjs", "cjs", "ts", "tsx", "jsx", "kt", "kts", "java", "py", "rb", "php", "swift",
        "c", "h", "cpp", "hpp", "cc", "cs", "go", "rs", "scala", "lua", "pl", "r",
        "sh", "bash", "zsh", "bat", "cmd", "ps1", "sql", "gradle", "pro", "properties",
        "ini", "cfg", "conf", "env", "toml", "yaml", "yml", "dockerfile", "makefile",
    )
    private val PLAIN = setOf(
        "txt", "text", "log", "out", "err", "nfo", "readme", "license", "tex", "latex", "bib",
        "srt", "vtt", "ass", "ssa", "sub", "sbv",
    )

    /** True when the app has a reader for this extension. */
    fun canRead(extension: String): Boolean {
        val ext = extension.lowercase().trim()
        if (ext.isEmpty()) return false
        return ext in JSON || ext in XML || ext in CSV || ext in MARKUP || ext in CODE || ext in PLAIN
    }

    fun kind(extension: String): TextKind {
        val ext = extension.lowercase().trim()
        return when {
            ext in JSON -> TextKind.JSON
            ext in XML -> TextKind.XML
            ext in CSV -> TextKind.CSV
            ext in CODE -> TextKind.CODE
            ext in MARKUP -> TextKind.MARKUP
            else -> TextKind.PLAIN
        }
    }

    /**
     * Whether the app can re-indent it.
     *
     * JSON only, and deliberately so: XML needs its own indenter, and offering "tidy" for a
     * format the app cannot tidy would be a switch that does nothing. XML tidying is a
     * separate piece of work, not a claim to make now.
     */
    fun canTidy(kind: TextKind): Boolean = kind == TextKind.JSON
}
