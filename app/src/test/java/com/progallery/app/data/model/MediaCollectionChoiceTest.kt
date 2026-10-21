package com.progallery.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The content URI has to be built from what MediaStore says a row is, not from what the
 * app calls it. Getting this wrong produces a file that is listed and then will not open —
 * which is exactly what used to happen to WebP files MediaStore did not classify as media.
 */
class MediaCollectionChoiceTest {

    @Test
    fun anImageRowIsAddressedThroughTheImagesView() {
        assertEquals(
            MediaStoreCollection.IMAGES,
            MediaCollectionChoice.forRow(MediaCollectionChoice.TYPE_IMAGE),
        )
    }

    @Test
    fun aVideoRowIsAddressedThroughTheVideoView() {
        assertEquals(
            MediaStoreCollection.VIDEO,
            MediaCollectionChoice.forRow(MediaCollectionChoice.TYPE_VIDEO),
        )
    }

    @Test
    fun anAudioRowIsAddressedThroughTheAudioView() {
        assertEquals(
            MediaStoreCollection.AUDIO,
            MediaCollectionChoice.forRow(MediaCollectionChoice.TYPE_AUDIO),
        )
    }

    @Test
    fun aRowMediaStoreDoesNotCallMediaIsAddressedThroughTheUnionView() {
        // This is the fix. A row with no media type is in none of the three media views,
        // so only the union view resolves for it.
        assertEquals(MediaStoreCollection.FILES, MediaCollectionChoice.forRow(0))
    }

    @Test
    fun anUnknownMediaTypeAlsoFallsBackToTheUnionView() {
        // Never guess an address: anything unrecognised still has one that resolves.
        listOf(-1, 4, 7, 99).forEach { hint ->
            assertEquals(
                "media_type $hint should use the union view",
                MediaStoreCollection.FILES,
                MediaCollectionChoice.forRow(hint),
            )
        }
    }

    @Test
    fun theChoiceTakesOnlyTheRowsOwnMediaType() {
        // The signature is the guarantee: there is no way to pass the app's display kind
        // into this decision, so the two can never disagree again.
        val methods = MediaCollectionChoice::class.java.declaredMethods
            .filterNot { it.isSynthetic }
            .map { it.name to it.parameterTypes.toList() }
        assertEquals(1, methods.size)
        assertEquals("forRow", methods.single().first)
        assertEquals(listOf(Int::class.javaPrimitiveType), methods.single().second)
    }
}
