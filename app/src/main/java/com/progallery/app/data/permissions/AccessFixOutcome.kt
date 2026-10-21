package com.progallery.app.data.permissions

import com.progallery.app.data.eventlog.FixOutcome

/**
 * Whether an access fix actually fixed anything.
 *
 * This is the half that was missing. The app logged that the user asked for a fix and never
 * logged what came of it, so a report could show **71 attempts and no explanation** — the log
 * proved a loop without saying what was looping.
 *
 * The rule, stated once so the screen and the log cannot disagree:
 *
 * - **success** — the access is granted now, and was not before. The attempt did its job.
 * - **no_op** — the state is exactly as it was. The tap changed nothing, which is the single
 *   most useful thing to know about a loop.
 * - **failed** — the state moved and still is not granted, or it went backwards. The attempt
 *   did something and did not achieve it.
 *
 * `state_before` and `state_after` are logged alongside, so the outcome can be checked rather
 * than believed.
 */
object AccessFixOutcome {

    fun of(before: AccessState, after: AccessState): FixOutcome = when {
        after == AccessState.GRANTED && before != AccessState.GRANTED -> FixOutcome.SUCCESS
        after == before -> FixOutcome.NO_OP
        else -> FixOutcome.FAILED
    }
}
