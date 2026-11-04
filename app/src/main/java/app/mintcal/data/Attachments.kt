package app.mintcal.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import app.mintcal.core.model.Attachment
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
