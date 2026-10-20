package com.progallery.app.data.mediastore

import android.provider.MediaStore
import com.progallery.app.data.model.SortOrder

/**
 * The SQL the MediaStore query needs for a sort order and a name search.
 *
 * Kept pure and separate so the ORDER BY can be unit tested: getting it wrong
 * silently shows the library in the wrong order, and with paging the order also
 * decides which page an item lands on.
 *
 * The ordering mirrors [com.progallery.app.domain.SortComparators] so the paged
 * grid and the full-list path agree.
 */
object MediaQuery {

    /** The `ORDER BY` clause for [sort]. */
    fun orderBy(sort: SortOrder): String = when (sort) {
        SortOrder.DATE_NEWEST -> "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        SortOrder.DATE_OLDEST -> "${MediaStore.MediaColumns.DATE_MODIFIED} ASC"
        SortOrder.NAME_ASC -> "${MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE ASC"
        SortOrder.NAME_DESC -> "${MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE DESC"
        SortOrder.SIZE_DESC -> "${MediaStore.MediaColumns.SIZE} DESC"
        SortOrder.SIZE_ASC -> "${MediaStore.MediaColumns.SIZE} ASC"
        SortOrder.TYPE -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} ASC, " +
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
    }

    /**
     * The selection for a name search, or a pair of nulls when [query] is blank.
     *
     * `%`, `_` and `\` in the user's text are escaped and `ESCAPE '\'` is declared,
     * so typing a percent sign searches for a percent sign instead of matching
     * everything.
     */
    fun selectionFor(query: String): Pair<String?, Array<String>?> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null to null
        val escaped = trimmed
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'"
        return selection to arrayOf("%$escaped%")
    }
}
