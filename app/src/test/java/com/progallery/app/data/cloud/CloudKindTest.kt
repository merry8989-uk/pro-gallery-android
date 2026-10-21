package com.progallery.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tapping a file has to open the right in-app viewer, and providers are inconsistent about
 * the MIME type they report — so the fallback to the name is the part that matters.
 */
class CloudKindTest {

    @Test
    fun aFolderIsAFolder() {
        assertEquals(CloudKind.FOLDER, CloudKinds.of("", "Photos", isDirectory = true))
        // Even when the provider reports a type for it, a directory is a directory.
        assertEquals(
            CloudKind.FOLDER,
            CloudKinds.of("resource/folder", "Photos", isDirectory = true),
        )
    }

    @Test
    fun theReportedMimeTypeIsUsedWhenItSaysSomething() {
        assertEquals(CloudKind.IMAGE, CloudKinds.of("image/jpeg", "a", false))
        assertEquals(CloudKind.VIDEO, CloudKinds.of("video/mp4", "a", false))
        assertEquals(CloudKind.AUDIO, CloudKinds.of("audio/mpeg", "a", false))
    }

    @Test
    fun aMimeTypeWithParametersIsStillRead() {
        assertEquals(CloudKind.IMAGE, CloudKinds.of("image/jpeg; charset=binary", "a", false))
    }

    @Test
    fun theMimeTypeCaseDoesNotMatter() {
        assertEquals(CloudKind.IMAGE, CloudKinds.of("IMAGE/PNG", "a", false))
    }

    @Test
    fun aGenericMimeTypeFallsBackToTheName() {
        // Providers really do report this for everything, which is why the name is consulted.
        listOf("application/octet-stream", "binary/octet-stream", "", "resource/folder")
            .forEach { generic ->
                assertEquals("$generic should fall back", CloudKind.IMAGE, CloudKinds.of(generic, "holiday.jpg", false))
                assertEquals("$generic should fall back", CloudKind.VIDEO, CloudKinds.of(generic, "clip.MP4", false))
                assertEquals("$generic should fall back", CloudKind.AUDIO, CloudKinds.of(generic, "song.opus", false))
            }
    }

    @Test
    fun somethingUnrecognisedIsNeitherPromisedNorGuessed() {
        assertEquals(CloudKind.OTHER, CloudKinds.of("application/zip", "archive.zip", false))
        assertEquals(CloudKind.OTHER, CloudKinds.of("", "noextension", false))
        assertEquals(CloudKind.OTHER, CloudKinds.of("", "trailing.", false))
        assertEquals(CloudKind.OTHER, CloudKinds.of("", "", false))
    }

    @Test
    fun foldersComeFirstThenNamesCaseInsensitively() {
        val documents = listOf(
            doc("zebra.jpg"),
            doc("Apple", isDirectory = true),
            doc("banana.jpg"),
            doc("Mango", isDirectory = true),
        )
        assertEquals(
            listOf("Apple", "Mango", "banana.jpg", "zebra.jpg"),
            CloudOrdering.sort(documents).map { it.name },
        )
    }

    private fun doc(name: String, isDirectory: Boolean = false) = CloudDocument(
        uri = "content://provider/document/$name",
        documentId = name,
        name = name,
        mimeType = if (isDirectory) "resource/folder" else "image/jpeg",
        sizeBytes = 0L,
        lastModifiedMillis = null,
        isDirectory = isDirectory,
    )
}
