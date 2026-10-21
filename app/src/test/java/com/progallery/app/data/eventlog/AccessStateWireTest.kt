package com.progallery.app.data.eventlog

import com.progallery.app.data.permissions.AccessState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The schema's names for the app's access states.
 *
 * A mapping rather than a second enum: two enums for one idea is how two parts of an app come to
 * disagree, and the wire names are what anything downstream joins on.
 */
class AccessStateWireTest {

    @Test
    fun everyStateHasASchemaName() {
        assertEquals("granted", AccessState.GRANTED.wire())
        assertEquals("partial", AccessState.PARTIAL.wire())
        assertEquals("denied", AccessState.DENIED.wire())
        assertEquals("not_applicable", AccessState.NOT_APPLICABLE.wire())
    }

    @Test
    fun everyStateRoundTrips() {
        AccessState.entries.forEach { state ->
            assertEquals(state, accessStateOfWire(state.wire()))
        }
    }

    @Test
    fun parsingToleratesCaseAndSpace() {
        assertEquals(AccessState.GRANTED, accessStateOfWire("  GRANTED "))
    }

    @Test
    fun anUnknownNameIsNullRatherThanAGuess() {
        // The one the schema names and the app cannot yet produce is not quietly mapped to
        // "denied" — it is reported as unknown, because it is unknown.
        assertNull(accessStateOfWire("permanently_denied"))
        assertNull(accessStateOfWire(""))
        assertNull(accessStateOfWire("nonsense"))
    }

    @Test
    fun everyWireNameIsDistinct() {
        val names = AccessState.entries.map { it.wire() }
        assertEquals(names.size, names.toSet().size)
    }
}
