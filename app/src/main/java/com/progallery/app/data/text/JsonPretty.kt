package com.progallery.app.data.text

/**
 * Re-indents a JSON document so it can be read.
 *
 * A token pass rather than a parse-and-re-serialise: parsing would turn `1` into `1.0` and
 * reorder nothing but reformat everything, losing exactly what the user came to look at. This
 * only moves whitespace, and leaves every character of the content alone.
 *
 * Refuses to touch anything that is not balanced, rather than producing something that looks
 * formatted but is not.
 */
object JsonPretty {

    fun indent(text: String, unit: String = "  "): Result<String> {
        val out = StringBuilder(text.length + text.length / 4)
        var depth = 0
        var inString = false
        var escaped = false

        /**
         * True immediately after an opening bracket and before anything has been written for
         * it. It exists so that `{}` and `[]` stay on one line, while a bracket with anything
         * inside it breaks the line — which is the whole point of indenting.
         */
        var openedAndEmpty = false

        fun write(character: Char) {
            if (openedAndEmpty) {
                out.append('\n').append(unit.repeat(depth))
                openedAndEmpty = false
            }
            out.append(character)
        }

        for (character in text) {
            if (inString) {
                out.append(character)
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
                continue
            }

            when (character) {
                '"' -> {
                    write(character)
                    inString = true
                }

                '{', '[' -> {
                    write(character)
                    depth++
                    openedAndEmpty = true
                }

                '}', ']' -> {
                    if (openedAndEmpty) {
                        // Nothing was written between the brackets, so they stay together.
                        out.append(character)
                        openedAndEmpty = false
                    } else {
                        depth--
                        if (depth < 0) {
                            return Result.failure(IllegalArgumentException("This JSON is not balanced."))
                        }
                        out.append('\n').append(unit.repeat(depth)).append(character)
                    }
                    depth = if (openedAndEmpty) depth - 1 else depth
                }

                ',' -> {
                    out.append(character).append('\n').append(unit.repeat(depth))
                    openedAndEmpty = false
                }

                ':' -> {
                    out.append(": ")
                    openedAndEmpty = false
                }

                ' ', '\t', '\n', '\r' -> {
                    // Existing whitespace is discarded; this is what puts it back.
                }

                else -> write(character)
            }
        }

        if (inString) return Result.failure(IllegalArgumentException("This JSON has an unclosed string."))
        if (depth != 0) return Result.failure(IllegalArgumentException("This JSON is not balanced."))
        return Result.success(out.toString())
    }

}
