package app.goke

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/** 从外部（通知、小组件、桌面快捷方式、磁贴、分享菜单、划词）进入应用时要做的事。 */
sealed interface DeepLink {
    /** 打开某条待办的底部抽屉。 */
    data class Task(
        val id: String,
    ) : DeepLink

    /** 打开某门课程的详情/编辑页。 */
    data class Course(
        val id: String,
    ) : DeepLink

    /** 新建待办；[text] 会按快速添加语法解析后带进编辑页，可为空。 */
    data class Add(
        val text: String,
        val courseId: String? = null,
    ) : DeepLink

    data object Today : DeepLink

    data object Todo : DeepLink

    data object Timetable : DeepLink

    data object Parcel : DeepLink
}

object DeepLinks {
    const val SCHEME = "goke"
    const val ACTION_ADD = "app.goke.action.ADD_TASK"

    fun taskUri(id: String): Uri =
        Uri
            .Builder()
            .scheme(SCHEME)
            .authority("task")
            .appendPath(id)
            .build()

    fun courseUri(id: String): Uri =
        Uri
            .Builder()
            .scheme(SCHEME)
            .authority("course")
            .appendPath(id)
            .build()

    fun addUri(
        text: String? = null,
        courseId: String? = null,
    ): Uri =
        Uri
            .Builder()
            .scheme(SCHEME)
            .authority("add")
            .apply {
                if (!text.isNullOrBlank()) appendQueryParameter("text", text)
                if (!courseId.isNullOrBlank()) appendQueryParameter("courseId", courseId)
            }.build()

    fun todayUri(): Uri =
        Uri
            .Builder()
            .scheme(SCHEME)
            .authority("today")
            .build()

    fun todoUri(): Uri =
        Uri
            .Builder()
            .scheme(SCHEME)
            .authority("todo")
            .build()

    fun timetableUri(): Uri =
        Uri
            .Builder()
            .scheme(SCHEME)
            .authority("timetable")
            .build()

    fun parcelUri(): Uri =
        Uri
            .Builder()
            .scheme(SCHEME)
            .authority("parcel")
            .build()

    fun parseUri(uri: Uri): DeepLink? {
        if (uri.scheme != SCHEME) return null
        return when (uri.host) {
            "task" -> uri.lastPathSegment?.takeIf { it.isNotBlank() }?.let { DeepLink.Task(it) }
            "course" -> uri.lastPathSegment?.takeIf { it.isNotBlank() }?.let { DeepLink.Course(it) }
            "add" -> DeepLink.Add(uri.getQueryParameter("text").orEmpty(), uri.getQueryParameter("courseId")?.takeIf { it.isNotBlank() })
            "today" -> DeepLink.Today
            "todo" -> DeepLink.Todo
            "timetable" -> DeepLink.Timetable
            "parcel" -> DeepLink.Parcel
            else -> null
        }
    }

    /** 不是深链的意图（导入文件等）返回 null，交给原有流程。 */
    @Suppress("DEPRECATION")
    fun parse(intent: Intent): DeepLink? =
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let(::parseUri)
            }

            ACTION_ADD -> {
                DeepLink.Add(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            }

            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/plain") == true && !intent.hasExtra(Intent.EXTRA_STREAM)) {
                    DeepLink.Add(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
                } else {
                    null
                }
            }

            Intent.ACTION_PROCESS_TEXT -> {
                DeepLink.Add(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty())
            }

            else -> {
                null
            }
        }

    /** 长按桌面图标出现的快捷方式（用动态快捷方式，避免静态 XML 写死调试包的包名）。 */
    fun publishShortcuts(ctx: Context) {
        fun shortcut(
            id: String,
            label: Int,
            uri: Uri,
        ): ShortcutInfoCompat =
            ShortcutInfoCompat
                .Builder(ctx, id)
                .setShortLabel(ctx.getString(label))
                .setIcon(IconCompat.createWithResource(ctx, R.drawable.ic_shortcut_mint))
                .setIntent(Intent(Intent.ACTION_VIEW, uri, ctx, MainActivity::class.java))
                .build()
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(
                ctx,
                listOf(
                    shortcut("add_task", R.string.shortcut_add_task, addUri()),
                    shortcut("today", R.string.shortcut_today, todayUri()),
                ),
            )
        }
    }
}
