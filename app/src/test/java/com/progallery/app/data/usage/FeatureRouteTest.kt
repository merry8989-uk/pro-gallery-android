package com.progallery.app.data.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A screen recorded under the wrong feature would make the whole seven-day review wrong without
 * anything looking broken, so the mapping is pinned.
 */
class FeatureRouteTest {

    @Test
    fun aScreenNamedAfterItsFeatureMapsToItself() {
        assertEquals(FeatureId.PHOTOS, featureForRoute("photos"))
        assertEquals(FeatureId.VIDEOS, featureForRoute("videos"))
        assertEquals(FeatureId.MEMORIES, featureForRoute("memories"))
        assertEquals(FeatureId.APP_MEDIA, featureForRoute("app_media"))
        assertEquals(FeatureId.DEEP_SCAN, featureForRoute("deep_scan"))
    }

    @Test
    fun aRouteWithArgumentsMapsToItsScreen() {
        assertEquals(FeatureId.VIEWER, featureForRoute("viewer?uri=content%3A%2F%2Fmedia%2F1"))
        assertEquals(FeatureId.MEMORIES, featureForRoute("memories/"))
    }

    @Test
    fun viewersBelongToTheFeatureThatOpenedThem() {
        assertEquals(FeatureId.VIEWER, featureForRoute("pdf_viewer"))
        assertEquals(FeatureId.VIEWER, featureForRoute("text_viewer"))
        assertEquals(FeatureId.PLAYER, featureForRoute("video_player"))
        assertEquals(FeatureId.PLAYER, featureForRoute("audio_player"))
        assertEquals(FeatureId.PLAYER, featureForRoute("stream_player"))
        assertEquals(FeatureId.EDITOR, featureForRoute("photo_editor"))
    }

    @Test
    fun anAlbumDetailBelongsToAlbums() {
        assertEquals(FeatureId.ALBUMS, featureForRoute("album_detail"))
        assertEquals(FeatureId.ALBUMS, featureForRoute("custom_album"))
    }

    @Test
    fun aRouteThatIsNotAPlaceMapsToNothing() {
        assertNull(featureForRoute(null))
        assertNull(featureForRoute(""))
        assertNull(featureForRoute("   "))
        assertNull(featureForRoute("something_unknown"))
    }

    @Test
    fun everyRouteTheAppHasEitherMapsOrIsDeliberatelyNotAPlace() {
        // Every value in Routes must be accounted for, so a new screen cannot silently go
        // unrecorded or, worse, be mapped to the wrong feature by accident.
        val routes = listOf(
            "home", "photos", "albums", "audio", "documents", "all_files", "settings",
            "permission_diagnostics", "android_folders", "recovery", "pdf_viewer", "audio_player",
            "audio_editor", "videos", "favourites", "trash", "deep_scan", "search", "stream",
            "stream_player", "memories", "app_media", "text_viewer", "cloud", "cloud_viewer",
            "webdav", "webdav_player", "viewer", "video_player", "photo_editor", "video_editor",
            "album_detail", "custom_album", "stickers",
        )
        val unmapped = routes.filter { featureForRoute(it) == null }
        assertEquals("these routes are not accounted for: $unmapped", emptyList<String>(), unmapped)
    }
}
