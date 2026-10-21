package com.progallery.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands the log to a mail app, already attached and written up.
 *
 * **Gmail is named when it is installed**, so the message composes in Gmail rather than asking
 * which app to use every single time. When it is not there, a chooser is the honest fallback —
 * naming a package that is absent would throw and leave the button looking broken.
 *
 * **No recipient is filled in.** There is no support address defined for this project — the
 * contact is an Instagram handle — and inventing one would mean a log sent to an address the
 * person did not choose. The subject, the body and the attachment are filled in; the To field is
 * theirs.
 */
object LogMail {

    private const val GMAIL = "com.google.android.gm"

    fun compose(
        context: Context,
        file: File,
        userCode: String,
        eventCount: Int,
        subjectSuffix: String? = null,
    ): Intent {
        val attachment = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val send = Intent(Intent.ACTION_SEND).apply {
            // A broad type on purpose: the attachment may be a CSV, a JSON, or plain text, and a
            // narrow type makes some mail apps drop the attachment silently.
            type = "*/*"
            putExtra(Intent.EXTRA_SUBJECT, buildString {
                append("ProGallery diagnostics")
                if (!subjectSuffix.isNullOrBlank()) append(" — ").append(subjectSuffix)
                append(" — ").append(userCode)
            })
            putExtra(Intent.EXTRA_TEXT, body(userCode, eventCount))
            putExtra(Intent.EXTRA_STREAM, attachment)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val gmail = Intent(send).setPackage(GMAIL)
        return if (gmail.resolveActivity(context.packageManager) != null) gmail else send
    }

    /**
     * The intent to start.
     *
     * Gmail directly when it is there; a chooser only when it is not. Wrapping the Gmail intent in
     * a chooser would ask which app to use every single time, which is exactly what naming the
     * package is for.
     */
    fun send(
        context: Context,
        file: File,
        userCode: String,
        eventCount: Int,
        subjectSuffix: String? = null,
    ): Intent {
        val gmail = compose(context, file, userCode, eventCount, subjectSuffix)
        val isGmail = gmail.`package` != null
        return if (isGmail) gmail else Intent.createChooser(gmail, "Send the diagnostics log")
    }

    /** The body: what this is, and what is not in it. */
    fun body(userCode: String, eventCount: Int): String = buildString {
        appendLine("ProGallery diagnostics log")
        appendLine()
        appendLine("Device code: $userCode")
        appendLine("Events attached: $eventCount")
        appendLine("Format: one JSON object per line (.jsonl inside the attachment)")
        appendLine()
        appendLine("What this contains: which screens were opened, which actions were taken and")
        appendLine("how they ended, access and permission states, timings, and crash types.")
        appendLine()
        appendLine("What it does not contain: photos, file names, file paths, contacts, location,")
        appendLine("search terms, or anything that identifies the person or the device beyond the")
        appendLine("random code above.")
        appendLine()
        appendLine("Please describe what went wrong when you sent this.")
        appendLine()
        appendLine("Contact: @osintgram_io on Instagram")
    }
}
