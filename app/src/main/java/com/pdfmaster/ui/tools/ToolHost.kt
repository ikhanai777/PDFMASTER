package com.pdfmaster.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pdfmaster.AppContainer
import com.pdfmaster.R
import com.pdfmaster.billing.ToolId
import com.pdfmaster.pdf.PasswordRequiredException
import com.pdfmaster.ui.LocalActions
import com.pdfmaster.ui.common.BusyDialog
import com.pdfmaster.ui.common.ErrorDialog
import com.pdfmaster.ui.common.ResultCard
import com.pdfmaster.ui.common.ScreenScaffold
import com.pdfmaster.ui.organize.OrganizeScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ToolHost(tool: ToolId, path: String?) {
    when (tool) {
        ToolId.MERGE -> MergeScreen()
        ToolId.SPLIT -> SplitScreen(path, extractOnly = false)
        ToolId.EXTRACT -> SplitScreen(path, extractOnly = true)
        ToolId.ORGANIZE_PAGES -> OrganizeScreen(path)
        ToolId.FILL_FORM -> FormFillScreen(path)
        else -> SingleFileTool(tool, path)
    }
}

/** Progress and outcome of a tool run. */
class RunState {
    var running by mutableStateOf(false)
    var progress by mutableStateOf<Float?>(null)
    var results by mutableStateOf<List<File>>(emptyList())
    var summary by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
}

/**
 * Runs [block] off the main thread, records the use for daily limits / trials only on
 * success, and registers PDF outputs in the library.
 */
fun CoroutineScope.runTool(
    state: RunState,
    container: AppContainer,
    tool: ToolId,
    errorText: (Throwable) -> String,
    block: suspend (onProgress: (Float) -> Unit) -> List<File>,
) {
    launch {
        state.running = true
        state.progress = null
        state.error = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                block { p -> state.progress = p }.also { files ->
                    files.filter { it.extension == "pdf" && it.absolutePath.startsWith(container.documents.docsDir.absolutePath) }
                        .forEach { container.documents.register(it) }
                }
            }
        }
        state.running = false
        result.onSuccess {
            state.results = it
            container.entitlements.recordCompletion(tool)
        }.onFailure { state.error = errorText(it) }
    }
}

@Composable
fun errorMessage(): (Throwable) -> String {
    val needsPassword = stringResource(R.string.error_password)
    val generic = stringResource(R.string.error_generic)
    return { t -> if (t is PasswordRequiredException) needsPassword else t.message?.let { "$generic\n\n$it" } ?: generic }
}

/** Standard layout for a tool: scrollable options, a primary action, and the result. */
@Composable
fun ToolLayout(
    tool: ToolId,
    state: RunState,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    content: @Composable () -> Unit,
) {
    val actions = LocalActions.current
    ScreenScaffold(title = toolTitle(tool), onBack = actions::back) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(tool.ui().description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
            Button(onClick = onAction, enabled = actionEnabled && !state.running, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(actionLabel)
            }
            if (state.results.isNotEmpty()) {
                ResultCard(state.results, onOpen = { actions.openViewer(it.absolutePath) }, extra = state.summary)
            }
        }
    }
    if (state.running) BusyDialog(stringResource(R.string.working), state.progress)
    state.error?.let { ErrorDialog(it) { state.error = null } }
}

@Composable
fun rememberRunState() = remember { RunState() }
