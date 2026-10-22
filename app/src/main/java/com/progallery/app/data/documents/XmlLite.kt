package com.progallery.app.data.documents

/**
 * Just enough XML to read the text out of a document.
 *
 * Hand-written rather than using the platform parser for a specific reason: `XmlPullParser` lives
 * in `android.jar`, whose stubs throw in a JVM unit test — so a reader built on it can only be
 * tested on a device, and a document reader that is only tested on a device is a document reader
 * nobody tests. This runs anywhere, so the formats are tested against containers the tests build.
 *
 * It is a tokeniser, not a validator: it produces start tags with their attributes, end tags and
 * text, and ignores everything else (comments, processing instructions, doctypes, CDATA is read
 * as text). Malformed input produces fewer events rather than an exception — a document that is
 * slightly wrong should still show what it can.
 */
object XmlLite {

    sealed interface Event {
        data class Start(val name: String, val attributes: Map<String, String>) : Event
        data class End(val name: String) : Event
        data class Text(val value: String) : Event
    }

    /** The local name, without any namespace prefix: `w:p` and `p` both become `p`. */
    fun localName(name: String): String = name.substringAfterLast(':')

    fun events(xml: String): List<Event> {
        val out = ArrayList<Event>()
        val text = StringBuilder()
        var index = 0

        fun flushText() {
            if (text.isNotEmpty()) {
                out.add(Event.Text(decodeEntities(text.toString())))
                text.setLength(0)
            }
        }

        while (index < xml.length) {
            val character = xml[index]
            if (character != '<') {
                text.append(character)
                index++
                continue
            }

            when {
                xml.startsWith("<!--", index) -> {
                    flushText()
                    index = (xml.indexOf("-->", index).takeIf { it >= 0 } ?: xml.length) + 3
                }
                xml.startsWith("<![CDATA[", index) -> {
                    flushText()
                    val close = xml.indexOf("]]>", index)
                    val end = if (close >= 0) close else xml.length
                    out.add(Event.Text(xml.substring(index + 9, end)))
                    index = end + 3
                }
                xml.startsWith("<?", index) -> {
                    flushText()
                    index = (xml.indexOf("?>", index).takeIf { it >= 0 } ?: xml.length) + 2
                }
                xml.startsWith("<!", index) -> {
                    flushText()
                    index = (xml.indexOf('>', index).takeIf { it >= 0 } ?: xml.length) + 1
                }
                else -> {
                    flushText()
                    val close = xml.indexOf('>', index)
                    if (close < 0) break
                    val inner = xml.substring(index + 1, close)
                    index = close + 1

                    if (inner.startsWith("/")) {
                        out.add(Event.End(inner.removePrefix("/").trim()))
                        continue
                    }
                    val selfClosing = inner.endsWith("/")
                    val body = if (selfClosing) inner.dropLast(1).trim() else inner.trim()
                    val name = body.substringBefore(' ')
                    out.add(Event.Start(name, attributesOf(body)))
                    if (selfClosing) out.add(Event.End(name))
                }
            }
        }
        flushText()
        return out
    }

    private fun attributesOf(tagBody: String): Map<String, String> {
        val attributes = LinkedHashMap<String, String>()
        // name="value" or name='value'
        var index = tagBody.indexOf(' ')
        if (index < 0) return attributes
        index++
        while (index < tagBody.length) {
            while (index < tagBody.length && tagBody[index].isWhitespace()) index++
            val nameStart = index
            while (index < tagBody.length && !tagBody[index].isWhitespace() && tagBody[index] != '=') index++
            val name = tagBody.substring(nameStart, index)
            while (index < tagBody.length && (tagBody[index].isWhitespace() || tagBody[index] == '=')) index++
            if (index >= tagBody.length) break
            val quote = tagBody[index]
            if (quote != '"' && quote != '\'') { if (name.isNotEmpty()) attributes[name] = ""; continue }
            val valueStart = index + 1
            val valueEnd = tagBody.indexOf(quote, valueStart).takeIf { it >= 0 } ?: tagBody.length
            if (name.isNotEmpty()) {
                attributes[name] = decodeEntities(tagBody.substring(valueStart, valueEnd))
            }
            index = valueEnd + 1
        }
        return attributes
    }

    /** The five named entities and the two numeric forms. Anything else is left as written. */
    fun decodeEntities(value: String): String {
        if ('&' !in value) return value
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character != '&') {
                out.append(character)
                index++
                continue
            }
            val end = value.indexOf(';', index)
            if (end < 0 || end - index > 12) {
                out.append(character)
                index++
                continue
            }
            val entity = value.substring(index + 1, end)
            val decoded = when {
                entity == "amp" -> "&"
                entity == "lt" -> "<"
                entity == "gt" -> ">"
                entity == "quot" -> "\""
                entity == "apos" -> "'"
                entity == "nbsp" -> " "
                entity.startsWith("#x") || entity.startsWith("#X") ->
                    entity.drop(2).toIntOrNull(16)?.let { codePoint(it) }
                entity.startsWith("#") -> entity.drop(1).toIntOrNull()?.let { codePoint(it) }
                else -> null
            }
            if (decoded == null) {
                out.append(character)
                index++
            } else {
                out.append(decoded)
                index = end + 1
            }
        }
        return out.toString()
    }

    private fun codePoint(value: Int): String? =
        if (value in 1..0x10FFFF) runCatching { String(Character.toChars(value)) }.getOrNull() else null

    /** UTF-8, with a byte-order mark dropped — the office formats write one. */
    fun decodeText(bytes: ByteArray): String {
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) 3 else 0
        return String(bytes, start, bytes.size - start, Charsets.UTF_8)
    }
}
