package com.github.reygnn.greenwall.imaging

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore

/**
 * PNG export through MediaStore. greenwall writes into `Pictures/greenwall`.
 *
 * Composition is not needed here — the transformer has already produced
 * the final output bitmap. This object is a thin MediaStore wrapper kept
 * separate from [ImageProcessing] so the I/O path is straightforward
 * platform plumbing rather than mixed with pixel-touching code.
 */
internal object ImageExporter {

    /**
     * Saves [bitmap] as PNG into `Pictures/greenwall/` under [displayName].
     * Uses the `IS_PENDING` two-phase write so partial files are never
     * visible to other apps. Returns the inserted [Uri].
     */
    fun savePng(context: Context, bitmap: Bitmap, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/greenwall")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create MediaStore entry for $displayName")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: error("Could not open output stream for $uri")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        return uri
    }
}
