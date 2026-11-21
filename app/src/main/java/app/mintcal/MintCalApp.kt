package app.mintcal

import android.app.Application
import android.content.Context
import app.mintcal.advanced.AdvancedController
import app.mintcal.calendar.CalendarSyncStore
import app.mintcal.calendar.SystemCalendarSync
import app.mintcal.core.change.Unify
import app.mintcal.data.AutoBackup
import app.mintcal.data.MintDb
import app.mintcal.data.ParcelRuleStore
import app.mintcal.data.Repository
import app.mintcal.data.SecretStore
import app.mintcal.data.SettingsStore
import app.mintcal.data.UpdateChecker
import app.mintcal.jxfw.JxfwManager
import app.mintcal.lanweb.LanWebManager
import app.mintcal.mcp.LocalMcpManager
import app.mintcal.mcp.McpStore
import app.mintcal.notify.Notifications
import app.mintcal.notify.ReminderScheduler
import app.mintcal.sync.PeriodicSync
import app.mintcal.sync.SyncManager
import app.mintcal.sync.SyncStore
import app.mintcal.widget.NextWidgetProvider
import app.mintcal.widget.ParcelWidgetProvider
import app.mintcal.widget.ScheduleWidgetProvider
import app.mintcal.widget.TodayWidgetProvider
import app.mintcal.widget.TodoWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 应用级容器：数据仓库、设置、密钥、提醒调度、本地 MCP 与高级修改预览控制器。open 仅为测试子类（SmokeTestApp）服务。 */
open class MintCalApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var repo: Repository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var parcelRules: ParcelRuleStore
        private set
    lateinit var secrets: SecretStore
        private set
    lateinit var reminders: ReminderScheduler
        private set
    lateinit var advanced: AdvancedController
        private set
    lateinit var mcp: LocalMcpManager
        private set
    lateinit var sync: SyncManager
        private set
    lateinit var calendarSync: SystemCalendarSync
        private set
    lateinit var jxfw: JxfwManager
        private set
    lateinit var lanWeb: LanWebManager
        private set
    lateinit var updateChecker: UpdateChecker
        private set

    /** 外部入口（通知 / 小组件 / 快捷方式 / 分享）待处理的动作，由根组合消费后清空。 */
    val links = MutableStateFlow<DeepLink?>(null)

    /** 有 Activity 在前台时为 true。小组件 tick / 开机 / 通知监听拉起的进程为 false：
     *  这些进程只需要读库刷新小组件与提醒，不需要网络同步和教务抓取。 */
    val uiActive = MutableStateFlow(false)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        repo = Repository(MintDb(this))
        settings = SettingsStore(this)
        // 首屏仅依赖 repo 与 settings：立即在 IO 启动读库，与其余控制器构造并行
        val loadJob =
            scope.launch {
                repo.load()
                // benchmark 变体：首启塞一份可滚动数据（release/debug 里 BENCHMARK=false，常量折叠后整段消失）
                if (BuildConfig.BENCHMARK) BenchSeed.seed(repo)
                // 一次性迁移：日程并进待办（「有时间的待办」），此后不再产生日程记录
                if (!settings.value.unifiedTasks) {
                    val snap = repo.snapshot.value
                    Unify.changeSet(snap, System.currentTimeMillis())?.let { repo.commit(it) }
                    settings.update { it.copy(unifiedTasks = true) }
                }
            }
        parcelRules = ParcelRuleStore(this)
        secrets = SecretStore(this)
        reminders = ReminderScheduler(this)
        advanced = AdvancedController(this)
        mcp = LocalMcpManager(repo, advanced, McpStore(this, secrets), scope)
        sync = SyncManager(repo, SyncStore(this, secrets), scope)
        calendarSync = SystemCalendarSync(this, repo, settings, CalendarSyncStore(this), scope)
        jxfw = JxfwManager(this, repo, secrets)
        lanWeb = LanWebManager(this, repo)
        updateChecker = UpdateChecker(settings)
        scope.launch {
            loadJob.join()
            // 通知渠道 / 快捷方式 / 规则预热 / Keystore 校验与过期缓存清理放到首屏读库之后，不与首帧抢 IO 和 Binder
            launch(Dispatchers.IO) {
                parcelRules.preload()
                jxfw.hydrate()
                sync.hydrate()
                updateChecker.clearStalePendingUpdate(cacheDir)
                Notifications.ensureChannels(this@MintCalApp)
                DeepLinks.publishShortcuts(this@MintCalApp)
            }
            jxfw.ensureAlarm()
            // 首次进入前台时按周期静默检测更新（小组件 / 闹钟拉起的后台进程不碰网络）
            launch(Dispatchers.IO) {
                uiActive.first { it }
                runCatching { updateChecker.maybeAutoCheck() }
            }
            // 回到前台才做网络同步、系统日历监听与教务抓取；后台拉起的进程（小组件 tick / 开机）不碰网络
            launch {
                uiActive.collect { active ->
                    calendarSync.updateObserving(active)
                    if (active) {
                        sync.requestSync(1000)
                        calendarSync.requestSync(1200)
                        launch { jxfw.catchUp() }
                    }
                }
            }
            // 登录状态下排后台周期同步（MCP / 其他设备的写入由此到达本机），退出登录自动取消。
            launch {
                sync.state
                    .map { it.account != null }
                    .distinctUntilChanged()
                    .collect { PeriodicSync.follow(this@MintCalApp, it) }
            }
            launch {
                calendarSync.state
                    .map { it.prefs.enabled }
                    .distinctUntilChanged()
                    .collect {
                        calendarSync.updateObserving(uiActive.value)
                        PeriodicSync.followCalendar(this@MintCalApp, it)
                    }
            }
            // 本地数据有变化后，稍后触发云端与系统日历对账
            launch {
                repo.snapshot.collect {
                    if (uiActive.value) {
                        sync.requestSync()
                        calendarSync.requestSync()
                    }
                }
            }
            // 数据或设置变化后重新排提醒并刷新全部 5 款桌面小组件（防抖，避免连续编辑时反复调度）
            combine(repo.snapshot, settings.flow) { s, st -> s to st }
                .debounce(800)
                .collect { (snap, st) ->
                    reminders.reschedule(snap, st)
                    if (st.quickAddNotif) Notifications.postQuickAdd(this@MintCalApp) else Notifications.cancelQuickAdd(this@MintCalApp)
                    launch(Dispatchers.IO) { AutoBackup.maybeRun(this@MintCalApp, snap, st) }
                    launch(Dispatchers.Default) {
                        TodayWidgetProvider.refreshAll(this@MintCalApp, snap, st)
                        TodoWidgetProvider.refreshAll(this@MintCalApp)
                        ParcelWidgetProvider.refreshAll(this@MintCalApp)
                        ScheduleWidgetProvider.refreshAll(this@MintCalApp, snap, st)
                        NextWidgetProvider.refreshAll(this@MintCalApp, snap, st)
                    }
                }
        }
    }
}

val Context.mintApp: MintCalApp get() = applicationContext as MintCalApp
