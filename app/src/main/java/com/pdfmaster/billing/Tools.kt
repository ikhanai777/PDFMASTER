package com.pdfmaster.billing

enum class Tier { FREE, FREE_DAILY, PRO }

enum class ToolGroup { SCAN, EDIT, ORGANISE, CONVERT, SECURE }

/**
 * Every tool in the hub. [available] tools ship in this build; the rest are shown in the
 * grid as "coming soon" with the roadmap phase so the hub reflects the full plan.
 */
enum class ToolId(
    val group: ToolGroup,
    val tier: Tier,
    val available: Boolean = true,
    val dailyLimit: Int = 0,
    val phase: Int = 1,
) {
    // Scan
    SCAN(ToolGroup.SCAN, Tier.FREE),
    IMAGES_TO_PDF(ToolGroup.SCAN, Tier.FREE),
    MAKE_SEARCHABLE(ToolGroup.SCAN, Tier.FREE),
    ID_CARD_SCAN(ToolGroup.SCAN, Tier.FREE, available = false, phase = 2),
    BOOK_SCAN(ToolGroup.SCAN, Tier.FREE, available = false, phase = 2),
    QR_SCAN(ToolGroup.SCAN, Tier.FREE, available = false, phase = 2),
    HANDWRITING_OCR(ToolGroup.SCAN, Tier.PRO, available = false, phase = 4),

    // Edit
    ANNOTATE(ToolGroup.EDIT, Tier.FREE),
    ADD_TEXT(ToolGroup.EDIT, Tier.FREE),
    SIGN(ToolGroup.EDIT, Tier.FREE),
    FILL_FORM(ToolGroup.EDIT, Tier.FREE),
    WATERMARK(ToolGroup.EDIT, Tier.PRO),
    PAGE_NUMBERS(ToolGroup.EDIT, Tier.PRO),
    EDIT_TEXT(ToolGroup.EDIT, Tier.PRO, available = false, phase = 2),
    EDIT_IMAGES(ToolGroup.EDIT, Tier.PRO, available = false, phase = 2),
    LINKS(ToolGroup.EDIT, Tier.PRO, available = false, phase = 2),
    HEADER_FOOTER(ToolGroup.EDIT, Tier.PRO, available = false, phase = 2),
    CREATE_FORM(ToolGroup.EDIT, Tier.PRO, available = false, phase = 3),
    DIGITAL_SIGNATURE(ToolGroup.EDIT, Tier.PRO, available = false, phase = 3),
    REQUEST_SIGNATURES(ToolGroup.EDIT, Tier.PRO, available = false, phase = 4),

    // Organise
    MERGE(ToolGroup.ORGANISE, Tier.FREE),
    SPLIT(ToolGroup.ORGANISE, Tier.FREE),
    EXTRACT(ToolGroup.ORGANISE, Tier.FREE),
    ORGANIZE_PAGES(ToolGroup.ORGANISE, Tier.FREE),
    CROP_RESIZE(ToolGroup.ORGANISE, Tier.PRO, available = false, phase = 2),
    N_UP(ToolGroup.ORGANISE, Tier.PRO, available = false, phase = 2),
    COMPARE(ToolGroup.ORGANISE, Tier.PRO, available = false, phase = 3),
    BATCH(ToolGroup.ORGANISE, Tier.PRO, available = false, phase = 3),
    WORKFLOWS(ToolGroup.ORGANISE, Tier.PRO, available = false, phase = 3),

    // Convert & optimise
    COMPRESS(ToolGroup.CONVERT, Tier.FREE_DAILY, dailyLimit = 3),
    GRAYSCALE(ToolGroup.CONVERT, Tier.FREE),
    PDF_TO_IMAGES(ToolGroup.CONVERT, Tier.FREE),
    PDF_TO_TEXT(ToolGroup.CONVERT, Tier.PRO),
    OFFICE_TO_PDF(ToolGroup.CONVERT, Tier.FREE_DAILY, available = false, dailyLimit = 3, phase = 2),
    PDF_TO_WORD(ToolGroup.CONVERT, Tier.PRO, available = false, phase = 2),
    PDF_TO_EXCEL(ToolGroup.CONVERT, Tier.PRO, available = false, phase = 2),
    PDF_TO_PPT(ToolGroup.CONVERT, Tier.PRO, available = false, phase = 2),
    PDF_TO_EPUB(ToolGroup.CONVERT, Tier.PRO, available = false, phase = 2),
    WEB_TO_PDF(ToolGroup.CONVERT, Tier.FREE, available = false, phase = 2),
    PDF_A(ToolGroup.CONVERT, Tier.PRO, available = false, phase = 3),
    REPAIR(ToolGroup.CONVERT, Tier.PRO, available = false, phase = 2),

    // Secure
    PROTECT(ToolGroup.SECURE, Tier.PRO),
    UNLOCK(ToolGroup.SECURE, Tier.FREE),
    REMOVE_HIDDEN_DATA(ToolGroup.SECURE, Tier.PRO),
    REDACT(ToolGroup.SECURE, Tier.PRO, available = false, phase = 2),
}
