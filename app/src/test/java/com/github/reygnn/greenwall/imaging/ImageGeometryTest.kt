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

    // ── displayPlacement (pan + zoom on top of fit-center) ───────

    @Test
    fun `displayPlacement at zoom 1 and zero pan equals fitCenter`() {
        val d = ImageGeometry.displayPlacement(100, 100, 200f, 200f)
        val f = ImageGeometry.fitCenter(100, 100, 200f, 200f)
        assertEquals(f.drawnW, d.drawnW, EPS)
        assertEquals(f.drawnH, d.drawnH, EPS)
        assertEquals(f.originX, d.originX, EPS)
        assertEquals(f.originY, d.originY, EPS)
    }

    @Test
    fun `displayPlacement at zoom 2 doubles drawn size and shifts origin`() {
        // 100x100 in 200x200 fit-center: drawn 200x200 at (0, 0).
        // Zoom 2 → drawn 400x400, origin (-100, -100). Canvas center stays at (100, 100).
        val d = ImageGeometry.displayPlacement(100, 100, 200f, 200f, zoom = 2f)
        assertEquals(400f, d.drawnW, EPS)
        assertEquals(400f, d.drawnH, EPS)
        assertEquals(-100f, d.originX, EPS)
        assertEquals(-100f, d.originY, EPS)
    }

    @Test
    fun `displayPlacement applies pan as a translation on top of zoom`() {
        val d = ImageGeometry.displayPlacement(
            bmpW = 100, bmpH = 100,
            canvasW = 200f, canvasH = 200f,
            panX = 30f, panY = -20f,
        )
        // Without zoom: drawn 200x200, origin (0,0). With pan: origin (30, -20).
        assertEquals(200f, d.drawnW, EPS)
        assertEquals(30f, d.originX, EPS)
        assertEquals(-20f, d.originY, EPS)
    }

    // ── canvasToBitmapPixel with pan / zoom ──────────────────────

    @Test
    fun `canvasToBitmapPixel at zoom 2 maps half the bitmap-coordinate range`() {
        // 100x100 in 200x200 at zoom 2: drawn 400x400 at origin (-100, -100).
        // Canvas center (100, 100) is the bitmap center pixel (50, 50).
        val center = ImageGeometry.canvasToBitmapPixel(
            100f, 100f, 100, 100, 200f, 200f, zoom = 2f,
        )
        assertEquals(50 to 50, center)

        // Canvas (0, 0) is bitmap (25, 25) because drawn origin is at (-100, -100)
        // and effective pixel size in canvas units is 4.
        val tl = ImageGeometry.canvasToBitmapPixel(
            0f, 0f, 100, 100, 200f, 200f, zoom = 2f,
        )
        assertEquals(25 to 25, tl)
    }

    @Test
    fun `canvasToBitmapPixel with pan shifts the mapping and out-of-region returns null`() {
        // 100x100 in 200x200 with pan (50, 0): drawn at (50, 0)..(250, 200).
        // Canvas (50, 0) → bitmap (0, 0); canvas (0, 0) → outside drawn region → null.
        val origin = ImageGeometry.canvasToBitmapPixel(
            50f, 0f, 100, 100, 200f, 200f, panX = 50f,
        )
        assertEquals(0 to 0, origin)

        val leftOfDraw = ImageGeometry.canvasToBitmapPixel(
            0f, 0f, 100, 100, 200f, 200f, panX = 50f,
        )
        assertNull(leftOfDraw)
    }

    @Test
    fun `canvasToBitmapPixel rejects sub-pixel taps just left of the bitmap`() {
        // Regression: 100x100 in 200x100 fits 1.0 wide, drawn from canvas
        // x=50..150. A tap at x=49.5 is half a canvas pixel LEFT of the
        // bitmap and must return null. `.toInt()` would truncate the
        // resulting -0.5 to 0 and falsely accept it as pixel (0, y);
        // `floor()` correctly produces -1 and the bounds check rejects.
        assertNull(ImageGeometry.canvasToBitmapPixel(49.5f, 50f, 100, 100, 200f, 100f))
        assertNull(ImageGeometry.canvasToBitmapPixel(49.99f, 50f, 100, 100, 200f, 100f))
        // The exact boundary is inclusive on the left edge.
        assertEquals(0 to 50, ImageGeometry.canvasToBitmapPixel(50f, 50f, 100, 100, 200f, 100f))
    }

    @Test
    fun `canvasToBitmapPixel rejects sub-pixel taps just above the bitmap`() {
        // Same shape of bug on the vertical axis. 100x100 in 100x200
        // fits 1.0 tall, drawn from canvas y=50..150. Taps at
        // y < 50 must return null even when y is in (-1, 0) relative
        // to the origin.
        assertNull(ImageGeometry.canvasToBitmapPixel(50f, 49.5f, 100, 100, 100f, 200f))
        assertEquals(50 to 0, ImageGeometry.canvasToBitmapPixel(50f, 50f, 100, 100, 100f, 200f))
    }

    @Test
    fun `canvasToBitmapPixel at high zoom rejects sub-pixel taps over multiple canvas pixels`() {
        // 100x100 in 200x200 at zoom 20: fit becomes 40 canvas px per
        // bitmap pixel, drawn origin shifts to (-1900, -1900). A tap
        // within (-40, 0) canvas px of the drawn origin would, under
        // `.toInt()` truncation, falsely register as pixel (0, 0).
        // floor() gives a negative index → null.
        val drawn = ImageGeometry.displayPlacement(100, 100, 200f, 200f, zoom = 20f)
        // Tap 20 canvas pixels left of the drawn origin: still inside
        // the "bad zone" of the old truncation behavior, must be null.
        assertNull(
            ImageGeometry.canvasToBitmapPixel(
                drawn.originX - 20f, drawn.originY + 10f,
                100, 100, 200f, 200f, zoom = 20f,
            )
        )
        // Tap exactly at the drawn origin → bitmap (0, 0).
        assertEquals(
            0 to 0,
            ImageGeometry.canvasToBitmapPixel(
                drawn.originX, drawn.originY,
                100, 100, 200f, 200f, zoom = 20f,
            )
        )
    }

    companion object {
        private const val EPS = 1e-4f
    }
}
