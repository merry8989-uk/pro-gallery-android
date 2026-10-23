package com.progallery.app.data.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.progallery.app.ui.navigation.Routes
import org.junit.Assert.assertTrue

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
        assertEquals(FeatureId.PLAYER, featureForRoute("media_player"))
        assertEquals(FeatureId.PLAYER, featureForRoute("media_player"))
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
        // Read from `Routes` rather than from a list kept here. The list that used to be in this
        // test claimed to cover "every value in Routes" and could not: adding a route to the app
        // left it silently unchecked, which is the failure it was written to prevent.
        val declared = Routes::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .mapNotNull { field ->
                field.isAccessible = true
                (field.get(null) as? String)?.substringBefore('?')
            }
            .distinct()

        assertTrue("no routes were found at all — the reflection is wrong", declared.size > 20)

        val unmapped = declared.filter { featureForRoute(it) == null }
        assertEquals("these routes are not accounted for: $unmapped", emptyList<String>(), unmapped)
    }
}
