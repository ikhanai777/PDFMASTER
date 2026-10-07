package com.pdfmaster.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdfmaster.R
import com.pdfmaster.billing.ToolId
import com.pdfmaster.core.formatBytes
import com.pdfmaster.ui.LocalActions
import com.pdfmaster.ui.Routes
import com.pdfmaster.ui.common.DocumentRow
import com.pdfmaster.ui.common.LocalContainer
import com.pdfmaster.ui.tools.ui
import kotlinx.coroutines.launch

private const val SUGGEST_COMPRESS_BYTES = 10L * 1024 * 1024

@Composable
fun HomeScreen() {
    val container = LocalContainer.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val recent by remember { container.documents.recent(30) }.collectAsState(initial = emptyList())
    val isPro by container.billing.isPro.collectAsState()

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            val files = uris.mapNotNull { runCatching { container.documents.importPdf(it) }.getOrNull() }
            files.singleOrNull()?.let { actions.openViewer(it.absolutePath) }
        }
    }

    val bigFile = recent.firstOrNull { it.sizeBytes > SUGGEST_COMPRESS_BYTES && !it.encrypted }
    val quick = listOf(ToolId.MERGE, ToolId.SIGN, ToolId.COMPRESS, ToolId.FILL_FORM, ToolId.SPLIT, ToolId.ORGANIZE_PAGES, ToolId.IMAGES_TO_PDF, ToolId.PROTECT)

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = actions.startScan,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Icon(Icons.Default.DocumentScanner, null, Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.scan_document), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { openLauncher.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.open_pdf))
                }
            }
        }

        if (bigFile != null) item {
            Card(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Compress, null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.suggest_compress, bigFile.name, formatBytes(bigFile.sizeBytes)),
                        Modifier.weight(1f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { actions.launchTool(ToolId.COMPRESS, bigFile.path) }) { Text(stringResource(R.string.compress)) }
                }
            }
        }

        item {
            Text(stringResource(R.string.quick_tools), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp))
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(quick) { tool ->
                    Column(
                        Modifier
                            .width(80.dp)
                            .clickable(role = Role.Button) { actions.launchTool(tool, null) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(16.dp)) {
                            Icon(tool.ui().icon, null, Modifier.padding(14.dp).size(26.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(tool.ui().title), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        if (!isPro) item {
            // The only Pro prompt outside tool entry, per the pricing rules.
            Card(
                Modifier.padding(16.dp).fillMaxWidth().clickable { actions.nav.navigate(Routes.PAYWALL) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WorkspacePremium, null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.pro_prompt_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.pro_prompt_body), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Text(stringResource(R.string.recent_files), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp))
        }
        if (recent.isEmpty()) item {
            Text(stringResource(R.string.no_recent), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(recent.take(15), key = { it.path }) { doc ->
            DocumentRow(doc, onClick = { actions.openViewer(doc.path) })
        }
    }
}
