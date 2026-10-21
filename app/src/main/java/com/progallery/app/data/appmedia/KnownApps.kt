package com.progallery.app.data.appmedia

/**
 * Messaging apps whose media this app looks for by name.
 *
 * **This list is a convenience, not the source of truth.** What is actually on the device is
 * found by reading the folders — see [AppMediaDetector] — so an app nobody listed here still
 * appears, and a package listed here that is not installed simply shows as not installed.
 *
 * The package names below were **checked against each app's own Play Store listing** rather
 * than guessed, because a wrong package name is worse than none: it would attribute one app's
 * media to another. Where a name could not be verified it is left out, and discovery covers it.
 *
 * * `com.whatsapp` / `com.whatsapp.w4b` — WhatsApp and WhatsApp Business.
 * * `org.telegram.messenger` and the other Telegram package names in circulation.
 * * `com.aratai.chat` — Arattai (Zoho).
 * * `com.friendsV` — Hyped.
 *
 * Sandesh is not listed: no package name for it could be verified, and inventing one would put
 * another app's media under its name. If it keeps media under `Android/media` it is still found
 * by discovery, and the system supplies its real name.
 */
object KnownApps {

    data class Known(val packageName: String, val name: String)

    val MESSAGING: List<Known> = listOf(
        Known("com.whatsapp", "WhatsApp"),
        Known("com.whatsapp.w4b", "WhatsApp Business"),
        Known("org.telegram.messenger", "Telegram"),
        Known("org.telegram.messenger.web", "Telegram (web)"),
        Known("org.telegram.plus", "Telegram Plus"),
        Known("org.thunderdog.challegram", "Telegram X"),
        Known("com.aratai.chat", "Arattai"),
        Known("com.friendsV", "Hyped"),
    )

    /** The name to show before the system is asked, or null when the app is not listed. */
    fun nameOf(packageName: String): String? =
        MESSAGING.firstOrNull { it.packageName == packageName }?.name
}
