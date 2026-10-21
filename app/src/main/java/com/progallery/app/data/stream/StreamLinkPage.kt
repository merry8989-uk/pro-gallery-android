package com.progallery.app.data.stream

/**
 * Tells a link to a **page** apart from a link to a **file**.
 *
 * This exists because of a real failure the user hit: a `youtu.be` address was pasted into
 * "Stream from link", and the app answered that "the container is not one Android can read —
 * MPEG program streams (.mpg), Windows Media (.wmv)…". Every word of that was wrong for the
 * situation. The address was a web page, and no codec pack changes that.
 *
 * The distinction is worth making **before** playback is attempted, because the answer is
 * knowable in advance and the user should not have to watch a black screen to find it out.
 *
 * It is deliberately a *warning*, never a refusal: the classifier can be wrong about an
 * unusual address, and a link that works must never be blocked by a guess.
 */
object StreamLinkPage {

    /**
     * Hosts whose addresses are pages about a video rather than the video itself.
     *
     * These are the ones that actually come up when someone shares a link. It is not a
     * complete list of the web, and does not need to be.
     */
    private val PAGE_HOSTS = setOf(
        "youtube.com", "youtu.be", "m.youtube.com", "music.youtube.com",
        "vimeo.com", "dailymotion.com", "dai.ly",
        "facebook.com", "fb.watch", "instagram.com", "tiktok.com",
        "twitter.com", "x.com", "twitch.tv", "rumble.com", "odysee.com",
        "drive.google.com", "mega.nz", "mediafire.com", "dropbox.com",
    )

    /** Path pieces that mean "this address is about a video", not "this address is a video". */
    private val PAGE_PATHS = setOf(
        "watch", "shorts", "video", "videos", "reel", "reels", "status", "embed", "clip", "p",
    )

    /** Whether [host] is a page host. Exposed for the screen's wording. */
    fun isPageHost(host: String): Boolean {
        val clean = host.lowercase().removePrefix("www.")
        return clean in PAGE_HOSTS || PAGE_HOSTS.any { clean.endsWith(".$it") }
    }

    /**
     * True when the address looks like a page rather than a file.
     *
     * Called on an already-parsed link, so the scheme and host are known to be sound.
     */
    fun looksLikeAPage(url: String): Boolean {
        // An explicit media extension is stronger evidence than the host's reputation. A file
        // served by a video site is still a file — `vimeo.com/download/clip.mp4` must not be
        // warned about just because of who serves it.
        val kind = StreamLinks.parse(url).getOrNull()?.kind
        if (kind != null && kind != StreamKind.UNKNOWN) return false

        val withoutScheme = url.substringAfter("://", url)
        val host = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if (isPageHost(host)) return true

        val path = withoutScheme.substringAfter('/', "").substringBefore('?').substringBefore('#')
        if (path.isEmpty()) return false
        val firstSegment = path.substringBefore('/').lowercase()
        return firstSegment in PAGE_PATHS
    }

    /**
     * What to say about it.
     *
     * Names the service when it is a known one, because "YouTube cannot be played here" is a
     * far better answer than a general remark about containers.
     */
    fun warning(url: String): String {
        val withoutScheme = url.substringAfter("://", url)
        val host = withoutScheme.substringBefore('/').substringBefore('?')
        val service = when {
            isPageHost(host) -> host.lowercase().removePrefix("www.").substringBefore('.')
                .replace("youtu", "YouTube")
                .replace("vimeo", "Vimeo")
                .replace("dailymotion", "Dailymotion")
                .replace("facebook", "Facebook")
                .replace("instagram", "Instagram")
                .replace("tiktok", "TikTok")
                .replace("twitch", "Twitch")
                .replace("rumble", "Rumble")
                .replace("odysee", "Odysee")
                .replace("twitter", "X")
                .replace("x", "X")
                .replace("drive", "Google Drive")
                .replace("mega", "MEGA")
                .replace("mediafire", "MediaFire")
                .replace("dropbox", "Dropbox")
            else -> null
        }

        val lead = if (service != null) {
            "This is a link to a $service page, not to a video file."
        } else {
            "This looks like a link to a web page, not to a video file."
        }

        return "$lead\n\nA page is shown by a browser, not played by a player — and a ${
            service ?: "service"
        } page also needs that service's own sign-in, which no codec pack changes. A direct " +
            "file link ends in a media extension: .mp4, .webm, .mkv, .m3u8 or .mpd. Those play " +
            "here. You can still try this address."
    }
}
