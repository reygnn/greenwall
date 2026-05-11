package com.github.reygnn.greenwall.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.github.reygnn.greenwall.R
import com.github.reygnn.greenwall.imaging.ImageGeometry

/**
 * Single-bitmap canvas: shows [source] fit-center. When [analysisVisible]
 * is true and [overlay] is non-null, the overlay bitmap replaces the
 * source — the overlay is the analysis output (full-size bitmap with
 * matching pixels recolored).
 *
 * When [pickerActive] is true, taps on the canvas are routed to [onPick]
 * (with bitmap-pixel coordinates) for taps inside the drawn bitmap, or
 * to [onCancel] for taps on the empty surrounding area. Outside picker
 * mode, taps are ignored.
 */
@Composable
fun ImageCanvas(
    source: ImageBitmap?,
    overlay: ImageBitmap?,
    analysisVisible: Boolean,
    pickerActive: Boolean,
    onPick: (Int, Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(pickerActive, source) {
                if (pickerActive && source != null) {
                    detectTapGestures { offset ->
                        val pixel = ImageGeometry.canvasToBitmapPixel(
                            canvasX = offset.x,
                            canvasY = offset.y,
                            bmpW = source.width,
                            bmpH = source.height,
                            canvasW = size.width.toFloat(),
                            canvasH = size.height.toFloat(),
                        )
                        if (pixel != null) {
                            onPick(pixel.first, pixel.second)
                        } else {
                            onCancel()
                        }
                    }
                }
            },
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
                val placement = ImageGeometry.fitCenter(
                    bmpW = shown.width,
                    bmpH = shown.height,
                    canvasW = size.width,
                    canvasH = size.height,
                )
                drawImage(
                    image = shown,
                    dstOffset = IntOffset(placement.originX.toInt(), placement.originY.toInt()),
                    dstSize = IntSize(placement.drawnW.toInt(), placement.drawnH.toInt()),
                )
            }
        }
    }
}
