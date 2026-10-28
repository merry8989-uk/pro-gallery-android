package com.progallery.app.data.webdav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule from the audit's P0-3: credentials never travel unencrypted.
 *
 * Basic authentication encodes a password; it does not encrypt it. The client attached the header to
 * whatever address it was given, and followed redirects with it — including to another host.
 */
class WebDavSecurityTest {

    @Test
    fun anHttpsAddressMayCarryCredentials() {
        assertTrue(
            WebDavSecurity.check("https://nas.example.com/dav/", hasCredentials = true)
                is WebDavSecurity.Verdict.Allowed,
        )
    }

    @Test
    fun aPlainHttpAddressMayNot() {
        val verdict = WebDavSecurity.check("http://nas.example.com/dav/", hasCredentials = true)
        assertTrue("credentials over HTTP must be refused", verdict is WebDavSecurity.Verdict.Refused)
        assertTrue(
            "and the reason should say why",
            (verdict as WebDavSecurity.Verdict.Refused).reason.contains("not HTTPS"),
        )
    }

    @Test
    fun httpIsFineWhenThereAreNoCredentials() {
        // A public share with no password is not sending anything secret, so it is not refused —
        // the rule is about credentials, not about encryption for its own sake.
        assertTrue(
            WebDavSecurity.check("http://nas.example.com/dav/", hasCredentials = false)
                is WebDavSecurity.Verdict.Allowed,
        )
    }

    @Test
    fun aRedirectToAnotherHostIsNotTheSameHost() {
        assertFalse(
            WebDavSecurity.sameHost("https://nas.example.com/dav/", "https://elsewhere.example.org/dav/"),
        )
    }

    @Test
    fun aRedirectOnTheSameHostIs() {
        assertTrue(
            WebDavSecurity.sameHost("https://nas.example.com/dav/", "https://NAS.example.com/dav/moved/"),
        )
    }

    @Test
    fun nonsenseIsTreatedAsInsecureRatherThanAllowed() {
        // A relative or malformed address cannot be shown to be HTTPS, so it is not one.
        assertFalse(WebDavSecurity.isHttps("/dav/"))
        assertFalse(WebDavSecurity.isHttps(""))
        assertFalse(WebDavSecurity.sameHost("nonsense", "also nonsense"))
    }
}
