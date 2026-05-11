package com.github.reygnn.greenwall.imaging

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMatchTransformTest {

    @Test
    fun `isNearTarget returns true for exact match at threshold zero`() {
        val pureGreen = 0xFF00FF00.toInt()
        assertTrue(ColorMatchTransform.isNearTarget(pureGreen, pureGreen, threshold = 0))
    }

    @Test
    fun `isNearTarget at threshold zero rejects any single-channel deviation`() {
        val target = 0xFF00FF00.toInt()
        val almost = 0xFF01FF00.toInt() // red differs by 1
        assertFalse(ColorMatchTransform.isNearTarget(almost, target, threshold = 0))
    }

    @Test
    fun `isNearTarget threshold matches per-channel max delta`() {
        val target = 0xFF808080.toInt()
        val variant = 0xFF8A8580.toInt() // diffs (R=10, G=5, B=0); max = 10
        assertTrue(ColorMatchTransform.isNearTarget(variant, target, threshold = 10))
        assertFalse(ColorMatchTransform.isNearTarget(variant, target, threshold = 9))
    }

    @Test
    fun `isNearTarget ignores alpha`() {
        val target = 0xFF00FF00.toInt()
        val sameRgbDifferentAlpha = 0x4400FF00 // alpha = 0x44, RGB identical
        assertTrue(ColorMatchTransform.isNearTarget(sameRgbDifferentAlpha, target, threshold = 0))
    }

    @Test
    fun `isNearTarget threshold of 255 matches anything`() {
        // Maximum per-channel delta is 255 (between 0x00 and 0xFF), so a
        // threshold of 255 trivially matches every pixel.
        val target = 0xFF00FF00.toInt()
        val opposite = 0xFFFF00FF.toInt()
        assertTrue(ColorMatchTransform.isNearTarget(opposite, target, threshold = 255))
    }

    @Test
    fun `applyAmoled replaces matching pixels with opaque black`() {
        val target = 0xFF00FF00.toInt()
        val red = 0xFFFF0000.toInt()
        val input = intArrayOf(target, red, target)
        val out = ColorMatchTransform.applyAmoled(input, target, threshold = 0)
        assertArrayEquals(
            intArrayOf(ColorMatchTransform.COLOR_AMOLED, red, ColorMatchTransform.COLOR_AMOLED),
            out,
        )
    }

    @Test
    fun `applyAmoled does not mutate the input array`() {
        val target = 0xFF00FF00.toInt()
        val input = intArrayOf(target, target, target)
        val snapshot = input.copyOf()
        ColorMatchTransform.applyAmoled(input, target, threshold = 0)
        assertArrayEquals(snapshot, input)
    }

    @Test
    fun `applyTransparent replaces matching pixels with full transparency`() {
        val target = 0xFF00FF00.toInt()
        val red = 0xFFFF0000.toInt()
        val input = intArrayOf(target, red)
        val out = ColorMatchTransform.applyTransparent(input, target, threshold = 0)
        assertArrayEquals(intArrayOf(ColorMatchTransform.COLOR_TRANSPARENT, red), out)
    }

    @Test
    fun `applyTransparent does not mutate the input array`() {
        val target = 0xFF00FF00.toInt()
        val input = intArrayOf(target, target)
        val snapshot = input.copyOf()
        ColorMatchTransform.applyTransparent(input, target, threshold = 0)
        assertArrayEquals(snapshot, input)
    }

    @Test
    fun `analyze marks matching pixels with the overlay color and counts them`() {
        val target = 0xFF00FF00.toInt()
        val overlay = 0xFFFF00FF.toInt() // magenta = green's complement
        val red = 0xFFFF0000.toInt()
        val black = 0xFF000000.toInt()
        val input = intArrayOf(target, red, target, black)

        val result = ColorMatchTransform.analyze(input, target, threshold = 0, overlayRgb = overlay)

        assertArrayEquals(intArrayOf(overlay, red, overlay, black), result.pixels)
        assertEquals(2, result.matchCount)
    }

    @Test
    fun `analyze does not mutate the input array`() {
        val target = 0xFF00FF00.toInt()
        val input = intArrayOf(target, target)
        val snapshot = input.copyOf()
        ColorMatchTransform.analyze(input, target, threshold = 0, overlayRgb = 0xFFFF00FF.toInt())
        assertArrayEquals(snapshot, input)
    }

    @Test
    fun `threshold absorbs realistic per-pixel keyer drift`() {
        // Simulates the slight per-pixel variation typical of AI image
        // generators: target is pure green, sampled pixels deviate by up
        // to ±4 per channel.
        val target = 0xFF00FF00.toInt()
        val drifted = intArrayOf(
            0xFF03FB04.toInt(), // diffs (3, 4, 4)
            0xFF02FE01.toInt(), // diffs (2, 1, 1)
            0xFF04FC00.toInt(), // diffs (4, 3, 0)
        )
        for (px in drifted) {
            assertTrue(
                "Pixel 0x${"%08X".format(px)} should match target with threshold 4",
                ColorMatchTransform.isNearTarget(px, target, threshold = 4),
            )
        }
    }

    @Test
    fun `pure black against a saturated target requires threshold 255`() {
        // Distance-based selection — pure black is not auto-excluded the
        // way chiaroscuro's AmoledTransform excludes it. With a pure-green
        // target, the per-channel max delta is exactly 255 (the G channel),
        // so threshold 254 rejects and 255 accepts.
        val target = 0xFF00FF00.toInt()
        val pureBlack = 0xFF000000.toInt()
        assertFalse(ColorMatchTransform.isNearTarget(pureBlack, target, threshold = 254))
        assertTrue(ColorMatchTransform.isNearTarget(pureBlack, target, threshold = 255))
    }

    @Test
    fun `empty pixel array round-trips through every transform`() {
        val empty = IntArray(0)
        val target = 0xFF00FF00.toInt()
        assertEquals(0, ColorMatchTransform.applyAmoled(empty, target, threshold = 10).size)
        assertEquals(0, ColorMatchTransform.applyTransparent(empty, target, threshold = 10).size)
        val analysis = ColorMatchTransform.analyze(empty, target, threshold = 10, overlayRgb = 0xFFFF00FF.toInt())
        assertEquals(0, analysis.pixels.size)
        assertEquals(0, analysis.matchCount)
    }

    // ── Despill tests ────────────────────────────────────────────

    @Test
    fun `despillPixel removes green tint from edge pixel when target is green`() {
        val green = 0xFF00FF00.toInt()
        // (10, 214, 10): a typical antialiased motif-edge pixel mostly
        // composed of green spill. After despill, G should be capped at
        // max(R=10, B=10) = 10, yielding a dark gray.
        val edge = 0xFF0AD60A.toInt()
        assertEquals(0xFF0A0A0A.toInt(), ColorMatchTransform.despillPixel(edge, green))
    }

    @Test
    fun `despillPixel removes blue tint from edge pixel when target is blue`() {
        val blue = 0xFF0000FF.toInt()
        // (10, 40, 240) → B capped at max(R=10, G=40) = 40 → (10, 40, 40).
        val edge = 0xFF0A28F0.toInt()
        assertEquals(0xFF0A2828.toInt(), ColorMatchTransform.despillPixel(edge, blue))
    }

    @Test
    fun `despillPixel removes pink tint when target has two dominant channels`() {
        val pink = 0xFFFF00FF.toInt()
        // (250, 60, 250) → R and B are dominant for pink; both capped at
        // the pixel's non-dominant G = 60 → (60, 60, 60).
        val edge = 0xFFFA3CFA.toInt()
        assertEquals(0xFF3C3C3C.toInt(), ColorMatchTransform.despillPixel(edge, pink))
    }

    @Test
    fun `despillPixel is a no-op for an achromatic target`() {
        val gray = 0xFF808080.toInt() // all channels equal → no channel is dominant
        val pixel = 0xFFABCDEF.toInt()
        assertEquals(pixel, ColorMatchTransform.despillPixel(pixel, gray))
    }

    @Test
    fun `despillPixel preserves alpha`() {
        val green = 0xFF00FF00.toInt()
        val edgeWithAlpha = 0x800AD60A.toInt() // alpha 0x80
        assertEquals(0x800A0A0A.toInt(), ColorMatchTransform.despillPixel(edgeWithAlpha, green))
    }

    @Test
    fun `despillPixel is idempotent`() {
        val green = 0xFF00FF00.toInt()
        val edge = 0xFF0AD60A.toInt()
        val once = ColorMatchTransform.despillPixel(edge, green)
        val twice = ColorMatchTransform.despillPixel(once, green)
        assertEquals(once, twice)
    }

    @Test
    fun `despillPixel leaves a pixel without keyer tint unchanged`() {
        val green = 0xFF00FF00.toInt()
        val red = 0xFFFF0000.toInt() // (255, 0, 0) — no green to suppress
        // G dominant for green target; cap = max(R=255, B=0) = 255; G=min(0,255)=0. No change.
        assertEquals(red, ColorMatchTransform.despillPixel(red, green))
    }

    @Test
    fun `applyAmoled despills non-matching edge pixels`() {
        val green = 0xFF00FF00.toInt()
        val edge = 0xFF0AD60A.toInt() // (10, 214, 10): Chebyshev dist to green = 41, won't match @ 24
        val red = 0xFFFF0000.toInt()
        val input = intArrayOf(green, edge, red)
        val out = ColorMatchTransform.applyAmoled(input, green, threshold = 24)
        assertEquals(ColorMatchTransform.COLOR_AMOLED, out[0])
        assertEquals(0xFF0A0A0A.toInt(), out[1]) // despill'd to dark gray
        assertEquals(red, out[2])                // unchanged (no green tint)
    }

    @Test
    fun `applyTransparent despills non-matching edge pixels`() {
        val green = 0xFF00FF00.toInt()
        val edge = 0xFF0AD60A.toInt()
        val out = ColorMatchTransform.applyTransparent(intArrayOf(green, edge), green, threshold = 24)
        assertEquals(ColorMatchTransform.COLOR_TRANSPARENT, out[0])
        assertEquals(0xFF0A0A0A.toInt(), out[1])
    }
}
