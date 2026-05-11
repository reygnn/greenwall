package com.github.reygnn.greenwall.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.github.reygnn.greenwall.R

/**
 * Single-bitmap canvas: shows [source] fit-center. When [analysisVisible]
 * is true and [overlay] is non-null, the overlay bitmap replaces the
 * source — the overlay is the analysis output (full-size bitmap with
 * matching pixels recolored).
 *
 * No gestures in v0.1 — pan/zoom can come later if needed.
 */
@Composable
fun ImageCanvas(
    source: ImageBitmap?,
    overlay: ImageBitmap?,
    analysisVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (source == null) {
            Text(
                text = stringResource(R.string.editor_empty_state),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        } else {
            val shown = if (analysisVisible && overlay != null) overlay else source
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height
                val fit = minOf(canvasW / shown.width, canvasH / shown.height)
                val drawnW = shown.width * fit
                val drawnH = shown.height * fit
                val x = (canvasW - drawnW) / 2f
                val y = (canvasH - drawnH) / 2f
                drawImage(
                    image = shown,
                    dstOffset = IntOffset(x.toInt(), y.toInt()),
                    dstSize = IntSize(drawnW.toInt(), drawnH.toInt()),
                )
            }
        }
    }
}
