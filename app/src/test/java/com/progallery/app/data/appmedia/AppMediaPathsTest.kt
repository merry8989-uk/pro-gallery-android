package com.progallery.app.data.appmedia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which app a file belongs to is read from where it is, never guessed from a list.
 */
class AppMediaPathsTest {

    @Test
    fun aFileUnderAndroidMediaBelongsToTheFolderNamedPackage() {
        assertEquals(
            "com.whatsapp",
            AppMediaPaths.packageOf(
                "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/IMG-20260405-WA0001.jpg",
            ),
        )
    }

    @Test
    fun everyMessagingAppIsFoundTheSameWay() {
        // The point of reading the path: an app nobody listed still works.
        val cases = mapOf(
            "/storage/emulated/0/Android/media/org.telegram.messenger/Telegram/Telegram Images/a.jpg" to "org.telegram.messenger",
            "/storage/emulated/0/Android/media/com.aratai.chat/Media/a.jpg" to "com.aratai.chat",
            "/storage/emulated/0/Android/media/com.friendsV/Media/a.jpg" to "com.friendsV",
            "/storage/emulated/0/Android/media/com.someone.unknown/Media/a.jpg" to "com.someone.unknown",
        )
        cases.forEach { (path, expected) ->
            assertEquals(path, expected, AppMediaPaths.packageOf(path))
        }
    }

    @Test
    fun aNestedFolderStillResolvesToThePackage() {
        assertEquals(
            "com.whatsapp",
            AppMediaPaths.packageOf(
                "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent/deep/a.jpg",
            ),
        )
    }

    @Test
    fun aFolderWithoutADotIsNotAPackage() {
        // Android/media itself, and any folder that is not a package name, is not an app.
        assertNull(AppMediaPaths.packageOf("/storage/emulated/0/Android/media/"))
        assertNull(AppMediaPaths.packageOf("/storage/emulated/0/Android/media/NotAPackage/a.jpg"))
    }

    @Test
    fun anOrdinaryPictureIsNotAppMedia() {
        assertFalse(AppMediaPaths.isAppMedia("/storage/emulated/0/DCIM/Camera/IMG_0001.jpg"))
        assertFalse(AppMediaPaths.isAppMedia("/storage/emulated/0/Pictures/a.jpg"))
        assertNull(AppMediaPaths.packageOf("/storage/emulated/0/DCIM/Camera/IMG_0001.jpg"))
    }

    @Test
    fun theLegacyWhatsAppFolderIsRecognised() {
        assertEquals(
            "com.whatsapp",
            AppMediaPaths.legacyPackageOf("/storage/emulated/0/WhatsApp/Media/WhatsApp Images/IMG-1.jpg"),
        )
    }

    @Test
    fun theLegacyTelegramFolderIsRecognised() {
        assertEquals(
            "org.telegram.messenger",
            AppMediaPaths.legacyPackageOf("/storage/emulated/0/Telegram/Telegram Images/a.jpg"),
        )
    }

    @Test
    fun aLegacyFolderIsNotRecheckedWhenItIsAlreadyUnderAndroidMedia() {
        // Android/media takes precedence, so a package folder named "WhatsApp" is not also
        // reported as the legacy one.
        assertNull(
            AppMediaPaths.legacyPackageOf(
                "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/a.jpg",
            ),
        )
    }

    @Test
    fun anUnrelatedTopLevelFolderIsNotAnApp() {
        assertNull(AppMediaPaths.legacyPackageOf("/storage/emulated/0/DCIM/Camera/a.jpg"))
        assertNull(AppMediaPaths.legacyPackageOf("/storage/emulated/0/Download/a.jpg"))
    }

    @Test
    fun ownerOfHandlesBothLayouts() {
        assertEquals(
            "com.whatsapp",
            AppMediaPaths.ownerOf("/storage/emulated/0/Android/media/com.whatsapp/Media/a.jpg"),
        )
        assertEquals(
            "com.whatsapp",
            AppMediaPaths.ownerOf("/storage/emulated/0/WhatsApp/Media/a.jpg"),
        )
        assertNull(AppMediaPaths.ownerOf("/storage/emulated/0/DCIM/Camera/a.jpg"))
    }

    @Test
    fun windowsSeparatorsAreTolerated() {
        // Not a real Android path, but the parser should not be defeated by one.
        assertEquals(
            "com.whatsapp",
            AppMediaPaths.packageOf("\\storage\\emulated\\0\\Android\\media\\com.whatsapp\\Media\\a.jpg"),
        )
    }
}
