package com.progallery.app.util

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] that never swallows [CancellationException].
 *
 * The standard `runCatching` catches `Throwable`, which includes
 * `CancellationException`. In a coroutine that means a cancelled operation is
 * turned into an ordinary failure: the caller reports an error, and worse, code
 * that "cleans up on failure" runs even though the user simply stopped the work.
 * Cancellation must always propagate.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

/**
 * True when [block] completed, false when it failed for an ordinary reason.
 * A [CancellationException] is rethrown so cancellation keeps working.
 */
inline fun ignoreUnlessCancelled(block: () -> Unit): Boolean =
    try {
        block()
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        false
    }
