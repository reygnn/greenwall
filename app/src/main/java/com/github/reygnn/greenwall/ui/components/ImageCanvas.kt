package com.github.reygnn.greenwall.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.github.reygnn.greenwall.R
import com.github.reygnn.greenwall.imaging.ImageGeometry
import com.github.reygnn.greenwall.model.ViewMode

/**
 * Single-bitmap canvas that shows source, analysis overlay, or preview
 * depending on [viewMode]. Falls back to source if the requested
 * variant is null (e.g. preview still computing).
 *
 * Pan / zoom: free 1-finger pan + 2-finger pinch zoom in [ZOOM_MIN]..
 * [ZOOM_MAX], applied to whichever bitmap is currently shown. State is
 * local to this composable and resets when the source bitmap changes.
 *
 * When [pickerActive] is true, transform gestures are replaced by tap
 * gestures: tap inside the drawn bitmap → [onPick] with bitmap pixel
 * indices; tap on the surrounding empty area → [onCancel]. The picker
 * mapping respects the current pan / zoom — what you see is what you
 * pick.
 */
@Composable
fun ImageCanvas(
    source: ImageBitmap?,
    overlay: ImageBitmap?,
    preview: ImageBitmap?,
    viewMode: ViewMode,
    pickerActive: Boolean,
    onPick: (Int, Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var offset by remember(source) { mutableStateOf(Offset.Zero) }
    var scale by remember(source) { mutableStateOf(1f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(pickerActive, source) {
                if (source == null) return@pointerInput
                if (pickerActive) {
                    detectTapGestures { tap ->
                        val pixel = ImageGeometry.canvasToBitmapPixel(
                            canvasX = tap.x,
                            canvasY = tap.y,
                            bmpW = source.width,
                            bmpH = source.height,
                            canvasW = size.width.toFloat(),
                            canvasH = size.height.toFloat(),
                            panX = offset.x,
                            panY = offset.y,
                            zoom = scale,
                        )
                        if (pixel != null) onPick(pixel.first, pixel.second)
                        else onCancel()
                    }
                } else {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(ZOOM_MIN, ZOOM_MAX)
                        val effectiveZoom = if (scale > 0f) newScale / scale else 1f
                        val canvasCenterX = size.width / 2f
                        val canvasCenterY = size.height / 2f
                        val newOffsetX =
                            effectiveZoom * offset.x +
                                (1f - effectiveZoom) * (centroid.x - canvasCenterX) +
                                pan.x
                        val newOffsetY =
                            effectiveZoom * offset.y +
                                (1f - effectiveZoom) * (centroid.y - canvasCenterY) +
                                pan.y
                        scale = newScale
                        offset = Offset(newOffsetX, newOffsetY)
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
            val shown = when (viewMode) {
                ViewMode.SOURCE -> source
                ViewMode.ANALYSIS -> overlay ?: source
                ViewMode.PREVIEW -> preview ?: source
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val placement = ImageGeometry.displayPlacement(
                    bmpW = shown.width,
                    bmpH = shown.height,
                    canvasW = size.width,
                    canvasH = size.height,
                    panX = offset.x,
                    panY = offset.y,
                    zoom = scale,
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

private const val ZOOM_MIN = 1f
private const val ZOOM_MAX = 20f
