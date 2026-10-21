package com.progallery.app.data.update

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/**
 * Reads the project's latest release from its **Atom feed**.
 *
 * Why this exists at all: the update check originally asked the GitHub API, which allows only
 * a small number of unauthenticated requests **per address** per hour. Mobile networks in
 * India put thousands of subscribers behind one address, so that budget is often already spent
 * by strangers — and the app reported "rate-limiting this device", which is a confusing way to
 * say "someone else used up the allowance". The Atom feed is an ordinary web page rather than
 * an API call, so it is not subject to that budget at all. The API remains as a fallback.
 *
 * The APK's address is not in the feed, but it does not need to be: a release's download URL
 * is deterministic from its tag. The tag is read from the release page's address.
 *
 * Namespace-tolerant, like the WebDAV reader: elements are matched on their local names,
 * because feeds are written by many hands.
 */
object ReleaseAtom {

    fun parse(xml: String, owner: String, repo: String): Result<UpdateRelease> {
        val document = try {
            DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(InputSource(StringReader(xml)))
        } catch (t: Exception) {
            return Result.failure(
                IllegalArgumentException("The release feed could not be read.", t),
            )
        }

        val entries = document.getElementsByTagNameNS("*", "entry")
        if (entries.length == 0) {
            // An empty feed is a valid answer: nothing has been published yet.
            return Result.failure(IllegalArgumentException("The release feed lists no releases."))
        }

        // The feed is newest first, so the first entry is the latest release.
        val entry = entries.item(0) as? Element
            ?: return Result.failure(IllegalArgumentException("The release feed's first entry could not be read."))

        val pageUrl = links(entry).firstOrNull { it.first == "alternate" }?.second
            ?: links(entry).firstOrNull()?.second
            ?: return Result.failure(IllegalArgumentException("The latest release has no page address."))

        val tag = pageUrl.trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalArgumentException("The latest release has no version tag."))

        return Result.success(
            UpdateRelease(
                tagName = tag,
                versionName = tag.removePrefix("v").removePrefix("V"),
                title = childText(entry, "title"),
                notes = childText(entry, "content")?.let(::plainText),
                pageUrl = pageUrl,
                // Deterministic from the tag, which is why the feed needs no asset list.
                apkUrl = "https://github.com/$owner/$repo/releases/download/$tag/pro-gallery-debug.apk",
                prerelease = false,
            ),
        )
    }

    /** The `(rel, href)` pairs of an entry's links. */
    private fun links(entry: Element): List<Pair<String, String>> {
        val found = ArrayList<Pair<String, String>>()
        val children = entry.getElementsByTagNameNS("*", "link")
        for (index in 0 until children.length) {
            val link = children.item(index) as? Element ?: continue
            val href = link.getAttribute("href").takeIf { it.isNotBlank() } ?: continue
            found += link.getAttribute("rel").ifBlank { "alternate" } to href
        }
        return found
    }

    private fun childText(parent: Element, localName: String): String? {
        val children = parent.getElementsByTagNameNS("*", localName)
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            val text = child.textContent
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    /**
     * Turns the feed's HTML into something readable in a dialog.
     *
     * The notes arrive as markup — `<p>`, `<strong>`, entities — and showing that raw would be
     * worse than showing nothing.
     */
    internal fun plainText(html: String): String {
        val withoutTags = html.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("</li\\s*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
        return unescape(withoutTags).trim()
    }

    private fun unescape(text: String): String {
        val named = mapOf(
            "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"",
            "&#39;" to "'", "&apos;" to "'", "&nbsp;" to " ",
        )
        var result = text
        named.forEach { (entity, character) -> result = result.replace(entity, character) }
        // Numeric entities, decimal and hexadecimal.
        result = Regex("&#(\\d+);").replace(result) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
        }
        return Regex("&#x([0-9a-fA-F]+);").replace(result) { match ->
            match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: match.value
        }
    }

    /** Kept for clarity in tests: the node type that matters. */
    internal fun isElement(node: Node): Boolean = node.nodeType == Node.ELEMENT_NODE
}
