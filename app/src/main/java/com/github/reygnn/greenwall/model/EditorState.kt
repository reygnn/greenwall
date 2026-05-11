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
 * Preset keyer colors offered as quick overrides to the auto-detection.
 * Each preset's [argb] is fully opaque (alpha = 0xFF). The values
 * correspond to the colors AI image generators most commonly use as
 * keyer backdrops.
 */
enum class KeyerPreset(val argb: Int) {
    GREEN(0xFF00FF00.toInt()),
    BLUE(0xFF0000FF.toInt()),
    PINK(0xFFFF00FF.toInt()),
}

/**
 * Aggregate editor state. Immutable — the ViewModel emits a new instance
 * on every change.
 */
data class EditorState(
    /** RGB of the target keyer color; alpha is ignored downstream. */
    val targetColor: Int = KeyerPreset.GREEN.argb,
    /** Chebyshev threshold per channel, clamped to [THRESHOLD_MIN] .. [THRESHOLD_MAX]. */
    val threshold: Int = DEFAULT_THRESHOLD,
    val outputMode: OutputMode = OutputMode.AMOLED,
    /** When true, the canvas shows the analysis overlay instead of the source. */
    val analysisVisible: Boolean = false,
    val sourceLoaded: Boolean = false,
    val isExporting: Boolean = false,
    val exportMessage: ExportMessage? = null,
) {
    companion object {
        const val DEFAULT_THRESHOLD = 24
        const val THRESHOLD_MIN = 0
        const val THRESHOLD_MAX = 255
    }
}

sealed class ExportMessage {
    data object Saved : ExportMessage()
    data class Error(val message: String?) : ExportMessage()
}
