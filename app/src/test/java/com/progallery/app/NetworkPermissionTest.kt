package com.progallery.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards a permission the app cannot work without, and that was genuinely missing.
 *
 * Three features open a connection — Stream from a link, WebDAV browsing, and the update
 * check. On Android, a socket opened without `INTERNET` in the manifest throws
 * `SecurityException: Permission denied`, so all three were dead on arrival while the code
 * that called them looked perfectly correct.
 *
 * The manifest is read here rather than assumed, because nothing else in the build would
 * catch its absence: it compiles, it lints, and the unit tests pass either way.
 */
class NetworkPermissionTest {

    private fun manifest(): String {
        val file = File("src/main/AndroidManifest.xml")
        assertTrue(
            "AndroidManifest.xml was not found at ${file.absolutePath}; this check cannot be skipped",
            file.exists(),
        )
        return file.readText()
    }

    @Test
    fun theInternetPermissionIsDeclared() {
        assertTrue(
            "INTERNET is required by Stream from a link, WebDAV browsing and the update check",
            manifest().contains("android.permission.INTERNET"),
        )
    }

    @Test
    fun thePermissionIsDeclaredOutsideAComment() {
        // A permission inside an XML comment grants nothing, so the declaration itself is
        // checked rather than the string merely appearing.
        val withoutComments = manifest().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        assertTrue(
            "INTERNET appears only inside a comment, which grants nothing",
            withoutComments.contains("""android:name="android.permission.INTERNET""""),
        )
    }
}
