package com.progallery.app.data.usage

/**
 * Turns a crash into something safe to keep.
 *
 * **The exception's message is thrown away, not filtered.** A message is the likeliest place
 * for a file path, a search term or a person's name to appear — "File not found:
 * /storage/emulated/0/DCIM/Holiday/beach.jpg" is a perfectly ordinary crash message — and a
 * filter can only remove the shapes someone thought of. Dropping it entirely cannot be
 * defeated by a message nobody anticipated.
 *
 * What survives is enough to find the fault: the exception's class name, and the class and
 * method of the first few frames. File names, line numbers and causes are left out too, because
 * none of them are needed to know *where* it broke.
 */
object CrashSanitizer {

    private const val MAX_FRAMES = 4

    fun record(throwable: Throwable, build: String): CrashRecord = CrashRecord(
        exceptionClass = throwable::class.java.simpleName.ifBlank { "Throwable" },
        // Built in one pass: mapping first and indexing after would misalign, because a
        // skipped frame would shift every later one.
        frames = throwable.stackTrace
            .take(MAX_FRAMES)
            .mapNotNull { frame ->
                val simpleClass = frame.className.substringAfterLast('.')
                if (simpleClass.isBlank()) null else "$simpleClass.${frame.methodName}"
            },
        build = build,
    )
}
