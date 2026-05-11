package com.github.reygnn.greenwall.imaging

import kotlin.math.abs

/**
 * Pure ARGB pixel kernels for replacing keyer-colored pixels in an image
 * with either AMOLED black or full transparency.
 *
 * Android-free: inputs and outputs are IntArray in the same ARGB8888
 * packing that android.graphics.Bitmap uses
 * (alpha << 24 | red << 16 | green << 8 | blue). This lets the pixel
 * logic be unit-tested on the JVM without Robolectric or instrumented
 * tests.
 *
 * Selection rule: **Chebyshev distance** in RGB space. A pixel is
 * considered "near the target keyer color" iff every R, G, B channel
 * differs from the target by at most [threshold]. Alpha is ignored —
 * callers normalize to ARGB_8888 before invoking.
 *
 * Constant parity (verified by definition of ARGB packing; cannot be
 * asserted in JVM tests because android.graphics.Color returns 0 under
 * unitTests.isReturnDefaultValues = true):
 *
 *   [COLOR_AMOLED]      == android.graphics.Color.BLACK       (0xFF000000)
 *   [COLOR_TRANSPARENT] == android.graphics.Color.TRANSPARENT (0x00000000)
 */
internal object ColorMatchTransform {

    /** Opaque pure black: 0xFF000000 as signed Int. */
    val COLOR_AMOLED: Int = 0xFF000000.toInt()

    /** Fully transparent: 0x00000000. */
    const val COLOR_TRANSPARENT: Int = 0

    /**
     * True iff [argb] is within Chebyshev distance [threshold] of
     * [targetRgb] — i.e. each of the R, G, B channels differs by at most
     * [threshold]. Alpha is ignored on both arguments.
     */
    fun isNearTarget(argb: Int, targetRgb: Int, threshold: Int): Boolean {
        val ar = (argb shr 16) and 0xFF
        val ag = (argb shr 8) and 0xFF
        val ab = argb and 0xFF
        val tr = (targetRgb shr 16) and 0xFF
        val tg = (targetRgb shr 8) and 0xFF
        val tb = targetRgb and 0xFF
        return abs(ar - tr) <= threshold &&
                abs(ag - tg) <= threshold &&
                abs(ab - tb) <= threshold
    }

    /**
     * Returns a new pixel array where every pixel near [targetRgb] is
     * replaced with [COLOR_AMOLED]. Input is not mutated.
     */
    fun applyAmoled(pixels: IntArray, targetRgb: Int, threshold: Int): IntArray {
        val out = pixels.copyOf()
        for (i in out.indices) {
            if (isNearTarget(out[i], targetRgb, threshold)) {
                out[i] = COLOR_AMOLED
            }
        }
        return out
    }

    /**
     * Returns a new pixel array where every pixel near [targetRgb] is
     * replaced with [COLOR_TRANSPARENT]. Input is not mutated.
     *
     * Bitmap-level callers must additionally flip `Bitmap.setHasAlpha(true)`
     * on the resulting bitmap — see CLAUDE.md hard rule #4. The kernel
     * itself only writes pixel values; the alpha-channel flag is a
     * Bitmap-object property and is the adapter's responsibility.
     */
    fun applyTransparent(pixels: IntArray, targetRgb: Int, threshold: Int): IntArray {
        val out = pixels.copyOf()
        for (i in out.indices) {
            if (isNearTarget(out[i], targetRgb, threshold)) {
                out[i] = COLOR_TRANSPARENT
            }
        }
        return out
    }

    /**
     * Returns the analysis overlay: every pixel near [targetRgb] is
     * replaced with [overlayRgb], paired with the count of replaced
     * pixels. Input is not mutated.
     *
     * The caller chooses [overlayRgb] — the typical convention is to
     * pass the complementary color of [targetRgb] so the overlay is
     * visible regardless of which keyer color is in use.
     */
    fun analyze(
        pixels: IntArray,
        targetRgb: Int,
        threshold: Int,
        overlayRgb: Int,
    ): ColorMatchAnalysis {
        val out = pixels.copyOf()
        var count = 0
        for (i in out.indices) {
            if (isNearTarget(out[i], targetRgb, threshold)) {
                out[i] = overlayRgb
                count++
            }
        }
        return ColorMatchAnalysis(out, count)
    }
}

/** Return type for [ColorMatchTransform.analyze]: overlay array + count. */
internal class ColorMatchAnalysis(
    val pixels: IntArray,
    val matchCount: Int,
)
