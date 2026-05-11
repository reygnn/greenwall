package com.github.reygnn.greenwall.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.greenwall.R
import com.github.reygnn.greenwall.model.ExportMessage
import com.github.reygnn.greenwall.ui.components.CommandsPanel
import com.github.reygnn.greenwall.ui.components.EditorFab
import com.github.reygnn.greenwall.ui.components.ImageCanvas
import com.github.reygnn.greenwall.viewmodel.EditorViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EditorScreen(
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sourceBitmap by viewModel.sourceBitmap.collectAsStateWithLifecycle()
    val overlayBitmap by viewModel.overlayBitmap.collectAsStateWithLifecycle()

    val pickSource = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.loadSource(context, uri) }

    val errorTemplate = stringResource(R.string.msg_error)
    val savedText = stringResource(R.string.msg_saved)
    LaunchedEffect(state.exportMessage) {
        when (val msg = state.exportMessage) {
            ExportMessage.Saved -> {
                Toast.makeText(context, savedText, Toast.LENGTH_SHORT).show()
                viewModel.clearExportMessage()
            }
            is ExportMessage.Error -> {
                Toast.makeText(
                    context,
                    errorTemplate.format(msg.message ?: "unknown"),
                    Toast.LENGTH_LONG,
                ).show()
                viewModel.clearExportMessage()
            }
            null -> Unit
        }
    }

    val defaultFilename = stringResource(R.string.export_default_filename)
    var commandsOpen by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val sourceImg = remember(sourceBitmap) { sourceBitmap?.asImageBitmap() }
            val overlayImg = remember(overlayBitmap) { overlayBitmap?.asImageBitmap() }

            ImageCanvas(
                source = sourceImg,
                overlay = overlayImg,
                analysisVisible = state.analysisVisible,
                pickerActive = state.pickerActive,
                onPick = { bx, by -> viewModel.pickColorAt(bx, by) },
                onCancel = viewModel::disablePicker,
                modifier = Modifier.fillMaxSize(),
            )

            EditorFab(
                canAnalyze = state.sourceLoaded,
                onToggleAnalysis = viewModel::toggleAnalysis,
                onRedetectKeyer = viewModel::redetectKeyer,
                onOpenCommands = { commandsOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )

            if (commandsOpen) {
                CommandsPanel(
                    state = state,
                    onPickSource = {
                        pickSource.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onUsePicker = {
                        commandsOpen = false
                        viewModel.enablePicker()
                    },
                    onThresholdChange = viewModel::setThreshold,
                    onOutputModeChange = viewModel::setOutputMode,
                    onSave = {
                        viewModel.saveResult(context, generateFilename(defaultFilename))
                    },
                    onClose = { commandsOpen = false },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                )
            }
        }
    }
}

private fun generateFilename(default: String): String {
    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return "greenwall_$ts.png".ifBlank { default }
}
