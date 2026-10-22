package com.progallery.app.data.documents

/** The document formats the app can open and read. */
enum class DocumentKind(val label: String) {
    DOCX("Word document"),
    XLSX("Spreadsheet"),
    EPUB("eBook"),
    ODT("OpenDocument"),
    PPTX("Presentation"),
    NONE(""),
}

/**
 * Which documents have a reader.
 *
 * Separate from [com.progallery.app.data.text.TextFormats] on purpose, and the text reader says
 * why in its own comment: these are zip containers, so reading their bytes as text shows
 * compressed noise. They need a reader that understands the container, which is this one.
 *
 * The list is deliberately short. Every format here is one the app can actually open — the
 * capability screen derives its answer from this same table, so a format added here and not
 * implemented there would be a promise the app does not keep.
 */
object DocumentFormats {

    private val DOCX = setOf("docx", "docm", "dotx")
    private val XLSX = setOf("xlsx", "xlsm", "xltx", "xlsb")
    private val EPUB = setOf("epub")
    private val ODT = setOf("odt", "ott", "ods", "ots", "odp", "otp", "odg", "fodt")
    private val PPTX = setOf("pptx", "pptm", "potx", "ppsx")

    fun canRead(extension: String): Boolean = kindOf(extension) != DocumentKind.NONE

    fun kindOf(extension: String): DocumentKind {
        val ext = extension.lowercase().trim()
        return when {
            ext in DOCX -> DocumentKind.DOCX
            ext in XLSX -> DocumentKind.XLSX
            ext in EPUB -> DocumentKind.EPUB
            ext in ODT -> DocumentKind.ODT
            ext in PPTX -> DocumentKind.PPTX
            else -> DocumentKind.NONE
        }
    }

    /** Every extension with a reader, for the capability screen. */
    fun allExtensions(): List<String> = (DOCX + XLSX + EPUB + ODT + PPTX).sorted()
}

/**
 * Reads the text out of a document.
 *
 * The four readers share one idea: a zip holds XML, and the XML holds text inside elements whose
 * names say what the text *is*. So each reader is a walk over the same tokeniser with a different
 * notion of what a paragraph is — `w:p` in Word, `text:p` in OpenDocument, a row in a
 * spreadsheet, a chapter in an eBook.
 *
 * Every reader is tested against a container the test builds itself, which is the only way to test
 * a zip reader honestly: a fixture checked into the repository is a fixture nobody can see the
 * inside of.
 */
object DocumentText {

    class Unreadable(message: String) : Exception(message)

    /** The text of a document, or a reason it could not be read. */
    fun read(extension: String, bytes: ByteArray): String = when (DocumentFormats.kindOf(extension)) {
        DocumentKind.DOCX -> docx(bytes)
        DocumentKind.XLSX -> xlsx(bytes)
        DocumentKind.EPUB -> epub(bytes)
        DocumentKind.ODT -> odt(bytes)
        DocumentKind.PPTX -> pptx(bytes)
        DocumentKind.NONE -> throw Unreadable("The app has no reader for .$extension")
    }

    // ---------------------------------------------------------------------------------------
    // Word
    // ---------------------------------------------------------------------------------------

    fun docx(bytes: ByteArray): String {
        val entries = openContainer(bytes)
        val document = ZipArchive.find(entries, "word/document.xml")
            ?: throw Unreadable("This does not look like a Word document — it has no document.xml")
        return paragraphs(
            xml = XmlLite.decodeText(document),
            paragraphTags = setOf("p"),
            breakTags = setOf("br", "cr"),
            cellTags = setOf("tc"),
            rowTags = setOf("tr"),
            tabTags = setOf("tab"),
            textTags = setOf("t"),
        )
    }

    // ---------------------------------------------------------------------------------------
    // Excel
    // ---------------------------------------------------------------------------------------

    fun xlsx(bytes: ByteArray): String {
        val entries = openContainer(bytes)
        val shared = sharedStrings(entries)

        // The first sheet, by number. Resolving the workbook's own order needs the relationship
        // parts, and "the first sheet" is what a person means by opening a spreadsheet.
        val sheet = entries.entries
            .filter { Regex("^xl/worksheets/sheet\\d+\\.xml$", RegexOption.IGNORE_CASE).matches(it.key) }
            .minByOrNull { it.key.length }
            ?.value
            ?: throw Unreadable("This does not look like a spreadsheet — it has no sheet")

        return sheetRows(XmlLite.decodeText(sheet), shared)
    }

