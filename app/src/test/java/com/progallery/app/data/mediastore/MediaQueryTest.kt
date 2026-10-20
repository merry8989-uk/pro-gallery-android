package com.progallery.app.data.mediastore

import com.progallery.app.data.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * With paging, the ORDER BY decides which page an item lands on, and the search
 * selection decides what exists at all — so both are worth pinning down.
 */
class MediaQueryTest {

    @Test
    fun everySortOrderMapsToAClause() {
        // A `when` over the enum would fail to compile on a new value; this catches
        // a blank or duplicated mapping instead.
        val clauses = SortOrder.entries.map { MediaQuery.orderBy(it) }
        clauses.forEach { assertTrue("a sort order mapped to a blank clause", it.isNotBlank()) }
        assertEquals("two sort orders share a clause", clauses.size, clauses.toSet().size)
    }

    @Test
    fun newestFirstSortsByDateDescending() {
        val clause = MediaQuery.orderBy(SortOrder.DATE_NEWEST)
        assertTrue(clause.contains("date_modified", ignoreCase = true))
        assertTrue(clause.endsWith("DESC", ignoreCase = true))
    }

    @Test
    fun nameSortsAreCaseInsensitive() {
        assertTrue(MediaQuery.orderBy(SortOrder.NAME_ASC).contains("NOCASE", ignoreCase = true))
        assertTrue(MediaQuery.orderBy(SortOrder.NAME_DESC).contains("NOCASE", ignoreCase = true))
    }

    @Test
    fun aBlankQueryHasNoSelection() {
        val (selection, args) = MediaQuery.selectionFor("")
        assertNull(selection)
        assertNull(args)
    }

    @Test
    fun aWhitespaceQueryHasNoSelection() {
        val (selection, args) = MediaQuery.selectionFor("   ")
        assertNull(selection)
        assertNull(args)
    }

    @Test
    fun aQueryBecomesALikeSelection() {
        val (selection, args) = MediaQuery.selectionFor("holiday")
        assertTrue(selection!!.contains("LIKE"))
        assertEquals(listOf("%holiday%"), args!!.toList())
    }

    @Test
    fun wildcardsInTheUsersTextAreEscaped() {
        // Typing "%" must search for a percent sign, not match the whole library.
        val (_, args) = MediaQuery.selectionFor("100%_done")
        assertEquals(listOf("%100\\%\\_done%"), args!!.toList())
    }

    @Test
    fun aBackslashIsEscapedToo() {
        val (_, args) = MediaQuery.selectionFor("a\\b")
        assertEquals(listOf("%a\\\\b%"), args!!.toList())
    }

    @Test
    fun theSelectionDeclaresAnEscapeCharacter() {
        val (selection, _) = MediaQuery.selectionFor("x")
        assertTrue("without ESCAPE the escaping above means nothing", selection!!.contains("ESCAPE"))
    }
}
