package com.progallery.app.data.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tokeniser.
 *
 * It exists so the document readers can be tested at all — `XmlPullParser` is in `android.jar`,
 * whose stubs throw in a JVM test — so its own edges are worth pinning: the things a real
 * document contains that a naive splitter gets wrong.
 */
class XmlLiteTest {

    private fun texts(xml: String): List<String> =
        XmlLite.events(xml).filterIsInstance<XmlLite.Event.Text>().map { it.value }

    @Test
    fun textBetweenTagsIsRead() {
        assertEquals(listOf("hello"), texts("<a>hello</a>"))
    }

    @Test
    fun aSelfClosingTagProducesStartAndEnd() {
        // Six: <a> start, then <b/> as start+end, then <c/> as start+end, then </a>.
        val events = XmlLite.events("<a><b/><c/></a>")
        assertEquals(6, events.size)
        assertTrue(events[1] is XmlLite.Event.Start)
        assertTrue(events[2] is XmlLite.Event.End)
    }

    @Test
    fun attributesAreReadWithEitherQuote() {
        val start = XmlLite.events("""<c r="A1" t='s'/>""")
            .filterIsInstance<XmlLite.Event.Start>().single()
        assertEquals("A1", start.attributes["r"])
        assertEquals("s", start.attributes["t"])
    }

    @Test
    fun aNamespacePrefixIsStrippedForTheLocalName() {
        assertEquals("p", XmlLite.localName("w:p"))
        assertEquals("p", XmlLite.localName("p"))
        assertEquals("table-cell", XmlLite.localName("table:table-cell"))
    }

    @Test
    fun commentsAreSkipped() {
        // Two text events rather than one joined run: the tokeniser flushes before every tag, and
        // a comment is a tag. The readers append every text event to the same line, so the split
        // makes no difference to what a document reads as — and asserting the join here would be
        // asserting something the tokeniser does not promise.
        assertEquals(listOf("a", "b"), texts("<a>a<!-- not this -->b</a>"))
        assertEquals("ab", texts("<a>a<!-- not this -->b</a>").joinToString(""))
    }

    @Test
    fun aProcessingInstructionIsSkipped() {
        assertEquals(listOf("x"), texts("<?xml version=\"1.0\"?><a>x</a>"))
    }

    @Test
    fun aDoctypeIsSkipped() {
        assertEquals(listOf("x"), texts("<!DOCTYPE html><a>x</a>"))
    }

    @Test
    fun cdataIsReadAsText() {
        // A document may wrap text in CDATA precisely because it contains angle brackets, and
        // dropping it would lose the text.
        assertEquals(listOf("a < b & c"), texts("<a><![CDATA[a < b & c]]></a>"))
    }

    @Test
    fun theFiveNamedEntitiesAreDecoded() {
        assertEquals("& < > \" '", XmlLite.decodeEntities("&amp; &lt; &gt; &quot; &apos;"))
        assertEquals("a b", XmlLite.decodeEntities("a&nbsp;b"))
    }

    @Test
    fun numericEntitiesAreDecoded() {
        assertEquals("A", XmlLite.decodeEntities("&#65;"))
        assertEquals("A", XmlLite.decodeEntities("&#x41;"))
        assertEquals("—", XmlLite.decodeEntities("&#8212;"))
    }

    @Test
    fun anUnknownEntityIsLeftAsWritten() {
        // Guessing at an entity the reader does not know would be inventing text.
        assertEquals("&weird;", XmlLite.decodeEntities("&weird;"))
        assertEquals("a & b", XmlLite.decodeEntities("a & b"))
    }

    @Test
    fun attributesHaveTheirEntitiesDecodedToo() {
        val start = XmlLite.events("""<a href="x?a=1&amp;b=2"/>""")
            .filterIsInstance<XmlLite.Event.Start>().single()
        assertEquals("x?a=1&b=2", start.attributes["href"])
    }

    @Test
    fun malformedXmlProducesFewerEventsRatherThanThrowing() {
        // A document that is slightly wrong should still show what it can.
        assertTrue(XmlLite.events("<a>unclosed").isNotEmpty())
        assertTrue(XmlLite.events("<<<>>>").isNotEmpty())
        assertEquals(emptyList<XmlLite.Event>(), XmlLite.events(""))
    }

    @Test
    fun aByteOrderMarkIsDropped() {
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hi".toByteArray()
        assertEquals("hi", XmlLite.decodeText(withBom))
        assertEquals("hi", XmlLite.decodeText("hi".toByteArray()))
    }
}
