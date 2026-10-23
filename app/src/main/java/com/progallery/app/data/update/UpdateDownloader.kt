package com.progallery.app.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches the update into the app, and **checks it before it can be installed**.
 *
 * The check is the reason this lives in the app rather than in a browser: a downloaded file is
 * only trustworthy if it is the same app signed by the same key. Android would refuse a
 * differently-signed APK anyway, but refusing it here means the reason can be *explained*
 * instead of appearing as a failed install.
 *
 * The file is written under `cache/shared/`, which is the directory the app's own FileProvider
 * already exposes, so the installer can be handed a URI without opening anything else up.
 */
@Singleton
class UpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val USER_AGENT = "AdvancedFileManager-UpdateDownload"
        const val DIRECTORY = "shared/updates"
    }

    /** What came of a download, for the dialog to show. */
    sealed interface Outcome {
        data class Ready(val file: File, val versionName: String) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    /**
     * Downloads [url] and verifies it.
     *
     * @param onProgress called with 0–100, or -1 when the server did not say how large the file
     *   is — in which case the UI shows an indeterminate bar rather than a made-up percentage.
     */
    suspend fun download(
        url: String,
        versionName: String,
        onProgress: (Int) -> Unit,
    ): Outcome = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val target = File(directory, "progallery-$versionName.apk")
        // A previous attempt's file is removed first, so a partial download can never be
        // mistaken for a complete one.
        target.delete()

        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (t: IOException) {
            return@withContext Outcome.Failed("The update could not be reached.")
        }

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return@withContext Outcome.Failed("The download was refused ($code).")
            }

            val total = connection.contentLengthLong
            var written = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(if (total > 0) ((written * 100) / total).toInt().coerceIn(0, 100) else -1)
                    }
                }
            }
        } catch (t: IOException) {
            target.delete()
            return@withContext Outcome.Failed("The download did not finish: ${t.message}")
        } finally {
            runCatching { connection.disconnect() }
        }

        if (target.length() == 0L) {
            target.delete()
            return@withContext Outcome.Failed("The download was empty.")
        }

        verify(target).fold(
            onSuccess = { Outcome.Ready(target, versionName) },
            onFailure = { failure ->
                // An unverified file is deleted rather than left where it could be opened by
                // hand later.
                target.delete()
                Outcome.Failed(failure.message ?: "The downloaded file could not be verified.")
            },
        )
    }

    /**
     * Whether the file really is this app, signed by the key this copy was signed with.
     *
     * Three things are checked, in increasing order of importance: that it is an APK at all,
     * that it is *this* app, and that its signing certificate matches.
     */
    internal fun verify(file: File): Result<Unit> {
        val info = runCatching {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, SIGNING_FLAGS)
        }.getOrNull() ?: return Result.failure(
            IllegalArgumentException("The downloaded file is not an Android app."),
        )

        if (info.packageName != context.packageName) {
            return Result.failure(
                IllegalArgumentException(
                    "The downloaded file is a different app (${info.packageName}).",
                ),
            )
        }

        val installed = installedSigners()
        val downloaded = signersOf(info)
        if (installed.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("This app's own signature could not be read."),
            )
        }
        if (downloaded.isEmpty()) {
            return Result.failure(IllegalArgumentException("The downloaded app is not signed."))
        }
        if (downloaded.none { it in installed }) {
            return Result.failure(
                IllegalArgumentException(
                    "The downloaded app is signed by a different key, so it was not installed.",
                ),
            )
        }

        return Result.success(Unit)
    }

    /** The signing certificates of the copy that is running, as fingerprints. */
    private fun installedSigners(): Set<String> = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, SIGNING_FLAGS)
        signersOf(info)
    }.getOrDefault(emptySet())

    private fun signersOf(info: android.content.pm.PackageInfo): Set<String> {
        val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            // History is used, not just the current signer, so an app that has rotated its
            // signing key is still recognised.
            signing.signingCertificateHistory ?: signing.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return certificates?.mapNotNull { certificate ->
            runCatching {
                MessageDigest.getInstance("SHA-256")
                    .digest(certificate.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte) }
            }.getOrNull()
        }?.toSet() ?: emptySet()
    }

    private val SIGNING_FLAGS: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
}
