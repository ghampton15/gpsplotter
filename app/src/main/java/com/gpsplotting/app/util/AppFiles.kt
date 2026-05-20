package com.gpsplotting.app.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

object AppFiles {

    fun readBytes(contentResolver: ContentResolver, uri: Uri): ByteArray =
        contentResolver.openInputStream(uri)!!.use { it.readBytes() }

    fun safeDisplayName(uri: Uri): String {
        val last = uri.lastPathSegment ?: "file"
        return last.substringAfterLast('/')
    }

    /**
     * Saves bytes into the **shared Downloads** folder (visible in Files / “Downloads”).
     * API 29+: uses MediaStore (no storage permission). API 26–28: writes to public Download dir
     * ([saveBytesToDownloadsLegacy]) — caller must hold [android.Manifest.permission.WRITE_EXTERNAL_STORAGE].
     */
    fun saveBytesToPublicDownloads(
        context: Context,
        displayName: String,
        bytes: ByteArray,
        mimeType: String = "application/octet-stream",
    ): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveBytesToDownloadsMediaStore(context, displayName, bytes, mimeType)
        } else {
            saveBytesToDownloadsLegacy(context, displayName, bytes)
        }
    }

    /** API 29+ — appears under Downloads in the system file picker / Files app. */
    fun saveBytesToDownloadsMediaStore(
        context: Context,
        displayName: String,
        bytes: ByteArray,
        mimeType: String = "application/octet-stream",
    ): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val details = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri =
            resolver.insert(collection, details) ?: throw IOException("Could not create file in Downloads.")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
            } ?: throw IOException("Could not open Downloads output stream.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                details.clear()
                details.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, details, null, null)
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return "Downloads/$displayName"
    }

    /** API 26–28 — requires WRITE_EXTERNAL_STORAGE to be granted at runtime. */
    fun saveBytesToDownloadsLegacy(context: Context, displayName: String, bytes: ByteArray): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = File(dir, displayName)
        FileOutputStream(file).use { it.write(bytes) }
        return file.absolutePath
    }

    fun writeDxfToByteArray(write: (OutputStream) -> Unit): ByteArray =
        ByteArrayOutputStream().also { write(it) }.toByteArray()
}
