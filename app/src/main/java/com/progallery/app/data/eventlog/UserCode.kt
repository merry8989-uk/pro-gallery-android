package com.progallery.app.data.eventlog

import java.security.SecureRandom

/**
 * The readable code every install gets, so rows from one device group together.
 *
 * Format `PG-XXX-XXX`, for example `PG-4F9-K2Q`. Two decisions in that:
 *
 * **The alphabet leaves out the characters people confuse.** Crockford's set drops I, L, O and U,
 * so a code read aloud or typed from a screenshot cannot turn into a different one. That matters
 * because the code's whole job is to be quoted by a person in a message.
 *
 * **It is random, and that is all it is.** Not derived from the device, the account, the phone
 * number or anything else about the person; not an advertising id and not a hardware id. Delete
 * the app and the next install gets a different code, which is the property that keeps it a
 * pseudonym rather than an identifier.
 *
 * Pure, so the format and its validation are unit tested.
 */
object UserCode {

    const val PREFIX = "PG"

    /** Crockford base32: no I, L, O or U, so nothing can be misread. */
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** Characters per group, and groups per code: `PG-XXX-XXX`. */
    private const val GROUP = 3
    private const val GROUPS = 2

    private const val LENGTH = GROUP * GROUPS

    /** The code for a given source of random bytes. Deterministic, so it can be tested. */
    fun from(random: ByteArray): String {
        val builder = StringBuilder(PREFIX)
        random.forEachIndexed { index, byte ->
            if (index >= LENGTH) return@forEachIndexed
            if (index % GROUP == 0) builder.append('-')
            // `and 0xFF` because a Kotlin Byte is signed; without it a byte above 127 would
            // index backwards into the alphabet.
            builder.append(ALPHABET[(byte.toInt() and 0xFF) % ALPHABET.length])
        }
        return builder.toString()
    }

    /** A fresh code from a secure source. */
    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(LENGTH)
        random.nextBytes(bytes)
        return from(bytes)
    }

    /**
     * Whether a string is one of our codes.
     *
     * Case is not held against it: a person copying a code may well lower-case it, and a code
     * rejected for that reason is a support question nobody needs.
     */
    fun isValid(code: String): Boolean {
        val normalised = code.trim().uppercase()
        if (!normalised.startsWith("$PREFIX-")) return false
        val body = normalised.removePrefix("$PREFIX-")
        val groups = body.split('-')
        if (groups.size != GROUPS) return false
        return groups.all { group ->
            group.length == GROUP && group.all { it in ALPHABET }
        }
    }

    /** The code as it should be shown and stored. */
    fun normalise(code: String): String = code.trim().uppercase()
}
