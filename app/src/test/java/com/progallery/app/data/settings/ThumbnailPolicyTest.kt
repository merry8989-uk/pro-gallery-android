package com.progallery.app.data.settings

import com.progallery.app.TestMedia
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-type switches have to mean something, so which kinds may show a real
 * preview is pinned down here.
 */
class ThumbnailPolicyTest {

    // TestMedia builds a MediaItem without Uri.parse, which a JVM test cannot use.
    private val image: MediaItem = TestMedia.item(id = 1, name = "a.jpg", type = MediaType.IMAGE)
    private val video: MediaItem = TestMedia.item(id = 2, name = "a.mp4", type = MediaType.VIDEO)
    private val audio: MediaItem = TestMedia.item(id = 3, name = "a.mp3", type = MediaType.AUDIO)
    private val document: MediaItem = TestMedia.item(id = 4, name = "a.pdf", type = MediaType.DOCUMENT)
    private val archive: MediaItem = TestMedia.item(id = 5, name = "a.zip", type = MediaType.ARCHIVE)

    @Test
    fun withThumbnailsOffNothingGetsARealPreview() {
        val policy = ThumbnailPolicy(generateThumbnails = false)
        listOf(image, video, audio, document, archive).forEach {
            assertFalse("${it.mediaType} should not preview", policy.allows(it))
        }
    }

    @Test
    fun imagesFollowTheMasterSwitchOnly() {
        val policy = ThumbnailPolicy(
            videoThumbnails = false,
            audioArtwork = false,
            documentPreviews = false,
        )
        assertTrue("the per-type switches are not about images", policy.allows(image))
    }

    @Test
    fun eachPerTypeSwitchControlsItsOwnKind() {
        assertFalse(ThumbnailPolicy(videoThumbnails = false).allows(video))
        assertTrue(ThumbnailPolicy(videoThumbnails = false).allows(audio))
        assertFalse(ThumbnailPolicy(audioArtwork = false).allows(audio))
        assertTrue(ThumbnailPolicy(audioArtwork = false).allows(document))
        assertFalse(ThumbnailPolicy(documentPreviews = false).allows(document))
    }

    @Test
    fun archivesAndUnknownTypesNeverPreview() {
        val policy = ThumbnailPolicy()
        assertFalse(policy.allows(archive))
    }

    @Test
    fun audioAndDocumentsAreTheEmbeddedCases() {
        val policy = ThumbnailPolicy()
        assertTrue("audio artwork is not the file's pixels", policy.needsEmbeddedPreview(audio))
        assertTrue("a PDF page has to be rendered", policy.needsEmbeddedPreview(document))
        assertFalse("an image is its own pixels", policy.needsEmbeddedPreview(image))
        assertFalse(policy.needsEmbeddedPreview(video))
    }

    @Test
    fun anEmbeddedPreviewIsNeverClaimedWhenItIsNotAllowed() {
        val policy = ThumbnailPolicy(audioArtwork = false)
        assertFalse(policy.needsEmbeddedPreview(audio))
    }

    @Test
    fun thePolicyIsBuiltFromSettings() {
        val settings = AppSettings.Defaults.copy(
            generateThumbnails = false,
            videoThumbnails = true,
            audioArtwork = true,
            documentPreviews = true,
        )
        // Settings are resolved before they are stored, so build the policy from a
        // resolved value the way the app does.
        val resolved = SettingsDependencies.resolve(settings)
        val policy = ThumbnailPolicy.from(resolved)
        assertFalse(policy.allows(image))
        assertFalse(policy.allows(video))
    }
}
