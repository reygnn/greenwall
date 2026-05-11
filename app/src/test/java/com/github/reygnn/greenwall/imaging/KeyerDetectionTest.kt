package com.github.reygnn.greenwall.imaging

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyerDetectionTest {

    @Test
    fun `uniform border yields the border color`() {
        val green = 0xFF00FF00.toInt()
        val pixels = IntArray(100) { green } // 10x10 all green
        val detected = KeyerDetection.detectFromBorder(pixels, width = 10, height = 10, borderPx = 2)
        assertEquals(green, detected)
    }

    @Test
    fun `output alpha is always FF even if input pixels were transparent`() {
        val transparentGreen = 0x0000FF00 // alpha = 0, RGB = green
        val pixels = IntArray(16) { transparentGreen } // 4x4
        val detected = KeyerDetection.detectFromBorder(pixels, width = 4, height = 4, borderPx = 1)
        assertEquals(0xFF00FF00.toInt(), detected)
    }

    @Test
    fun `median is robust to a handful of motif pixels intruding into the border`() {
        // 10x10 image, border thickness 2 → 100 - 36 = 64 inner pixels,
        // 36 border pixels. Replace 5 of those border pixels with red
        // (motif intrusion); the remaining 31 stay green.
        // Per channel: median of 36 samples lies at index 18.
        //   R: 31 zeros + 5 reds (255) → sorted index 18 = 0.
        //   G: 5 zeros + 31 greens (255) → sorted index 18 = 255.
        //   B: all 0 → median 0.
        // So the detected color is still 0xFF00FF00.
        val green = 0xFF00FF00.toInt()
        val red = 0xFFFF0000.toInt()
        val pixels = IntArray(100) { i ->
            val y = i / 10
            val x = i % 10
            val onBorder = y < 2 || y >= 8 || x < 2 || x >= 8
            if (onBorder) green else 0xFF000000.toInt()
        }
        // Stomp the first five border pixels with red.
        for (k in 0 until 5) pixels[k] = red

        val detected = KeyerDetection.detectFromBorder(pixels, width = 10, height = 10, borderPx = 2)
        assertEquals(green, detected)
    }

    @Test
    fun `slight per-pixel keyer drift collapses into the unjittered median`() {
        // 8x8 image, border 2px wide. Border pixels are green with
        // uniformly random ±3 per-channel jitter; inner pixels are pure
        // black (irrelevant — only the border is sampled).
        //
        // For a uniform-on-{-3..3} jitter clamped to [0, 255]:
        //   R median is 0 (over half the samples clamp to 0).
        //   G median is 255 (over half the samples clamp to 255).
        //   B median is 0 (same reasoning as R).
        // We assert "close to" the expected values to leave a small
        // margin against any quirk of Random's distribution.
        val width = 8
        val height = 8
        val rand = Random(42)
        val pixels = IntArray(width * height) { idx ->
            val y = idx / width
            val x = idx % width
            val onBorder = y < 2 || y >= height - 2 || x < 2 || x >= width - 2
            if (!onBorder) 0xFF000000.toInt() else {
                val r = (0 + rand.nextInt(7) - 3).coerceIn(0, 255)
                val g = (255 + rand.nextInt(7) - 3).coerceIn(0, 255)
                val b = (0 + rand.nextInt(7) - 3).coerceIn(0, 255)
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val detected = KeyerDetection.detectFromBorder(pixels, width = width, height = height, borderPx = 2)
        val dr = (detected shr 16) and 0xFF
        val dg = (detected shr 8) and 0xFF
        val db = detected and 0xFF
        assertTrue("R drift: $dr", dr <= 4)
        assertTrue("G drift: $dg", dg >= 251)
        assertTrue("B drift: $db", db <= 4)
    }

    @Test
    fun `borderPx larger than half the image samples every pixel`() {
        // 2x2 image: with borderPx = 10 the whole image is the "border".
        // Pixels: two black (0) and two white (255). Sorted per channel
        // [0, 0, 255, 255], median index 2 → 255 → expected white.
        val pixels = intArrayOf(
            0xFF000000.toInt(),
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
        )
        val detected = KeyerDetection.detectFromBorder(pixels, width = 2, height = 2, borderPx = 10)
        assertEquals(0xFFFFFFFF.toInt(), detected)
    }

    @Test
    fun `zero-sized image returns the fallback color`() {
        assertEquals(
            KeyerDetection.FALLBACK_COLOR,
            KeyerDetection.detectFromBorder(IntArray(0), width = 0, height = 0),
        )
    }

    @Test
    fun `pixel buffer smaller than declared dimensions returns the fallback`() {
        assertEquals(
            KeyerDetection.FALLBACK_COLOR,
            KeyerDetection.detectFromBorder(IntArray(5), width = 10, height = 10),
        )
    }

    @Test
    fun `non-positive borderPx returns the fallback`() {
        val pixels = IntArray(16) { 0xFF00FF00.toInt() }
        assertEquals(
            KeyerDetection.FALLBACK_COLOR,
            KeyerDetection.detectFromBorder(pixels, width = 4, height = 4, borderPx = 0),
        )
        assertEquals(
            KeyerDetection.FALLBACK_COLOR,
            KeyerDetection.detectFromBorder(pixels, width = 4, height = 4, borderPx = -1),
        )
    }
}
