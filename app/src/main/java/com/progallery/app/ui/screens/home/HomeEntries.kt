package com.progallery.app.ui.screens.home

import com.progallery.app.data.model.MediaType

/**
 * Which Home entry points a settings state implies.
 *
 * Turning a file category off in Settings has to remove its entry point here too,
 * otherwise Home keeps offering a folder that is guaranteed to be empty. Pure, so
 * the rule can be tested without a device.
 */
object HomeEntries {

    /** The type tiles to show, in display order. */
    fun typeTiles(enabledKinds: Set<MediaType>): List<MediaType> =
        listOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO, MediaType.DOCUMENT)
            .filter { it in enabledKinds }

    /** "All files", "Albums" and "Favourites" are meaningless with nothing enabled. */
    fun showCollections(enabledKinds: Set<MediaType>): Boolean = enabledKinds.isNotEmpty()

    /** True when the user has switched every category off and Home can show nothing. */
    fun isEmpty(enabledKinds: Set<MediaType>): Boolean = enabledKinds.isEmpty()
}
