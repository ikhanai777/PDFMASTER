package com.pdfmaster.ui.tools

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DynamicForm
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.HideSource
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.automirrored.filled.SendToMobile
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.pdfmaster.R
import com.pdfmaster.billing.ToolGroup
import com.pdfmaster.billing.ToolId

data class ToolUi(@StringRes val title: Int, @StringRes val description: Int, val icon: ImageVector)

fun ToolId.ui(): ToolUi = when (this) {
    ToolId.SCAN -> ToolUi(R.string.tool_scan, R.string.tool_scan_desc, Icons.Default.DocumentScanner)
    ToolId.IMAGES_TO_PDF -> ToolUi(R.string.tool_images_to_pdf, R.string.tool_images_to_pdf_desc, Icons.Default.PhotoLibrary)
    ToolId.MAKE_SEARCHABLE -> ToolUi(R.string.tool_make_searchable, R.string.tool_make_searchable_desc, Icons.Default.ImageSearch)
    ToolId.ID_CARD_SCAN -> ToolUi(R.string.tool_id_card, R.string.tool_id_card_desc, Icons.Default.Badge)
    ToolId.BOOK_SCAN -> ToolUi(R.string.tool_book_scan, R.string.tool_book_scan_desc, Icons.AutoMirrored.Filled.MenuBook)
    ToolId.QR_SCAN -> ToolUi(R.string.tool_qr, R.string.tool_qr_desc, Icons.Default.QrCodeScanner)
    ToolId.HANDWRITING_OCR -> ToolUi(R.string.tool_handwriting, R.string.tool_handwriting_desc, Icons.Default.Gesture)
    ToolId.ANNOTATE -> ToolUi(R.string.tool_annotate, R.string.tool_annotate_desc, Icons.Default.Brush)
    ToolId.ADD_TEXT -> ToolUi(R.string.tool_add_text, R.string.tool_add_text_desc, Icons.Default.TextFields)
    ToolId.SIGN -> ToolUi(R.string.tool_sign, R.string.tool_sign_desc, Icons.Default.Draw)
    ToolId.FILL_FORM -> ToolUi(R.string.tool_fill_form, R.string.tool_fill_form_desc, Icons.Default.DynamicForm)
    ToolId.WATERMARK -> ToolUi(R.string.tool_watermark, R.string.tool_watermark_desc, Icons.Default.WaterDrop)
    ToolId.PAGE_NUMBERS -> ToolUi(R.string.tool_page_numbers, R.string.tool_page_numbers_desc, Icons.Default.FormatListNumbered)
    ToolId.EDIT_TEXT -> ToolUi(R.string.tool_edit_text, R.string.tool_edit_text_desc, Icons.Default.EditNote)
    ToolId.EDIT_IMAGES -> ToolUi(R.string.tool_edit_images, R.string.tool_edit_images_desc, Icons.Default.Image)
    ToolId.LINKS -> ToolUi(R.string.tool_links, R.string.tool_links_desc, Icons.Default.AddLink)
    ToolId.HEADER_FOOTER -> ToolUi(R.string.tool_header_footer, R.string.tool_header_footer_desc, Icons.AutoMirrored.Filled.Notes)
    ToolId.CREATE_FORM -> ToolUi(R.string.tool_create_form, R.string.tool_create_form_desc, Icons.Default.DashboardCustomize)
    ToolId.DIGITAL_SIGNATURE -> ToolUi(R.string.tool_digital_signature, R.string.tool_digital_signature_desc, Icons.Default.VerifiedUser)
    ToolId.REQUEST_SIGNATURES -> ToolUi(R.string.tool_request_signatures, R.string.tool_request_signatures_desc, Icons.AutoMirrored.Filled.SendToMobile)
    ToolId.MERGE -> ToolUi(R.string.tool_merge, R.string.tool_merge_desc, Icons.AutoMirrored.Filled.CallMerge)
    ToolId.SPLIT -> ToolUi(R.string.tool_split, R.string.tool_split_desc, Icons.AutoMirrored.Filled.CallSplit)
    ToolId.EXTRACT -> ToolUi(R.string.tool_extract, R.string.tool_extract_desc, Icons.Default.ContentCut)
    ToolId.ORGANIZE_PAGES -> ToolUi(R.string.tool_organize, R.string.tool_organize_desc, Icons.Default.GridView)
    ToolId.CROP_RESIZE -> ToolUi(R.string.tool_crop_resize, R.string.tool_crop_resize_desc, Icons.Default.Crop)
    ToolId.N_UP -> ToolUi(R.string.tool_n_up, R.string.tool_n_up_desc, Icons.Default.ViewModule)
    ToolId.COMPARE -> ToolUi(R.string.tool_compare, R.string.tool_compare_desc, Icons.Default.Compare)
    ToolId.BATCH -> ToolUi(R.string.tool_batch, R.string.tool_batch_desc, Icons.Default.BurstMode)
    ToolId.WORKFLOWS -> ToolUi(R.string.tool_workflows, R.string.tool_workflows_desc, Icons.Default.AccountTree)
    ToolId.COMPRESS -> ToolUi(R.string.tool_compress, R.string.tool_compress_desc, Icons.Default.Compress)
    ToolId.GRAYSCALE -> ToolUi(R.string.tool_grayscale, R.string.tool_grayscale_desc, Icons.Default.FilterBAndW)
    ToolId.PDF_TO_IMAGES -> ToolUi(R.string.tool_pdf_to_images, R.string.tool_pdf_to_images_desc, Icons.Default.Photo)
    ToolId.PDF_TO_TEXT -> ToolUi(R.string.tool_pdf_to_text, R.string.tool_pdf_to_text_desc, Icons.AutoMirrored.Filled.TextSnippet)
    ToolId.OFFICE_TO_PDF -> ToolUi(R.string.tool_office_to_pdf, R.string.tool_office_to_pdf_desc, Icons.Default.Description)
    ToolId.PDF_TO_WORD -> ToolUi(R.string.tool_pdf_to_word, R.string.tool_pdf_to_word_desc, Icons.AutoMirrored.Filled.Article)
    ToolId.PDF_TO_EXCEL -> ToolUi(R.string.tool_pdf_to_excel, R.string.tool_pdf_to_excel_desc, Icons.Default.TableChart)
    ToolId.PDF_TO_PPT -> ToolUi(R.string.tool_pdf_to_ppt, R.string.tool_pdf_to_ppt_desc, Icons.Default.Slideshow)
    ToolId.PDF_TO_EPUB -> ToolUi(R.string.tool_pdf_to_epub, R.string.tool_pdf_to_epub_desc, Icons.AutoMirrored.Filled.MenuBook)
    ToolId.WEB_TO_PDF -> ToolUi(R.string.tool_web_to_pdf, R.string.tool_web_to_pdf_desc, Icons.Default.Language)
    ToolId.PDF_A -> ToolUi(R.string.tool_pdf_a, R.string.tool_pdf_a_desc, Icons.Default.Inventory2)
    ToolId.REPAIR -> ToolUi(R.string.tool_repair, R.string.tool_repair_desc, Icons.Default.Handyman)
    ToolId.PROTECT -> ToolUi(R.string.tool_protect, R.string.tool_protect_desc, Icons.Default.Lock)
    ToolId.UNLOCK -> ToolUi(R.string.tool_unlock, R.string.tool_unlock_desc, Icons.Default.LockOpen)
    ToolId.REMOVE_HIDDEN_DATA -> ToolUi(R.string.tool_remove_hidden, R.string.tool_remove_hidden_desc, Icons.Default.CleaningServices)
    ToolId.REDACT -> ToolUi(R.string.tool_redact, R.string.tool_redact_desc, Icons.Default.HideSource)
}

@StringRes
fun ToolGroup.title(): Int = when (this) {
    ToolGroup.SCAN -> R.string.group_scan
    ToolGroup.EDIT -> R.string.group_edit
    ToolGroup.ORGANISE -> R.string.group_organise
    ToolGroup.CONVERT -> R.string.group_convert
    ToolGroup.SECURE -> R.string.group_secure
}

@Composable
fun toolTitle(tool: ToolId): String = stringResource(tool.ui().title)