    private fun sharedStrings(entries: Map<String, ByteArray>): List<String> {
        val raw = ZipArchive.find(entries, "xl/sharedStrings.xml") ?: return emptyList()
        val strings = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        XmlLite.events(XmlLite.decodeText(raw)).forEach { event ->
            when (event) {
                is XmlLite.Event.Start -> {
                    when (XmlLite.localName(event.name)) {
                        // A string is a run of text; `<r>` splits it into formatting runs, and
                        // `<t>` inside either is the text itself.
                        "si" -> { depth = 0; current.setLength(0) }
                        "t" -> depth++
                    }
                }
                is XmlLite.Event.Text -> if (depth > 0) current.append(event.value)
                is XmlLite.Event.End -> when (XmlLite.localName(event.name)) {
                    "t" -> if (depth > 0) depth--
                    "si" -> strings.add(current.toString())
                }
            }
        }
        return strings
    }

    private fun sheetRows(xml: String, shared: List<String>): String {
        val out = StringBuilder()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var cellType: String? = null
        var inValue = false
        var inInline = false

        fun flushRow() {
            val line = row.joinToString("\t").trimEnd('\t')
            if (line.isNotBlank()) out.append(line).append('\n')
            row.clear()
        }

        XmlLite.events(xml).forEach { event ->
            when (event) {
                is XmlLite.Event.Start -> when (XmlLite.localName(event.name)) {
                    "row" -> { row.clear() }
                    "c" -> {
                        cell.setLength(0)
                        cellType = event.attributes["t"]
                        inValue = false
                        inInline = false
                    }
                    "v" -> inValue = true
                    "is" -> inInline = true
                    "t" -> if (inInline) inInline = true
                }
                is XmlLite.Event.Text -> if (inValue || inInline) cell.append(event.value)
                is XmlLite.Event.End -> when (XmlLite.localName(event.name)) {
                    "v" -> inValue = false
                    "is" -> inInline = false
                    "c" -> {
                        val raw = cell.toString()
                        // `t="s"` means the value is an index into the shared strings; anything
                        // else is the value itself. Getting this wrong shows numbers where words
                        // should be, which is the classic spreadsheet-reader bug.
                        row.add(
                            when (cellType) {
                                "s" -> shared.getOrNull(raw.trim().toIntOrNull() ?: -1) ?: raw
                                "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                                else -> raw
                            },
                        )
                        cell.setLength(0)
                        cellType = null
                    }
                    "row" -> flushRow()
                }
            }
        }
        flushRow()
        return out.toString().trimEnd()
    }

    // ---------------------------------------------------------------------------------------
    // OpenDocument
    // ---------------------------------------------------------------------------------------

    fun odt(bytes: ByteArray): String {
        val entries = openContainer(bytes)
        val content = ZipArchive.find(entries, "content.xml")
            ?: throw Unreadable("This does not look like an OpenDocument file — it has no content.xml")
        // Spreadsheets in the OpenDocument family keep their cells in `table:table-cell`.
        val isSpreadsheet = ZipArchive.find(entries, "mimetype")
            ?.let { XmlLite.decodeText(it) }
            ?.contains("spreadsheet") == true
        return if (isSpreadsheet) {
            odsRows(XmlLite.decodeText(content))
        } else {
            paragraphs(
                xml = XmlLite.decodeText(content),
                paragraphTags = setOf("p", "h"),
                breakTags = setOf("line-break"),
                cellTags = emptySet(),
                rowTags = emptySet(),
                tabTags = setOf("tab"),
                textTags = emptySet(),
            )
        }
    }

    private fun odsRows(xml: String): String {
        val out = StringBuilder()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var inCell = false

        XmlLite.events(xml).forEach { event ->
            when (event) {
                is XmlLite.Event.Start -> when (XmlLite.localName(event.name)) {
                    "table-row" -> row.clear()
                    "table-cell", "covered-table-cell" -> { cell.setLength(0); inCell = true }
                    "p" -> if (inCell && cell.isNotEmpty()) cell.append(' ')
                }
                is XmlLite.Event.Text -> if (inCell) cell.append(event.value)
                is XmlLite.Event.End -> when (XmlLite.localName(event.name)) {
                    "table-cell", "covered-table-cell" -> {
                        row.add(cell.toString().trim())
                        cell.setLength(0)
                        inCell = false
                    }
                    "table-row" -> {
                        val line = row.joinToString("\t").trimEnd('\t')
                        if (line.isNotBlank()) out.append(line).append('\n')
                        row.clear()
                    }
                }
            }
        }
        return out.toString().trimEnd()
    }

    // ---------------------------------------------------------------------------------------
    // ePub
    // ---------------------------------------------------------------------------------------

