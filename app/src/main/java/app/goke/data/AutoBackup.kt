package app.goke.data

import android.content.Context
import app.goke.core.export.ExportBundle
import app.goke.core.model.Snapshot
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自动备份：数据变化时检查（节流为每天一次），把全量快照写成
 * ExportBundle JSON 到应用专属外部目录 `backup/`，保留最近 [Settings.backupKeep] 份。
 * 目录卸载即删——是「防手滑」而不是异地容灾；正式迁移仍走「数据与备份」的手动导出。
 */
object AutoBackup {
    private const val INTERVAL_MS = 20 * 3600_000L

    fun maybeRun(
        ctx: Context,
        snap: Snapshot,
        st: Settings,
    ) {
        if (!st.autoBackup) return
        val sp = ctx.getSharedPreferences("backup", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("last", 0) < INTERVAL_MS) return
        val dir = ctx.getExternalFilesDir(null)?.resolve("backup") ?: return
        runCatching {
            dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now))
            File(dir, "goke-$stamp.json").writeText(ExportBundle(exportedAt = now, snapshot = snap).toJson(), Charsets.UTF_8)
            sp.edit().putLong("last", now).apply()
            dir
                .listFiles { f -> f.name.startsWith("goke-") && f.name.endsWith(".json") }
                ?.sortedByDescending { it.name }
                ?.drop(st.backupKeep.coerceAtLeast(1))
                ?.forEach { it.delete() }
        }
    }
}
