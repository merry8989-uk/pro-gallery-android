package com.progallery.app.data.eventlog

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which screen the person is on, for the crash handler to name.
 *
 * A crash line that says *"IllegalArgumentException"* and nothing else cannot be acted on. The one
 * piece of context that turns it into something actionable is **where the person was** — a crash in
 * the photo editor and a crash in the archive screen are different problems with the same type name.
 *
 * The navigation host sets this as it moves, and it is deliberately tiny: it is read from an
 * uncaught-exception handler, which must not depend on anything that could itself be broken.
 */
@Singleton
class CurrentScreen @Inject constructor() {

    @Volatile
    private var screen: String? = null

    fun set(name: String?) {
        screen = name
    }

    fun get(): String? = screen
}
