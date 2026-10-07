package com.pdfmaster.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pdfmaster.R
import com.pdfmaster.billing.ToolId
import com.pdfmaster.core.PageRange
import com.pdfmaster.ui.common.LocalContainer
import com.pdfmaster.ui.common.PdfInputCard
import com.pdfmaster.ui.common.rememberPdfInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class SplitMode { RANGES, EVERY_N, BOOKMARKS }

@Composable
fun SplitScreen(path: String?, extractOnly: Boolean) {
    val tool = if (extractOnly) ToolId.EXTRACT else ToolId.SPLIT
    val container = LocalContainer.current
    val input = rememberPdfInput(path)
    val scope = rememberCoroutineScope()
    val state = rememberRunState()
    val err = errorMessage()
    var mode by remember { mutableStateOf(SplitMode.RANGES) }
    var ranges by remember { mutableStateOf("") }
    var everyN by remember { mutableStateOf("1") }
    var bookmarkStarts by remember { mutableStateOf<List<Int>>(emptyList()) }
    val pageCount = input.info?.pageCount ?: 0

    LaunchedEffect(input.file, input.password, input.info) {
        val file = input.file ?: return@LaunchedEffect
        if (input.info?.needsPassword != false) return@LaunchedEffect
        bookmarkStarts = withContext(Dispatchers.IO) {
            runCatching {
                container.pdfOps.open(file, input.password).use { doc -> container.pdfOps.outline(doc).filter { it.level == 0 && it.pageIndex >= 0 }.map { it.pageIndex } }
            }.getOrDefault(emptyList())
        }
    }

    val groups: Result<List<List<Int>>> = remember(mode, ranges, everyN, pageCount, bookmarkStarts, extractOnly) {
        runCatching {
            if (pageCount == 0) return@runCatching emptyList()
            when {
                extractOnly -> listOf(PageRange.parse(ranges, pageCount))
                mode == SplitMode.RANGES -> PageRange.parseGroups(ranges, pageCount)
                mode == SplitMode.EVERY_N -> PageRange.everyN(pageCount, everyN.toIntOrNull()?.takeIf { it > 0 } ?: throw PageRange.ParseException("—"))
                else -> PageRange.fromStartPoints(pageCount, bookmarkStarts)
            }
        }
    }
    val groupList = groups.getOrNull().orEmpty()

    ToolLayout(
        tool, state,
        if (extractOnly) stringResource(R.string.action_extract) else stringResource(R.string.split_into_n, groupList.size),
        input.ready && groupList.isNotEmpty() && groupList.all { it.isNotEmpty() },
        onAction = {
            val file = input.file ?: return@ToolLayout
            scope.runTool(state, container, tool, err) {
                if (extractOnly) {
                    val out = container.documents.newOutputFile(file.nameWithoutExtension + " (pages ${ranges.trim()})")
                    container.pdfOps.extract(file, groupList.first(), out, input.password)
                    listOf(out)
                } else {
                    container.pdfOps.split(file, groupList, container.documents.docsDir, file.nameWithoutExtension, input.password)
                }
            }
        },
    ) {
        PdfInputCard(input)
        if (!extractOnly) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(mode == SplitMode.RANGES, { mode = SplitMode.RANGES }, label = { Text(stringResource(R.string.split_by_ranges)) })
            FilterChip(mode == SplitMode.EVERY_N, { mode = SplitMode.EVERY_N }, label = { Text(stringResource(R.string.split_every_n)) })
            FilterChip(mode == SplitMode.BOOKMARKS, { mode = SplitMode.BOOKMARKS }, enabled = bookmarkStarts.isNotEmpty(), label = { Text(stringResource(R.string.split_by_bookmarks)) })
        }
        when {
            extractOnly || mode == SplitMode.RANGES -> OutlinedTextField(
                ranges, { ranges = it }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(if (extractOnly) R.string.pages_to_extract else R.string.ranges)) },
                placeholder = { Text(if (extractOnly) "3-7" else "1-3, 4-10, 11-") },
                singleLine = true,
                isError = ranges.isNotBlank() && groups.isFailure,
                supportingText = {
                    Text(
                        if (ranges.isNotBlank() && groups.isFailure) groups.exceptionOrNull()?.message.orEmpty()
                        else stringResource(if (extractOnly) R.string.extract_hint else R.string.ranges_hint)
                    )
                },
            )
            mode == SplitMode.EVERY_N -> OutlinedTextField(
                everyN, { everyN = it.filter(Char::isDigit).take(5) },
                label = { Text(stringResource(R.string.pages_per_file)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            else -> Text(stringResource(R.string.bookmark_split_hint, bookmarkStarts.size), style = MaterialTheme.typography.bodyMedium)
        }
        if (groupList.isNotEmpty()) Text(
            groupList.take(8).joinToString("\n") { g -> if (g.size == 1) "• ${g.first() + 1}" else "• ${g.first() + 1}–${g.last() + 1}" } +
                if (groupList.size > 8) "\n…" else "",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
