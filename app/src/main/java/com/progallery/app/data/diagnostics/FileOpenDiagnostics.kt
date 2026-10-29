package com.progallery.app.data.diagnostics

import android.os.Build

/**
 * One file-open attempt, as a structured record.
 *
 * **Successes are recorded as well as failures.** A log of failures only tells you something failed;
 * it does not tell you how often. Twenty failures mean one thing if `.webp` was opened twenty-five
 * times and another if it was opened five thousand times. Without the denominator there is no rate,
 * and without a rate there is no way to tell a broken format from one unlucky file.
 *
 * **The reason is only classified when the evidence supports it.** `stage` says where it stopped —
 * opening the file, decoding it, starting playback, parsing — and `failure_reason` is only filled in
 * when something actually said why. Guessing a reason is not diagnosis; it is throwing darts in the
 * dark and then believing the holes.
 *
 * **Extension and MIME type are recorded separately**, because they disagree more often than people
 * expect: a `.opus` file's MIME can be `audio/ogg`, and a correct extension guarantees nothing about
 * the bytes behind it.
 *
 * **No file name, no path, no content.** A size **bucket** rather than a size, so a report cannot be
 * matched against a particular photo. This is a record a person can send without sending their
 * library.
 */
object FileOpenDiagnostics {

    /** Which part of the app opened the file. */
    enum class Feature(val label: String) {
        IMAGE_VIEWER("image_viewer"),
        VIDEO_PLAYER("video_player"),
        AUDIO_PLAYER("audio_player"),
        PDF_READER("pdf_reader"),
        TEXT_READER("text_reader"),
        ARCHIVE_READER("archive_reader"),
        DOCUMENT_READER("document_reader"),
        UNKNOWN("unknown"),
    }

    /** How far it got. The stage is where a failure happened, and it is the first useful question. */
    enum class Stage(val label: String) {
        /** Getting a stream from the address — permission, missing file, a provider refusing. */
        OPEN("open"),

        /** Reading and interpreting the bytes — an image decoder, a zip, a text decoder. */
        DECODE("decode"),

        /** Handing the file to the player and starting it. */
        PLAYBACK("playback"),

        /** Parsing a structured document — a PDF page, a spreadsheet. */
        PARSE("parse"),

        /** Passing it to another app, because nothing here can read it. */
        HANDOFF("handoff"),
        ;
    }

    /** A coarse size, so a report cannot be matched to a particular file. */
    fun sizeBucket(bytes: Long?): String = when {
        bytes == null || bytes < 0 -> "unknown"
        bytes == 0L -> "empty"
        bytes < 100L * 1024 -> "<100KB"
        bytes < 1024L * 1024 -> "100KB-1MB"
        bytes < 10L * 1024 * 1024 -> "1-10MB"
        bytes < 100L * 1024 * 1024 -> "10-100MB"
        else -> ">100MB"
    }

    /** Where the file came from, which decides what a failure means. */
    fun sourceOf(uriScheme: String?): String = when (uriScheme?.lowercase()) {
        "content" -> "media_store"
        "file" -> "file_path"
        "http", "https" -> "network"
        null, "" -> "unknown"
        else -> uriScheme.lowercase()
    }

    /**
     * A successful open. Recorded because the failures are only meaningful next to it.
     */
    fun opened(
        feature: Feature,
        extension: String,
        mimeType: String?,
        bytes: Long?,
        uriScheme: String?,
        stage: Stage = Stage.DECODE,
        appVersion: String,
        eventId: String,
    ): Map<String, String> = base(
        event = "file_opened",
        feature = feature,
        extension = extension,
        mimeType = mimeType,
        bytes = bytes,
        uriScheme = uriScheme,
        stage = stage,
        appVersion = appVersion,
        eventId = eventId,
    )

    /**
     * A failed open.
     *
     * [reason] must come from something that actually said why — an error code, an exception, a
     * refused stream. Pass `null` when nothing said anything and the record will say `unclassified`
     * rather than invent a cause.
     */
    fun failed(
        feature: Feature,
        extension: String,
        mimeType: String?,
        bytes: Long?,
        uriScheme: String?,
        stage: Stage,
        reason: String?,
        exception: String? = null,
        appVersion: String,
        eventId: String,
    ): Map<String, String> = base(
        event = "file_open_failed",
        feature = feature,
        extension = extension,
        mimeType = mimeType,
        bytes = bytes,
        uriScheme = uriScheme,
        stage = stage,
        appVersion = appVersion,
        eventId = eventId,
    ) + buildMap {
        put("failure_reason", reason?.take(80) ?: "unclassified")
        exception?.let { put("exception", it.substringAfterLast('.').take(60)) }
    }

    private fun base(
        event: String,
        feature: Feature,
        extension: String,
        mimeType: String?,
        bytes: Long?,
        uriScheme: String?,
        stage: Stage,
        appVersion: String,
        eventId: String,
    ): Map<String, String> = buildMap {
        put("event", event)
        put("feature", feature.label)
        put("extension", extension.lowercase().ifBlank { "unknown" })
        put("mime_type", mimeType?.take(80) ?: "unknown")
        put("file_size_bucket", sizeBucket(bytes))
        put("source", sourceOf(uriScheme))
        put("stage", stage.label)
        // Device context travels with every event so a report is readable on its own, without the
        // session header a live log has. The model is a string a person already knows they own.
        // `runCatching` because these are Android statics: on a device they are the whole point, and
        // in a JVM unit test they throw "not mocked". A record that cannot be built in a test cannot
        // be tested, and this object exists to be tested.
        put("android_version", runCatching { Build.VERSION.SDK_INT }.getOrDefault(0).toString())
        put("device_model", runCatching { Build.MODEL ?: "unknown" }.getOrDefault("unknown").take(40))
        put("app_version", appVersion)
        put("event_id", eventId)
    }
}
