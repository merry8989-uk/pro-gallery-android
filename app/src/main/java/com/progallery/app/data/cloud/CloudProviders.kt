package com.progallery.app.data.cloud

/** How a provider entry should be opened. */
enum class LaunchPlan {
    /** The provider's own app is installed, so open it. */
    APP,

    /** No app, so open the provider's website. */
    BROWSER,
}

/**
 * A cloud service offered in the sidebar.
 *
 * [note] is not decoration: it says plainly what the entry does and does not do, so the
 * sidebar never implies that the app browses a service in-app when it does not.
 */
data class CloudProvider(
    val id: String,
    val name: String,
    /** The provider's own app, when it has one. */
    val packageName: String,
    val webUrl: String,
    val note: String,
)

/**
 * The cloud entries, and the rule for opening them.
 *
 * **What these entries are.** Each one opens the provider's own app if it is installed,
 * and its website otherwise. That is a real, working thing to do — but it is *not*
 * browsing the service inside ProGallery, and the sidebar says so rather than leaving it
 * to be discovered.
 *
 * **Why they are not browsed in-app.** Reading a Drive, OneDrive or Box library inside
 * another app needs OAuth with an application id issued to *that* app's developer, which
 * means registering an app with each provider. Those credentials cannot be invented here.
 *
 * **What does work in-app, today**, is a direct media link: the Stream screen plays an
 * address straight from any of these services when the service gives one. And WebDAV
 * browsing works fully in-app, with no registration at all — see the WebDAV entries.
 *
 * Pure, so which plan applies is unit tested without a device.
 */
object CloudProviders {

    fun launchPlan(provider: CloudProvider, appInstalled: Boolean): LaunchPlan =
        if (appInstalled) LaunchPlan.APP else LaunchPlan.BROWSER

    /** The address the plan should use. */
    fun target(provider: CloudProvider, plan: LaunchPlan): String = when (plan) {
        LaunchPlan.APP -> provider.packageName
        LaunchPlan.BROWSER -> provider.webUrl
    }

    val GOOGLE_DRIVE = CloudProvider(
        id = "google_drive",
        name = "Google Drive",
        packageName = "com.google.android.apps.docs",
        webUrl = "https://drive.google.com",
        note = "Opens Google Drive. To play something from it, share a direct link and " +
            "use Stream.",
    )

    val ONE_DRIVE = CloudProvider(
        id = "onedrive",
        name = "OneDrive",
        packageName = "com.microsoft.skydrive",
        webUrl = "https://onedrive.live.com",
        note = "Opens OneDrive. To play something from it, share a direct link and use Stream.",
    )

    val BOX = CloudProvider(
        id = "box",
        name = "Box",
        packageName = "com.box.android",
        webUrl = "https://app.box.com",
        note = "Opens Box. To play something from it, share a direct link and use Stream.",
    )

    val ALL: List<CloudProvider> = listOf(GOOGLE_DRIVE, ONE_DRIVE, BOX)
}
