package com.pdfmaster.ui.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pdfmaster.R
import com.pdfmaster.core.formatDate
import com.pdfmaster.data.db.DocumentEntity
import com.pdfmaster.ui.LocalActions
import com.pdfmaster.ui.common.DocumentRow
import com.pdfmaster.ui.common.LocalContainer
import com.pdfmaster.ui.common.shareFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private enum class Filter { ALL, RECENT, STARRED }

@Composable
fun FilesScreen() {
    val container = LocalContainer.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val docs by container.documents.documents.collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var contentHits by remember { mutableStateOf<Set<String>>(emptySet()) }
    val pendingDelete = remember { mutableStateListOf<String>() }
    var renaming by remember { mutableStateOf<DocumentEntity?>(null) }
    var versionsOf by remember { mutableStateOf<DocumentEntity?>(null) }

    LaunchedEffect(query) {
        delay(250)
        contentHits = if (query.length >= 2) container.documents.searchContent(query) else emptySet()
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch { uris.forEach { runCatching { container.documents.importPdf(it) } } }
    }

    val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
    val shown = docs
        .filter { it.path !in pendingDelete }
        .filter {
            when (filter) {
                Filter.ALL -> true
                Filter.RECENT -> it.lastOpenedAt > weekAgo
                Filter.STARRED -> it.starred
            }
        }
        .filter { query.isBlank() || it.name.contains(query, true) || it.tags.contains(query, true) || it.path in contentHits }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { importLauncher.launch(arrayOf("application/pdf")) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.import_pdf)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.tab_files), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text(stringResource(R.string.search_files)) },
                )
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter == Filter.ALL, { filter = Filter.ALL }, label = { Text(stringResource(R.string.filter_all)) })
                    FilterChip(filter == Filter.RECENT, { filter = Filter.RECENT }, label = { Text(stringResource(R.string.filter_recent)) })
                    FilterChip(filter == Filter.STARRED, { filter = Filter.STARRED }, label = { Text(stringResource(R.string.filter_starred)) })
                }
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp)) {
                    Text(
                        stringResource(if (docs.isEmpty()) R.string.library_empty else R.string.no_matches),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.path }) { doc ->
                    var menu by remember { mutableStateOf(false) }
                    DocumentRow(doc, onClick = { actions.openViewer(doc.path) }) {
                        Row {
                            IconButton(onClick = { scope.launch { container.documents.setStarred(doc.path, !doc.starred) } }) {
                                Icon(
                                    if (doc.starred) Icons.Default.Star else Icons.Default.StarBorder,
                                    stringResource(if (doc.starred) R.string.unstar else R.string.star),
                                    tint = if (doc.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, onClick = {
                                        menu = false
                                        val f = File(doc.path); shareFile(context, container.documents.shareUri(f), f.name)
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; renaming = doc })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.versions)) }, onClick = { menu = false; versionsOf = doc })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
                                        menu = false
                                        pendingDelete += doc.path
                                        scope.launch {
                                            val result = snackbar.showSnackbar(
                                                context.getString(R.string.deleted_x, doc.name),
                                                actionLabel = context.getString(R.string.undo),
                                                duration = SnackbarDuration.Short,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) pendingDelete -= doc.path
                                            else { container.documents.delete(doc.path); pendingDelete -= doc.path }
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { doc ->
        var name by remember(doc.path) { mutableStateOf(doc.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    renaming = null
                    scope.launch { runCatching { container.documents.rename(doc.path, name) } }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    versionsOf?.let { doc ->
        val file = File(doc.path)
        val versions = remember(doc.path) { container.documents.versions(file) }
        AlertDialog(
            onDismissRequest = { versionsOf = null },
            title = { Text(stringResource(R.string.versions)) },
            text = {
                if (versions.isEmpty()) Text(stringResource(R.string.no_versions))
                else LazyColumn {
                    items(versions) { v ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(formatDate(v.nameWithoutExtension.toLongOrNull() ?: v.lastModified()), Modifier.weight(1f))
                            TextButton(onClick = {
                                versionsOf = null
                                scope.launch {
                                    runCatching { container.documents.restoreVersion(file, v) }
                                    snackbar.showSnackbar(context.getString(R.string.version_restored))
                                }
                            }) { Text(stringResource(R.string.restore)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { versionsOf = null }) { Text(stringResource(R.string.close)) } },
        )
    }
}
