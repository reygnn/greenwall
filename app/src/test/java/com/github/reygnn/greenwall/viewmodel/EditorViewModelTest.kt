package com.github.reygnn.greenwall.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import app.cash.turbine.test
import com.github.reygnn.greenwall.imaging.complementaryRgb
import com.github.reygnn.greenwall.model.EditorState
import com.github.reygnn.greenwall.model.ExportMessage
import com.github.reygnn.greenwall.model.OutputMode
import com.github.reygnn.greenwall.model.ViewMode
import com.github.reygnn.greenwall.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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
        assertEquals(ViewMode.SOURCE, s.viewMode)
        assertFalse(s.pickerActive)
        assertFalse(s.sourceLoaded)
        assertFalse(s.isExporting)
        assertNull(s.exportMessage)
        assertNull(vm.sourceBitmap.value)
        assertNull(vm.overlayBitmap.value)
        assertNull(vm.previewBitmap.value)
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
    fun `loadSource resets pickerActive and viewMode to defaults`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val vm = newViewModel(
                loader = FakeLoader(returns = source),
                transformer = FakeTransformer(detectedKeyer = GREEN),
            )
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.enablePicker()
            vm.toggleAnalysis()
            advanceUntilIdle()
            assertTrue(vm.state.value.pickerActive)
            assertEquals(ViewMode.ANALYSIS, vm.state.value.viewMode)

            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            assertFalse(vm.state.value.pickerActive)
            assertEquals(ViewMode.SOURCE, vm.state.value.viewMode)
        }

    @Test
    fun `setTargetColor updates state and invalidates both cached bitmaps`() =
        runTest(mainRule.testDispatcher) {
            val (vm, transformer, overlay, preview) = bothCachesWarmed(GREEN)
            assertEquals(overlay, vm.overlayBitmap.value)
            assertEquals(preview, vm.previewBitmap.value)

            vm.setTargetColor(BLUE)

            assertEquals(BLUE, vm.state.value.targetColor)
            assertNull(vm.overlayBitmap.value)
            assertNull(vm.previewBitmap.value)
            // Sanity: the transformer was used to warm both caches once each.
            assertEquals(1, transformer.analyzeCalls)
            assertEquals(1, transformer.amoledCalls)
        }

    @Test
    fun `setTargetColor is a no-op when the value already matches`() =
        runTest(mainRule.testDispatcher) {
            val (vm, _, overlay, _) = bothCachesWarmed(GREEN)

            vm.setTargetColor(GREEN)

            assertEquals(overlay, vm.overlayBitmap.value)
        }

    @Test
    fun `setTargetColor normalizes alpha to FF`() = runTest(mainRule.testDispatcher) {
        val vm = newViewModel()
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
    fun `setThreshold invalidates both cached bitmaps`() = runTest(mainRule.testDispatcher) {
        val (vm, _, _, _) = bothCachesWarmed(GREEN)

        vm.setThreshold(100)

        assertNull(vm.overlayBitmap.value)
        assertNull(vm.previewBitmap.value)
    }

    @Test
    fun `setOutputMode updates state and invalidates only the preview cache`() =
        runTest(mainRule.testDispatcher) {
            val (vm, _, overlay, preview) = bothCachesWarmed(GREEN)
            assertEquals(OutputMode.AMOLED, vm.state.value.outputMode)

            vm.setOutputMode(OutputMode.TRANSPARENT)

            assertEquals(OutputMode.TRANSPARENT, vm.state.value.outputMode)
            assertEquals(overlay, vm.overlayBitmap.value) // overlay still valid
            assertNull(vm.previewBitmap.value)            // preview invalidated
            // ensure the assertion isn't a false positive
            assertNotNull(preview)
        }

    // ── viewMode toggles ─────────────────────────────────────────

    @Test
    fun `toggleAnalysis flips between SOURCE and ANALYSIS`() = runTest(mainRule.testDispatcher) {
        val vm = newViewModel()
        vm.toggleAnalysis()
        assertEquals(ViewMode.ANALYSIS, vm.state.value.viewMode)
        vm.toggleAnalysis()
        assertEquals(ViewMode.SOURCE, vm.state.value.viewMode)
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

    // ── Preview ──────────────────────────────────────────────────

    @Test
    fun `togglePreview flips between SOURCE and PREVIEW`() = runTest(mainRule.testDispatcher) {
        val source = mockk<Bitmap>(relaxed = true)
        val vm = newViewModel(
            loader = FakeLoader(returns = source),
            transformer = FakeTransformer(detectedKeyer = GREEN),
        )
        vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
        advanceUntilIdle()

        vm.togglePreview()
        advanceUntilIdle()
        assertEquals(ViewMode.PREVIEW, vm.state.value.viewMode)

        vm.togglePreview()
        assertEquals(ViewMode.SOURCE, vm.state.value.viewMode)
    }

    @Test
    fun `togglePreview on triggers preview compute when no cache`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val preview = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                amoledOutput = preview,
            )
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            vm.togglePreview()
            advanceUntilIdle()

            assertEquals(preview, vm.previewBitmap.value)
            assertEquals(1, transformer.amoledCalls)
        }

    @Test
    fun `togglePreview reuses the cached preview on subsequent toggles`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val preview = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                amoledOutput = preview,
            )
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.togglePreview()
            advanceUntilIdle()
            vm.togglePreview()

            vm.togglePreview()
            advanceUntilIdle()

            assertEquals(1, transformer.amoledCalls)
            assertEquals(preview, vm.previewBitmap.value)
        }

    @Test
    fun `runPreview in AMOLED mode calls applyAmoled and caches its output`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val amoledOut = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(detectedKeyer = GREEN, amoledOutput = amoledOut)
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            vm.runPreview()
            advanceUntilIdle()

            assertEquals(1, transformer.amoledCalls)
            assertEquals(0, transformer.transparentCalls)
            assertEquals(amoledOut, vm.previewBitmap.value)
        }

    @Test
    fun `runPreview in TRANSPARENT mode calls applyTransparent and caches its output`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val transparentOut = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                transparentOutput = transparentOut,
            )
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.setOutputMode(OutputMode.TRANSPARENT)

            vm.runPreview()
            advanceUntilIdle()

            assertEquals(0, transformer.amoledCalls)
            assertEquals(1, transformer.transparentCalls)
            assertEquals(transparentOut, vm.previewBitmap.value)
        }

    @Test
    fun `runPreview is a no-op when no source is loaded`() = runTest(mainRule.testDispatcher) {
        val transformer = FakeTransformer(detectedKeyer = 0)
        val vm = newViewModel(transformer = transformer)

        vm.runPreview()
        advanceUntilIdle()

        assertEquals(0, transformer.amoledCalls)
        assertEquals(0, transformer.transparentCalls)
        assertNull(vm.previewBitmap.value)
    }

    // ── Keyer redetect ───────────────────────────────────────────

    @Test
    fun `redetectKeyer re-runs detection on the current source`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(detectedKeyer = 0xFFAABBCC.toInt())
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            assertEquals(1, transformer.detectCalls)

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

    @Test
    fun `redetectKeyer invalidates both overlay and preview caches`() =
        runTest(mainRule.testDispatcher) {
            // Regression: the original implementation cleared the
            // analysis overlay but left _previewBitmap intact. After
            // 🎯 with a freshly-redetected color, toggling to PREVIEW
            // would show a stale image computed against the previous
            // target color.
            val (vm, _, overlay, preview) = bothCachesWarmed(GREEN)
            assertEquals(overlay, vm.overlayBitmap.value)
            assertEquals(preview, vm.previewBitmap.value)

            vm.redetectKeyer()
            advanceUntilIdle()

            assertNull(vm.overlayBitmap.value)
            assertNull(vm.previewBitmap.value)
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
    fun `enablePicker forces viewMode back to SOURCE`() = runTest(mainRule.testDispatcher) {
        // The canvas-tap → bitmap-pixel mapping reads from the source
        // bitmap regardless of which view mode is shown. If the user
        // enables the picker while looking at PREVIEW (despill'd) or
        // ANALYSIS (complement-recolored), what they see is not what
        // they'd pick — switch back to SOURCE so the tap is WYSIWYG.
        val source = mockk<Bitmap>(relaxed = true)
        val vm = newViewModel(
            loader = FakeLoader(returns = source),
            transformer = FakeTransformer(detectedKeyer = GREEN),
        )
        vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
        advanceUntilIdle()
        vm.toggleAnalysis()
        advanceUntilIdle()
        assertEquals(ViewMode.ANALYSIS, vm.state.value.viewMode)

        vm.enablePicker()

        assertTrue(vm.state.value.pickerActive)
        assertEquals(ViewMode.SOURCE, vm.state.value.viewMode)
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
                every { getPixel(50, 50) } returns 0x00112233
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

    // ── Picker view-mode preservation ───────────────────────────
    // enablePicker forces SOURCE so the tap is WYSIWYG; disablePicker
    // and pickColorAt must put the user back where they were so they
    // don't have to manually re-toggle ANALYSIS / PREVIEW.

    @Test
    fun `disablePicker restores the pre-picker ANALYSIS view without recompute when target is unchanged`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val overlay = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(detectedKeyer = GREEN, analysisOverlay = overlay)
            val vm = newViewModel(
                loader = FakeLoader(returns = source),
                transformer = transformer,
            )
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.toggleAnalysis()
            advanceUntilIdle()
            val analyzeCallsBefore = transformer.analyzeCalls
            assertEquals(ViewMode.ANALYSIS, vm.state.value.viewMode)

            vm.enablePicker()
            assertEquals(ViewMode.SOURCE, vm.state.value.viewMode)
            vm.disablePicker()
            advanceUntilIdle()

            assertEquals(ViewMode.ANALYSIS, vm.state.value.viewMode)
            // Overlay cache survived the pick (target unchanged), so no
            // recompute was needed.
            assertEquals(analyzeCallsBefore, transformer.analyzeCalls)
            assertNotNull(vm.overlayBitmap.value)
        }

    @Test
    fun `pickColorAt restores ANALYSIS and recomputes the overlay when target changed`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true).apply {
                every { width } returns 100
                every { height } returns 100
                every { getPixel(50, 50) } returns 0x00112233
            }
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                analysisOverlay = mockk(relaxed = true),
            )
            val vm = newViewModel(
                loader = FakeLoader(returns = source),
                transformer = transformer,
            )
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.toggleAnalysis()
            advanceUntilIdle()
            val analyzeCallsBefore = transformer.analyzeCalls

            vm.enablePicker()
            vm.pickColorAt(50, 50)
            advanceUntilIdle()

            // Restored to ANALYSIS — user doesn't have to re-toggle.
            assertEquals(ViewMode.ANALYSIS, vm.state.value.viewMode)
            assertEquals(0xFF112233.toInt(), vm.state.value.targetColor)
            // Target changed → cache was invalidated during pick → restore
            // path kicked off a fresh analysis using the NEW target color.
            assertEquals(analyzeCallsBefore + 1, transformer.analyzeCalls)
            assertNotNull(vm.overlayBitmap.value)
        }

    @Test
    fun `pickColorAt restores PREVIEW and recomputes the preview when target changed`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true).apply {
                every { width } returns 100
                every { height } returns 100
                every { getPixel(50, 50) } returns 0x00112233
            }
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                amoledOutput = mockk(relaxed = true),
            )
            val vm = newViewModel(
                loader = FakeLoader(returns = source),
                transformer = transformer,
            )
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.togglePreview()
            advanceUntilIdle()
            val amoledCallsBefore = transformer.amoledCalls

            vm.enablePicker()
            vm.pickColorAt(50, 50)
            advanceUntilIdle()

            assertEquals(ViewMode.PREVIEW, vm.state.value.viewMode)
            assertEquals(0xFF112233.toInt(), vm.state.value.targetColor)
            assertEquals(amoledCallsBefore + 1, transformer.amoledCalls)
            assertNotNull(vm.previewBitmap.value)
        }

    @Test
    fun `pickColorAt out-of-bounds restores PREVIEW without recompute`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true).apply {
                every { width } returns 100
                every { height } returns 100
            }
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                amoledOutput = mockk(relaxed = true),
            )
            val vm = newViewModel(
                loader = FakeLoader(returns = source),
                transformer = transformer,
            )
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.togglePreview()
            advanceUntilIdle()
            val amoledCallsBefore = transformer.amoledCalls
            val targetBefore = vm.state.value.targetColor

            vm.enablePicker()
            vm.pickColorAt(-1, 50) // out-of-bounds → no target change
            advanceUntilIdle()

            assertEquals(ViewMode.PREVIEW, vm.state.value.viewMode)
            assertEquals(targetBefore, vm.state.value.targetColor)
            // Cache survived (target unchanged), no recompute needed.
            assertEquals(amoledCallsBefore, transformer.amoledCalls)
            assertNotNull(vm.previewBitmap.value)
        }

    @Test
    fun `loadSource clears any preserved view mode from a prior picker session`() =
        runTest(mainRule.testDispatcher) {
            // If the user enables the picker while in PREVIEW, then loads
            // a different image without disabling the picker (e.g. via the
            // OS file picker triggered some other way), the next picker
            // session on the new image must not silently restore PREVIEW.
            val source = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                amoledOutput = mockk(relaxed = true),
            )
            val vm = newViewModel(
                loader = FakeLoader(returns = source),
                transformer = transformer,
            )
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.togglePreview()
            advanceUntilIdle()
            vm.enablePicker()
            // Don't disable — simulate a fresh load mid-pick.
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            // Fresh picker session on the new source should record SOURCE,
            // not the stale PREVIEW from before the reload.
            vm.enablePicker()
            vm.disablePicker()

            assertEquals(ViewMode.SOURCE, vm.state.value.viewMode)
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
    fun `saveResult lets CancellationException propagate and does not emit Error`() =
        runTest(mainRule.testDispatcher) {
            // Regression for the original `runCatching { ... }.getOrElse { ... }`
            // which caught CancellationException too and surfaced it as
            // ExportMessage.Error("Job was cancelled"). The fix re-throws
            // CancellationException so structured concurrency is preserved.
            //
            // viewModelScope uses a SupervisorJob, so the cancelled child
            // does not fail the surrounding test scope.
            //
            // Note: isExporting remains `true` after this path because the
            // `_state.update { isExporting = false, ... }` line lives after
            // withContext and is unreachable when withContext re-throws.
            // In production this only happens during VM destruction where
            // the state is discarded — harmless. The assertion below pins
            // this as deliberate behavior, not an accidental regression.
            val source = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                amoledOutput = mockk(relaxed = true),
            )
            val exporter = CountingExporter(
                throwing = CancellationException("simulated cancellation"),
            )
            val vm = newViewModel(
                loader = FakeLoader(returns = source),
                transformer = transformer,
                exporter = exporter,
            )
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            vm.saveResult(mockk(relaxed = true), "x.png")
            advanceUntilIdle()

            // The crucial assertion: cancellation is NOT mis-reported as an
            // export error.
            assertNull(
                "exportMessage must remain null when the save coroutine is cancelled",
                vm.state.value.exportMessage,
            )
            // Documented side effect of structured cancellation: isExporting
            // stays true because the post-withContext update is skipped.
            assertTrue(vm.state.value.isExporting)
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

    // ── Live recompute on parameter changes ──────────────────────
    //
    // When the user changes target / threshold / outputMode while
    // already viewing ANALYSIS or PREVIEW, the matching cache must be
    // refreshed automatically — otherwise the canvas collapses back to
    // the bare source bitmap (`overlay ?: source` / `preview ?: source`
    // in ImageCanvas) and live feedback during slider drags is lost.

    @Test
    fun `setThreshold in ANALYSIS view re-runs analysis`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val overlay1 = mockk<Bitmap>(relaxed = true)
            val overlay2 = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                analysisOverlay = overlay1,
            )
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.toggleAnalysis()
            advanceUntilIdle()
            assertEquals(overlay1, vm.overlayBitmap.value)
            assertEquals(1, transformer.analyzeCalls)

            transformer.analysisOverlay = overlay2
            vm.setThreshold(50)
            advanceUntilIdle()

            assertEquals(2, transformer.analyzeCalls)
            assertEquals(overlay2, vm.overlayBitmap.value)
            assertEquals(ViewMode.ANALYSIS, vm.state.value.viewMode)
        }

    @Test
    fun `setThreshold in PREVIEW view re-runs preview`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val preview1 = mockk<Bitmap>(relaxed = true)
            val preview2 = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                amoledOutput = preview1,
            )
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.togglePreview()
            advanceUntilIdle()
            assertEquals(preview1, vm.previewBitmap.value)
            assertEquals(1, transformer.amoledCalls)

            transformer.amoledOutput = preview2
            vm.setThreshold(75)
            advanceUntilIdle()

            assertEquals(2, transformer.amoledCalls)
            assertEquals(preview2, vm.previewBitmap.value)
        }

    @Test
    fun `setThreshold in SOURCE view does not trigger any recompute`() =
        runTest(mainRule.testDispatcher) {
            // The point of auto-recompute is to keep the live view live;
            // when the user is viewing the bare source, neither cache
            // needs to be populated until they toggle into it.
            val source = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(detectedKeyer = GREEN)
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            vm.setThreshold(50)
            advanceUntilIdle()

            assertEquals(0, transformer.analyzeCalls)
            assertEquals(0, transformer.amoledCalls)
            assertEquals(0, transformer.transparentCalls)
        }

    @Test
    fun `rapid setThreshold drag in ANALYSIS coalesces into a single recompute`() =
        runTest(mainRule.testDispatcher) {
            // A slider drag fires setThreshold on every tick. The debounced
            // recompute trigger must collapse the whole burst into one
            // analyze() once the value settles, instead of spawning a
            // pixel-kernel worker per tick (battery / heat on big bitmaps).
            val source = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                analysisOverlay = mockk(relaxed = true),
            )
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.toggleAnalysis()
            advanceUntilIdle()
            assertEquals(1, transformer.analyzeCalls)

            // Many ticks with no virtual time advancing between them, so
            // they all land inside a single debounce window.
            for (t in 30..60) vm.setThreshold(t)
            advanceUntilIdle()

            assertEquals("a settled drag must recompute exactly once", 2, transformer.analyzeCalls)
            // The recompute reflects the latest slider value, not an
            // intermediate one that got coalesced away.
            assertEquals(60, vm.state.value.threshold)
        }

    @Test
    fun `well-separated setThreshold changes each trigger their own recompute`() =
        runTest(mainRule.testDispatcher) {
            // Debounce coalesces a burst, but it must not permanently
            // suppress: two changes that each settle before the next one
            // arrives recompute independently.
            val source = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                analysisOverlay = mockk(relaxed = true),
            )
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.toggleAnalysis()
            advanceUntilIdle()
            assertEquals(1, transformer.analyzeCalls)

            vm.setThreshold(50)
            advanceUntilIdle() // value settles → recompute #2
            vm.setThreshold(120)
            advanceUntilIdle() // settles again → recompute #3

            assertEquals(3, transformer.analyzeCalls)
        }

    @Test
    fun `setTargetColor in PREVIEW view re-runs preview`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                amoledOutput = mockk(relaxed = true),
            )
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.togglePreview()
            advanceUntilIdle()
            assertEquals(1, transformer.amoledCalls)

            transformer.amoledOutput = mockk(relaxed = true)
            vm.setTargetColor(BLUE)
            advanceUntilIdle()

            assertEquals(2, transformer.amoledCalls)
            assertNotNull(vm.previewBitmap.value)
        }

    @Test
    fun `setOutputMode in PREVIEW view re-runs preview with the new mode`() =
        runTest(mainRule.testDispatcher) {
            val source = mockk<Bitmap>(relaxed = true)
            val transformer = FakeTransformer(
                detectedKeyer = GREEN,
                amoledOutput = mockk(relaxed = true),
                transparentOutput = mockk(relaxed = true),
            )
            val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
            vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()
            vm.togglePreview() // computes AMOLED preview
            advanceUntilIdle()
            assertEquals(1, transformer.amoledCalls)
            assertEquals(0, transformer.transparentCalls)

            vm.setOutputMode(OutputMode.TRANSPARENT)
            advanceUntilIdle()

            // No additional AMOLED call; a TRANSPARENT preview was computed instead.
            assertEquals(1, transformer.amoledCalls)
            assertEquals(1, transformer.transparentCalls)
            assertEquals(transformer.transparentOutput, vm.previewBitmap.value)
        }

    // ── Fixtures ─────────────────────────────────────────────────

    /**
     * Builds a VM with a loaded source and both analysis-overlay and
     * preview caches warmed, then returns the components for assertion.
     */
    private fun TestScope.bothCachesWarmed(target: Int): WarmedCaches {
        val source = mockk<Bitmap>(relaxed = true)
        val overlay = mockk<Bitmap>(relaxed = true)
        val preview = mockk<Bitmap>(relaxed = true)
        val transformer = FakeTransformer(
            detectedKeyer = target,
            analysisOverlay = overlay,
            amoledOutput = preview,
        )
        val vm = newViewModel(loader = FakeLoader(returns = source), transformer = transformer)
        vm.loadSource(mockk(relaxed = true), mockk(relaxed = true))
        advanceUntilIdle()
        vm.toggleAnalysis()
        advanceUntilIdle()
        vm.toggleAnalysis() // back to SOURCE so the overlay cache persists
        vm.togglePreview()
        advanceUntilIdle()
        vm.togglePreview() // back to SOURCE so the preview cache persists
        return WarmedCaches(vm, transformer, overlay, preview)
    }

    private data class WarmedCaches(
        val vm: EditorViewModel,
        val transformer: FakeTransformer,
        val overlay: Bitmap,
        val preview: Bitmap,
    )

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
