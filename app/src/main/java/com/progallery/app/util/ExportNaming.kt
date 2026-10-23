package com.progallery.app.util

/**
 * The prefix the editors put on a file they save.
 *
 * A constant rather than the string typed three times, because it was typed three times and the app
 * was renamed — and three copies is how a rename ends up half done. It has no space in it: this
 * becomes part of a file name, and `AdvancedFileManager_1234.png` is easier to live with than
 * `Advanced File Manager_1234.png`.
 */
object ExportNaming {
    const val PREFIX = "AdvancedFileManager"
}
