package com.pdfmaster.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdfmaster.R
import com.pdfmaster.billing.Tier
import com.pdfmaster.billing.ToolGroup
import com.pdfmaster.billing.ToolId
import com.pdfmaster.ui.LocalActions
import com.pdfmaster.ui.common.LocalContainer
import com.pdfmaster.ui.common.ProBadge

@Composable
fun ToolsScreen() {
    val actions = LocalActions.current
    val container = LocalContainer.current
    val isPro by container.billing.isPro.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var showSoon by rememberSaveable { mutableStateOf(true) }

    val titles = ToolId.entries.associateWith { stringResource(it.ui().title) }
    val descriptions = ToolId.entries.associateWith { stringResource(it.ui().description) }
    val visible = ToolId.entries.filter { tool ->
        (showSoon || tool.available) &&
            (query.isBlank() || titles.getValue(tool).contains(query, true) || descriptions.getValue(tool).contains(query, true))
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(stringResource(R.string.tab_tools), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text(stringResource(R.string.search_tools, ToolId.entries.size)) },
                )
                Row(Modifier.padding(top = 8.dp)) {
                    FilterChip(selected = showSoon, onClick = { showSoon = !showSoon }, label = { Text(stringResource(R.string.show_coming_soon)) })
                }
            }
        }
        ToolGroup.entries.forEach { group ->
            val tools = visible.filter { it.group == group }
            if (tools.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = group.name) {
                    Text(stringResource(group.title()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                }
                items(tools, key = { it.name }) { tool ->
                    ToolTile(tool, titles.getValue(tool), isPro) { actions.launchTool(tool, null) }
                }
            }
        }
    }
}

@Composable
private fun ToolTile(tool: ToolId, title: String, isPro: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .heightIn(min = 104.dp)
            .alpha(if (tool.available) 1f else 0.55f)
            .clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(tool.ui().icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            when {
                !tool.available -> Text(stringResource(R.string.soon), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                tool.tier == Tier.PRO && !isPro -> ProBadge()
                tool.tier == Tier.FREE_DAILY && !isPro -> Text(stringResource(R.string.free_per_day, tool.dailyLimit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
