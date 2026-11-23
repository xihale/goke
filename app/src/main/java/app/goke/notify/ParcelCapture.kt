package app.goke.notify

import android.content.Context
import android.content.res.Resources
import app.goke.R
import app.goke.core.change.Edits
import app.goke.core.model.Source
import app.goke.gokeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** 快递取件文本入库（通知监听与短信广播共用）：串行化解析、查重并生成待办与通知。 */
object ParcelCapture {
    private val mutex = Mutex()
    private val _redactedDetected = MutableStateFlow(false)
    val redactedDetected: StateFlow<Boolean> = _redactedDetected.asStateFlow()

    fun isRedactedText(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        val sysId = Resources.getSystem().getIdentifier("redacted_notification_message", "string", "android")
        if (sysId != 0) {
            val sysText = runCatching { Resources.getSystem().getString(sysId) }.getOrNull()
            if (!sysText.isNullOrBlank() && t.contains(sysText)) return true
        }
        return t.contains("敏感通知内容") ||
            t.contains("Sensitive notification content", ignoreCase = true)
    }

    fun noteIfRedacted(text: String): Boolean {
        if (!isRedactedText(text)) return false
        _redactedDetected.value = true
        return true
    }

    suspend fun ingest(
        ctx: Context,
        text: String,
    ): Boolean =
        withContext(Dispatchers.Default) {
            val app = ctx.gokeApp
            if (!app.settings.value.parcelEnabled) return@withContext false
            if (noteIfRedacted(text)) return@withContext false
            val info = app.parcelRules.value.parse(text, LocalDateTime.now()) ?: return@withContext false
            mutex.withLock {
                app.repo.ready.first { it }
                val snap =
                    app.repo.snapshot.value
                        .live()
                val dup =
                    snap.tasks.any {
                        it.source == Source.PARCEL &&
                            it.extra["code"] == info.code &&
                            it.extra["place"] == info.place &&
                            !it.done
                    }
                if (dup) return@withLock false
                val task = ParcelTasks.build(ctx, info)
                val cs = Edits.save(snap, task, ctx.getString(R.string.parcel_op_desc), System.currentTimeMillis())
                app.repo.commit(cs)
                Notifications.postParcelCaptured(
                    ctx,
                    task.id,
                    info.code,
                    info.place.ifBlank { info.company },
                )
                true
            }
        }
}
