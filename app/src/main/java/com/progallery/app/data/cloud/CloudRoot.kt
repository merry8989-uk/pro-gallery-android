package com.progallery.app.data.cloud

/**
 * A place a cloud provider says it can be browsed from.
 *
 * These are exactly the entries the system's own file picker shows in its sidebar, because
 * they come from the same source: every app that implements a documents provider publishes
 * its roots, and any app may list them.
 */
data class CloudRoot(
    val rootId: String,
    val title: String,
    val summary: String?,
    /** The tree address, used to browse and to ask for access. */
    val treeUri: String,
    /** The provider's authority, which is how two accounts of one service are told apart. */
    val authority: String,
    /** The provider's own id for this root. */
    val documentId: String,
    val canCreate: Boolean,
) {
    val isDeviceStorage: Boolean get() = CloudRootClassifier.isDeviceStorage(authority)
}

/**
 * Which roots are the device's own storage rather than a service.
 *
 * Pure, so the distinction is tested rather than guessed at. It matters because the app
 * already browses the device's storage through MediaStore and its own scanners; listing
 * "Internal storage" again under Cloud would be a duplicate, and the point of the Cloud
 * section is the services.
 */
object CloudRootClassifier {

    /** The platform's own providers: the device's storage, downloads and media. */
    private val DEVICE_AUTHORITIES = setOf(
        "com.android.externalstorage.documents",
        "com.android.providers.downloads.documents",
        "com.android.providers.media.documents",
        "com.android.shell.documents",
    )

    fun isDeviceStorage(authority: String): Boolean =
        authority.trim().lowercase() in DEVICE_AUTHORITIES
}
