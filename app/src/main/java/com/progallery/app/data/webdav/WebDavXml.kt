package com.progallery.app.data.webdav

import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/**
 * Reads a WebDAV `PROPFIND` reply, and resolves the addresses inside it.
 *
 * Pure — it takes the server's XML as a string and returns entries — so the whole
 * parsing decision is unit tested against real reply shapes, without a server.
 *
 * Two tolerances, both deliberate, because WebDAV servers differ:
 *
 *  * **Elements are matched by local name, ignoring the namespace prefix.** The same
 *    reply may arrive as `d:response`, `D:response`, `lp1:response` or with no prefix at
 *    all; the prefix is not the server's contract, the name is.
 *  * **A `propstat` without a `200` status is still read** when no `200` one exists, so a
 *    server that reports properties differently does not produce empty entries.
 */
object WebDavXml {

    /** How many entries a single folder listing will hold, so a huge folder cannot flood memory. */
    const val MAX_ENTRIES = 5_000

    fun parseListing(xml: String, baseUrl: String): Result<List<WebDavEntry>> {
        val document = try {
            DocumentBuilderFactory.newInstance()
                .apply {
                    isNamespaceAware = true
                    // The reply is data, not a document: never fetch anything it mentions.
                    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                    isExpandEntityReferences = false
                }
                .newDocumentBuilder()
                .parse(InputSource(StringReader(xml)))
        } catch (t: Exception) {
            return Result.failure(
                IllegalArgumentException("The server's reply could not be read.", t),
            )
        }

        val base = asCollection(baseUrl)
        val responses = document.getElementsByTagNameNS("*", "response")
        val entries = ArrayList<WebDavEntry>()

        for (index in 0 until responses.length) {
            if (entries.size >= MAX_ENTRIES) break
            val response = responses.item(index) as? Element ?: continue
            val entry = readResponse(response, base) ?: continue
            // The first response is normally the collection that was asked for.
            if (entry.url == base || entry.url == base.removeSuffix("/")) continue
            entries += entry
        }

        return Result.success(entries)
    }

    private fun readResponse(response: Element, base: String): WebDavEntry? {
        val href = firstDescendant(response, "href")?.textContent?.trim().orEmpty()
        if (href.isEmpty()) return null

        val url = resolve(base, href)
        val prop = bestProp(response)

        val displayName = prop?.let { firstDescendant(it, "displayname")?.textContent?.trim() }
        val resourceType = prop?.let { firstDescendant(it, "resourcetype") }
        val isDirectory = resourceType != null && hasDescendant(resourceType, "collection")

        val name = displayName?.takeIf { it.isNotBlank() }
            ?: runCatching { URLDecoder.decode(lastSegment(href), "UTF-8") }.getOrDefault(lastSegment(href))

        return WebDavEntry(
            url = url,
            name = name.ifBlank { url },
            isDirectory = isDirectory,
            sizeBytes = prop?.let { firstDescendant(it, "getcontentlength")?.textContent?.trim() }
                ?.toLongOrNull() ?: 0L,
            mimeType = prop?.let { firstDescendant(it, "getcontenttype")?.textContent?.trim() }
                ?.takeIf { it.isNotBlank() },
            lastModifiedMillis = prop
                ?.let { firstDescendant(it, "getlastmodified")?.textContent?.trim() }
                ?.let { parseHttpDate(it) },
        )
    }

    /** The `prop` of the `propstat` that succeeded, or the first one there is. */
    private fun bestProp(response: Element): Element? {
        val propstats = response.getElementsByTagNameNS("*", "propstat")
        var fallback: Element? = null
        for (index in 0 until propstats.length) {
            val propstat = propstats.item(index) as? Element ?: continue
            val prop = firstDescendant(propstat, "prop") ?: continue
            if (fallback == null) fallback = prop
            val status = firstDescendant(propstat, "status")?.textContent.orEmpty()
            if (status.contains(" 200 ") || status.endsWith("200")) return prop
        }
        return fallback
    }

    /**
     * Resolves a reply's `href` against the collection that was asked for.
     *
     * Servers answer with absolute paths, full URLs, or relative paths, all three in the
     * wild, so all three are handled.
     */
    fun resolve(baseUrl: String, href: String): String {
        val base = asCollection(baseUrl)
        val trimmed = href.trim()
        if (trimmed.isEmpty()) return base
        return runCatching { URI(base).resolve(trimmed).toString() }.getOrDefault(trimmed)
    }

    /**
     * The collection above [url], or null when [url] is the server's root.
     *
     * Used for "go up one folder", so it must not walk past the root.
     */
    fun parentOf(url: String): String? {
        val collection = asCollection(url)
        val uri = runCatching { URI(collection) }.getOrNull() ?: return null
        val path = uri.path.orEmpty().trimEnd('/')
        if (path.isEmpty()) return null
        val cut = path.lastIndexOf('/')
        if (cut < 0) return null
        val parentPath = path.substring(0, cut + 1)
        return runCatching {
            URI(uri.scheme, uri.authority, parentPath, null, null).toString()
        }.getOrNull()
    }

    /** Ensures a collection URL ends in "/", which is what makes resolution behave. */
    fun asCollection(url: String): String = if (url.endsWith("/")) url else "$url/"

    private fun parseHttpDate(raw: String): Long? {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM d HH:mm:ss yyyy",
        )
        formats.forEach { pattern ->
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(raw)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun lastSegment(href: String): String =
        href.trimEnd('/').substringAfterLast('/')

    private fun firstDescendant(parent: Element, localName: String): Element? {
        val found = parent.getElementsByTagNameNS("*", localName)
        return found.item(0) as? Element
    }

    private fun hasDescendant(parent: Element, localName: String): Boolean =
        parent.getElementsByTagNameNS("*", localName).length > 0

    @Suppress("unused")
    private fun Node.asElement(): Element? = this as? Element
}
