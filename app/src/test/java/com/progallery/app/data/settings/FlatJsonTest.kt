package com.progallery.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlatJsonTest {

    @Test
    fun roundTripsEverySupportedValueType() {
        val values = linkedMapOf<String, Any?>(
            "flag" to true,
            "off" to false,
            "count" to 42L,
            "name" to "hello",
            "nothing" to null,
        )
        assertEquals(values, FlatJson.decode(FlatJson.encode(values)))
    }

    @Test
    fun roundTripsStringArrays() {
        val values = mapOf<String, Any?>("list" to listOf("a", "b", "c"))
        assertEquals(values, FlatJson.decode(FlatJson.encode(values)))
    }

    @Test
    fun escapesQuotesBackslashesAndNewlines() {
        val tricky = "he said \"hi\"\nand \\ left"
        val decoded = FlatJson.decode(FlatJson.encode(mapOf<String, Any?>("s" to tricky)))
        assertEquals(tricky, decoded["s"])
    }

    @Test
    fun readsUnicodeEscapes() {
        val decoded = FlatJson.decode("""{"s":"caf\u00e9"}""")
        assertEquals("café", decoded["s"])
    }

    @Test
    fun toleratesWhitespaceAndKeyOrder() {
        val decoded = FlatJson.decode("  {\n  \"b\" : 2 ,\n \"a\":1 }\n")
        assertEquals(1L, decoded["a"])
        assertEquals(2L, decoded["b"])
    }

    @Test
    fun readsNegativeNumbers() {
        assertEquals(-7L, FlatJson.decode("""{"n":-7}""")["n"])
    }

    @Test
    fun readsAnEmptyObject() {
        assertEquals(emptyMap<String, Any?>(), FlatJson.decode("{}"))
    }

    @Test
    fun readsAnEmptyArray() {
        assertEquals(listOf<String>(), FlatJson.decode("""{"l":[]}""")["l"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTrailingContent() {
        FlatJson.decode("""{"a":1} garbage""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnUnterminatedString() {
        FlatJson.decode("""{"a":"oops}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnArrayWhereAnObjectIsExpected() {
        FlatJson.decode("[1,2,3]")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAMissingColon() {
        FlatJson.decode("""{"a" 1}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnUnknownEscape() {
        FlatJson.decode("""{"a":"\q"}""")
    }

    @Test
    fun decodesNullAsNull() {
        assertNull(FlatJson.decode("""{"a":null}""")["a"])
    }
}
