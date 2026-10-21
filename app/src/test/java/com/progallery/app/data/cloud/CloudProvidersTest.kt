package com.progallery.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sidebar must not imply that a service is browsed in-app when it is not, and the
 * rule for opening a provider has to be predictable.
 */
class CloudProvidersTest {

    @Test
    fun theProvidersThatCanBeOpenedAreOffered() {
        // Terabox was removed at the user's request: it publishes no official API, so the
        // only honest entry for it was a browser link, and that is not what was wanted.
        assertEquals(
            listOf("Google Drive", "OneDrive", "Box"),
            CloudProviders.ALL.map { it.name },
        )
    }

    @Test
    fun theInstalledAppIsPreferred() {
        CloudProviders.ALL.forEach { provider ->
            assertEquals(
                "${provider.name} should open its app when it is installed",
                LaunchPlan.APP,
                CloudProviders.launchPlan(provider, appInstalled = true),
            )
        }
    }

    @Test
    fun theWebsiteIsUsedWhenTheAppIsNotInstalled() {
        CloudProviders.ALL.forEach { provider ->
            assertEquals(
                LaunchPlan.BROWSER,
                CloudProviders.launchPlan(provider, appInstalled = false),
            )
        }
    }

    @Test
    fun theTargetMatchesThePlan() {
        CloudProviders.ALL.forEach { provider ->
            assertEquals(provider.packageName, CloudProviders.target(provider, LaunchPlan.APP))
            assertEquals(provider.webUrl, CloudProviders.target(provider, LaunchPlan.BROWSER))
        }
    }

    @Test
    fun everyEntrySaysWhatItActuallyDoes() {
        CloudProviders.ALL.forEach { provider ->
            assertTrue("${provider.name} needs a note", provider.note.isNotBlank())
            assertTrue(
                "${provider.name} should say it opens the provider",
                provider.note.startsWith("Opens ${provider.name}"),
            )
        }
    }

    @Test
    fun theOtherThreePointAtTheStreamScreenForPlayback() {
        listOf(CloudProviders.GOOGLE_DRIVE, CloudProviders.ONE_DRIVE, CloudProviders.BOX)
            .forEach { provider ->
                assertTrue(
                    "${provider.name} should name the way that does work",
                    provider.note.contains("direct link") && provider.note.contains("Stream"),
                )
            }
    }

    @Test
    fun everyWebAddressIsHttps() {
        CloudProviders.ALL.forEach { provider ->
            assertTrue("${provider.name} must use https", provider.webUrl.startsWith("https://"))
        }
    }

    @Test
    fun theIdsAreUnique() {
        val ids = CloudProviders.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
