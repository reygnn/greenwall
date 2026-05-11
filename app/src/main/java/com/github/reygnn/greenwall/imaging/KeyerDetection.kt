package com.github.reygnn.greenwall.imaging

/**
 * Pure detection kernel: estimates the dominant keyer color in an image
 * by sampling its outer border and taking the per-channel median of the
 * sampled pixels.
 *
 * The **median**, not the mean, is used because the border may include a
 * few non-keyer pixels where the motif reaches the edge; the median is
 * robust to those outliers as long as keyer pixels remain the majority
 * of the sample. The slight per-pixel keyer variation that AI image
 * generators introduce is absorbed into the downstream threshold of
 * [ColorMatchTransform], not into the detection step.
 *
 * Android-free: inputs are IntArray in ARGB8888 packing; the output is
 * an Int in the same packing with alpha forced to 0xFF.
 */
internal object KeyerDetection {

    /** Default border thickness in pixels — used if no value is passed. */
    const val DEFAULT_BORDER_PX = 8

    /** Sentinel returned for degenerate inputs (empty buffer, bad dims). */
    val FALLBACK_COLOR: Int = 0xFF000000.toInt()

    /**
     * Returns the per-channel median RGB of the outer [borderPx] pixels
     * of an image of [width] x [height], packed as 0xFF_RR_GG_BB
     * (alpha = 0xFF).
     *
     * The border is the rectangular ring of all pixels whose row OR
     * column index lies within [borderPx] of an edge. If [borderPx] is
     * larger than half the smaller dimension, the entire image is
     * sampled.
     *
     * Returns [FALLBACK_COLOR] for degenerate inputs: non-positive
     * dimensions, non-positive [borderPx], or a [pixels] buffer that is
     * smaller than [width] * [height].
     */
    fun detectFromBorder(
        pixels: IntArray,
        width: Int,
        height: Int,
        borderPx: Int = DEFAULT_BORDER_PX,
    ): Int {
        if (width <= 0 || height <= 0 || borderPx <= 0) return FALLBACK_COLOR
        if (pixels.size < width * height) return FALLBACK_COLOR

        val clamped = minOf(borderPx, (width + 1) / 2, (height + 1) / 2)
        val innerW = (width - 2 * clamped).coerceAtLeast(0)
        val innerH = (height - 2 * clamped).coerceAtLeast(0)
        val count = width * height - innerW * innerH
        if (count == 0) return FALLBACK_COLOR

        val rs = IntArray(count)
        val gs = IntArray(count)
        val bs = IntArray(count)
        var i = 0
        for (y in 0 until height) {
            val rowOnBorder = y < clamped || y >= height - clamped
            for (x in 0 until width) {
                val onBorder = rowOnBorder || x < clamped || x >= width - clamped
                if (onBorder) {
                    val p = pixels[y * width + x]
                    rs[i] = (p shr 16) and 0xFF
                    gs[i] = (p shr 8) and 0xFF
                    bs[i] = p and 0xFF
                    i++
                }
            }
        }
        rs.sort()
        gs.sort()
        bs.sort()
        val mid = count / 2
        return FALLBACK_COLOR or (rs[mid] shl 16) or (gs[mid] shl 8) or bs[mid]
    }
}
