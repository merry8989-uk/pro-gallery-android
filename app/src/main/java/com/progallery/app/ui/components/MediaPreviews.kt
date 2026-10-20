package com.progallery.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.progallery.app.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Previews that do not come from the file's own pixels.
 *
 *  * **Audio** shows the artwork embedded in the file, read with
 *    [MediaMetadataRetriever.embeddedPicture]. Coil has no decoder for that, which is
 *    why it lives here.
 *  * **PDF** shows the first page, rendered with the platform [PdfRenderer] — the same
 *    engine the in-app reader uses.
 *
 * Both are genuinely expensive compared with an image tile, which is what the per-type
 * switches exist for. Results are cached in memory by URI, so scrolling does not
 * re-render a page, and the cache is bounded rather than growing with the library.
 *
 * Anything that cannot be produced returns null, and the caller falls back to the type
 * icon — a tile never goes blank.
 */
object MediaPreviews {

    /** Roughly 40 tiles' worth; small enough to be harmless, large enough to help. */
    private const val CACHE_ENTRIES = 40

    /** Rendered page width for a document preview, in pixels. */
    private const val PDF_PREVIEW_WIDTH = 320

    private val cache = LruCache<String, ImageBitmap>(CACHE_ENTRIES)

    suspend fun load(context: Context, item: MediaItem): ImageBitmap? = withContext(Dispatchers.IO) {
        cache.get(item.mediaKey)?.let { return@withContext it }

        val bitmap = runCatching {
            when {
                item.isAudio -> embeddedArtwork(context, item)
                item.isDocument -> if (item.isPdf) pdfFirstPage(context, item) else null
                else -> null
            }
        }.getOrNull() ?: return@withContext null

        val image = bitmap.asImageBitmap()
        cache.put(item.mediaKey, image)
        image
    }

    /** Forgets a cached preview, so a freed bitmap is not held on to. */
    fun forget(mediaKey: String) {
        cache.remove(mediaKey)
    }

    private fun embeddedArtwork(context: Context, item: MediaItem): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, item.uri)
            val picture = retriever.embeddedPicture ?: return null
            BitmapFactory.decodeByteArray(picture, 0, picture.size)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun pdfFirstPage(context: Context, item: MediaItem): Bitmap? {
        val descriptor = context.contentResolver.openFileDescriptor(item.uri, "r") ?: return null
        return descriptor.use { openPdfPage(it) }
    }

    private fun openPdfPage(descriptor: ParcelFileDescriptor): Bitmap? =
        try {
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount <= 0) return null
                renderer.openPage(0).use { page ->
                    val height = (PDF_PREVIEW_WIDTH.toFloat() * page.height / page.width)
                        .toInt()
                        .coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(
                        PDF_PREVIEW_WIDTH,
                        height,
                        Bitmap.Config.ARGB_8888,
                    )
                    // A white page, so a transparent PDF does not render as a black tile.
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        } catch (t: Throwable) {
            null
        }
}
