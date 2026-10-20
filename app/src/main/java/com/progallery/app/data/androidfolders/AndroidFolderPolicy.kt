package com.progallery.app.data.androidfolders

import android.os.Build

/** The restricted locations under the shared-storage `Android/` folder. */
enum class AndroidFolderTarget(val label: String) {
    /** `Android/media` — shared media written by other apps (WhatsApp, Telegram…). */
    MEDIA("Android/media"),

    /** `Android/data/<our package>` — our own folder, always ours to read. */
    OWN_DATA("Android/data (this app)"),

    /** `Android/data/<other apps>` — blocked by the OS from Android 11. */
    OTHER_APP_DATA("Android/data (other apps)"),

    /** `Android/obb` — blocked by the OS from Android 11. */
    OBB("Android/obb"),
}

/** Whether a location can actually be read, and why (or why not). */
data class Reachability(
    val target: AndroidFolderTarget,
    val reachable: Boolean,
    val reason: String,
)

/**
 * What a **non-rooted** app can and cannot read under `Android/`.
 *
 * This is a platform rule, not a preference, and it is the same for every app:
 *
 *  - `Android/data/<other apps>` and `Android/obb` became unreadable in
 *    **Android 11 (API 30)**. Neither "All files access"
 *    (`MANAGE_EXTERNAL_STORAGE`) nor the Storage Access Framework can open them —
 *    the system picker refuses to grant them and the file APIs return nothing.
 *    Reading them requires root, which this app deliberately does not use.
 *  - On **Android 10 and below** they are ordinary storage and can be read.
 *  - `Android/media` is *shared media*, so it stays readable — with All files
 *    access on Android 11+, or with the normal media permission before that.
 *  - Our own `Android/data/<our package>` is always ours.
 *
 * The policy is pure so it can be unit tested, and the UI states it plainly
 * instead of offering a button that could never work.
 */
object AndroidFolderPolicy {

    fun evaluate(sdkInt: Int = Build.VERSION.SDK_INT, hasAllFilesAccess: Boolean): List<Reachability> {
        val scopedStorageEnforced = sdkInt >= Build.VERSION_CODES.R // API 30

        return listOf(
            Reachability(
                target = AndroidFolderTarget.MEDIA,
                reachable = !scopedStorageEnforced || hasAllFilesAccess,
                reason = when {
                    !scopedStorageEnforced ->
                        "Shared media — readable with the normal storage permission."
                    hasAllFilesAccess ->
                        "Shared media — readable with All files access."
                    else ->
                        "Needs \"All files access\" on Android 11+. Media here is also indexed by the system, so it may already appear in Photos."
                },
            ),
            Reachability(
                target = AndroidFolderTarget.OWN_DATA,
                reachable = true,
                reason = "This app's own folder — always readable.",
            ),
            Reachability(
                target = AndroidFolderTarget.OTHER_APP_DATA,
                reachable = !scopedStorageEnforced,
                reason = if (scopedStorageEnforced) {
                    "Blocked by Android 11+. No permission unlocks this — the system refuses it to every non-rooted app, " +
                        "including \"All files access\" and the folder picker."
                } else {
                    "Android 10 or older: this is ordinary storage and can be scanned."
                },
            ),
            Reachability(
                target = AndroidFolderTarget.OBB,
                reachable = !scopedStorageEnforced,
                reason = if (scopedStorageEnforced) {
                    "Blocked by Android 11+ for every non-rooted app."
                } else {
                    "Android 10 or older: readable."
                },
            ),
        )
    }

    /** Targets that can be scanned on this device right now. */
    fun reachableTargets(
        sdkInt: Int = Build.VERSION.SDK_INT,
        hasAllFilesAccess: Boolean,
    ): List<AndroidFolderTarget> = evaluate(sdkInt, hasAllFilesAccess)
        .filter { it.reachable }
        .map { it.target }
}
