package rs.chimera.android.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

data class FileInfo(
    val name: String,
    val uri: Uri,
    val size: Long = 0,
)

internal fun queryProfileFileInfo(
    context: Context,
    uri: Uri,
): FileInfo = context.contentResolver.query(
    uri,
    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
    null,
    null,
    null,
)?.use { cursor ->
    if (!cursor.moveToFirst()) return@use null
    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
    FileInfo(
        name = nameIndex.takeIf { it >= 0 }?.let(cursor::getString)?.ifBlank { "profile" } ?: "profile",
        uri = uri,
        size = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong) ?: 0L,
    )
} ?: FileInfo(name = "profile", uri = uri)
