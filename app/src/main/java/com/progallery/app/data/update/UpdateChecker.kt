package com.progallery.app.data.update

import javax.inject.Inject
import javax.inject.Singleton
import com.progallery.app.data.eventlog.EventLogStore
import com.progallery.app.data.eventlog.EventName

/** The outcome of asking whether a newer version exists. */
sealed interface UpdateCheck {
    /** A newer version is published. */
    data class Available(
        val release: UpdateRelease,
        val currentVersion: String,
    ) : UpdateCheck

    /** This is the newest published version. */
    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String,
    ) : UpdateCheck

    /** The question could not be answered, and why. */
    data class Failed(val reason: String) : UpdateCheck
}

/**
 * Answers "is there a newer version", and nothing else.
 *
 * The comparison is the whole point, so it is done by [VersionName] — pure and tested —
 * rather than by looking at whether the two strings differ. A release whose tag is not a
 * version, or one that is older, is reported as up to date: the app must never tell the
 * user to "upgrade" to something older than what they have.
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val client: GitHubReleaseClient,
    private val eventLog: com.progallery.app.data.eventlog.EventLogStore,
) {

    suspend fun check(currentVersion: String): UpdateCheck {
        val startedAt = System.currentTimeMillis()
        val result = client.latestRelease()

        // The call itself, and then what came back. Logging only the request is the mistake the
        // standard names: a request without its result says a check happened, not how it went.
        result.fold(
            onSuccess = { release ->
                eventLog.log(
                    EventName.API_REQUEST,
                    details = mapOf(
                        "endpoint" to ENDPOINT,
                        "method" to "GET",
                        "status_code" to "200",
                        "latency_ms" to (System.currentTimeMillis() - startedAt).toString(),
                    ),
                )
            },
            onFailure = { failure ->
                eventLog.log(
                    EventName.NETWORK_ERROR,
                    details = mapOf(
                        "endpoint" to ENDPOINT,
                        "method" to "GET",
                        "latency_ms" to (System.currentTimeMillis() - startedAt).toString(),
                    ),
                )
                if (failure.message?.contains("busy with other requests") == true) {
                    eventLog.log(EventName.RATE_LIMITED, details = mapOf("endpoint" to ENDPOINT))
                }
            },
        )

        return result.fold(
            onSuccess = { release ->
                if (VersionName.isNewer(release.versionName, currentVersion)) {
                    UpdateCheck.Available(release, currentVersion)
                } else {
                    UpdateCheck.UpToDate(currentVersion, release.displayVersion)
                }
            },
            onFailure = { failure ->
                UpdateCheck.Failed(failure.message ?: "The update check could not be completed.")
            },
        )
    }

    private companion object {
        /** The host and route only — never a query, which could carry a token. */
        const val ENDPOINT = "releases.atom"
    }
}
