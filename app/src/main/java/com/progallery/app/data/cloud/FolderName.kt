package com.progallery.app.data.cloud

/** What a folder name is allowed to be, and why not when it is not. */
sealed interface FolderNameCheck {
    data class Ok(val name: String) : FolderNameCheck
    data class Invalid(val reason: String) : FolderNameCheck
}

/**
 * Whether a folder name can be used, and why not when it cannot.
 *
 * Pure, so every rule is tested rather than discovered by a folder that silently fails to appear.
 * The rules are the ones the platform actually enforces — a name with a `/` in it is not a name, it
 * is a path — plus the two that are a person's own mistake worth naming: nothing at all, and one
 * that is far too long for any filesystem.
 */
object FolderName {

    /** Most filesystems refuse a name over 255 bytes; this is the stricter, countable version. */
    const val MAX_LENGTH = 255

    fun check(raw: String): FolderNameCheck {
        // Trailing spaces are stripped rather than rejected: a name typed on a phone keyboard picks
        // them up constantly, and refusing it would be pedantic about something invisible.
        val name = raw.trim()

        if (name.isEmpty()) return FolderNameCheck.Invalid("Give the folder a name.")
        if (name.length > MAX_LENGTH) {
            return FolderNameCheck.Invalid("That name is longer than $MAX_LENGTH characters.")
        }
        if (name.contains('/')) {
            return FolderNameCheck.Invalid("A folder name cannot contain a slash.")
        }
        if (name.contains('\\')) {
            return FolderNameCheck.Invalid("A folder name cannot contain a backslash.")
        }
        // The two the platform reserves, which would otherwise become a folder you cannot enter.
        if (name == "." || name == "..") {
            return FolderNameCheck.Invalid("\"$name\" is reserved and cannot be used.")
        }
        // Control characters are invisible in a list and legal in some filesystems and not others.
        if (name.any { it.isISOControl() }) {
            return FolderNameCheck.Invalid("A folder name cannot contain control characters.")
        }
        return FolderNameCheck.Ok(name)
    }
}
