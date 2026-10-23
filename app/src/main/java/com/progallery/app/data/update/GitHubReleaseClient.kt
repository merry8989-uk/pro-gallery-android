package com.progallery.app.data.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the project's latest release.
 *
 * **Two sources, in this order, and the order matters.**
 *
 * 1. The **Atom feed**. It is an ordinary web page rather than an API call, so it is not
 *    subject to GitHub's per-address request budget. That budget is why this check failed:
 *    it allows only a small number of unauthenticated requests per hour **per address**, and
 *    mobile networks in India put thousands of subscribers behind one address, so the
 *    allowance is often already spent by strangers. The app then reported "rate-limiting this
 *    device", which is a confusing way to say "someone else used it up".
 * 2. The **API**, as a fallback. It carries the asset list, so it is worth trying when the
 *    feed cannot be read — but it is not the thing to depend on.
 *
 * Plain HTTP on purpose, as with WebDAV: one request, no dependency.
 */
@Singleton
class GitHubReleaseClient @Inject constructor() {

    private companion object {
        const val OWNER = "merry8989-uk"
        const val REPO = "pro-gallery-android"

        /** Not rate-limited: it is the web feed, not the API. */
        const val RELEASES_ATOM = "https://github.com/$OWNER/$REPO/releases.atom"

        /** Rate-limited per address, and therefore only a fallback. */
        const val LATEST_RELEASE = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000

        /** GitHub refuses a request with no user agent. */
        const val USER_AGENT = "AdvancedFileManager-UpdateCheck"
    }

    suspend fun latestRelease(): Result<UpdateRelease> = withContext(Dispatchers.IO) {
        // The feed first, because it is the source that keeps working.
        fetch(RELEASES_ATOM, "application/atom+xml, application/xml;q=0.9, */*;q=0.8") { body ->
            ReleaseAtom.parse(body, OWNER, REPO)
        }.let { fromFeed ->
            if (fromFeed.isSuccess) return@withContext fromFeed

            fetch(LATEST_RELEASE, "application/vnd.github+json") { body ->
                ReleaseJson.parse(body)
            }.let { fromApi ->
                if (fromApi.isSuccess) return@withContext fromApi


                // Neither worked. The API's reason is usually the more specific of the two, so
                // it is the one shown — unless it never got as far as answering.
                val apiReason = fromApi.exceptionOrNull()?.message
                val feedReason = fromFeed.exceptionOrNull()?.message
                // Both endpoints are named. The device's log recorded only `releases.atom` failing
                // while the dialog on the screen named `api.github.com` — so the log and the screen
                // disagreed about what the app had even tried, and neither said both were attempted.
                val tried = "Both endpoints were tried: releases.atom and api.github.com."
                Result.failure(
                    IllegalArgumentException(
                        (apiReason?.takeIf { it.isNotBlank() } ?: feedReason
                            ?: "The update check could not be completed.") + "\n\n" + tried,
                    ),
                )
            }
        }
    }

    /** One GET, with the reply handed to [parse]. */
    private fun <T> fetch(
        address: String,
        accept: String,
        parse: (String) -> Result<T>,
    ): Result<T> {
        val connection = try {
            URL(address).openConnection() as HttpURLConnection
        } catch (t: IOException) {
            return Result.failure(IllegalArgumentException("The update server could not be reached.", t))
        }

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", USER_AGENT)

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                Result.failure(IllegalArgumentException(describe(code)))
            } else {
                parse(connection.inputStream.use { it.readBytes().decodeToString() })
            }
        } catch (t: IOException) {
            Result.failure(
                IllegalArgumentException(
                    "The update server did not answer: ${t.message}.\n\n" +
                        "This is a connection to GitHub, so it is usually the network rather " +
                        "than the app: a firewall, a captive portal, or a DNS server answering " +
                        "with the wrong address. Try another network — mobile data instead of " +
                        "Wi-Fi, or the other way round — or open the releases page in a browser, " +
                        "which may reach it when this cannot.",
                    t,
                ),
            )
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /** What each reply code means here, in words worth reading. */
    private fun describe(code: Int): String = when (code) {
        403 -> "The update server is busy with other requests from this network. Try again shortly."
        404 -> "No published release was found."
        429 -> "Too many update checks. Try again in a little while."
        HttpURLConnection.HTTP_OK -> "The update server's reply was not understood."
        else -> "The update server answered with $code."
    }
}
