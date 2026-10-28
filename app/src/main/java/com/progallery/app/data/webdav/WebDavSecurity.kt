package com.progallery.app.data.webdav

/**
 * Whether a WebDAV request may carry credentials.
 *
 * Basic authentication **encodes** a username and password; it does not encrypt them. Over plain
 * HTTP they are readable by anyone on the network — a café's Wi-Fi, a hotel, a shared office. The
 * client used to attach the header to whatever address it was given.
 *
 * Pure, so the rule is tested rather than trusted, and so it can be applied before any socket is
 * opened.
 */
object WebDavSecurity {

    /** What a request is allowed to do. */
    sealed interface Verdict {
        data object Allowed : Verdict
        data class Refused(val reason: String) : Verdict
    }

    /**
     * Whether [url] may carry [hasCredentials].
     *
     * Two rules, and the second is the one people forget:
     *
     * - **Credentials need HTTPS.** No exceptions, no "just this once", no silent downgrade.
     * - **A redirect to another host drops them.** A server does not get to forward somebody's
     *   password to a server it names.
     */
    fun check(url: String, hasCredentials: Boolean): Verdict {
        if (!hasCredentials) return Verdict.Allowed
        if (isHttps(url)) return Verdict.Allowed
        return Verdict.Refused(
            "This server's address is not HTTPS. The app will not send a username and password " +
                "over an unencrypted connection — anyone on the network could read them. Use an " +
                "https:// address, or remove the credentials.",
        )
    }

    /** Whether two addresses are the same host, which is the only case a redirect may keep them. */
    fun sameHost(from: String, to: String): Boolean = runCatching {
        val a = java.net.URL(from).host
        val b = java.net.URL(to).host
        a != null && b != null && a.equals(b, ignoreCase = true)
    }.getOrDefault(false)

    fun isHttps(url: String): Boolean =
        runCatching { java.net.URL(url).protocol.equals("https", ignoreCase = true) }
            .getOrDefault(false)
}
