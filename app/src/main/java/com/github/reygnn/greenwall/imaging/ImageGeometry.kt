package com.github.reygnn.greenwall.imaging

/**
 * Pure-Kotlin geometry helpers for mapping between canvas-space
 * coordinates (Compose pixels) and bitmap-space pixel indices.
 *
 * No Android imports — JVM-testable without Robolectric.
 */
internal object ImageGeometry {

    /** Fit-center placement of a bitmap inside a canvas. */
    data class FitPlacement(
        val drawnW: Float,
        val drawnH: Float,
        val originX: Float,
        val originY: Float,
    )

    /**
     * Computes the fit-center placement of a [bmpW] × [bmpH] bitmap
     * inside a [canvasW] × [canvasH] canvas. Returns an empty
     * placement (all zeros) for degenerate inputs.
     */
    fun fitCenter(bmpW: Int, bmpH: Int, canvasW: Float, canvasH: Float): FitPlacement {
        if (bmpW <= 0 || bmpH <= 0 || canvasW <= 0f || canvasH <= 0f) {
            return FitPlacement(0f, 0f, 0f, 0f)
        }
        val fit = minOf(canvasW / bmpW, canvasH / bmpH)
        val drawnW = bmpW * fit
        val drawnH = bmpH * fit
        return FitPlacement(
            drawnW = drawnW,
            drawnH = drawnH,
            originX = (canvasW - drawnW) / 2f,
            originY = (canvasH - drawnH) / 2f,
        )
    }

    /**
     * Maps a canvas-space tap at ([canvasX], [canvasY]) to bitmap
     * pixel indices for a fit-center bitmap of [bmpW] × [bmpH] inside
     * a canvas of [canvasW] × [canvasH]. Returns `null` if the tap
     * falls outside the drawn bitmap region (on the surrounding empty
     * canvas area) or if any dimension is degenerate.
     */
    fun canvasToBitmapPixel(
        canvasX: Float,
        canvasY: Float,
        bmpW: Int,
        bmpH: Int,
        canvasW: Float,
        canvasH: Float,
    ): Pair<Int, Int>? {
        val placement = fitCenter(bmpW, bmpH, canvasW, canvasH)
        if (placement.drawnW <= 0f) return null
        val fit = placement.drawnW / bmpW
        val bx = ((canvasX - placement.originX) / fit).toInt()
        val by = ((canvasY - placement.originY) / fit).toInt()
        if (bx < 0 || by < 0 || bx >= bmpW || by >= bmpH) return null
        return bx to by
    }
}
