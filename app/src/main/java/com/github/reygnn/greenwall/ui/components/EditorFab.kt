package com.github.reygnn.greenwall.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Floating "tools" FAB with a vertical speed-dial menu and free-form
 * drag positioning. Mirrors coverup's pattern: drag to reposition, tap
 * the main FAB to expand the speed-dial, tap a mini-FAB to run its
 * action and collapse.
 *
 * Mini-FABs (top to bottom when expanded):
 *  - ☰  open the floating [CommandsPanel] for less-frequent actions
 *  - 🎯  re-run keyer auto-detection on the current source
 *  - 👁  toggle the analysis overlay
 *
 * Position is local UI state — not persisted across process death.
 */
@Composable
fun EditorFab(
    canAnalyze: Boolean,
    onToggleAnalysis: () -> Unit,
    onRedetectKeyer: () -> Unit,
    onOpenCommands: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offset += drag
                }
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(visible = expanded) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MiniFab(symbol = "☰", enabled = true) {
                        expanded = false
                        onOpenCommands()
                    }
                    MiniFab(symbol = "🎯", enabled = canAnalyze) {
                        expanded = false
                        onRedetectKeyer()
                    }
                    MiniFab(symbol = "👁", enabled = canAnalyze) {
                        expanded = false
                        onToggleAnalysis()
                    }
                }
            }

            FloatingActionButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "✕" else "🛠", fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun MiniFab(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = { if (enabled) onClick() },
    ) {
        Text(symbol, fontSize = 20.sp)
    }
}
