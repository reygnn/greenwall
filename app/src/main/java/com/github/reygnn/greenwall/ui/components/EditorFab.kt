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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.reygnn.greenwall.R
import kotlin.math.roundToInt

/**
 * Floating "tools" FAB with a vertical speed-dial menu and free-form
 * drag positioning. Mirrors coverup's pattern: drag to reposition, tap
 * the main FAB to expand the speed-dial, tap a mini-FAB to run its
 * action and collapse.
 *
 * Mini-FABs (top to bottom when expanded — fan upward from the main FAB):
 *  - ☰  open the floating [CommandsPanel] for less-frequent actions
 *  - 🎯  re-run keyer auto-detection on the current source
 *  - 🖼  toggle the preview view (final output before saving)
 *  - 👁  toggle the analysis overlay
 *
 * Every button carries an explicit content description for TalkBack —
 * the bare emoji is not enough on its own.
 *
 * Position is local UI state — not persisted across process death.
 */
@Composable
fun EditorFab(
    canAnalyze: Boolean,
    onToggleAnalysis: () -> Unit,
    onTogglePreview: () -> Unit,
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
                    MiniFab(
                        symbol = "☰",
                        contentDescription = stringResource(R.string.cd_open_commands),
                        enabled = true,
                    ) {
                        expanded = false
                        onOpenCommands()
                    }
                    MiniFab(
                        symbol = "🎯",
                        contentDescription = stringResource(R.string.cd_redetect_keyer),
                        enabled = canAnalyze,
                    ) {
                        expanded = false
                        onRedetectKeyer()
                    }
                    MiniFab(
                        symbol = "🖼",
                        contentDescription = stringResource(R.string.cd_toggle_preview),
                        enabled = canAnalyze,
                    ) {
                        expanded = false
                        onTogglePreview()
                    }
                    MiniFab(
                        symbol = "👁",
                        contentDescription = stringResource(R.string.cd_toggle_analysis),
                        enabled = canAnalyze,
                    ) {
                        expanded = false
                        onToggleAnalysis()
                    }
                }
            }

            val mainFabDesc = stringResource(R.string.cd_editor_fab)
            FloatingActionButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.semantics { contentDescription = mainFabDesc },
            ) {
                Text(if (expanded) "✕" else "🛠", fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun MiniFab(
    symbol: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SmallFloatingActionButton(
        onClick = { if (enabled) onClick() },
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.38f)
            .semantics {
                this.contentDescription = contentDescription
                if (!enabled) disabled()
            },
    ) {
        Text(symbol, fontSize = 20.sp)
    }
}
