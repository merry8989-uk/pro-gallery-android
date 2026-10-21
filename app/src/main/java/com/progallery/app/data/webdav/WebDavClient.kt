package com.progallery.app.data.webdav

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Talks to a WebDAV server.
 *
 * Plain HTTP on purpose: `HttpURLConnection` supports the `PROPFIND` method and Basic
 * authentication with no extra dependency, and WebDAV is not an area where a large client
 * library buys much.
 *
 * Two honest limits, both stated in the UI as well:
 *
 *  * **Basic authentication only.** That is what the great majority of WebDAV servers
 *    offer, and it is what the user supplies. No OAuth, no certificates.
 *  * **A self-signed certificate is refused** — the platform's default trust decides. The
 *    app does not weaken TLS to make a server connect.
 */
@Singleton
class WebDavClient @Inject constructor() {

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000

        /** Ask only for what the app shows. */
        val PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:displayname/>
                <d:getcontentlength/>
                <d:getcontenttype/>
                <d:getlastmodified/>
                <d:resourcetype/>
              </d:prop>
            </d:propfind>
        """.trimIndent()
    }

    /** Lists a collection. [url] must be the collection's own address. */
    suspend fun list(
        server: WebDavServer,
        password: String,
        url: String,
    ): Result<List<WebDavEntry>> = withContext(Dispatchers.IO) {
        val collection = WebDavXml.asCollection(url)
        val connection = try {
            (URL(collection).openConnection() as HttpURLConnection)
        } catch (t: IOException) {
            return@withContext Result.failure(
                IllegalArgumentException("That address could not be reached.", t),
            )
        }

        try {
            connection.requestMethod = "PROPFIND"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Depth", "1")
            connection.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
            if (server.username.isNotEmpty() || password.isNotEmpty()) {
                connection.setRequestProperty("Authorization", basicAuth(server.username, password))
            }
            connection.doOutput = true
            connection.outputStream.use { it.write(PROPFIND_BODY.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code != HTTP_MULTI_STATUS) {
                return@withContext Result.failure(IllegalArgumentException(describe(code)))
            }

            val body = connection.inputStream.use { it.readBytes().decodeToString() }
            WebDavXml.parseListing(body, collection)
        } catch (t: IOException) {
            Result.failure(IllegalArgumentException("The server did not answer: ${t.message}", t))
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /** What each reply code means for this app, in words worth reading. */
    private fun describe(code: Int): String = when (code) {
        401 -> "The username or password was refused."
        403 -> "The server refused access to that folder."
        404 -> "That folder was not found on the server."
        405, 501 -> "That address does not accept WebDAV requests."
        HTTP_MULTI_STATUS -> "The server's reply was not understood."
        else -> "The server answered with $code."
    }

    private fun basicAuth(username: String, password: String): String {
        val raw = "$username:$password".toByteArray(Charsets.UTF_8)
        return "Basic " + Base64.getEncoder().encodeToString(raw)
    }

    private const val HTTP_MULTI_STATUS = 207
}
