package app.goke.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.goke.R
import app.goke.core.export.ExportBundle
import app.goke.core.importer.IcsWriter
import app.goke.core.model.Snapshot
import app.goke.data.Settings
import java.io.File

/** 导出与分享：文件写入缓存目录，经 FileProvider 交给系统分享面板（用户可存到文件、发给他人）。 */
object Sharing {
    private fun dir(ctx: Context) = File(ctx.cacheDir, "share").apply { mkdirs() }

    fun exportIcs(
        ctx: Context,
        snap: Snapshot,
    ): File = File(dir(ctx), "goke.ics").also { it.writeText(IcsWriter.write(snap.live()), Charsets.UTF_8) }

    fun exportJson(
        ctx: Context,
        snap: Snapshot,
    ): File =
        File(dir(ctx), "goke-backup.json").also {
            it.writeText(ExportBundle(exportedAt = System.currentTimeMillis(), snapshot = snap).toJson(), Charsets.UTF_8)
        }

    fun exportTimetableImage(
        ctx: Context,
        snap: Snapshot,
        st: Settings,
        week: Int,
    ): File? {
        val bmp = TimetableImage.render(ctx, snap.live(), st, week) ?: return null
        return File(dir(ctx), "timetable-week$week.png").also { f ->
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
        }
    }

    fun share(
        ctx: Context,
        file: File,
        mime: String,
    ) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send =
            Intent(Intent.ACTION_SEND)
                .setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, ctx.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }
}
