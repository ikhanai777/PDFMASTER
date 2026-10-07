package com.pdfmaster.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pdfmaster.R
import com.pdfmaster.billing.ToolId
import com.pdfmaster.pdf.FieldType
import com.pdfmaster.pdf.FormField
import com.pdfmaster.ui.LocalActions
import com.pdfmaster.ui.Routes
import com.pdfmaster.ui.common.LocalContainer
import com.pdfmaster.ui.common.PdfInputCard
import com.pdfmaster.ui.common.ProBadge
import com.pdfmaster.ui.common.SectionLabel
import com.pdfmaster.ui.common.rememberPdfInput
import com.pdfmaster.ui.viewer.ViewerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FormFillScreen(path: String?) {
    val container = LocalContainer.current
    val actions = LocalActions.current
    val input = rememberPdfInput(path)
    val scope = rememberCoroutineScope()
    val state = rememberRunState()
    val err = errorMessage()
    val isPro by container.billing.isPro.collectAsState()
    var fields by remember { mutableStateOf<List<FormField>?>(null) }
    val values = remember { mutableStateMapOf<String, String>() }
    var flatten by remember { mutableStateOf(false) }
    var autoFilled by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(input.file, input.info, input.password) {
        val file = input.file ?: return@LaunchedEffect
        if (!input.ready) return@LaunchedEffect
        fields = null
        val loaded = withContext(Dispatchers.IO) { runCatching { container.formOps.readFields(file, input.password) }.getOrDefault(emptyList()) }
        values.clear()
        loaded.forEach { values[it.name] = it.value }
        fields = loaded
    }

    ToolLayout(ToolId.FILL_FORM, state, stringResource(R.string.save_filled_form), input.ready && !fields.isNullOrEmpty(), onAction = {
        val file = input.file ?: return@ToolLayout
        val changed = values.filter { (k, v) -> fields?.firstOrNull { it.name == k }?.value != v }
        scope.runTool(state, container, ToolId.FILL_FORM, err) {
            val out = container.documents.newOutputFile(file.nameWithoutExtension + " (filled)")
            container.formOps.fill(file, out, changed, flatten && isPro, input.password)
            listOf(out)
        }
    }) {
        PdfInputCard(input)
        val list = fields
        when {
            !input.ready -> Unit
            list == null -> Text(stringResource(R.string.reading))
            list.isEmpty() -> Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.no_form_fields))
                    FilledTonalButton(onClick = { input.file?.let { actions.openViewer(it.absolutePath, ViewerMode.ANNOTATE) } }) {
                        Text(stringResource(R.string.add_text_instead))
                    }
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val profile = container.profile.load()
                            var n = 0
                            list.filter { it.type == FieldType.TEXT && !it.readOnly && values[it.name].isNullOrBlank() }.forEach { f ->
                                (profile.valueForField(f.name) ?: profile.valueForField(f.label))?.let { values[f.name] = it; n++ }
                            }
                            autoFilled = n
                        }
                    }) {
                        Icon(Icons.Default.AutoAwesome, null); Text(" " + stringResource(R.string.auto_fill))
                    }
                }
                autoFilled?.let {
                    Text(
                        if (it == 0) stringResource(R.string.auto_fill_none) else stringResource(R.string.auto_filled_n, it),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (it == 0) androidx.compose.material3.TextButton(onClick = { actions.nav.navigate(Routes.PROFILE) }) { Text(stringResource(R.string.edit_profile)) }
                }
                var lastPage = -2
                list.forEach { field ->
                    if (field.pageIndex != lastPage) {
                        lastPage = field.pageIndex
                        if (field.pageIndex >= 0) SectionLabel(stringResource(R.string.page_n, field.pageIndex + 1))
                    }
                    FieldEditor(field, values[field.name].orEmpty()) { values[field.name] = it }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(flatten && isPro, { flatten = it }, enabled = isPro)
                    Text(stringResource(R.string.flatten_form), Modifier.weight(1f))
                    if (!isPro) ProBadge()
                }
                Text(stringResource(R.string.sign_after_fill), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FieldEditor(field: FormField, value: String, onChange: (String) -> Unit) {
    when (field.type) {
        FieldType.TEXT -> OutlinedTextField(
            value, onChange, Modifier.fillMaxWidth(),
            label = { Text(field.label) },
            enabled = !field.readOnly,
            singleLine = !field.multiline,
            minLines = if (field.multiline) 3 else 1,
        )
        FieldType.CHECKBOX -> Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(value.toBoolean(), { onChange(it.toString()) }, enabled = !field.readOnly)
            Text(field.label)
        }
        FieldType.RADIO, FieldType.CHOICE -> Column {
            Text(field.label, style = MaterialTheme.typography.labelLarge)
            field.options.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(value == option, { onChange(option) }, enabled = !field.readOnly)
                    Text(option)
                }
            }
        }
        FieldType.SIGNATURE -> Text("✍ " + field.label + " — " + stringResource(R.string.signature_field_hint), style = MaterialTheme.typography.bodyMedium)
        FieldType.OTHER -> Unit
    }
}
