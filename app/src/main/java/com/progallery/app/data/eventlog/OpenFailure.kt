package com.progallery.app.data.eventlog

/**
 * Records that a file could not be opened, with enough detail to act on.
 *
 * **This exists because four of the app's five viewers logged nothing when they failed.** The text
 * viewer put *"This file could not be opened"* on the screen and told the log nothing; so did the
 * image viewer, the PDF viewer and the archive viewer. Only the player recorded its failures.
 *
 * The consequence: a diagnostics export — the one artefact meant to explain what is broken — was
 * **silent about exactly the failures a person was reporting**. Every question about "why does this
 * file not open" then had to be answered by reading code and guessing, and two guesses in a row were
 * wrong.
 *
 * What is recorded, and why:
 *
 * - **the extension**, because the first question is always "which format".
 * - **the reason** in words, because that is what the screen showed.
 * - **whether the address was even readable**, because "the file is gone or unreadable" and "the
 *   reader could not parse it" are different problems with different fixes.
 *
 * Nothing about the file's contents, its path, or its name goes in — a log is a thing people send.
 */
object OpenFailure {

    fun details(
        viewer: String,
        extension: String,
        reason: String?,
        uriReadable: Boolean? = null,
    ): Map<String, String> = buildMap {
        put("where", "open")
        put("viewer", viewer)
        put("extension", extension.lowercase().ifBlank { "unknown" })
        put("reason", reason?.take(120) ?: "no reason given")
        uriReadable?.let { put("uri_readable", it.toString()) }
    }
}
