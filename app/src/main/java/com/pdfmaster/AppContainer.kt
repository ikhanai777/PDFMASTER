package com.pdfmaster

import android.content.Context
import android.net.Uri
import com.pdfmaster.billing.BillingManager
import com.pdfmaster.billing.Entitlements
import com.pdfmaster.core.CryptoBox
import com.pdfmaster.data.DocumentRepository
import com.pdfmaster.data.Prefs
import com.pdfmaster.data.ProfileStore
import com.pdfmaster.data.SignatureStore
import com.pdfmaster.data.db.AppDatabase
import com.pdfmaster.ocr.OcrEngine
import com.pdfmaster.pdf.FormOps
import com.pdfmaster.pdf.PdfOps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Something handed to the app from outside (share sheet, "Open with"). */
sealed interface IncomingRequest {
    data class OpenPdf(val uri: Uri) : IncomingRequest
    data class Pdfs(val uris: List<Uri>) : IncomingRequest
    data class Images(val uris: List<Uri>) : IncomingRequest
}

/** Manual dependency container; one per process. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs = Prefs(context)
    val crypto = CryptoBox()
    val database = AppDatabase.create(context)
    val pdfOps = PdfOps(File(context.cacheDir, "pdfbox"))
    val formOps = FormOps(pdfOps)
    val ocr = OcrEngine()
    val documents = DocumentRepository(context, database.documents(), pdfOps, appScope)
    val signatures = SignatureStore(File(context.filesDir, "signatures"), crypto)
    val profile = ProfileStore(File(context.filesDir, "profile.bin"), crypto)
    val billing = BillingManager(context, prefs, appScope, BuildConfig.DEBUG)
    val entitlements = Entitlements(prefs, billing.isPro)

    /** Pending share/open request, consumed by the navigation host. */
    val incoming = MutableStateFlow<IncomingRequest?>(null)

    /** Scan pages handed from the scanner to the review screen. */
    val pendingScan = MutableStateFlow<List<Uri>>(emptyList())
}
