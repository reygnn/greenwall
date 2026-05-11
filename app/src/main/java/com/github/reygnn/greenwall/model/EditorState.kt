package com.github.reygnn.greenwall.model

/**
 * Output format for the keyed image.
 *
 *  - [AMOLED] replaces matching pixels with opaque pure black (0xFF000000),
 *    so an AMOLED display can turn those pixels off.
 *  - [TRANSPARENT] replaces matching pixels with full transparency
 *    (0x00000000), producing a cutout that layers cleanly elsewhere.
 */
enum class OutputMode { AMOLED, TRANSPARENT }

/**
 * What the editor canvas is currently showing.
 *
 *  - [SOURCE] — the loaded source image, untouched.
 *  - [ANALYSIS] — the analysis overlay: matching pixels recolored to the
 *    complement of [EditorState.targetColor], everything else unchanged.
 *  - [PREVIEW] — the final result for the current [EditorState.outputMode]
 *    (AMOLED or transparent applied), 1:1 what would be written to disk
 *    by Save.
 *
 * Mutually exclusive: exactly one mode is active at a time.
 */
enum class ViewMode { SOURCE, ANALYSIS, PREVIEW }

/**
 * Aggregate editor state. Immutable — the ViewModel emits a new instance
 * on every change.
 */
data class EditorState(
    /** RGB of the target keyer color; alpha is always 0xFF. */
    val targetColor: Int = DEFAULT_TARGET_COLOR,
    /** Chebyshev threshold per channel, clamped to [THRESHOLD_MIN] .. [THRESHOLD_MAX]. */
    val threshold: Int = DEFAULT_THRESHOLD,
    val outputMode: OutputMode = OutputMode.AMOLED,
    /** What the canvas is currently rendering. */
    val viewMode: ViewMode = ViewMode.SOURCE,
    /** When true, the next canvas tap picks a color from the source. */
    val pickerActive: Boolean = false,
    val sourceLoaded: Boolean = false,
    val isExporting: Boolean = false,
    val exportMessage: ExportMessage? = null,
) {
    companion object {
        /** Default target = canonical pure green (Gemini's typical keyer). */
        val DEFAULT_TARGET_COLOR: Int = 0xFF00FF00.toInt()
        const val DEFAULT_THRESHOLD = 24
        const val THRESHOLD_MIN = 0
        const val THRESHOLD_MAX = 255
    }
}

sealed class ExportMessage {
    data object Saved : ExportMessage()
    data class Error(val message: String?) : ExportMessage()
}