    fun epub(bytes: ByteArray): String {
        val entries = openContainer(bytes)
        val container = ZipArchive.find(entries, "META-INF/container.xml")
            ?: throw Unreadable("This does not look like an ePub — it has no container.xml")

        val opfPath = XmlLite.events(XmlLite.decodeText(container))
            .filterIsInstance<XmlLite.Event.Start>()
            .firstOrNull { XmlLite.localName(it.name) == "rootfile" }
            ?.attributes?.get("full-path")
            ?: throw Unreadable("This ePub does not say which file describes it")

        val opf = entries[opfPath]
            ?: entries.entries.firstOrNull { it.key.equals(opfPath, ignoreCase = true) }?.value
            ?: throw Unreadable("This ePub is missing its own description file")

        val base = opfPath.substringBeforeLast('/', "")
        val manifest = LinkedHashMap<String, String>()
        val spine = mutableListOf<String>()
        var inSpine = false

        XmlLite.events(XmlLite.decodeText(opf)).forEach { event ->
            when (event) {
                is XmlLite.Event.Start -> when (XmlLite.localName(event.name)) {
                    "item" -> {
                        val id = event.attributes["id"]
                        val href = event.attributes["href"]
                        if (id != null && href != null) manifest[id] = href
                    }
                    "spine" -> inSpine = true
                    "itemref" -> event.attributes["idref"]?.let { if (inSpine) spine.add(it) }
                }
                is XmlLite.Event.End ->
                    if (XmlLite.localName(event.name) == "spine") inSpine = false
                else -> Unit
            }
        }

        // The spine is the reading order — not the file order, which is why it is followed.
        val chapters = spine.mapNotNull { manifest[it] }
            .ifEmpty { manifest.values.toList() }

        val out = StringBuilder()
        chapters.forEach { href ->
            val path = if (base.isEmpty()) href else "$base/$href"
            val content = entries[path]
                ?: entries.entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value
                ?: return@forEach
            val text = paragraphs(
                xml = XmlLite.decodeText(content),
                paragraphTags = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote"),
                breakTags = setOf("br"),
                cellTags = setOf("td", "th"),
                rowTags = setOf("tr"),
                tabTags = emptySet(),
                textTags = emptySet(),
            )
            if (text.isNotBlank()) {
                if (out.isNotEmpty()) out.append("\n\n")
                out.append(text)
            }
        }
        if (out.isEmpty()) throw Unreadable("This ePub has no readable chapters")
        return out.toString()
    }

    // ---------------------------------------------------------------------------------------
    // PowerPoint
    // ---------------------------------------------------------------------------------------

    fun pptx(bytes: ByteArray): String {
        val entries = openContainer(bytes)
        val slides = slidePathsInOrder(entries)
        if (slides.isEmpty()) {
            throw Unreadable("This does not look like a presentation — it has no slides")
        }

        val out = StringBuilder()
        slides.forEachIndexed { index, path ->
            val content = entries[path] ?: return@forEachIndexed
            val text = paragraphs(
                xml = XmlLite.decodeText(content),
                paragraphTags = setOf("p"),
                breakTags = setOf("br"),
                cellTags = emptySet(),
                rowTags = emptySet(),
                tabTags = setOf("tab"),
                // PowerPoint keeps its words in `<a:t>`, exactly as Word keeps them in `<w:t>`,
                // and puts the pretty-printing between elements — so the same rule applies.
                textTags = setOf("t"),
            )
            if (text.isNotBlank()) {
                if (out.isNotEmpty()) out.append("\n\n")
                out.append("Slide ").append(index + 1).append('\n').append(text)
            }
        }
        if (out.isEmpty()) throw Unreadable("This presentation has no readable text")
        return out.toString()
    }

    /**
     * The slides, in the order the presentation says — not the order the files sort in.
     *
     * A deck's slides are named `slide1.xml`, `slide2.xml` and so on, and they are *usually* in
     * that order, which is exactly why reading them by filename looks correct until a slide is
     * reordered or deleted and the numbers no longer match the running order. `presentation.xml`
     * lists the slides in reading order and the relationships file says which file each one is;
     * the filename is the fallback, not the rule.
     */
    private fun slidePathsInOrder(entries: Map<String, ByteArray>): List<String> {
        val presentation = ZipArchive.find(entries, "ppt/presentation.xml")
        val relationships = ZipArchive.find(entries, "ppt/_rels/presentation.xml.rels")

        if (presentation != null && relationships != null) {
            val targets = mutableMapOf<String, String>()
            XmlLite.events(XmlLite.decodeText(relationships)).forEach { event ->
                if (event is XmlLite.Event.Start &&
                    XmlLite.localName(event.name) == "Relationship"
                ) {
                    val id = event.attributes["Id"]
                    val target = event.attributes["Target"]
                    if (id != null && target != null) targets[id] = target
                }
            }

            val order = mutableListOf<String>()
            XmlLite.events(XmlLite.decodeText(presentation)).forEach { event ->
                if (event is XmlLite.Event.Start && XmlLite.localName(event.name) == "sldId") {
                    // The attribute is namespaced, so the key carries the prefix and the local
                    // name is what to match on.
                    val id = event.attributes.entries
                        .firstOrNull { XmlLite.localName(it.key) == "id" }?.value
                    if (id != null) targets[id]?.let { order.add(it) }
                }
            }

            val resolved = order.mapNotNull { target ->
                val trimmed = target.removePrefix("/")
                val path = if (trimmed.startsWith("ppt/")) trimmed else "ppt/$trimmed"
                entries.keys.firstOrNull { it.equals(path, ignoreCase = true) }
            }
            if (resolved.isNotEmpty()) return resolved
        }

        return entries.keys
            .filter { Regex("^ppt/slides/slide\\d+\\.xml$", RegexOption.IGNORE_CASE).matches(it) }
            .sortedBy { it.substringAfterLast("slide").substringBefore('.').toIntOrNull() ?: 0 }
    }

