package com.progallery.app.data.update

import javax.inject.Inject
import javax.inject.Singleton

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
) {

    suspend fun check(currentVersion: String): UpdateCheck =
        client.latestRelease().fold(
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
