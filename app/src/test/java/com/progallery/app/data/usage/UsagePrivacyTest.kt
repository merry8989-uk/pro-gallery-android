package com.progallery.app.data.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guarantees about what this app **cannot** record.
 *
 * These are the tests that matter most in this package: not "does the counter work", but "is
 * there any way for a filename, a path or a search term to end up in the stored data".
 */
class UsagePrivacyTest {

    @Test
    fun anExceptionMessageContainingAPathIsNotKept() {
        // This is the ordinary case, not a contrived one: a crash about a missing file carries
        // the file's whole path in its message.
        val crash = IllegalArgumentException(
            "File not found: /storage/emulated/0/DCIM/Holiday/beach.jpg",
        )
        val record = CrashSanitizer.record(crash, build = "1.24.0")

        val serialised = UsageCodec.encode(
            DeviceProfile("Pixel", 34, "1.24.0", 40),
            listOf(UsageDay(java.time.LocalDate.of(2026, 10, 21), crashes = listOf(record))),
        )

        assertFalse("a path must never reach the stored file", serialised.contains("/storage"))
        assertFalse("nor a filename", serialised.contains("beach.jpg"))
        assertFalse("nor the message at all", serialised.contains("File not found"))
        assertTrue("but the fault must still be identifiable", serialised.contains("IllegalArgumentException"))
    }

    @Test
    fun aSearchTermInAnExceptionMessageIsNotKept() {
        val crash = RuntimeException("Failed while searching for 'wedding photos 2019'")
        val record = CrashSanitizer.record(crash, build = "1.24.0")
        val serialised = UsageCodec.encode(
            DeviceProfile("Pixel", 34, "1.24.0", 40),
            listOf(UsageDay(java.time.LocalDate.of(2026, 10, 21), crashes = listOf(record))),
        )
        assertFalse(serialised.contains("wedding"))
        assertFalse(serialised.contains("2019"))
    }

    @Test
    fun aCauseChainIsNotFollowed() {
        val cause = IllegalStateException("contact Amita at 98xxxxxx21")
        val crash = RuntimeException("wrapper", cause)
        val record = CrashSanitizer.record(crash, build = "1.24.0")
        assertFalse(record.exceptionClass.contains("Amita"))
        assertFalse(record.frames.any { it.contains("Amita") })
    }

    @Test
    fun theFramesAreClassesAndMethodsOnly() {
        val crash = IllegalStateException("boom")
        val record = CrashSanitizer.record(crash, build = "1.24.0")
        record.frames.forEach { frame ->
            assertTrue("$frame should look like Class.method", frame.contains('.'))
            assertFalse("no file name in a frame", frame.contains(".kt"))
            assertFalse("no line number in a frame", frame.any { it.isDigit() })
        }
    }

    @Test
    fun theRecordedVocabularyIsClosedSoNothingElseCanBeWritten() {
        // The structural guarantee: a day holds only enums and numbers. There is no field a
        // filename could be assigned to, which is stronger than remembering not to assign one.
        val day = UsageDay(
            day = java.time.LocalDate.of(2026, 10, 21),
            events = mapOf(UsageEvent.SEARCH_PERFORMED to 2),
            features = mapOf(FeatureId.PHOTOS to 5),
            performance = mapOf(PerfOperation.LIBRARY_LOAD to PerfTotals(1, 900, 900)),
        )
        val serialised = UsageCodec.encode(DeviceProfile("Pixel", 34, "1.24.0", 40), listOf(day))
        // Every identifier in the file is one of the known ids.
        val known = (UsageEvent.entries.map { it.id } +
            FeatureId.entries.map { it.id } +
            PerfOperation.entries.map { it.id }).toSet()
        serialised.lineSequence()
            .filter { it.startsWith("event=") || it.startsWith("feature=") || it.startsWith("perf=") }
            .forEach { line ->
                val id = line.substringAfter('=').substringBefore(' ')
                assertTrue("$id is not a known identifier", id in known)
            }
    }

    @Test
    fun theFileSaysWhatItIs() {
        val text = UsageCodec.encode(DeviceProfile("Pixel", 34, "1.24.0", 40), emptyList())
        assertTrue("the file should explain itself", text.contains("counts only"))
        assertTrue(text.contains("No filenames"))
    }

    @Test
    fun aDeviceIdentifierIsNotPartOfTheProfile() {
        // Only a model, a version and a build. Nothing that identifies *this* device.
        val profile = DeviceProfile("Pixel 6", 34, "1.24.0", 40)
        val text = UsageCodec.encode(profile, emptyList())
        listOf("androidId", "serial", "imei", "advertisingId", "account").forEach { forbidden ->
            assertFalse("$forbidden must not be recorded", text.contains(forbidden))
        }
    }

    @Test
    fun anEventIdNeverLooksLikeAPath() {
        (UsageEvent.entries.map { it.id } + FeatureId.entries.map { it.id } +
            PerfOperation.entries.map { it.id }).forEach { id ->
            assertFalse("$id looks like a path", id.contains('/') || id.contains('\\'))
            assertFalse("$id looks like a file name", id.endsWith(".jpg") || id.endsWith(".png"))
            assertTrue("$id should be lower-case words", id == id.lowercase())
        }
    }

    @Test
    fun theIdentifiersAreUnique() {
        val all = UsageEvent.entries.map { it.id } + FeatureId.entries.map { it.id } +
            PerfOperation.entries.map { it.id }
        assertEquals("identifiers must not collide across kinds", all.size, all.toSet().size)
    }
}
