package com.autodj.analysis

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Lists the audio files in a folder the user picked (and its subfolders), via the Storage Access Framework. */
object FolderScanner {
    /** Blocking; call off the main thread. */
    fun audioFiles(context: Context, tree: Uri): List<Uri> {
        val out = mutableListOf<Uri>()
        val pending = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(tree)))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE)
        while (pending.isNotEmpty()) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, pending.removeFirst())
            try {
                context.contentResolver.query(children, columns, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0)
                        val mime = c.getString(1).orEmpty()
                        when {
                            mime == DocumentsContract.Document.MIME_TYPE_DIR -> pending.add(id)
                            mime.startsWith("audio/") -> out += DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        }
                    }
                }
            } catch (_: Exception) {
                // An unreadable subfolder is skipped, not fatal.
            }
        }
        return out
    }

    fun name(context: Context, tree: Uri): String {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        try {
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        } catch (_: Exception) {
        }
        return tree.lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: "Folder"
    }
}
