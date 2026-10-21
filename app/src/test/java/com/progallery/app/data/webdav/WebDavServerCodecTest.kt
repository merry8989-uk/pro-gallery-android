package com.progallery.app.data.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The store is line-based, so the risk is a value breaking the format it lives in. That,
 * and the round trip, is what this pins down.
 */
class WebDavServerCodecTest {

    private fun stored(id: String = "1", name: String = "Home", password: String = "secret") =
        StoredWebDavServer(
            server = WebDavServer(
                id = id,
                name = name,
                url = "https://cloud.example.com/dav/",
                username = "alice",
            ),
            password = password,
        )

    @Test
    fun oneServerSurvivesARoundTrip() {
        val original = listOf(stored())
        assertEquals(original, WebDavServerCodec.decode(WebDavServerCodec.encode(original)))
    }

    @Test
    fun severalServersSurviveARoundTrip() {
        val original = listOf(stored(id = "1"), stored(id = "2", name = "Work"), stored(id = "3", name = "NAS"))
        assertEquals(original, WebDavServerCodec.decode(WebDavServerCodec.encode(original)))
    }

    @Test
    fun aPasswordWithSpacesSurvives() {
        val original = listOf(stored(password = "correct horse battery staple"))
        assertEquals(original, WebDavServerCodec.decode(WebDavServerCodec.encode(original)))
    }

    @Test
    fun aNewlineInAValueCannotBreakTheFormat() {
        // Without stripping, this password would look like a second server.
        val original = listOf(stored(password = "pass\nword"))
        val decoded = WebDavServerCodec.decode(WebDavServerCodec.encode(original))
        assertEquals("one entry, not two", 1, decoded.size)
        assertEquals("pass word", decoded.single().password)
    }

    @Test
    fun aTabInAValueCannotShiftTheFields() {
        val original = listOf(stored(name = "My\tServer"))
        val decoded = WebDavServerCodec.decode(WebDavServerCodec.encode(original))
        assertEquals(1, decoded.size)
        assertEquals("My Server", decoded.single().server.name)
        assertEquals("the password must not have moved", "secret", decoded.single().password)
    }

    @Test
    fun anEmptyStoreIsAnEmptyList() {
        assertTrue(WebDavServerCodec.decode("").isEmpty())
        assertTrue(WebDavServerCodec.decode("\n\n  \n").isEmpty())
    }

    @Test
    fun aCorruptedLineIsSkippedRatherThanLosingEverything() {
        val good = WebDavServerCodec.encode(listOf(stored(id = "1")))
        val decoded = WebDavServerCodec.decode("garbage line\n$good\nnot\tenough")
        assertEquals("the readable entry survives", 1, decoded.size)
        assertEquals("1", decoded.single().server.id)
    }

    @Test
    fun aLineWithNoUrlIsSkipped() {
        assertTrue(WebDavServerCodec.decode("1\tName\t\talice\tsecret").isEmpty())
    }

    @Test
    fun anUnnamedServerFallsBackToItsAddress() {
        val decoded = WebDavServerCodec.decode("1\t\thttps://cloud.example.com/dav/\talice\tsecret")
        assertEquals("https://cloud.example.com/dav/", decoded.single().server.name)
    }

    @Test
    fun theEncodedFormDoesNotClaimToBeJson() {
        // It is not a settings export, and must not be mistaken for one.
        val encoded = WebDavServerCodec.encode(listOf(stored()))
        assertTrue(encoded.isNotBlank())
        assertTrue("no JSON braces in a line format", !encoded.contains("{"))
    }
}
