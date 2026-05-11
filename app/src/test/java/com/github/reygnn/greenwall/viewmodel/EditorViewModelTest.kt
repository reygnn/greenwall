package com.github.reygnn.greenwall.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import app.cash.turbine.test
import com.github.reygnn.greenwall.imaging.complementaryRgb
import com.github.reygnn.greenwall.model.EditorState
import com.github.reygnn.greenwall.model.ExportMessage
import com.github.reygnn.greenwall.model.OutputMode
import com.github.reygnn.greenwall.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun `initial state matches the documented defaults`() = runTest(mainRule.testDispatcher) {
        val vm = newViewModel()
        val s = vm.state.value
        assertEquals(EditorState.DEFAULT_TARGET_COLOR, s.targetColor)
        assertEquals(EditorState.DEFAULT_THRESHOLD, s.threshold)
        assertEquals(OutputMode.AMOLED, s.outputMode)
        assertFalse(s.analysisVisible)
        assertFalse(s.pickerActive)
        assertFalse(s.sourceLoaded)
        assertFalse(s.isExporting)
        assertNull(s.exportMessage)
        assertNull(vm.sourceBitmap.value)
        assertNull(vm.overlayBitmap.value)
    }

    @Test
    fun `loadSource stores the bitmap and auto-detects the keyer color`() =
        runTest(mainRule.testDispatcher) {
            val bitmap = mockk<Bitmap>(relaxed = true)
            val detected = 0xFF112233.toInt()
            val transformer = FakeTransformer(detectedKeyer = detected)
            val vm = newViewModel(
                loader = FakeLoader(returns = bitmap),
                transformer = transformer,
            )

            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            assertTrue(vm.state.value.sourceLoaded)
            assertEquals(bitmap, vm.sourceBitmap.value)
            assertEquals(detected, vm.state.value.targetColor)
            assertEquals(1, transformer.detectCalls)
        }

    @Test
    fun `loadSource leaves sourceLoaded false and keeps prior target when loader returns null`() =
        runTest(mainRule.testDispatcher) {
            val transformer = FakeTransformer(detectedKeyer = 0xFF112233.toInt())
            val vm = newViewModel(loader = FakeLoader(returns = null), transformer = transformer)
            val initialTarget = vm.state.value.targetColor

            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            assertFalse(vm.state.value.sourceLoaded)
            assertEquals(initialTarget, vm.state.value.targetColor)
            assertNull(vm.sourceBitmap.value)
            assertEquals(0, transformer.detectCalls)
        }

    @Test
    fun `loadSource resets pickerActive`() = runTest(mainRule.testDispatcher) {
        val source = mockk<Bitmap>(relaxed = true)
        val vm = newViewModel(
            loader = FakeLoader(returns = source),
            transformer = FakeTransformer(detectedKeyer = GREEN),
        )
        vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
        advanceUntilIdle()
        vm.enablePicker()
        assertTrue(vm.state.value.pickerActive)

        vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
        advanceUntilIdle()

        assertFalse(vm.state.value.pickerActive)
    }

    @Test
    fun `setTargetColor updates state and invalidates the cached overlay`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val overlay = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                analysisOverlay = overlay,
            )
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            vm.toggleAnalysis()
            advanceUntilIdle()
            assertEquals(overlay, vm.overlayBitmap.value)

            vm.setTargetColor(BLUE)
            assertEquals(BLUE, vm.state.value.targetColor)
            assertNull("overlay must be invalidated", vm.overlayBitmap.value)
        }

    @Test
    fun `setTargetColor is a no-op when the value already matches`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val overlay = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                analysisOverlay = overlay,
            )
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.toggleAnalysis()
            advanceUntilIdle()

            // Already at GREEN; re-setting it must not clear the overlay.
            vm.setTargetColor(GREEN)

            assertEquals(overlay, vm.overlayBitmap.value)
        }

    @Test
    fun `setTargetColor normalizes alpha to FF`() = runTest(mainRule.testDispatcher) {
        val vm = newViewModel()
        // Caller passes 0x00112233 (alpha = 0). Stored value must be opaque.
        vm.setTargetColor(0x00112233)
        assertEquals(0xFF112233.toInt(), vm.state.value.targetColor)
    }

    @Test
    fun `setThreshold clamps to 0_255`() = runTest(mainRule.testDispatcher) {
        val vm = newViewModel()
        vm.setThreshold(-50)
        assertEquals(0, vm.state.value.threshold)
        vm.setThreshold(999)
        assertEquals(255, vm.state.value.threshold)
        vm.setThreshold(42)
        assertEquals(42, vm.state.value.threshold)
    }

    @Test
    fun `setThreshold invalidates the cached overlay`() = runTest(mainRule.testDispatcher) {
        val source = mockk<Bitmap>(relaxed = true)
        val overlay = mockk<Bitmap>(relaxed = true)
        val transformer = FakeTransformer(
            detectedKeyer = GREEN,
            analysisOverlay = overlay,
        )
        val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
        vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
        advanceUntilIdle()
        vm.toggleAnalysis()
        advanceUntilIdle()

        vm.setThreshold(100)

        assertNull(vm.overlayBitmap.value)
    }

    @Test
    fun `setOutputMode updates state`() = runTest(mainRule.testDispatcher) {
        val vm = newViewModel()
        assertEquals(OutputMode.AMOLED, vm.state.value.outputMode)

        vm.setOutputMode(OutputMode.TRANSPARENT)
        assertEquals(OutputMode.TRANSPARENT, vm.state.value.outputMode)
    }

    @Test
    fun `toggleAnalysis flips analysisVisible`() = runTest(mainRule.testDispatcher) {
        val vm = newViewModel()
        vm.toggleAnalysis()
        assertTrue(vm.state.value.analysisVisible)
        vm.toggleAnalysis()
        assertFalse(vm.state.value.analysisVisible)
    }

    @Test
    fun `toggleAnalysis on triggers analysis when no overlay is cached`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val overlay = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                analysisOverlay = overlay,
            )
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            vm.toggleAnalysis()
            advanceUntilIdle()

            assertEquals(overlay, vm.overlayBitmap.value)
            assertEquals(1, transformer.analyzeCalls)
        }

    @Test
    fun `toggleAnalysis on does not recompute when overlay is cached`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val overlay = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                analysisOverlay = overlay,
            )
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.toggleAnalysis()
            advanceUntilIdle()
            vm.toggleAnalysis()
            assertEquals(1, transformer.analyzeCalls)

            vm.toggleAnalysis()
            advanceUntilIdle()

            assertEquals("analyze must not be re-run while cache is valid", 1, transformer.analyzeCalls)
            assertEquals(overlay, vm.overlayBitmap.value)
        }

    @Test
    fun `runAnalysis uses the complementary color of the target as overlay color`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                analysisOverlay = mockk(relaxed = true),
            )
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            vm.runAnalysis()
            advanceUntilIdle()

            assertEquals(complementaryRgb(GREEN), transformer.lastAnalyzeOverlay)
        }

    @Test
    fun `runAnalysis is a no-op when no source is loaded`() = runTest(mainRule.testDispatcher) {
        val transformer = FakeTransformer(detectedKeyer = 0)
        val vm = newViewModel(transformer = transformer)

        vm.runAnalysis()
        advanceUntilIdle()

        assertEquals(0, transformer.analyzeCalls)
        assertNull(vm.overlayBitmap.value)
    }

    @Test
    fun `redetectKeyer re-runs detection on the current source`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(detectedKeyer = 0xFFAABBCC.toInt())
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            assertEquals(1, transformer.detectCalls)

            // Manual override, then ask for re-detection.
            vm.setTargetColor(PINK)
            assertEquals(PINK, vm.state.value.targetColor)

            vm.redetectKeyer()
            advanceUntilIdle()

            assertEquals(2, transformer.detectCalls)
            assertEquals(0xFFAABBCC.toInt(), vm.state.value.targetColor)
        }

    @Test
    fun `redetectKeyer is a no-op when no source is loaded`() = runTest(mainRule.testDispatcher) {
        val transformer = FakeTransformer(detectedKeyer = 0xFFAABBCC.toInt())
        val vm = newViewModel(transformer = transformer)

        vm.redetectKeyer()
        advanceUntilIdle()

        assertEquals(0, transformer.detectCalls)
    }

    // ── Picker ───────────────────────────────────────────────────

    @Test
    fun `enablePicker activates picker mode when a source is loaded`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val vm = newViewModel(
                loader = FakeLoader(returns = source),
                transformer = FakeTransformer(detectedKeyer = GREEN),
            )
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            vm.enablePicker()

            assertTrue(vm.state.value.pickerActive)
        }

    @Test
    fun `enablePicker is a no-op when no source is loaded`() = runTest(mainRule.testDispatcher) {
        val vm = newViewModel()
        vm.enablePicker()
        assertFalse(vm.state.value.pickerActive)
    }

    @Test
    fun `disablePicker deactivates picker mode`() = runTest(mainRule.testDispatcher) {
        val source = mockk<Bitmap>(relaxed = true)
        val vm = newViewModel(
            loader = FakeLoader(returns = source),
            transformer = FakeTransformer(detectedKeyer = GREEN),
        )
        vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
        advanceUntilIdle()
        vm.enablePicker()
        assertTrue(vm.state.value.pickerActive)

        vm.disablePicker()

        assertFalse(vm.state.value.pickerActive)
    }

    @Test
    fun `pickColorAt reads source pixel sets target and disables picker`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true).apply {
                every { width } returns 100
                every { height } returns 100
                every { getPixel(50, 50) } returns 0x00112233 // alpha 0 to verify normalization
            }
            val vm = newViewModel(
                loader = FakeLoader(returns = source),
                transformer = FakeTransformer(detectedKeyer = GREEN),
            )
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.enablePicker()

            vm.pickColorAt(50, 50)

            assertEquals(0xFF112233.toInt(), vm.state.value.targetColor)
            assertFalse(vm.state.value.pickerActive)
        }

    @Test
    fun `pickColorAt with out-of-bounds coords keeps target and disables picker`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true).apply {
                every { width } returns 100
                every { height } returns 100
            }
            val vm = newViewModel(
                loader = FakeLoader(returns = source),
                transformer = FakeTransformer(detectedKeyer = GREEN),
            )
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            val targetBeforePick = vm.state.value.targetColor
            vm.enablePicker()

            vm.pickColorAt(-1, 50)

            assertEquals(targetBeforePick, vm.state.value.targetColor)
            assertFalse(vm.state.value.pickerActive)
        }

    @Test
    fun `pickColorAt is a no-op when no source is loaded`() = runTest(mainRule.testDispatcher) {
        val vm = newViewModel()
        val targetBeforePick = vm.state.value.targetColor

        vm.pickColorAt(50, 50)

        assertEquals(targetBeforePick, vm.state.value.targetColor)
        assertFalse(vm.state.value.pickerActive)
    }

    // ── Save ─────────────────────────────────────────────────────

    @Test
    fun `saveResult in AMOLED mode applies the amoled transform and saves`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val amoledOut = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                amoledOutput = amoledOut,
            )
            val exporter = CountingExporter()
            val vm = newViewModel(
                loader = FakeLoader(returns = source),
                transformer = transformer,
                exporter = exporter,
            )
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            vm.saveResult(mockk(relaxed = true), "x.png")
            advanceUntilIdle()

            assertEquals(1, transformer.amoledCalls)
            assertEquals(0, transformer.transparentCalls)
            assertEquals(1, exporter.saveCalls)
            assertEquals(amoledOut, exporter.lastBitmap)
            assertEquals("x.png", exporter.lastName)
            assertEquals(ExportMessage.Saved, vm.state.value.exportMessage)
            assertFalse(vm.state.value.isExporting)
        }

    @Test
    fun `saveResult in TRANSPARENT mode applies the transparent transform and saves`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val transparentOut = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                transparentOutput = transparentOut,
            )
            val exporter = CountingExporter()
            val vm = newViewModel(
                loader = FakeLoader(returns = source),
                transformer = transformer,
                exporter = exporter,
            )
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.setOutputMode(OutputMode.TRANSPARENT)

            vm.saveResult(mockk(relaxed = true), "x.png")
            advanceUntilIdle()

            assertEquals(0, transformer.amoledCalls)
            assertEquals(1, transformer.transparentCalls)
            assertEquals(transparentOut, exporter.lastBitmap)
            assertEquals(ExportMessage.Saved, vm.state.value.exportMessage)
        }

    @Test
    fun `saveResult emits Error when the exporter throws`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                amoledOutput = mockk(relaxed = true),
            )
            val exporter = CountingExporter(throwing = RuntimeException("disk full"))
            val vm = newViewModel(
                loader = FakeLoader(returns = source),
                transformer = transformer,
                exporter = exporter,
            )
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            vm.saveResult(mockk(relaxed = true), "x.png")
            advanceUntilIdle()

            val msg = vm.state.value.exportMessage
            assertNotNull(msg)
            assertTrue(msg is ExportMessage.Error)
            assertEquals("disk full", (msg as ExportMessage.Error).message)
            assertFalse(vm.state.value.isExporting)
        }

    @Test
    fun `saveResult is a no-op when no source is loaded`() = runTest(mainRule.testDispatcher) {
        val transformer = FakeTransformer(detectedKeyer = 0)
        val exporter = CountingExporter()
        val vm = newViewModel(transformer = transformer, exporter = exporter)

        vm.saveResult(mockk(relaxed = true), "x.png")
        advanceUntilIdle()

        assertEquals(0, transformer.amoledCalls)
        assertEquals(0, exporter.saveCalls)
        assertNull(vm.state.value.exportMessage)
    }

    @Test
    fun `clearExportMessage clears the field`() = runTest(mainRule.testDispatcher) {
        val source = mockk<Bitmap>(relaxed = true)
        val vm = newViewModel(
            loader = FakeLoader(returns = source),
            transformer = FakeTransformer(
                detectedKeyer = GREEN,
                amoledOutput = mockk(relaxed = true),
            ),
            exporter = CountingExporter(),
        )
        vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
        advanceUntilIdle()
        vm.saveResult(mockk(relaxed = true), "x.png")
        advanceUntilIdle()
        assertNotNull(vm.state.value.exportMessage)

        vm.clearExportMessage()

        assertNull(vm.state.value.exportMessage)
    }

    @Test
    fun `state flow emits target color and threshold updates in order`() =
        runTest(mainRule.testDispatcher) {
            val vm = newViewModel()
            vm.state.test {
                assertEquals(EditorState.DEFAULT_TARGET_COLOR, awaitItem().targetColor)

                vm.setTargetColor(BLUE)
                assertEquals(BLUE, awaitItem().targetColor)

                vm.setThreshold(99)
                assertEquals(99, awaitItem().threshold)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── Fixtures ─────────────────────────────────────────────────

    private fun newViewModel(
        loader: EditorViewModel.BitmapLoader = FakeLoader(returns = null),
        transformer: EditorViewModel.BitmapTransformer = FakeTransformer(detectedKeyer = 0),
        exporter: EditorViewModel.BitmapExporter = CountingExporter(),
    ): EditorViewModel = EditorViewModel(
        loader = loader,
        transformer = transformer,
        exporter = exporter,
        ioDispatcher = mainRule.testDispatcher,
    )

    private class FakeLoader(private val returns: Bitmap?) : EditorViewModel.BitmapLoader {
        override fun load(context: Context, uri: Uri): Bitmap? = returns
    }

    private class FakeTransformer(
        var detectedKeyer: Int,
        var amoledOutput: Bitmap = mockk(relaxed = true),
        var transparentOutput: Bitmap = mockk(relaxed = true),
        var analysisOverlay: Bitmap = mockk(relaxed = true),
        var analysisMatchCount: Int = 0,
    ) : EditorViewModel.BitmapTransformer {

        var amoledCalls = 0
        var transparentCalls = 0
        var analyzeCalls = 0
        var detectCalls = 0
        var lastAnalyzeOverlay: Int? = null

        override fun applyAmoled(source: Bitmap, targetRgb: Int, threshold: Int): Bitmap {
            amoledCalls++
            return amoledOutput
        }

        override fun applyTransparent(source: Bitmap, targetRgb: Int, threshold: Int): Bitmap {
            transparentCalls++
            return transparentOutput
        }

        override fun analyze(
            source: Bitmap,
            targetRgb: Int,
            threshold: Int,
            overlayRgb: Int,
        ): AnalysisResult {
            analyzeCalls++
            lastAnalyzeOverlay = overlayRgb
            return AnalysisResult(analysisOverlay, analysisMatchCount)
        }

        override fun detectKeyerColor(source: Bitmap, borderPx: Int): Int {
            detectCalls++
            return detectedKeyer
        }
    }

    private class CountingExporter(
        private val throwing: Throwable? = null,
    ) : EditorViewModel.BitmapExporter {
        var saveCalls = 0
        var lastBitmap: Bitmap? = null
        var lastName: String? = null

        override fun save(context: Context, bitmap: Bitmap, displayName: String) {
            saveCalls++
            lastBitmap = bitmap
            lastName = displayName
            throwing?.let { throw it }
        }
    }

    companion object {
        private val GREEN: Int = 0xFF00FF00.toInt()
        private val BLUE: Int = 0xFF0000FF.toInt()
        private val PINK: Int = 0xFFFF00FF.toInt()
    }
}
