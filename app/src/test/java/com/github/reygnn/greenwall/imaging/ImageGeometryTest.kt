package com.github.reygnn.greenwall.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImageGeometryTest {

    // ── fitCenter ────────────────────────────────────────────────

    @Test
    fun `fitCenter for square bitmap in landscape canvas fits by height`() {
        // 100x100 bitmap in 200x100 canvas → fit by height (1.0), centered horizontally.
        val p = ImageGeometry.fitCenter(100, 100, 200f, 100f)
        assertEquals(100f, p.drawnW, EPS)
        assertEquals(100f, p.drawnH, EPS)
        assertEquals(50f, p.originX, EPS)
        assertEquals(0f, p.originY, EPS)
    }

    @Test
    fun `fitCenter for square bitmap in portrait canvas fits by width`() {
        val p = ImageGeometry.fitCenter(100, 100, 100f, 200f)
        assertEquals(100f, p.drawnW, EPS)
        assertEquals(100f, p.drawnH, EPS)
        assertEquals(0f, p.originX, EPS)
        assertEquals(50f, p.originY, EPS)
    }

    @Test
    fun `fitCenter for same-ratio canvas fills exactly`() {
        val p = ImageGeometry.fitCenter(200, 100, 400f, 200f)
        assertEquals(400f, p.drawnW, EPS)
        assertEquals(200f, p.drawnH, EPS)
        assertEquals(0f, p.originX, EPS)
        assertEquals(0f, p.originY, EPS)
    }

    @Test
    fun `fitCenter returns zero placement for degenerate inputs`() {
        val z = ImageGeometry.fitCenter(0, 100, 100f, 100f)
        assertEquals(0f, z.drawnW, EPS)
        assertEquals(0f, z.drawnH, EPS)

        val z2 = ImageGeometry.fitCenter(100, 100, 0f, 100f)
        assertEquals(0f, z2.drawnW, EPS)
    }

    // ── canvasToBitmapPixel ──────────────────────────────────────

    @Test
    fun `canvasToBitmapPixel maps canvas center to bitmap center`() {
        // 100x100 bitmap in 200x200 canvas → fit 2.0, centered.
        val p = ImageGeometry.canvasToBitmapPixel(100f, 100f, 100, 100, 200f, 200f)
        assertNotNull(p)
        assertEquals(50, p!!.first)
        assertEquals(50, p.second)
    }

    @Test
    fun `canvasToBitmapPixel maps drawn-bitmap corners to bitmap corners`() {
        // 100x100 in 200x200 → fit 2.0, drawn from (0, 0) to (200, 200).
        val tl = ImageGeometry.canvasToBitmapPixel(0f, 0f, 100, 100, 200f, 200f)
        assertEquals(0 to 0, tl)

        // Tap just inside the bottom-right corner of the drawn region.
        val br = ImageGeometry.canvasToBitmapPixel(199f, 199f, 100, 100, 200f, 200f)
        assertEquals(99 to 99, br)
    }

    @Test
    fun `canvasToBitmapPixel returns null for taps outside the drawn bitmap`() {
        // 100x100 in 200x100 canvas → fit 1.0, drawn from (50, 0) to (150, 100).
        // Tap on the left empty band:
        assertNull(ImageGeometry.canvasToBitmapPixel(10f, 50f, 100, 100, 200f, 100f))
        // Tap on the right empty band:
        assertNull(ImageGeometry.canvasToBitmapPixel(180f, 50f, 100, 100, 200f, 100f))
    }

    @Test
    fun `canvasToBitmapPixel returns null for degenerate inputs`() {
        assertNull(ImageGeometry.canvasToBitmapPixel(10f, 10f, 0, 100, 100f, 100f))
        assertNull(ImageGeometry.canvasToBitmapPixel(10f, 10f, 100, 100, 0f, 100f))
        assertNull(ImageGeometry.canvasToBitmapPixel(10f, 10f, 100, 100, 100f, 0f))
    }

    @Test
    fun `canvasToBitmapPixel handles non-square bitmap in non-matching canvas`() {
        // 200x100 bitmap in 200x200 canvas → fit by width (1.0). Drawn at
        // (0, 50) to (200, 150). Tap at canvas (100, 100) is at bitmap center.
        val p = ImageGeometry.canvasToBitmapPixel(100f, 100f, 200, 100, 200f, 200f)
        assertEquals(100 to 50, p)

        // Tap above the drawn region (in the empty top band) returns null.
        assertNull(ImageGeometry.canvasToBitmapPixel(100f, 20f, 200, 100, 200f, 200f))
    }

    companion object {
        private const val EPS = 1e-4f
    }
}
