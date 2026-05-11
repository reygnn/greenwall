package com.github.reygnn.greenwall.imaging

import android.graphics.Bitmap

/**
 * Thin Android adapter around [ColorMatchTransform] and [KeyerDetection].
 *
 * Converts between Bitmap and IntArray, delegates the logic to the
 * pure kernels, wraps the result back into a Bitmap.
 *
 * ─── Testing policy ──────────────────────────────────────────────
 *
 * Most of this file is intentionally NOT covered by unit tests. Every
 * non-transparency function is a three-step adapter:
 *   1. Bitmap → IntArray  (via Bitmap.getPixels)
 *   2. Delegate to ColorMatchTransform / KeyerDetection  (fully unit-
 *      tested on the JVM)
 *   3. IntArray → Bitmap  (via Bitmap.copy + Bitmap.setPixels)
 *
 * Steps 1 and 3 are pure Android framework calls whose correctness is
 * guaranteed by the platform. The Robolectric test
 * (`ImageProcessingRobolectricTest`) covers the one non-trivial
 * interaction: [applyTransparent] must flip `Bitmap.setHasAlpha(true)`
 * so [Bitmap.compress] emits an alpha channel in the resulting PNG
 * (CLAUDE.md hard rule #4).
 */
internal object ImageProcessing {

    /** Returns a new bitmap with matching pixels replaced by AMOLED black. Source not mutated. */
    fun applyAmoled(source: Bitmap, targetRgb: Int, threshold: Int): Bitmap {
        val pixels = source.toArgbArray()
        val transformed = ColorMatchTransform.applyAmoled(pixels, targetRgb, threshold)
        return source.copiedWith(transformed)
    }

    /**
     * Returns a new bitmap with matching pixels replaced by full
     * transparency. Source not mutated.
     *
     * IMPORTANT: We force [Bitmap.setHasAlpha]`(true)` on the result.
     * Without this, a source bitmap decoded from an opaque container
     * (e.g. a JPEG, or a PNG without alpha channel) would propagate
     * `hasAlpha = false` through [Bitmap.copy], and [Bitmap.compress]
     * would then strip the alpha channel when writing the PNG —
     * producing an opaque image despite our pixel array containing
     * 0x00000000 values. Setting the flag explicitly is the only
     * reliable way to tell Android's PNG encoder to keep the alpha
     * channel intact.
     */
    fun applyTransparent(source: Bitmap, targetRgb: Int, threshold: Int): Bitmap {
        val pixels = source.toArgbArray()
        val transformed = ColorMatchTransform.applyTransparent(pixels, targetRgb, threshold)
        val result = source.copiedWith(transformed)
        result.setHasAlpha(true)
        return result
    }

    /** Returns overlay bitmap (matches marked with [overlayRgb]) + match count. Source not mutated. */
    fun analyze(
        source: Bitmap,
        targetRgb: Int,
        threshold: Int,
        overlayRgb: Int,
    ): ColorMatchAnalysisBitmap {
        val pixels = source.toArgbArray()
        val analysis = ColorMatchTransform.analyze(pixels, targetRgb, threshold, overlayRgb)
        return ColorMatchAnalysisBitmap(
            bitmap = source.copiedWith(analysis.pixels),
            matchCount = analysis.matchCount,
        )
    }

    /** Auto-detects the dominant keyer color from the source's outer border. */
    fun detectKeyerColor(
        source: Bitmap,
        borderPx: Int = KeyerDetection.DEFAULT_BORDER_PX,
    ): Int {
        val pixels = source.toArgbArray()
        return KeyerDetection.detectFromBorder(pixels, source.width, source.height, borderPx)
    }

    private fun Bitmap.toArgbArray(): IntArray {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels
    }

    private fun Bitmap.copiedWith(pixels: IntArray): Bitmap {
        val result = copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}

/** Return type for [ImageProcessing.analyze]: bitmap + count. */
internal class ColorMatchAnalysisBitmap(
    val bitmap: Bitmap,
    val matchCount: Int,
)
