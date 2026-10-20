package com.progallery.app.data.settings

/**
 * A tiny JSON reader and writer for the settings export.
 *
 * Hand-rolled deliberately: the settings file is a flat object of booleans, numbers
 * and strings, and `org.json` is stubbed out in JVM unit tests — using it would mean
 * the export format could only be tested on a device. This keeps it testable with no
 * new dependency and no Gradle plugin.
 *
 * It is a *format* reader, not a general JSON parser: it accepts a flat object with
 * string, number, boolean and string-array values, which is exactly what [SettingsExport]
 * writes, and rejects anything else rather than guessing.
 */
internal object FlatJson {

    class MalformedJson(message: String) : IllegalArgumentException(message)

    fun encode(values: Map<String, Any?>): String = buildString {
        append('{')
        var first = true
        values.forEach { (key, value) ->
            if (!first) append(',')
            first = false
            append(quote(key))
            append(':')
            append(encodeValue(value))
        }
        append('}')
    }

    private fun encodeValue(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> value.toString()
        is Int, is Long -> value.toString()
        is String -> quote(value)
        is List<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",") {
            encodeValue(it)
        }
        else -> throw IllegalArgumentException("cannot encode ${value::class.java.simpleName}")
    }

    private fun quote(text: String): String = buildString {
        append('"')
        text.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    /** Reads a flat object. Throws [MalformedJson] on anything it cannot read. */
    fun decode(text: String): Map<String, Any?> {
        val reader = Reader(text)
        val result = reader.readObject()
        reader.skipWhitespace()
        if (!reader.atEnd()) throw MalformedJson("unexpected trailing content at ${reader.position}")
        return result
    }

    private class Reader(private val text: String) {
        var position = 0
            private set

        fun atEnd(): Boolean = position >= text.length

        fun skipWhitespace() {
            while (position < text.length && text[position].isWhitespace()) position++
        }

        fun readObject(): Map<String, Any?> {
            skipWhitespace()
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
                skipWhitespace()
                values[key] = readValue()
                skipWhitespace()
                when (val next = peek()) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return values
                    }
                    else -> throw MalformedJson("expected ',' or '}' at $position, found '$next'")
                }
            }
        }

        private fun readValue(): Any? {
            skipWhitespace()
            return when (val c = peek()) {
                '"' -> readString()
                '[' -> readStringArray()
                't', 'f' -> readBoolean()
                'n' -> {
                    expectWord("null")
                    null
                }
                else -> if (c == '-' || c.isDigit()) readNumber() else {
                    throw MalformedJson("unexpected '$c' at $position")
                }
            }
        }

        private fun readStringArray(): List<String> {
            expect('[')
            val items = ArrayList<String>()
            skipWhitespace()
            if (peek() == ']') {
                position++
                return items
            }
            while (true) {
                skipWhitespace()
                items += readString()
                skipWhitespace()
                when (val next = peek()) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return items
                    }
                    else -> throw MalformedJson("expected ',' or ']' at $position")
                }
            }
        }

        private fun readBoolean(): Boolean = when {
            matches("true") -> true
            matches("false") -> false
            else -> throw MalformedJson("expected a boolean at $position")
        }

        private fun readNumber(): Long {
            val start = position
            if (peek() == '-') position++
            while (position < text.length && text[position].isDigit()) position++
            if (start == position) throw MalformedJson("expected a number at $position")
            return text.substring(start, position).toLongOrNull()
                ?: throw MalformedJson("number out of range at $start")
        }

        private fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                if (atEnd()) throw MalformedJson("unterminated string")
                when (val c = text[position++]) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        if (atEnd()) throw MalformedJson("unterminated escape")
                        when (val escape = text[position++]) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                if (position + 4 > text.length) {
                                    throw MalformedJson("truncated unicode escape")
                                }
                                val hex = text.substring(position, position + 4)
                                position += 4
                                builder.append(
                                    hex.toIntOrNull(16)?.toChar()
                                        ?: throw MalformedJson("bad unicode escape \\u$hex"),
                                )
                            }
                            else -> throw MalformedJson("unknown escape \\$escape")
                        }
                    }
                    else -> builder.append(c)
                }
            }
        }

        private fun matches(word: String): Boolean {
            if (!text.startsWith(word, position)) return false
            position += word.length
            return true
        }

        private fun expectWord(word: String) {
            if (!matches(word)) throw MalformedJson("expected '$word' at $position")
        }

        private fun expect(c: Char) {
            if (peek() != c) throw MalformedJson("expected '$c' at $position")
            position++
        }

        private fun peek(): Char =
            if (atEnd()) throw MalformedJson("unexpected end of input") else text[position]
    }
}
