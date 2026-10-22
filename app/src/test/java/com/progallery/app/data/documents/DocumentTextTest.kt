package com.progallery.app.data.documents

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The document readers.
 *
 * Every container here is **built by the test**, not checked in as a fixture: a fixture is a blob
 * whose insides nobody can see, so a test against one proves the reader matches that one file
 * rather than that it reads the format. Building the zip in the test also means the assertions can
 * be about the XML that produced the text.
 */
class DocumentTextTest {

    /** Builds a zip from name-to-content pairs. */
    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun docx(documentXml: String): ByteArray = zip(
        "[Content_Types].xml" to "<Types/>",
        "word/document.xml" to documentXml,
    )

    // ---- Word ----

    @Test
    fun aWordDocumentReadsAsParagraphs() {
        val bytes = docx(
            """<w:document><w:body>
                <w:p><w:r><w:t>First line</w:t></w:r></w:p>
                <w:p><w:r><w:t>Second </w:t></w:r><w:r><w:t>line</w:t></w:r></w:p>
               </w:body></w:document>""",
        )
        assertEquals("First line\nSecond line", DocumentText.docx(bytes))
    }

    @Test
    fun runsInsideOneParagraphStayOnOneLine() {
        // Word splits a sentence into runs whenever the formatting changes, so a reader that
        // broke on every run would put a newline in the middle of a sentence.
        val bytes = docx(
            "<w:document><w:body><w:p>" +
                "<w:r><w:t>bold</w:t></w:r><w:r><w:t> and </w:t></w:r><w:r><w:t>plain</w:t></w:r>" +
                "</w:p></w:body></w:document>",
        )
        assertEquals("bold and plain", DocumentText.docx(bytes))
    }

    @Test
    fun aTableRowBecomesOneTabbedLine() {
        val bytes = docx(
            """<w:document><w:body><w:tbl>
                <w:tr><w:tc><w:p><w:r><w:t>Name</w:t></w:r></w:p></w:tc>
                      <w:tc><w:p><w:r><w:t>Age</w:t></w:r></w:p></w:tc></w:tr>
                <w:tr><w:tc><w:p><w:r><w:t>Asha</w:t></w:r></w:p></w:tc>
                      <w:tc><w:p><w:r><w:t>31</w:t></w:r></w:p></w:tc></w:tr>
               </w:tbl></w:body></w:document>""",
        )
        assertEquals("Name\tAge\nAsha\t31", DocumentText.docx(bytes))
    }

    @Test
    fun entitiesAndBreaksAreHandled() {
        val bytes = docx(
            "<w:document><w:body><w:p><w:r><w:t>a &amp; b</w:t><w:br/><w:t>c</w:t></w:r></w:p></w:body></w:document>",
        )
        assertTrue(DocumentText.docx(bytes).contains("a & b"))
        assertTrue("the break should be a line break", DocumentText.docx(bytes).contains("a & b\nc"))
    }

    @Test
    fun aWordFileWithoutItsDocumentIsRefusedClearly() {
        val failure = runCatching { DocumentText.docx(zip("junk.txt" to "hello")) }.exceptionOrNull()
        assertTrue(failure is DocumentText.Unreadable)
        assertTrue(failure!!.message!!.contains("document.xml"))
    }

    // ---- Excel ----

    private fun xlsx(sheet: String, shared: String? = null): ByteArray = zip(
        *listOfNotNull(
            "xl/worksheets/sheet1.xml" to sheet,
            shared?.let { "xl/sharedStrings.xml" to it },
        ).toTypedArray(),
    )

    @Test
    fun sharedStringsAreResolvedRatherThanShownAsNumbers() {
        // The classic spreadsheet-reader bug: `t="s"` means the value is an index into the shared
        // strings, and showing the index instead of the word is what it looks like when missed.
        val bytes = xlsx(
            sheet = """<worksheet><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                <row r="2"><c r="A2" t="s"><v>1</v></c><c r="B2"><v>42</v></c></row>
               </sheetData></worksheet>""",
            shared = "<sst><si><t>Name</t></si><si><t>Asha</t></si></sst>",
        )
        assertEquals("Name\tAsha\nAsha\t42", DocumentText.xlsx(bytes))
    }

    @Test
    fun aBooleanCellIsShownAsTrueOrFalse() {
        val bytes = xlsx(sheet = """<worksheet><sheetData>
            <row><c r="A1" t="b"><v>1</v></c><c r="B1" t="b"><v>0</v></c></row>
           </sheetData></worksheet>""")
        assertEquals("TRUE\tFALSE", DocumentText.xlsx(bytes))
    }

    @Test
    fun inlineStringsAreRead() {
        val bytes = xlsx(sheet = """<worksheet><sheetData>
            <row><c r="A1" t="inlineStr"><is><t>inline</t></is></c></row>
           </sheetData></worksheet>""")
        assertEquals("inline", DocumentText.xlsx(bytes))
    }

    @Test
    fun aSharedStringSplitIntoRunsIsJoined() {
        // Word-processor-style formatting inside a cell splits one string into several `<r>`.
        val bytes = xlsx(
            sheet = """<worksheet><sheetData><row><c r="A1" t="s"><v>0</v></c></row></sheetData></worksheet>""",
            shared = "<sst><si><r><t>bold</t></r><r><t> plain</t></r></si></sst>",
        )
        assertEquals("bold plain", DocumentText.xlsx(bytes))
    }

