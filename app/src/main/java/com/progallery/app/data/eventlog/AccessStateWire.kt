package com.progallery.app.data.eventlog

import com.progallery.app.data.permissions.AccessState

/**
 * The schema's names for the app's access states.
 *
 * A mapping rather than a second enum, because the app already has `AccessState` and two enums
 * for one idea drift apart. The wire names are the schema's, and they are fixed — an access log
 * that spells a state differently from the standard cannot be joined with anything.
 *
 * One gap, stated rather than papered over: the schema also names `permanently_denied`, and the
 * app's enum does not distinguish it from `denied`. Telling them apart needs
 * `shouldShowRequestPermissionRationale`, which is a change to the permission layer rather than
 * to logging, so it is not pretended here.
 */
fun AccessState.wire(): String = when (this) {
    AccessState.GRANTED -> "granted"
    AccessState.PARTIAL -> "partial"
    AccessState.DENIED -> "denied"
    AccessState.NOT_APPLICABLE -> "not_applicable"
}

fun accessStateOfWire(value: String): AccessState? = when (value.trim().lowercase()) {
    "granted" -> AccessState.GRANTED
    "partial" -> AccessState.PARTIAL
    "denied" -> AccessState.DENIED
    "not_applicable" -> AccessState.NOT_APPLICABLE
    else -> null
}
