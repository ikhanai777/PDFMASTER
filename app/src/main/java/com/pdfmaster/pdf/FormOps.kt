package com.pdfmaster.pdf

import com.pdfmaster.core.SafeFile
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import java.io.File

enum class FieldType { TEXT, CHECKBOX, RADIO, CHOICE, SIGNATURE, OTHER }

data class FormField(
    val name: String,
    val label: String,
    val type: FieldType,
    val value: String,
    val options: List<String> = emptyList(),
    val readOnly: Boolean = false,
    val multiline: Boolean = false,
    val pageIndex: Int = -1,
)

/** Reads and fills AcroForm fields. */
class FormOps(private val ops: PdfOps) {

    fun readFields(file: File, password: String? = null): List<FormField> =
        ops.open(file, password).use { doc ->
            val form = doc.documentCatalog.acroForm ?: return emptyList()
            val pages = doc.pages
            form.fieldTree.filterIsInstance<PDTerminalField>().map { field ->
                val pageIndex = field.widgets.firstOrNull()?.page?.let { pages.indexOf(it) } ?: -1
                FormField(
                    name = field.fullyQualifiedName,
                    label = (field.alternateFieldName ?: field.partialName ?: field.fullyQualifiedName).trim(),
                    type = typeOf(field),
                    value = valueOf(field),
                    options = optionsOf(field),
                    readOnly = field.isReadOnly,
                    multiline = (field as? PDTextField)?.isMultiline == true,
                    pageIndex = pageIndex,
                )
            }.sortedWith(compareBy({ if (it.pageIndex < 0) Int.MAX_VALUE else it.pageIndex }))
        }

    /**
     * Writes [values] (field name → value) into a copy of [src]. Checkboxes take "true"/"false".
     * Hybrid XFA data is removed so Adobe Reader shows the AcroForm values we just set.
     */
    fun fill(src: File, out: File, values: Map<String, String>, flatten: Boolean, password: String? = null) {
        ops.open(src, password).use { doc ->
            val form = doc.documentCatalog.acroForm ?: throw IllegalStateException("No form fields")
            form.cosObject.removeItem(COSName.XFA)
            for ((name, value) in values) {
                val field = form.getField(name) ?: continue
                if (field.isReadOnly) continue
                try {
                    setValue(field, value)
                } catch (e: Exception) {
                    // Could not build an appearance (e.g. missing font): let the viewer draw it.
                    form.needAppearances = true
                    runCatching { setValue(field, value) }
                }
            }
            if (flatten) runCatching { form.flatten() }
            if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
            SafeFile.write(out) { doc.save(it) }
        }
    }

    private fun setValue(field: PDField, value: String) {
        when (field) {
            is PDCheckBox -> if (value.toBoolean()) field.check() else field.unCheck()
            is PDRadioButton -> if (value.isNotEmpty()) field.value = value
            is PDChoice -> if (value.isNotEmpty()) field.setValue(value)
            is PDTextField -> field.value = value
            is PDSignatureField -> Unit
            else -> field.setValue(value)
        }
    }

    private fun typeOf(field: PDField) = when (field) {
        is PDCheckBox -> FieldType.CHECKBOX
        is PDRadioButton -> FieldType.RADIO
        is PDChoice -> FieldType.CHOICE
        is PDTextField -> FieldType.TEXT
        is PDSignatureField -> FieldType.SIGNATURE
        else -> FieldType.OTHER
    }

    private fun valueOf(field: PDField): String = runCatching {
        when (field) {
            is PDCheckBox -> field.isChecked.toString()
            is PDRadioButton -> field.value ?: ""
            is PDChoice -> field.value.firstOrNull() ?: ""
            else -> field.valueAsString ?: ""
        }
    }.getOrDefault("")

    private fun optionsOf(field: PDField): List<String> = runCatching {
        when (field) {
            is PDRadioButton -> field.onValues.toList()
            is PDChoice -> field.options
            else -> emptyList()
        }
    }.getOrDefault(emptyList())
}
