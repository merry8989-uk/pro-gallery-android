package com.progallery.app.data.settings

import com.progallery.app.data.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The export is a file the user keeps or shares, so it has to round-trip exactly,
 * refuse what it cannot understand, and carry nothing private.
 */
class SettingsExportTest {

    /** Every field set away from its default, so a missed field shows up. */
    private val nonDefault = AppSettings(
        advancedMode = true,
        quickScanEnabled = false,
        deepScanEnabled = false,
        safScanEnabled = false,
        signatureDetectionEnabled = false,
        duplicateDetectionEnabled = false,
        scanImages = false,
        scanVideos = false,
        scanAudio = false,
        scanDocuments = false,
        scanArchives = false,
        scanOtherFiles = false,
        generateThumbnails = false,
        thumbnailCacheLimitMb = 128,
        showFileNames = false,
        showFileSize = false,
        includeHiddenFiles = true,
        includeExtensionlessFiles = false,
        maxRecursionDepth = 4,
        maxScanResults = 1000,
        signatureReadBytes = 2048,
        minFileSizeBytes = 100L,
        maxFileSizeBytes = 10_000L,
        performanceMode = PerformanceMode.BATTERY_SAVER,
        maxConcurrentTasks = 2,
        scanBatchSize = 250,
        pauseWhenBackgrounded = true,
        showRootStatus = true,
        experimentalMethodsEnabled = true,
        themeMode = ThemeMode.DARK,
        defaultLayout = LayoutMode.LIST,
        gridSize = GridSize.LARGE,
        defaultSort = SortOrder.NAME_ASC,
        hasSeenDefaultGalleryPrompt = true,
    )

    @Test
    fun everyFieldSurvivesARoundTrip() {
        val decoded = SettingsExport.decode(SettingsExport.encode(nonDefault)).getOrThrow()
        assertEquals(nonDefault.sanitized(), decoded)
    }

    @Test
    fun defaultsAlsoRoundTrip() {
        val defaults = AppSettings.Defaults
        assertEquals(defaults.sanitized(), SettingsExport.decode(SettingsExport.encode(defaults)).getOrThrow())
    }

    @Test
    fun aRoundTripChangesNothingTheAppReliesOn() {
        // The export must not be able to smuggle in a value the app rejects.
        val decoded = SettingsExport.decode(SettingsExport.encode(nonDefault)).getOrThrow()
        assertEquals(decoded, decoded.sanitized())
    }

    @Test
    fun aNewerFormatIsRefusedRatherThanHalfApplied() {
        val fromTheFuture = """{"formatVersion":${SettingsExport.FORMAT_VERSION + 1},"quickScanEnabled":false}"""
        val result = SettingsExport.decode(fromTheFuture)
        assertTrue(result.isFailure)
        assertTrue(
            "the message should explain why",
            result.exceptionOrNull()?.message?.contains("newer version") == true,
        )
    }

    @Test
    fun aFileWithNoFormatVersionIsRefused() {
        val result = SettingsExport.decode("""{"quickScanEnabled":false}""")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("format version") == true)
    }

    @Test
    fun malformedJsonIsRefusedWithAReadableMessage() {
        val result = SettingsExport.decode("this is not json")
        assertTrue(result.isFailure)
        assertEquals("This is not a valid settings file.", result.exceptionOrNull()?.message)
    }

    @Test
    fun unknownKeysAreIgnored() {
        val json = """{"formatVersion":1,"somethingFromTheFuture":true,"quickScanEnabled":false}"""
        val decoded = SettingsExport.decode(json).getOrThrow()
        assertFalse("a known key must still be applied", decoded.quickScanEnabled)
    }

    @Test
    fun missingKeysFallBackToDefaults() {
        val decoded = SettingsExport.decode("""{"formatVersion":1}""").getOrThrow()
        assertEquals(AppSettings.Defaults.sanitized(), decoded)
    }

    @Test
    fun anUnknownEnumValueFallsBackInsteadOfCrashing() {
        val json = """{"formatVersion":1,"themeMode":"NEON"}"""
        assertEquals(ThemeMode.SYSTEM, SettingsExport.decode(json).getOrThrow().themeMode)
    }

    @Test
    fun theExportCarriesNothingPrivate() {
        // An export is shared or kept, so it must not leak where the user's files are,
        // nor any access grant.
        val text = SettingsExport.encode(nonDefault)
        listOf("content://", "file://", "/storage", "/sdcard", "uri", "path", "token", "grant")
            .forEach { forbidden ->
                assertFalse(
                    "the export mentions \"$forbidden\"",
                    text.contains(forbidden, ignoreCase = true),
                )
            }
    }

    @Test
    fun theExportIsVersionedAndSelfDescribing() {
        val text = SettingsExport.encode(AppSettings.Defaults)
        assertTrue(text.startsWith("{"))
        assertTrue(text.contains("\"formatVersion\":${SettingsExport.FORMAT_VERSION}"))
    }
}
