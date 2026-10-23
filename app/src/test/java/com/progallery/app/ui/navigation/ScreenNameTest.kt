package com.progallery.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The screen names, which a real device showed were being split in two.
 *
 * The log had `settings` and `Settings`, `audio` and `Audio`, `photos` and `Photos`, `documents` and
 * `Documents` — the navigation host logging the route while two screens logged a display name. Every
 * count the weekly review is built from was therefore halved, so the data used to decide what to
 * improve was itself the thing most in need of it.
 */
class ScreenNameTest {

    @Test
    fun everyRouteHasExactlyOneName() {
        val names = ScreenName.all()
        assertEquals("every name must be distinct", names.size, names.values.toSet().size)
        names.forEach { (route, name) ->
            assertEquals("$route must map to itself", name, ScreenName.of(route))
            assertTrue("a name should not be blank", name.isNotBlank())
        }
    }

    @Test
    fun aRouteWithArgumentsIsMatchedOnTheRouteAlone() {
        // `recovery?start={start}` is the same screen whichever way it was opened.
        assertEquals(ScreenName.of(Routes.RECOVERY), ScreenName.of("${Routes.RECOVERY}?start=true"))
    }

    @Test
    fun anUnknownRouteRecordsNothingRatherThanGuessing() {
        assertNull(ScreenName.of("not_a_screen"))
        assertNull(ScreenName.of(null))
        assertNull(ScreenName.of(""))
    }

    @Test
    fun theNamesAreTheOnesAPersonWouldRecognise() {
        // Not the route strings: this is what appears in a log somebody might read.
        assertEquals("AccessDiagnostics", ScreenName.of(Routes.PERMISSION_DIAGNOSTICS))
        assertEquals("Settings", ScreenName.of(Routes.SETTINGS))
        assertEquals("AllFiles", ScreenName.of(Routes.ALL_FILES))
    }
}
