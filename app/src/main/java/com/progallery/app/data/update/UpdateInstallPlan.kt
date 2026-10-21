package com.progallery.app.data.update

/**
 * What the update dialog should offer next.
 *
 * The update used to hand the address to a browser, which is the wrong place for it: the file
 * belongs to this app, and sending someone out of the app to fetch it loses the download, the
 * verification and the install.
 *
 * Kept pure so the awkward cases are decided by a test rather than discovered on a phone:
 * a release with no APK, a copy installed by Play, and an app that is not yet allowed to
 * install packages.
 */
enum class UpdateInstallStep {
    /** Nothing downloaded yet, and there is an APK to fetch. */
    DOWNLOAD,

    /** Downloaded, but Android will not let this app install packages until the user allows it. */
    ALLOW_INSTALLS,

    /** Downloaded and verified — hand it to the system installer. */
    INSTALL,

    /** Play owns this install, so an update should come from there. */
    PLAY,

    /** The release carries no installable file. */
    UNAVAILABLE,
}

object UpdateInstallPlan {

    fun step(
        installedFromPlay: Boolean,
        hasApk: Boolean,
        downloaded: Boolean,
        canInstallPackages: Boolean,
    ): UpdateInstallStep = when {
        !hasApk -> UpdateInstallStep.UNAVAILABLE
        installedFromPlay -> UpdateInstallStep.PLAY
        !downloaded -> UpdateInstallStep.DOWNLOAD
        !canInstallPackages -> UpdateInstallStep.ALLOW_INSTALLS
        else -> UpdateInstallStep.INSTALL
    }

    /** The button's label for a step. */
    fun actionLabel(step: UpdateInstallStep): String = when (step) {
        UpdateInstallStep.DOWNLOAD -> "Download and install"
        UpdateInstallStep.ALLOW_INSTALLS -> "Allow installs"
        UpdateInstallStep.INSTALL -> "Install"
        UpdateInstallStep.PLAY -> "Open the store"
        UpdateInstallStep.UNAVAILABLE -> "Release page"
    }

    /**
     * What to say, and it must not pretend the install can be silent.
     *
     * Android shows its own confirmation for an app installed from outside the store, and no
     * permission removes that. Saying so beforehand is the difference between a normal step and
     * an unexplained system dialog.
     */
    fun note(step: UpdateInstallStep): String? = when (step) {
        UpdateInstallStep.DOWNLOAD ->
            "The file is fetched here and checked against this app's own signing key before " +
                "anything is installed."
        UpdateInstallStep.ALLOW_INSTALLS ->
            "Android needs your permission once, for this app to install an update. It opens " +
                "the setting; nothing is installed until you return and press Install."
        UpdateInstallStep.INSTALL ->
            "Android will show its own confirmation. That step cannot be skipped for an app " +
                "installed outside the store."
        UpdateInstallStep.PLAY ->
            "This copy was installed by Play, so the update should come from there — updating " +
                "it from a file can cause a conflict."
        UpdateInstallStep.UNAVAILABLE ->
            "This release carries no installable file. The release page has the details."
    }

    /**
     * Whether the downloaded file should be offered for install at all.
     *
     * Only when it was verified. An APK whose signature does not match this app's would be
     * refused by Android anyway, but refusing it here means the reason can be explained rather
     * than surfacing as a failed install.
     */
    fun canOfferInstall(verified: Boolean): Boolean = verified
}
