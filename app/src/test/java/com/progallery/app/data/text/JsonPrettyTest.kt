package com.progallery.app.data.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The indenter must only move whitespace. If it changed the content, the user would be reading
 * something that is not what is in their file.
 */
class JsonPrettyTest {

    private fun pretty(json: String) = JsonPretty.indent(json).getOrThrow()

    @Test
    fun aFlatObjectIsSpreadOverLines() {
        assertEquals(
            """
            {
              "a": 1,
              "b": "two"
            }
            """.trimIndent(),
            pretty("""{"a":1,"b":"two"}"""),
        )
    }

    @Test
    fun nestingIsIndented() {
        assertEquals(
            """
            {
              "a": {
                "b": [
                  1,
                  2
                ]
              }
            }
            """.trimIndent(),
            pretty("""{"a":{"b":[1,2]}}"""),
        )
    }

    @Test
    fun anEmptyObjectStaysOnOneLine() {
        assertEquals("{}", pretty("{}"))
        assertEquals("[]", pretty("[]"))
        assertEquals("{\n  \"a\": {}\n}", pretty("""{"a":{}}"""))
    }

    @Test
    fun numbersAreNotReformatted() {
        // This is why it is a token pass and not a parse: 1 must not become 1.0.
        val out = pretty("""{"n":1,"f":1.50,"e":1e3,"neg":-2}""")
        assertTrue(out.contains("\"n\": 1"))
        assertTrue(out.contains("\"f\": 1.50"))
        assertTrue(out.contains("\"e\": 1e3"))
        assertTrue(out.contains("\"neg\": -2"))
    }

    @Test
    fun bracesAndCommasInsideStringsAreLeftAlone() {
        val out = pretty("""{"s":"a, b { c } [ d ]"}""")
        assertTrue("the string content must be untouched", out.contains("""a, b { c } [ d ]"""))
    }

    @Test
    fun anEscapedQuoteDoesNotEndTheString() {
        val out = pretty("""{"s":"he said \"hi\", then left"}""")
        assertTrue(out.contains("""he said \"hi\", then left"""))
    }

    @Test
    fun anEscapedBackslashDoesNotEscapeTheClosingQuote() {
        val out = pretty("""{"s":"back\\"}""")
        assertTrue(out.contains("""back\\"""))
    }

    @Test
    fun alreadyIndentedInputIsTidiedNotDoubled() {
        val once = pretty("""{"a":1}""")
        assertEquals(once, pretty(once))
    }

    @Test
    fun unbalancedInputIsRefusedRatherThanFormatted() {
        assertTrue(JsonPretty.indent("""{"a":1""").isFailure)
        assertTrue(JsonPretty.indent("""{"a":1}}""").isFailure)
        assertTrue(JsonPretty.indent("""{"a":"unclosed}""").isFailure)
    }

    @Test
    fun anEmptyDocumentIsLeftEmpty() {
        assertEquals("", pretty(""))
        assertEquals("", pretty("   \n  "))
    }

    @Test
    fun aTopLevelArrayIsHandled() {
        assertEquals("[\n  1,\n  2\n]", pretty("[1,2]"))
    }

    @Test
    fun theIndentWidthCanBeChosen() {
        assertEquals("{\n\t\"a\": 1\n}", pretty("""{"a":1}""".let { JsonPretty.indent(it, "\t").getOrThrow() }))
    }
}