    // ---------------------------------------------------------------------------------------
    // Shared
    // ---------------------------------------------------------------------------------------

    private fun openContainer(bytes: ByteArray): Map<String, ByteArray> = try {
        ZipArchive.read(bytes)
    } catch (tooLarge: ZipArchive.TooLarge) {
        throw Unreadable(tooLarge.message ?: "This file is too large to open")
    }

    /**
     * Turns XML into readable lines.
     *
     * Inside a table cell the paragraph breaks become spaces, because a cell is one line of a
     * table and breaking it would scatter the row down the page. The row break is what ends the
     * line instead — which is the difference between a table that reads as a table and one that
     * reads as a list of words.
     */
    private fun paragraphs(
        xml: String,
        paragraphTags: Set<String>,
        breakTags: Set<String>,
        cellTags: Set<String>,
        rowTags: Set<String>,
        tabTags: Set<String>,
        textTags: Set<String>,
    ): String {
        val out = StringBuilder()
        val line = StringBuilder()
        var cellDepth = 0

        // Where the current cell's own text begins. A separator space belongs between two pieces
        // of text *inside one cell*, not at the start of an empty one — comparing against the
        // whole line instead puts a space after every tab, so "Name\t Age" rather than
        // "Name\tAge".
        var cellStart = 0

        // How deep inside an element that carries text we are. Word keeps its words in `<w:t>`
        // and puts the pretty-printing between elements — newlines and indentation that are not
        // part of the document at all. Without this, a formatted document reads as its own
        // indentation. Inside such an element whitespace is significant (a run that is a single
        // space is a real space), so it is only the *outside* whitespace that is dropped.
        var textDepth = 0

        fun flushLine() {
            val text = line.toString().trim()
            if (text.isNotEmpty()) {
                if (out.isNotEmpty()) out.append('\n')
                out.append(text)
            }
            line.setLength(0)
        }

        XmlLite.events(xml).forEach { event ->
            when (event) {
                is XmlLite.Event.Start -> {
                    val name = XmlLite.localName(event.name)
                    when {
                        name in cellTags -> {
                            cellDepth++
                            if (line.isNotEmpty()) line.append('\t')
                            // After the tab, not before: the marker is where the cell's own text
                            // starts, and a marker left on the far side of the tab makes the tab
                            // itself look like cell content.
                            cellStart = line.length
                        }
                        name in rowTags -> if (cellDepth == 0) flushLine()
                        name in tabTags -> line.append('\t')
                        name in textTags -> textDepth++
                        name in breakTags -> line.append(if (cellDepth > 0) ' ' else '\n')
                        name in paragraphTags ->
                            if (cellDepth == 0) {
                                flushLine()
                            } else if (line.length > cellStart) {
                                line.append(' ')
                            }
                    }
                }
                is XmlLite.Event.End -> {
                    val name = XmlLite.localName(event.name)
                    when {
                        name in textTags -> if (textDepth > 0) textDepth--
                        name in cellTags -> if (cellDepth > 0) { cellDepth--; cellStart = line.length }
                        name in rowTags -> if (cellDepth == 0) flushLine()
                        name in paragraphTags -> if (cellDepth == 0) flushLine()
                    }
                }
                is XmlLite.Event.Text -> {
                    // Inside a text element, whitespace is part of the text. Outside one, only
                    // real content is taken — a heading written without a `p` is still a heading,
                    // and the indentation around it is not.
                    if (textDepth > 0 || event.value.isNotBlank()) line.append(event.value)
                }
            }
        }
        flushLine()
        return out.toString()
    }
}
