package com.github.reygnn.greenwall.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.reygnn.greenwall.imaging.ImageExporter
import com.github.reygnn.greenwall.imaging.ImageProcessing
import com.github.reygnn.greenwall.imaging.KeyerDetection
import com.github.reygnn.greenwall.imaging.complementaryRgb
import com.github.reygnn.greenwall.model.EditorState
import com.github.reygnn.greenwall.model.ExportMessage
import com.github.reygnn.greenwall.model.OutputMode
import com.github.reygnn.greenwall.model.ViewMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Result of analyzing a bitmap for keyer matches: the overlay [bitmap]
 * (matching pixels recolored to the complementary overlay color) and
 * the number of pixels that matched.
 */
data class AnalysisResult(val overlay: Bitmap, val matchCount: Int)

class EditorViewModel(
    private val loader: BitmapLoader = AndroidBitmapLoader,
    private val transformer: BitmapTransformer = AndroidBitmapTransformer,
    private val exporter: BitmapExporter = AndroidBitmapExporter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _sourceBitmap = MutableStateFlow<Bitmap?>(null)
    val sourceBitmap: StateFlow<Bitmap?> = _sourceBitmap.asStateFlow()

    private val _overlayBitmap = MutableStateFlow<Bitmap?>(null)
    val overlayBitmap: StateFlow<Bitmap?> = _overlayBitmap.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    /**
     * Loads the image at [uri]. On success, auto-detects the keyer color
     * from the image's outer border and stores it as the new target.
     * On failure, the source stays unloaded and the prior target is kept.
     * Resets the picker mode and view mode in either case, and clears
     * both cached preview and analysis bitmaps.
     */
    fun loadSource(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(ioDispatcher) { loader.load(context, uri) }
            _overlayBitmap.value = null
            _previewBitmap.value = null
            _sourceBitmap.value = bitmap
            if (bitmap == null) {
                _state.update {
                    it.copy(
                        sourceLoaded = false,
                        pickerActive = false,
                        viewMode = ViewMode.SOURCE,
                    )
                }
                return@launch
            }
            val detected = withContext(ioDispatcher) {
                transformer.detectKeyerColor(bitmap, KeyerDetection.DEFAULT_BORDER_PX)
            }
            _state.update {
                it.copy(
                    sourceLoaded = true,
                    targetColor = detected,
                    pickerActive = false,
                    viewMode = ViewMode.SOURCE,
                )
            }
        }
    }

    fun setTargetColor(rgb: Int) {
        // Force alpha = 0xFF so callers (auto-detect, picker, presets) can
        // pass any ARGB int without worrying about transparent target colors
        // leaking into the UI swatch.
        val normalized = rgb or 0xFF000000.toInt()
        if (_state.value.targetColor == normalized) return
        _state.update { it.copy(targetColor = normalized) }
        _overlayBitmap.value = null
        _previewBitmap.value = null
    }

    /** Activates pick-from-canvas mode. No-op if no source is loaded. */
    fun enablePicker() {
        if (!_state.value.sourceLoaded) return
        if (_state.value.pickerActive) return
        _state.update { it.copy(pickerActive = true) }
    }

    /** Deactivates pick-from-canvas mode. Idempotent. */
    fun disablePicker() {
        if (!_state.value.pickerActive) return
        _state.update { it.copy(pickerActive = false) }
    }

    /**
     * Picks the source pixel at ([bitmapX], [bitmapY]) as the new target
     * color and deactivates picker mode. Out-of-bounds coordinates or a
     * missing source short-circuit to just disabling the picker.
     */
    fun pickColorAt(bitmapX: Int, bitmapY: Int) {
        val source = _sourceBitmap.value
        if (source == null) {
            disablePicker()
            return
        }
        if (bitmapX < 0 || bitmapY < 0 || bitmapX >= source.width || bitmapY >= source.height) {
            disablePicker()
            return
        }
        val picked = source.getPixel(bitmapX, bitmapY)
        setTargetColor(picked)
        disablePicker()
    }

    fun setThreshold(value: Int) {
        val clamped = value.coerceIn(EditorState.THRESHOLD_MIN, EditorState.THRESHOLD_MAX)
        if (_state.value.threshold == clamped) return
        _state.update { it.copy(threshold = clamped) }
        _overlayBitmap.value = null
        _previewBitmap.value = null
    }

    fun setOutputMode(mode: OutputMode) {
        if (_state.value.outputMode == mode) return
        _state.update { it.copy(outputMode = mode) }
        // Analysis overlay is independent of outputMode (it shows the match
        // mask only); only the preview cache needs to drop.
        _previewBitmap.value = null
    }

    /**
     * Switches the canvas between SOURCE and ANALYSIS. When entering
     * ANALYSIS, kicks off [runAnalysis] if no overlay is cached. When
     * called from PREVIEW, switches directly to ANALYSIS.
     */
    fun toggleAnalysis() {
        val current = _state.value.viewMode
        val next = if (current == ViewMode.ANALYSIS) ViewMode.SOURCE else ViewMode.ANALYSIS
        _state.update { it.copy(viewMode = next) }
        if (next == ViewMode.ANALYSIS && _overlayBitmap.value == null) {
            runAnalysis()
        }
    }

    /**
     * Switches the canvas between SOURCE and PREVIEW. When entering
     * PREVIEW, kicks off [runPreview] if no preview is cached. When
     * called from ANALYSIS, switches directly to PREVIEW.
     */
    fun togglePreview() {
        val current = _state.value.viewMode
        val next = if (current == ViewMode.PREVIEW) ViewMode.SOURCE else ViewMode.PREVIEW
        _state.update { it.copy(viewMode = next) }
        if (next == ViewMode.PREVIEW && _previewBitmap.value == null) {
            runPreview()
        }
    }

    /** Recomputes the overlay against the current source + target + threshold. */
    fun runAnalysis() {
        val source = _sourceBitmap.value ?: return
        val target = _state.value.targetColor
        val threshold = _state.value.threshold
        val overlayColor = complementaryRgb(target)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                transformer.analyze(source, target, threshold, overlayColor)
            }
            _overlayBitmap.value = result.overlay
        }
    }

    /**
     * Recomputes the preview bitmap by applying the current [OutputMode]
     * transformation to the source — same call path as [saveResult], so
     * the cached preview is 1:1 what would be written to disk.
     */
    fun runPreview() {
        val source = _sourceBitmap.value ?: return
        val target = _state.value.targetColor
        val threshold = _state.value.threshold
        val mode = _state.value.outputMode
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                when (mode) {
                    OutputMode.AMOLED -> transformer.applyAmoled(source, target, threshold)
                    OutputMode.TRANSPARENT -> transformer.applyTransparent(source, target, threshold)
                }
            }
            _previewBitmap.value = result
        }
    }

    /** Re-runs keyer auto-detection on the currently loaded source. */
    fun redetectKeyer() {
        val source = _sourceBitmap.value ?: return
        viewModelScope.launch {
            val detected = withContext(ioDispatcher) {
                transformer.detectKeyerColor(source, KeyerDetection.DEFAULT_BORDER_PX)
            }
            _state.update { it.copy(targetColor = detected) }
            _overlayBitmap.value = null
        }
    }

    /**
     * Applies the current [OutputMode] transformation to the source and
     * saves the result as a PNG. Sets `isExporting` while the work runs,
     * then emits an [ExportMessage] in [EditorState].
     */
    fun saveResult(context: Context, displayName: String) {
        val source = _sourceBitmap.value ?: return
        val target = _state.value.targetColor
        val threshold = _state.value.threshold
        val mode = _state.value.outputMode
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true) }
            val message: ExportMessage = withContext(ioDispatcher) {
                runCatching {
                    val result = when (mode) {
                        OutputMode.AMOLED -> transformer.applyAmoled(source, target, threshold)
                        OutputMode.TRANSPARENT -> transformer.applyTransparent(source, target, threshold)
                    }
                    exporter.save(context, result, displayName)
                    ExportMessage.Saved as ExportMessage
                }.getOrElse { e -> ExportMessage.Error(e.message) }
            }
            _state.update { it.copy(isExporting = false, exportMessage = message) }
        }
    }

    fun clearExportMessage() {
        _state.update { it.copy(exportMessage = null) }
    }

    /**
     * Indirection over BitmapFactory + ContentResolver so VM tests can
     * supply mocked bitmaps without hitting the framework's null stubs.
     */
    interface BitmapLoader {
        fun load(context: Context, uri: Uri): Bitmap?
    }

    /**
     * Indirection over [ImageProcessing] so VM tests can fake out the
     * pixel work without touching real Bitmap APIs.
     */
    interface BitmapTransformer {
        fun applyAmoled(source: Bitmap, targetRgb: Int, threshold: Int): Bitmap
        fun applyTransparent(source: Bitmap, targetRgb: Int, threshold: Int): Bitmap
        fun analyze(source: Bitmap, targetRgb: Int, threshold: Int, overlayRgb: Int): AnalysisResult
        fun detectKeyerColor(source: Bitmap, borderPx: Int): Int
    }

    /** Indirection over [ImageExporter.savePng] so VM tests can intercept saves. */
    interface BitmapExporter {
        fun save(context: Context, bitmap: Bitmap, displayName: String)
    }

    private object AndroidBitmapLoader : BitmapLoader {
        override fun load(context: Context, uri: Uri): Bitmap? =
            runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            }.getOrNull()
    }

    private object AndroidBitmapTransformer : BitmapTransformer {
        override fun applyAmoled(source: Bitmap, targetRgb: Int, threshold: Int): Bitmap =
            ImageProcessing.applyAmoled(source, targetRgb, threshold)

        override fun applyTransparent(source: Bitmap, targetRgb: Int, threshold: Int): Bitmap =
            ImageProcessing.applyTransparent(source, targetRgb, threshold)

        override fun analyze(
            source: Bitmap,
            targetRgb: Int,
            threshold: Int,
            overlayRgb: Int,
        ): AnalysisResult {
            val r = ImageProcessing.analyze(source, targetRgb, threshold, overlayRgb)
            return AnalysisResult(r.bitmap, r.matchCount)
        }

        override fun detectKeyerColor(source: Bitmap, borderPx: Int): Int =
            ImageProcessing.detectKeyerColor(source, borderPx)
    }

    private object AndroidBitmapExporter : BitmapExporter {
        override fun save(context: Context, bitmap: Bitmap, displayName: String) {
            ImageExporter.savePng(context, bitmap, displayName)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { EditorViewModel() }
        }
    }
}
