package com.github.reygnn.greenwall.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Real-image verification of the despill pipeline against
 * `femdroid.png` — a representative AI-generated tattoo motif on a
 * green keyer backdrop (704 × 1504, RGB, no alpha).
 *
 * Why a real image: the synthetic tests in [ColorMatchTransformTest]
 * pin the despill math on hand-crafted pixels, but cannot catch
 * regressions like "I forgot to call despill before/after match
 * replacement" on the kind of antialiased edges AI generators
 * actually emit. The invariants here are mathematical — for any pixel
 * of the despilled output `G ≤ max(R, B)` holds with zero tolerance,
 * because despill caps G at exactly that value when the target is
 * pure green and matched pixels (COLOR_AMOLED / COLOR_TRANSPARENT)
 * trivially satisfy it as well.
 *
 * `@Config(sdk = [36])` + `@GraphicsMode(NATIVE)` mandatory — see
 * TESTING_CONVENTIONS.md §3.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DespillRobolectricTest {

    @Test
    fun `detectKeyerColor on femdroid identifies a clearly green-dominant backdrop`() {
        // AI generators don't emit a pure 0xFF00FF00 — typical Gemini
        // output is a fluorescent lime-green (something like R ≈ 0,
        // G ≈ 200, B ≈ 0). The assertion is therefore "G dominates
        // strongly" rather than "G is near 255".
        val source = loadTestImage()
        val detected = ImageProcessing.detectKeyerColor(source)
        val r = (detected shr 16) and 0xFF
        val g = (detected shr 8) and 0xFF
        val b = detected and 0xFF
        val msg = "Detected color should be clearly green-dominant. Got (R=$r, G=$g, B=$b)"
        assertTrue(msg, g > 100)
        assertTrue(msg, g > r + 50)
        assertTrue(msg, g > b + 50)
    }

    @Test
    fun `applyAmoled on femdroid satisfies the despill invariant and produces AMOLED pixels`() {
        val source = loadTestImage()
        val keyer = ImageProcessing.detectKeyerColor(source)
        val result = ImageProcessing.applyAmoled(source, keyer, threshold = 24)

        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)

        var violations = 0
        var amoledCount = 0
        for (p in pixels) {
            if (p == ColorMatchTransform.COLOR_AMOLED) amoledCount++
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (g > maxOf(r, b)) violations++
        }
        assertEquals("Pixels violating G <= max(R, B) after despill", 0, violations)
        assertTrue("There should be AMOLED-black pixels in the output", amoledCount > 0)
    }

    @Test
    fun `applyTransparent on femdroid satisfies the despill invariant and produces transparent pixels`() {
        val source = loadTestImage()
        val keyer = ImageProcessing.detectKeyerColor(source)
        val result = ImageProcessing.applyTransparent(source, keyer, threshold = 24)

        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)

        var violations = 0
        var transparentCount = 0
        for (p in pixels) {
            if (((p ushr 24) and 0xFF) == 0) transparentCount++
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (g > maxOf(r, b)) violations++
        }
        assertEquals("Pixels violating G <= max(R, B) after despill", 0, violations)
        assertTrue("There should be transparent pixels in the output", transparentCount > 0)
    }

    private fun loadTestImage(): Bitmap {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("femdroid.png")) {
            "femdroid.png not found on the test classpath"
        }
        return stream.use { BitmapFactory.decodeStream(it) }
            ?: error("Failed to decode femdroid.png")
    }
}
