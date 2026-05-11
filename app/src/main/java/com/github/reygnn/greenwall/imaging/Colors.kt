package com.github.reygnn.greenwall.imaging

/**
 * Returns the per-channel RGB complement of [argb] — each channel is
 * replaced by `255 - channel`. Alpha is forced to `0xFF` so the result
 * is always opaque.
 *
 * Used to pick a contrasting overlay color for analysis: complement of
 * green is magenta, complement of blue is yellow, complement of pink
 * is dark cyan-ish — each pair is visually distinct on the original
 * keyer backdrop.
 */
internal fun complementaryRgb(argb: Int): Int {
    val r = 255 - ((argb shr 16) and 0xFF)
    val g = 255 - ((argb shr 8) and 0xFF)
    val b = 255 - (argb and 0xFF)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
