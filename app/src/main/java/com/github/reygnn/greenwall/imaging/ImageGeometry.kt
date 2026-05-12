package com.github.reygnn.greenwall.imaging

import kotlin.math.floor

/**
 * Pure-Kotlin geometry helpers for mapping between canvas-space
 * coordinates (Compose pixels) and bitmap-space pixel indices,
 * with optional pan/zoom transforms layered on top of fit-center.
 *
 * No Android imports — JVM-testable without Robolectric.
 */
internal object ImageGeometry {

    /** Placement of a bitmap inside a canvas. */
    data class FitPlacement(
        val drawnW: Float,
        val drawnH: Float,
        val originX: Float,
        val originY: Float,
    )

    /**
     * Computes the fit-center placement of a [bmpW] × [bmpH] bitmap
     * inside a [canvasW] × [canvasH] canvas (no pan or zoom). Returns
     * an empty placement (all zeros) for degenerate inputs.
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
     * Like [fitCenter], but with an additional zoom factor and pan
     * offset layered on top: the bitmap is first laid out fit-center,
     * then scaled by [zoom] (uniformly, keeping the same canvas center
     * as the fixed point), then translated by ([panX], [panY]) in
     * canvas pixels. Zoom of 1.0 and zero pan reduces to plain
     * [fitCenter].
     */
    fun displayPlacement(
        bmpW: Int, bmpH: Int,
        canvasW: Float, canvasH: Float,
        panX: Float = 0f, panY: Float = 0f,
        zoom: Float = 1f,
    ): FitPlacement {
        val fit = fitCenter(bmpW, bmpH, canvasW, canvasH)
        if (fit.drawnW <= 0f) return fit
        val drawnW = fit.drawnW * zoom
        val drawnH = fit.drawnH * zoom
        return FitPlacement(
            drawnW = drawnW,
            drawnH = drawnH,
            originX = (canvasW - drawnW) / 2f + panX,
            originY = (canvasH - drawnH) / 2f + panY,
        )
    }

    /**
     * Maps a canvas-space tap at ([canvasX], [canvasY]) to bitmap
     * pixel indices for a fit-center bitmap of [bmpW] × [bmpH] in a
     * canvas of [canvasW] × [canvasH], optionally with a pan offset
     * and zoom factor (defaults reduce to plain fit-center). Returns
     * `null` if the tap falls outside the drawn bitmap region or any
     * dimension is degenerate.
     *
     * Floor (not truncate) is used to convert canvas → bitmap so that
     * taps less than one drawn-pixel above or to the left of the
     * bitmap correctly resolve to negative indices and get rejected.
     * `.toInt()` truncates toward zero — `(-0.5f).toInt() == 0` —
     * which would falsely accept those taps as pixel (0, 0). At
     * `zoom = 20` the bad zone is up to 20 canvas pixels wide.
     */
    fun canvasToBitmapPixel(
        canvasX: Float,
        canvasY: Float,
        bmpW: Int,
        bmpH: Int,
        canvasW: Float,
        canvasH: Float,
        panX: Float = 0f,
        panY: Float = 0f,
        zoom: Float = 1f,
    ): Pair<Int, Int>? {
        if (bmpW <= 0 || bmpH <= 0) return null
        val placement = displayPlacement(bmpW, bmpH, canvasW, canvasH, panX, panY, zoom)
        if (placement.drawnW <= 0f) return null
        val fit = placement.drawnW / bmpW
        val bx = floor((canvasX - placement.originX) / fit).toInt()
        val by = floor((canvasY - placement.originY) / fit).toInt()
        if (bx < 0 || by < 0 || bx >= bmpW || by >= bmpH) return null
        return bx to by
    }
}
