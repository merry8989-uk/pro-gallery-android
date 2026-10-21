package com.progallery.app.data.model

/**
 * Which MediaStore collection a row's content URI should be built from.
 *
 * This exists because of a real bug: the URI used to be built from the app's *guessed*
 * kind, so a file that MediaStore itself does not call an image — a WebP in a folder it
 * treats as non-media, a file under `Android/media/…` — was addressed as
 * `content://media/external/images/media/<id>`. That URI does not resolve, because the
 * images view only contains rows whose `media_type` is IMAGE. The file appeared in the
 * list and then would not open.
 *
 * The fix is to ask **MediaStore** which collection the row belongs to, and to use the
 * union view for everything it does not classify as media. What the app *calls* the file
 * is still decided separately — that is a display question, not an addressing one.
 *
 * Pure, so the mapping is unit tested.
 */
enum class MediaStoreCollection { IMAGES, VIDEO, AUDIO, FILES }

object MediaCollectionChoice {

    /** MediaStore's `media_type` values, as the platform defines them. */
    const val TYPE_IMAGE = 1
    const val TYPE_VIDEO = 2
    const val TYPE_AUDIO = 3

    /**
     * The collection for a row.
     *
     * [mediaTypeHint] is the row's own `media_type`, or 0 when the provider did not
     * report one. When it is 0 the row is not in any of the three media views, so the
     * union view is the only address that resolves.
     */
    fun forRow(mediaTypeHint: Int): MediaStoreCollection = when (mediaTypeHint) {
        TYPE_IMAGE -> MediaStoreCollection.IMAGES
        TYPE_VIDEO -> MediaStoreCollection.VIDEO
        TYPE_AUDIO -> MediaStoreCollection.AUDIO
        else -> MediaStoreCollection.FILES
    }
}
