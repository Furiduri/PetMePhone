package com.petmephone.spike.imeviability

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Persists findings to app-specific external storage (design.md decision 14) — no manual
 * transcription. Every entry is appended, never overwritten, so a device tested twice keeps both
 * runs.
 */
class FindingsRepository(private val context: Context) {

    private val findingsFile: File
        get() = File(context.getExternalFilesDir(null), FILE_NAME)

    fun append(entry: FindingsEntry) {
        val file = findingsFile
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText(HEADER)
        }
        file.appendText(entry.toMarkdown())
    }

    fun readAllOrEmpty(): String = findingsFile.takeIf { it.exists() }?.readText().orEmpty()

    fun path(): String = findingsFile.absolutePath

    fun shareIntent(): Intent? {
        val file = findingsFile
        if (!file.exists()) return null
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private companion object {
        const val FILE_NAME = "ime-viability-findings.md"
        const val HEADER = "# IME viability spike — findings\n\n"
    }
}
