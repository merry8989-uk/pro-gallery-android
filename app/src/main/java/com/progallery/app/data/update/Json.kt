package com.progallery.app.data.update

/**
 * A small JSON reader.
 *
 * `org.json` is stubbed out in JVM unit tests, and the update check reads a payload whose
 * shape is an external contract — exactly the thing worth testing without a device. So the
 * reader lives here, is small, and is covered by tests against a real GitHub reply.
 *
 * Numbers come back as [Double], which is all JSON offers; nothing here needs more.
 */
internal object Json {

    class Malformed(message: String) : IllegalArgumentException(message)

    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.readValue()
        reader.skipWhitespace()
        if (!reader.atEnd()) throw Malformed("unexpected trailing content at ${reader.position}")
        return value
    }

    @Suppress("UNCHECKED_CAST")
    fun objectAt(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun arrayAt(value: Any?): List<Any?>? = value as? List<Any?>

    fun stringAt(container: Map<String, Any?>, key: String): String? =
        (container[key] as? String)?.takeIf { it.isNotBlank() }

    fun booleanAt(container: Map<String, Any?>, key: String): Boolean? =
        container[key] as? Boolean

    private class Reader(private val text: String) {
        var position = 0
            private set

        fun atEnd(): Boolean = position >= text.length

        fun skipWhitespace() {
            while (position < text.length && text[position].isWhitespace()) position++
        }

        fun readValue(): Any? {
            skipWhitespace()
            return when (val c = peek()) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't', 'f' -> readBoolean()
                'n' -> {
                    expectWord("null")
                    null
                }
                else -> if (c == '-' || c.isDigit()) readNumber() else {
                    throw Malformed("unexpected '$c' at $position")
                }
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val values = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                position++
                return values
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                values[key] = readValue()
                skipWhitespace()
                when (val next = peek()) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return values
                    }
                    else -> throw Malformed("expected ',' or '}' at $position")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val items = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                position++
                return items
            }
            while (true) {
                items += readValue()
                skipWhitespace()
                when (val next = peek()) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return items
                    }
                    else -> throw Malformed("expected ',' or ']' at $position")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                if (atEnd()) throw Malformed("unterminated string")
                when (val c = text[position++]) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        if (atEnd()) throw Malformed("unterminated escape")
                        when (val escape = text[position++]) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                if (position + 4 > text.length) throw Malformed("truncated \\u escape")
                                val hex = text.substring(position, position + 4)
                                position += 4
                                builder.append(
                                    hex.toIntOrNull(16)?.toChar()
                                        ?: throw Malformed("bad unicode escape \\u$hex"),
                                )
                            }
                            else -> throw Malformed("unknown escape \\$escape")
                        }
                    }
                    else -> builder.append(c)
                }
            }
        }

        private fun readBoolean(): Boolean = when {
            matches("true") -> true
            matches("false") -> false
            else -> throw Malformed("expected a boolean at $position")
        }

        private fun readNumber(): Double {
            val start = position
            if (peek() == '-') position++
            while (position < text.length && (text[position].isDigit() || text[position] == '.' ||
                    text[position] == 'e' || text[position] == 'E' || text[position] == '+' ||
                    text[position] == '-')
            ) {
                position++
            }
            return text.substring(start, position).toDoubleOrNull()
                ?: throw Malformed("bad number at $start")
        }

        private fun matches(word: String): Boolean {
            if (!text.startsWith(word, position)) return false
            position += word.length
            return true
        }

        private fun expectWord(word: String) {
            if (!matches(word)) throw Malformed("expected '$word' at $position")
        }

        private fun expect(c: Char) {
            if (peek() != c) throw Malformed("expected '$c' at $position")
            position++
        }

        private fun peek(): Char =
            if (atEnd()) throw Malformed("unexpected end of input") else text[position]
    }
}
