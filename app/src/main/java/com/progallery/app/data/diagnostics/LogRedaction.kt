package com.progallery.app.data.diagnostics

/**
 * What a file may look like in the diagnostics log.
 *
 * Pure, because this is a privacy guarantee and a guarantee worth pinning down: with
 * paths off, the log must not be able to reconstruct where anything lives. It keeps the
 * extension — useful for "what kind of file failed" — and throws away the name.
 */
object LogRedaction {

    const val HIDDEN = "(path hidden)"

    /**
     * The form [path] may take in the log.
     *
     * With [allowPaths] the path is used as given. Otherwise only the extension
     * survives: `DCIM/Camera/IMG_2024.jpg` becomes `*.jpg`, which says the file was an
     * image without saying whose, or where.
     */
    fun describe(path: String, allowPaths: Boolean): String {
        if (allowPaths) return path

        val name = path.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        return if (dot in 1 until name.length - 1) {
            "*${name.substring(dot)}"
        } else {
            HIDDEN
        }
    }
}
