package com.progallery.app.data.webdav

/** A server together with its password, as held in local storage. */
data class StoredWebDavServer(
    val server: WebDavServer,
    val password: String,
)

/**
 * Reads and writes the stored servers.
 *
 * One server per line, fields separated by a tab. Every field is stripped of tabs and
 * newlines on the way in, so a value can never break the format it lives in — which is
 * the whole risk with a line-based store.
 *
 * Pure, so the round trip and the escaping are unit tested.
 *
 * **The password lives here and nowhere else.** It is not part of `AppSettings`, so it
 * cannot reach the settings export, and it is never written to the diagnostics log.
 */
object WebDavServerCodec {

    private const val FIELD = "\t"
    private const val FIELDS = 5

    fun encode(servers: List<StoredWebDavServer>): String =
        servers.joinToString("\n") { stored ->
            listOf(
                stored.server.id,
                stored.server.name,
                stored.server.url,
                stored.server.username,
                stored.password,
            ).joinToString(FIELD) { sanitize(it) }
        }

    fun decode(raw: String): List<StoredWebDavServer> = raw
        .split("\n")
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split(FIELD)
            // A line that is not exactly the shape we wrote is skipped rather than
            // guessed at, so a corrupted store loses one entry, not all of them.
            if (parts.size != FIELDS) return@mapNotNull null
            val id = parts[0].trim()
            val url = parts[2].trim()
            if (id.isEmpty() || url.isEmpty()) return@mapNotNull null
            StoredWebDavServer(
                server = WebDavServer(
                    id = id,
                    name = parts[1].ifBlank { url },
                    url = url,
                    username = parts[3],
                ),
                password = parts[4],
            )
        }

    /** Nothing may carry a tab or a newline into the store, or the format breaks. */
    private fun sanitize(value: String): String =
        value.replace(FIELD, " ").replace("\n", " ").replace("\r", " ")
}
