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
 * The app is distributed as a signed APK from the project's releases, so that feed is the
 * authoritative answer to "is there a newer version".
 *
 * Plain HTTP on purpose, as with WebDAV: one request, no dependency.
 *
 * **The rate limit is real and is reported honestly.** GitHub allows a limited number of
 * unauthenticated requests per address per hour, and answers 403 when they run out. That is
 * surfaced as "try again later" rather than as a failure of the app.
 */
@Singleton
class GitHubReleaseClient @Inject constructor() {

    private companion object {
        const val LATEST_RELEASE =
            "https://api.github.com/repos/merry8989-uk/pro-gallery-android/releases/latest"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000

        /** GitHub refuses a request with no user agent. */
        const val USER_AGENT = "ProGallery-UpdateCheck"
    }

    suspend fun latestRelease(): Result<UpdateRelease> = withContext(Dispatchers.IO) {
        val connection = try {
            (URL(LATEST_RELEASE).openConnection() as HttpURLConnection)
        } catch (t: IOException) {
            return@withContext Result.failure(
                IllegalArgumentException("The update server could not be reached.", t),
            )
        }

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", USER_AGENT)

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(IllegalArgumentException(describe(code)))
            }

            val body = connection.inputStream.use { it.readBytes().decodeToString() }
            ReleaseJson.parse(body)
        } catch (t: IOException) {
            Result.failure(IllegalArgumentException("The update server did not answer: ${t.message}", t))
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /** What each reply code means here, in words worth reading. */
    private fun describe(code: Int): String = when (code) {
        403 -> "The update server is rate-limiting this device for now. Try again in an hour."
        404 -> "No published release was found."
        HttpURLConnection.HTTP_OK -> "The update server's reply was not understood."
        else -> "The update server answered with $code."
    }
}
