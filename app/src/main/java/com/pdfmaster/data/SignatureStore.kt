package com.pdfmaster.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pdfmaster.core.CryptoBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

data class SavedSignature(val id: String, val isInitials: Boolean, val file: File)

/** Signatures and initials, stored as Keystore-encrypted transparent PNGs. */
class SignatureStore(private val dir: File, private val crypto: CryptoBox) {

    private val _items = MutableStateFlow<List<SavedSignature>>(emptyList())
    val items: StateFlow<List<SavedSignature>> = _items.asStateFlow()

    init {
        dir.mkdirs()
        refresh()
    }

    private fun refresh() {
        _items.value = dir.listFiles { f -> f.extension == "sig" }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { SavedSignature(it.nameWithoutExtension, it.name.startsWith("ini_"), it) }
    }

    suspend fun save(bitmap: Bitmap, initials: Boolean) = withContext(Dispatchers.IO) {
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        val prefix = if (initials) "ini_" else "sig_"
        crypto.writeEncrypted(File(dir, "$prefix${System.currentTimeMillis()}.sig"), bytes)
        refresh()
    }

    suspend fun load(sig: SavedSignature): Bitmap? = withContext(Dispatchers.IO) {
        crypto.readEncrypted(sig.file)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    suspend fun delete(sig: SavedSignature) = withContext(Dispatchers.IO) {
        sig.file.delete()
        refresh()
    }
}
