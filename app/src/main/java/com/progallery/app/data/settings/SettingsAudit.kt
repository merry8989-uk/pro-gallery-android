package com.progallery.app.data.settings

import com.progallery.app.data.update.Json

/**
 * What changed between two sets of settings.
 *
 * Written as a diff of the **export**, not of the data class, and that is the whole trick: the
 * export already names every field and is guarded by a test that fails when a new setting is added
 * without being listed. Reusing it means this cannot quietly stop reporting a field that was added
 * later — which is exactly how an audit trail goes stale while still looking complete.
 *
 * Pure, so what it reports is tested rather than trusted.
 */
object SettingsAudit {

    /** A ceiling, because an audit line is a line and not a transcript of the whole file. */
    const val MAX_REPORTED = 6

    fun changed(before: AppSettings, after: AppSettings): Map<String, String> {
        val old = values(before)
        val new = values(after)

        return (old.keys + new.keys)
            .sorted()
            .mapNotNull { key ->
                val was = old[key]
                val now = new[key]
                if (was == now) null else key to "${was ?: "unset"} -> ${now ?: "unset"}"
            }
            .take(MAX_REPORTED)
            .toMap()
    }

    /** The settings as the export names them, which is the field list that cannot go stale. */
    private fun values(settings: AppSettings): Map<String, Any?> =
        Json.objectAt(Json.parse(SettingsExport.encode(settings))).orEmpty()
}