    @Test
    fun aSpreadsheetWithoutASheetIsRefused() {
        val failure = runCatching { DocumentText.xlsx(zip("junk.txt" to "x")) }.exceptionOrNull()
        assertTrue(failure is DocumentText.Unreadable)
    }

    // ---- ePub ----

    @Test
    fun theSpineDecidesTheReadingOrderNotTheFileOrder() {
        // Chapters named out of order on purpose: a reader that sorted by filename would show
        // chapter two before chapter one, which is the bug this asserts against.
        val bytes = zip(
            "META-INF/container.xml" to
                """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
            "OEBPS/content.opf" to
                """<package><manifest>
                     <item id="b" href="b.xhtml" media-type="application/xhtml+xml"/>
                     <item id="a" href="a.xhtml" media-type="application/xhtml+xml"/>
                   </manifest><spine>
                     <itemref idref="a"/><itemref idref="b"/>
                   </spine></package>""",
            "OEBPS/a.xhtml" to "<html><body><p>Chapter one</p></body></html>",
            "OEBPS/b.xhtml" to "<html><body><p>Chapter two</p></body></html>",
        )
        assertEquals("Chapter one\n\nChapter two", DocumentText.epub(bytes))
    }

    @Test
    fun headingsAndParagraphsBothBecomeLines() {
        val bytes = zip(
            "META-INF/container.xml" to
                """<container><rootfiles><rootfile full-path="c.opf"/></rootfiles></container>""",
            "c.opf" to
                """<package><manifest><item id="c" href="c.xhtml"/></manifest>
                   <spine><itemref idref="c"/></spine></package>""",
            "c.xhtml" to "<html><body><h1>Title</h1><p>Body text</p><p>More</p></body></html>",
        )
        assertEquals("Title\nBody text\nMore", DocumentText.epub(bytes))
    }

    @Test
    fun anEpubWithoutAContainerIsRefused() {
        val failure = runCatching { DocumentText.epub(zip("junk.txt" to "x")) }.exceptionOrNull()
        assertTrue(failure is DocumentText.Unreadable)
        assertTrue(failure!!.message!!.contains("container.xml"))
    }

    // ---- OpenDocument ----

    @Test
    fun anOpenDocumentTextReadsAsParagraphs() {
        val bytes = zip(
            "mimetype" to "application/vnd.oasis.opendocument.text",
            "content.xml" to
                """<office:document-content><office:body><office:text>
                     <text:h>Heading</text:h><text:p>Body</text:p>
                   </office:text></office:body></office:document-content>""",
        )
        assertEquals("Heading\nBody", DocumentText.odt(bytes))
    }

    @Test
    fun anOpenDocumentSpreadsheetReadsAsRows() {
        val bytes = zip(
            "mimetype" to "application/vnd.oasis.opendocument.spreadsheet",
            "content.xml" to
                """<office:document-content><office:body><office:spreadsheet><table:table>
                     <table:table-row><table:table-cell><text:p>A</text:p></table:table-cell>
                                       <table:table-cell><text:p>1</text:p></table:table-cell></table:table-row>
                     <table:table-row><table:table-cell><text:p>B</text:p></table:table-cell>
                                       <table:table-cell><text:p>2</text:p></table:table-cell></table:table-row>
                   </table:table></office:spreadsheet></office:body></office:document-content>""",
        )
        assertEquals("A\t1\nB\t2", DocumentText.odt(bytes))
    }

    // ---- the container guard ----

    @Test
    fun anArchiveThatExpandsTooFarIsRefusedRatherThanAllocated() {
        // A few kilobytes of zeros expand past the per-part ceiling. The cap has to be enforced
        // while reading: trusting the declared size is exactly what a zip bomb relies on.
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            val zeros = ByteArray(1 shl 20)
            repeat(ZipArchive.MAX_ENTRY_BYTES / zeros.size + 8) { zip.write(zeros) }
            zip.closeEntry()
        }
        val failure = runCatching { DocumentText.docx(out.toByteArray()) }.exceptionOrNull()
        assertTrue("expected a refusal, got $failure", failure is DocumentText.Unreadable)
    }

    // ---- the facade ----

    @Test
    fun theFormatListMatchesWhatIsImplemented() {
        // The capability screen reads this table, so a format listed without a reader would be a
        // promise the app does not keep.
        DocumentFormats.allExtensions().forEach { extension ->
            val kind = DocumentFormats.kindOf(extension)
            assertTrue("$extension has no kind", kind != DocumentKind.NONE)
            assertTrue("$extension is not readable", DocumentFormats.canRead(extension))
        }
        assertTrue(DocumentFormats.canRead("docx"))
        assertTrue(DocumentFormats.canRead("XLSX"))
        assertTrue(!DocumentFormats.canRead("txt"))
        assertTrue(!DocumentFormats.canRead(""))
    }

    @Test
    fun anUnsupportedExtensionSaysSo() {
        val failure = runCatching { DocumentText.read("txt", "hello".toByteArray()) }.exceptionOrNull()
        assertTrue(failure is DocumentText.Unreadable)
    }
}
