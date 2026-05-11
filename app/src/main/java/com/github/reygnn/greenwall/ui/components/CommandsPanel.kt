package com.github.reygnn.greenwall.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.reygnn.greenwall.R
import com.github.reygnn.greenwall.model.EditorState
import com.github.reygnn.greenwall.model.OutputMode
import kotlin.math.roundToInt

/**
 * Floating Material card with the less-frequent editor actions:
 *  - Pick source image
 *  - Show current target color swatch + activate the pipette (pick from canvas)
 *  - Threshold slider (0..255)
 *  - Output mode segmented control (AMOLED / Transparent)
 *  - Save PNG
 *
 * The title row is the drag handle — drag handling is restricted to it
 * so slider interactions inside the body still work. Tap the ✕ in the
 * title to close. Position is local UI state; not persisted.
 */
@Composable
fun CommandsPanel(
    state: EditorState,
    onPickSource: () -> Unit,
    onUsePicker: () -> Unit,
    onThresholdChange: (Int) -> Unit,
    onOutputModeChange: (OutputMode) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var offset by remember { mutableStateOf(Offset.Zero) }

    Card(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .widthIn(min = 280.dp, max = 360.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            offset += drag
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.cp_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                val closeDesc = stringResource(R.string.cd_close_panel)
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.semantics { contentDescription = closeDesc },
                ) { Text("✕") }
            }

            OutlinedButton(
                onClick = onPickSource,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.bc_pick_source)) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorSwatch(argb = state.targetColor)
                OutlinedButton(
                    onClick = onUsePicker,
                    enabled = state.sourceLoaded,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.cp_use_picker)) }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.bc_threshold),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        state.threshold.toString(),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Slider(
                    value = state.threshold.toFloat(),
                    onValueChange = { onThresholdChange(it.roundToInt()) },
                    valueRange = EditorState.THRESHOLD_MIN.toFloat()..
                        EditorState.THRESHOLD_MAX.toFloat(),
                )
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.outputMode == OutputMode.AMOLED,
                    onClick = { onOutputModeChange(OutputMode.AMOLED) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.output_amoled)) }
                SegmentedButton(
                    selected = state.outputMode == OutputMode.TRANSPARENT,
                    onClick = { onOutputModeChange(OutputMode.TRANSPARENT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.output_transparent)) }
            }

            Button(
                onClick = onSave,
                enabled = state.sourceLoaded && !state.isExporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.bc_save_png))
            }
        }
    }
}

@Composable
private fun ColorSwatch(argb: Int) {
    val shape = MaterialTheme.shapes.small
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(36.dp)
            .background(Color(argb), shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
    )
}
