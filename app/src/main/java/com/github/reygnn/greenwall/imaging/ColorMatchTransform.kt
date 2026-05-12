package com.github.reygnn.greenwall.imaging

import kotlin.math.abs

/**
 * Pure ARGB pixel kernels for replacing keyer-colored pixels in an image
 * with either AMOLED black or full transparency, plus a despill pass
 * that cleans the residual keyer tint from non-matched pixels (typically
 * the antialiased motif edges).
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
 * Despill rule: for each non-matched pixel, the "dominant" channels of
 * the target color (channels above the mean of target's three channels)
 * are capped at the maximum of the pixel's own non-dominant channels.
 * For a pure-green target this removes the green tint from edge pixels
 * without affecting which pixels are kept. See [despillPixel].
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
     * Caps the dominant channels of [targetRgb] inside [argb] at the
     * maximum of the pixel's own non-dominant channels. Alpha is
     * preserved verbatim.
     *
     * "Dominant" = channel value strictly greater than the mean of the
     * three target channels. For a pure-green target only G is dominant;
     * for pure pink (R + B) both R and B are dominant; for an
     * achromatic target (all channels equal) no channel is dominant
     * and the pixel is returned unchanged.
     *
     * Idempotent: applying despill twice gives the same result as
     * applying it once.
     */
    fun despillPixel(argb: Int, targetRgb: Int): Int {
        val tr = (targetRgb shr 16) and 0xFF
        val tg = (targetRgb shr 8) and 0xFF
        val tb = targetRgb and 0xFF
        val mean = (tr + tg + tb) / 3
        val rDom = tr > mean
        val gDom = tg > mean
        val bDom = tb > mean
        if (!rDom && !gDom && !bDom) return argb

        val a = (argb shr 24) and 0xFF
        var r = (argb shr 16) and 0xFF
        var g = (argb shr 8) and 0xFF
        var b = argb and 0xFF
        // Three-dominant is impossible (cannot have all three channels
        // strictly above their integer mean simultaneously), and the
        // none-dominant case returned early above, so the `when` is
        // exhaustive over the reachable cases.
        val cap = when {
            rDom && gDom -> b
            rDom && bDom -> g
            gDom && bDom -> r
            rDom -> maxOf(g, b)
            gDom -> maxOf(r, b)
            bDom -> maxOf(r, g)
            else -> error("unreachable: at least one channel must be dominant here")
        }
        if (rDom) r = minOf(r, cap)
        if (gDom) g = minOf(g, cap)
        if (bDom) b = minOf(b, cap)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Returns a new pixel array where every pixel near [targetRgb] is
     * replaced with [COLOR_AMOLED] and every other pixel is despill'd
     * via [despillPixel]. Input is not mutated.
     */
    fun applyAmoled(pixels: IntArray, targetRgb: Int, threshold: Int): IntArray {
        val out = pixels.copyOf()
        for (i in out.indices) {
            out[i] = if (isNearTarget(out[i], targetRgb, threshold)) {
                COLOR_AMOLED
            } else {
                despillPixel(out[i], targetRgb)
            }
        }
        return out
    }

    /**
     * Returns a new pixel array where every pixel near [targetRgb] is
     * replaced with [COLOR_TRANSPARENT] and every other pixel is
     * despill'd via [despillPixel]. Input is not mutated.
     *
     * Bitmap-level callers must additionally flip `Bitmap.setHasAlpha(true)`
     * on the resulting bitmap — see CLAUDE.md hard rule #4. The kernel
     * itself only writes pixel values; the alpha-channel flag is a
     * Bitmap-object property and is the adapter's responsibility.
     */
    fun applyTransparent(pixels: IntArray, targetRgb: Int, threshold: Int): IntArray {
        val out = pixels.copyOf()
        for (i in out.indices) {
            out[i] = if (isNearTarget(out[i], targetRgb, threshold)) {
                COLOR_TRANSPARENT
            } else {
                despillPixel(out[i], targetRgb)
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
