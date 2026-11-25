package app.goke.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import app.goke.core.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** 待办附件的本机存储：文件放私有目录，记录里只存文件名。 */
object Attachments {
    private const val DIR = "attachments"

    fun dir(ctx: Context): File = File(ctx.filesDir, DIR).apply { mkdirs() }

    fun path(
        ctx: Context,
        a: Attachment,
    ): File = File(dir(ctx), a.file)

    /** 相册 / 文件选择 / 剪贴板给的外部 uri → 复制进私有目录。读不到时返回 null。挂起：复制跑在 IO。 */
    suspend fun import(
        ctx: Context,
        uri: Uri,
    ): Attachment? =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = ctx.contentResolver
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
                val target = File(dir(ctx), "${UUID.randomUUID()}.$ext")
                resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                    ?: return@runCatching null
                Attachment(displayName(ctx, uri) ?: "附件.$ext", mime, target.name)
            }.getOrNull()
        }

    fun delete(
        ctx: Context,
        a: Attachment,
    ) {
        path(ctx, a).delete()
    }

    /** 将图片附件原样导出到系统相册（`Pictures/Goke`），不重新压缩。 */
    suspend fun saveToGallery(
        ctx: Context,
        a: Attachment,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val src = path(ctx, a)
            if (!src.exists()) return@withContext false
            val mime = a.mime.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            val ext = src.extension.ifBlank { MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg" }
            val rawName = a.name.trim().ifBlank { "goke_${System.currentTimeMillis()}.$ext" }
            val displayName = if (rawName.contains('.')) rawName else "$rawName.$ext"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = ctx.contentResolver
                val values =
                    ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Images.Media.MIME_TYPE, mime)
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Goke")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
                runCatching {
                    resolver.openOutputStream(uri)?.use { out ->
                        src.inputStream().use { input -> input.copyTo(out) }
                    } ?: error("openOutputStream null")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    true
                }.onFailure {
                    resolver.delete(uri, null, null)
                }.getOrDefault(false)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Goke").apply { mkdirs() }
                runCatching {
                    val base = displayName.substringBeforeLast('.')
                    val suffix = displayName.substringAfterLast('.', ext)
                    var dest = File(dir, displayName)
                    var idx = 1
                    while (dest.exists()) {
                        dest = File(dir, "${base}_$idx.$suffix")
                        idx++
                    }
                    src.inputStream().use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
                    MediaScannerConnection.scanFile(ctx, arrayOf(dest.absolutePath), arrayOf(mime), null)
                    true
                }.getOrDefault(false)
            }
        }

    private fun displayName(
        ctx: Context,
        uri: Uri,
    ): String? =
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
}
