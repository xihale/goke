package app.goke.jxfw

import app.goke.core.jxfw.JxfwGrade
import app.goke.core.jxfw.JxfwNotice
import app.goke.core.jxfw.JxfwNoticeSource
import app.goke.core.jxfw.TzcsParse
import app.goke.core.jxfw.TzcsYearReport
import app.goke.core.jxfw.XkCourseItem
import app.goke.core.jxfw.XkCreditWallet
import app.goke.core.jxfw.XkOccupiedSlot
import app.goke.core.jxfw.XkWindowStatus

/** 教务系统访问错误。[kind] 供 UI 决定提示与降级路径。 */
class JxfwException(
    message: String,
    val kind: Kind = Kind.OTHER,
) : Exception(message) {
    enum class Kind {
        /** 连不上 / 超时 / 非 2xx */
        NETWORK,

        /** 账号或密码错误 */
        CREDENTIALS,

        /** 需要图片/滑块验证码（多次失败后出现） */
        CAPTCHA,

        /** 需要短信二次验证 */
        SMS,

        /** 会话中途失效 */
        SESSION,

        /** 返回结构不符合预期 */
        PROTOCOL,

        /** 当前开源构建未包含私有协议适配器 */
        UNSUPPORTED,

        OTHER,
    }
}

/** 教务同步状态（UI 订阅）。密码不在此处。 */
data class JxfwState(
    val username: String? = null,
    val busy: Boolean = false,
    val progress: String? = null,
    val autoSync: Boolean = true,
    val term: String? = null,
    val lastFullAt: Long? = null,
    val lastWeeklyAt: Long? = null,
    val lastSyncedWeek: Int? = null,
    val lastResult: String? = null,
    val lastError: String? = null,
    val lastErrorKind: JxfwException.Kind? = null,
) {
    val loggedIn: Boolean get() = username != null
}

/** 本地成绩缓存与刷新状态（仅本机只读缓存，不入 Repository/Sync）。 */
data class JxfwGradesState(
    val grades: List<JxfwGrade> = emptyList(),
    val fetchedAt: Long? = null,
    val busy: Boolean = false,
    val lastError: String? = null,
    val lastErrorKind: JxfwException.Kind? = null,
)

/** 本地教务通知缓存与刷新状态（仅本机只读缓存，不入 Repository/Sync）。 */
data class JxfwNoticesState(
    val notices: List<JxfwNotice> = emptyList(),
    val unreadIds: Set<String> = emptySet(),
    val enabled: Boolean = true,
    val publicEnabled: Boolean = true,
    val internalEnabled: Boolean = true,
    val fetchedAt: Long? = null,
    val busy: Boolean = false,
    val lastError: String? = null,
    val lastErrorKind: JxfwException.Kind? = null,
) {
    /** 指定来源是否已开启。 */
    fun isSourceEnabled(source: JxfwNoticeSource): Boolean =
        when (source) {
            JxfwNoticeSource.PUBLIC -> publicEnabled
            JxfwNoticeSource.INTERNAL -> internalEnabled
        }

    /** 仅包含当前已开启来源的未读通知 ID（关闭某来源后不计入未读红点与计数）。 */
    val activeUnreadIds: Set<String>
        get() {
            if (unreadIds.isEmpty()) return emptySet()
            val enabledNoticeIds =
                notices
                    .asSequence()
                    .filter { isSourceEnabled(it.source) }
                    .map { it.id }
                    .toHashSet()
            return unreadIds.filterTo(LinkedHashSet()) { it in enabledNoticeIds }
        }
}

/** 选课状态（进程内只读/操作状态，不入 Repository/Sync）。 */
data class JxfwCourseSelectionState(
    val window: XkWindowStatus? = null,
    val wallet: XkCreditWallet? = null,
    val available: List<XkCourseItem> = emptyList(),
    val availableTotal: Int = 0,
    val currentPage: Int = 1,
    val enrolled: List<XkCourseItem> = emptyList(),
    val occupiedSlots: List<XkOccupiedSlot> = emptyList(),
    val searchKey: String = "",
    val searchValue: String = "",
    val reachedEnd: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val actionBusyId: String? = null,
    val fetchedAt: Long? = null,
    val lastError: String? = null,
    val lastErrorKind: JxfwException.Kind? = null,
) {
    /** 是否还有更多可选课程分页。 */
    val hasMore: Boolean
        get() = !reachedEnd && available.size < availableTotal
}

/** 体测平台访问错误。[kind] 供 UI 决定提示与交互（如刷新验证码）。 */
class TzcsException(
    message: String,
    val kind: Kind = Kind.OTHER,
) : Exception(message) {
    enum class Kind {
        NETWORK,
        CREDENTIALS,
        CAPTCHA,
        SESSION,
        PROTOCOL,
        UNSUPPORTED,
        OTHER,
    }
}

/** 体测成绩状态（仅本机只读缓存，不入 Repository/Sync）。 */
data class TzcsState(
    val username: String? = null,
    val hasSavedPassword: Boolean = false,
    val reports: List<TzcsYearReport> = emptyList(),
    val fetchedAt: Long? = null,
    val busy: Boolean = false,
    val lastError: String? = null,
    val lastErrorKind: TzcsException.Kind? = null,
) {
    val loggedIn: Boolean get() = username != null

    /** 最新已出成绩的学年报告（若都未录入则回退到最新学年）。 */
    val latestSummaryReport: TzcsYearReport?
        get() = reports.firstOrNull { TzcsParse.hasRecordedScores(it) } ?: reports.firstOrNull()
}
