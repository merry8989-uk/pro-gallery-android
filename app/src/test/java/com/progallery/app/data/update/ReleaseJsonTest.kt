package com.progallery.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The releases feed is an external contract, so the reader is tested against a reply shaped
 * the way GitHub actually answers — including the parts that do not matter, which must not
 * throw the reader off.
 */
class ReleaseJsonTest {

    /** Modelled on a real reply: extra fields, nested objects, a long body with escapes. */
    private val reply = """
        {
          "url": "https://api.github.com/repos/merry8989-uk/pro-gallery-android/releases/1",
          "id": 1,
          "tag_name": "v1.16.0",
          "name": "ProGallery 1.16.0",
          "draft": false,
          "prerelease": false,
          "created_at": "2026-10-21T05:00:00Z",
          "body": "## Fixed\n\n- The content address was built from a guess.\n- \"Quoted\" text and a \\\\ backslash.",
          "html_url": "https://github.com/merry8989-uk/pro-gallery-android/releases/tag/v1.16.0",
          "author": { "login": "merry8989-uk", "id": 42 },
          "assets": [
            {
              "name": "pro-gallery-release.aab",
              "size": 7156192,
              "content_type": "application/octet-stream",
              "browser_download_url": "https://github.com/merry8989-uk/pro-gallery-android/releases/download/v1.16.0/pro-gallery-release.aab"
            },
            {
              "name": "pro-gallery-debug.apk",
              "size": 23683496,
              "content_type": "application/vnd.android.package-archive",
              "browser_download_url": "https://github.com/merry8989-uk/pro-gallery-android/releases/download/v1.16.0/pro-gallery-debug.apk"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun aRealReplyIsRead() {
        val release = ReleaseJson.parse(reply).getOrThrow()
        assertEquals("v1.16.0", release.tagName)
        assertEquals("1.16.0", release.versionName)
        assertEquals("ProGallery 1.16.0", release.title)
        assertEquals(
            "https://github.com/merry8989-uk/pro-gallery-android/releases/tag/v1.16.0",
            release.pageUrl,
        )
        assertFalse(release.prerelease)
    }

    @Test
    fun theInstallableApkIsChosenOverTheBundle() {
        // A signed bundle cannot be installed by hand, so the APK is the one that matters.
        val release = ReleaseJson.parse(reply).getOrThrow()
        assertTrue(release.apkUrl!!.endsWith("pro-gallery-debug.apk"))
    }

    @Test
    fun theReleaseNotesSurviveEscapes() {
        val notes = ReleaseJson.parse(reply).getOrThrow().notes!!
        assertTrue(notes.contains("The content address was built from a guess."))
        assertTrue("a quoted word must survive", notes.contains("\"Quoted\" text"))
        assertTrue("a backslash must survive", notes.contains("\\"))
    }

    @Test
    fun aBundleOnlyReleaseHasNoApk() {
        val json = """
            {"tag_name":"v9.0","html_url":"https://x/9","assets":[
              {"name":"app.aab","browser_download_url":"https://x/app.aab"}]}
        """.trimIndent()
        assertNull(ReleaseJson.parse(json).getOrThrow().apkUrl)
    }

    @Test
    fun aReleaseWithNoAssetsIsStillReadable() {
        val json = """{"tag_name":"v9.0","html_url":"https://x/9","assets":[]}"""
        val release = ReleaseJson.parse(json).getOrThrow()
        assertNull(release.apkUrl)
        assertEquals("9.0", release.versionName)
    }

    @Test
    fun aTagWithoutTheVIsAlsoRead() {
        val json = """{"tag_name":"1.16.0","html_url":"https://x/9"}"""
        assertEquals("1.16.0", ReleaseJson.parse(json).getOrThrow().versionName)
    }

    @Test
    fun aReplyWithNoTagIsRefusedWithAReadableReason() {
        val result = ReleaseJson.parse("""{"html_url":"https://x/9"}""")
        assertTrue(result.isFailure)
        assertEquals("The latest release has no version tag.", result.exceptionOrNull()?.message)
    }

    @Test
    fun aReplyWithNoPageIsRefused() {
        val result = ReleaseJson.parse("""{"tag_name":"v1.0"}""")
        assertTrue(result.isFailure)
        assertEquals("The latest release has no page address.", result.exceptionOrNull()?.message)
    }

    @Test
    fun malformedJsonIsRefusedWithAReadableReason() {
        val result = ReleaseJson.parse("not json at all")
        assertTrue(result.isFailure)
        assertEquals("The update server's reply could not be read.", result.exceptionOrNull()?.message)
    }

    @Test
    fun aJsonArrayWhereAnObjectWasExpectedIsRefused() {
        val result = ReleaseJson.parse("""[{"tag_name":"v1.0"}]""")
        assertTrue(result.isFailure)
        assertEquals(
            "The update server's reply was not in the expected form.",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun anEmptyBodyIsTreatedAsNoNotes() {
        val json = """{"tag_name":"v1.0","html_url":"https://x","body":""}"""
        assertNull(ReleaseJson.parse(json).getOrThrow().notes)
    }

    @Test
    fun theDisplayVersionFallsBackToTheTag() {
        val json = """{"tag_name":"v1.0","html_url":"https://x"}"""
        assertEquals("1.0", ReleaseJson.parse(json).getOrThrow().displayVersion)
    }
}
