package com.progallery.app.util

/**
 * The app's name, where a `Context` is not available to ask for it.
 *
 * `@string/app_name` is the real source — it is what the launcher and every screen read. But a pure
 * object has no context, and two of the places the name appears are pure: the header of an exported
 * usage file, and the message explaining why a settings file was rejected. Neither can call
 * `stringResource`.
 *
 * Two sources for one name is a drift waiting to happen, so `tools/check_docs.py` **asserts they are
 * equal** and fails the build if they are not. That is the whole reason this constant is allowed to
 * exist rather than the name being typed into those two files.
 */
object AppIdentity {
    const val NAME = "Advanced File Manager"
}
