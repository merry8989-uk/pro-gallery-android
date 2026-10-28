package com.progallery.app.data.eventlog

/**
 * What a crash actually needs to say, and what it used to.
 *
 * The log recorded `exception_type=IllegalArgumentException; fatal=true` and **nothing else**. That
 * is a fact about a crash without being an explanation of one: it says the app died, not why, not
 * where, and not what was being done. A person reading it can do nothing with it, and neither could
 * I — three crashes in one exported log, all the same, all unactionable.
 *
 * What is recorded now:
 *
 * - **`message`** — the exception's own words, which is usually the cause in one line.
 * - **`where`** — the **first frame inside this app**, which is the line that broke. A crash in a
 *   library is that library's frame at the top and *our* call at the top of ours.
 * - **`stack`** — the first few frames, so a reader can see the shape of the call.
 * - **`thread`** — a crash on a worker and a crash on the main thread are different problems.
 * - **`screen`** — where the person was, which is the context that makes the rest make sense.
 *
 * **No file paths, no names, no content.** A log is a thing people send; class names and a message
 * are structure, not personal data. Where a message might carry a path, the app's own "include file
 * paths in diagnostics" switch governs it — off by default, as it always was.
 */
object CrashDetail {

    /** How many frames are kept. Enough to see the shape, short enough to read in a log line. */
    const val MAX_FRAMES = 6

    fun of(
        throwable: Throwable,
        screen: String?,
        threadName: String?,
        includePaths: Boolean = false,
    ): Map<String, String> = buildMap {
        put("exception_type", throwable.javaClass.name.substringAfterLast('.'))
        put("fatal", "true")
        put("message", redact(throwable.message, includePaths).take(300).ifBlank { "no message" })
        put("where", firstAppFrame(throwable))
        put("stack", stackHead(throwable, includePaths))
        put("thread", threadName?.take(40) ?: "unknown")
        put("screen", screen?.take(40) ?: "unknown")
        // The chain matters: a wrapper exception's own message is often "failed to do X", and the
        // cause underneath is the real answer.
        throwable.cause?.let { cause ->
            put("cause_type", cause.javaClass.name.substringAfterLast('.'))
            put(
                "cause_message",
                redact(cause.message, includePaths).take(200).ifBlank { "no message" },
            )
        }
    }

    /**
     * The first frame that belongs to this app.
     *
     * A stack's top frame is often a library's — `java.lang.reflect`, `android.os`, `kotlinx` — and
     * naming that tells a reader nothing about where **this** app went wrong.
     */
    fun firstAppFrame(throwable: Throwable): String =
        throwable.stackTrace.firstOrNull { it.className.startsWith("com.progallery.app") }
            ?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
            ?: throwable.stackTrace.firstOrNull()
                ?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
            ?: "unknown"

    /** The first [MAX_FRAMES] frames, as `Class.method:line`. */
    fun stackHead(throwable: Throwable, includePaths: Boolean = false): String =
        throwable.stackTrace
            .take(MAX_FRAMES)
            .joinToString(" <- ") { frame ->
                val where = "${frame.className.substringAfterLast('.')}.${frame.methodName}:${frame.lineNumber}"
                if (includePaths) where else redact(where, false)
            }
            .ifBlank { "no stack" }

    /**
     * Removes anything that looks like a filesystem path from a message.
     *
     * A crash message can carry the address of the file being handled. That is exactly the thing the
     * logging rules forbid by default, and exactly the thing the app's own switch exists to allow.
     */
    fun redact(message: String?, includePaths: Boolean): String {
        val text = message.orEmpty()
        if (includePaths || text.isEmpty()) return text
        // `content://…`, `/storage/…`, `/data/…` — the shapes an Android path takes.
        return text
            .replace(Regex("""(content|file)://\S+"""), "<path>")
            .replace(Regex("""/\S*/\S*"""), "<path>")
    }
}
