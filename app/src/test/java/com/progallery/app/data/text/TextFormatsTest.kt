package com.progallery.app.data.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Documents screen was full of `.json` and `.csv` files with only an icon, because
 * knowing a file is a document is not the same as being able to show it.
 */
class TextFormatsTest {

    @Test
    fun theFormatsPeopleActuallyHaveAreReadable() {
        listOf("txt", "log", "json", "xml", "csv", "md", "html", "css", "js", "kt", "py", "sh", "yaml", "ini", "srt")
            .forEach { ext -> assertTrue("$ext should be readable", TextFormats.canRead(ext)) }
    }

    @Test
    fun theCaseDoesNotMatter() {
        assertTrue(TextFormats.canRead("JSON"))
        assertTrue(TextFormats.canRead("Csv"))
    }

    @Test
    fun anOfficeFileIsNotOfferedAsText() {
        // A .docx is a zip archive. Showing its bytes would be worse than saying nothing.
        listOf("docx", "xlsx", "pptx", "odt", "pdf", "epub", "zip", "apk", "jpg", "mp4", "bin")
            .forEach { ext -> assertFalse("$ext must not be read as text", TextFormats.canRead(ext)) }
    }

    @Test
    fun anEmptyExtensionIsNotReadable() {
        assertFalse(TextFormats.canRead(""))
        assertFalse(TextFormats.canRead("   "))
    }

    @Test
    fun theKindIsRecognisedForDisplay() {
        assertEquals(TextKind.JSON, TextFormats.kind("json"))
        assertEquals(TextKind.XML, TextFormats.kind("xml"))
        assertEquals(TextKind.CSV, TextFormats.kind("csv"))
        assertEquals(TextKind.CODE, TextFormats.kind("kt"))
        assertEquals(TextKind.MARKUP, TextFormats.kind("html"))
        assertEquals(TextKind.PLAIN, TextFormats.kind("log"))
    }

    @Test
    fun onlyJsonIsOfferedTidying() {
        // XML would need its own indenter. Offering it for a format the app cannot tidy would
        // be a button that does nothing.
        assertTrue(TextFormats.canTidy(TextKind.JSON))
        assertFalse(TextFormats.canTidy(TextKind.XML))
        assertFalse(TextFormats.canTidy(TextKind.CSV))
        assertFalse(TextFormats.canTidy(TextKind.CODE))
        assertFalse(TextFormats.canTidy(TextKind.PLAIN))
    }

    @Test
    fun everyKindHasALabelToShow() {
        TextKind.entries.forEach { assertTrue(it.label.isNotBlank()) }
    }
}
