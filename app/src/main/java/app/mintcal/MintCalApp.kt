package app.mintcal

import android.app.Application
import android.content.Context
import app.mintcal.advanced.AdvancedController
import app.mintcal.core.change.Unify
import app.mintcal.data.AutoBackup
import app.mintcal.data.MintDb
import app.mintcal.data.ParcelRuleStore
import app.mintcal.data.Repository
import app.mintcal.data.SecretStore
import app.mintcal.data.SettingsStore
import app.mintcal.focus.FocusController
import app.mintcal.jxfw.JxfwManager
import app.mintcal.notify.Notifications
import app.mintcal.notify.ReminderScheduler
import app.mintcal.sync.PeriodicSync
import app.mintcal.sync.SyncManager
import app.mintcal.sync.SyncStore
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 应用级容器：数据仓库、设置、密钥、提醒调度、高级修改控制器。open 仅为测试子类（SmokeTestApp）服务。 */
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
    lateinit var sync: SyncManager
        private set
    lateinit var jxfw: JxfwManager
        private set

    /** 专注计时：运行状态存 SharedPreferences，倒计时到点由 [app.mintcal.focus.FocusReceiver] 补记。 */
    lateinit var focus: FocusController
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
        parcelRules = ParcelRuleStore(this)
        secrets = SecretStore(this)
        reminders = ReminderScheduler(this)
        advanced = AdvancedController(this)
        sync = SyncManager(repo, SyncStore(this, secrets), scope)
        jxfw = JxfwManager(this, repo, secrets)
        focus = FocusController(this)
        // 通知渠道 / 桌面快捷方式都是 Binder IPC，不挡 Application 主线程的首帧
        scope.launch(Dispatchers.Default) {
            Notifications.ensureChannels(this@MintCalApp)
            DeepLinks.publishShortcuts(this@MintCalApp)
        }
        scope.launch {
            repo.load()
            // 一次性迁移：日程并进待办（「有时间的待办」），此后不再产生日程记录
            if (!settings.value.unifiedTasks) {
                val snap = repo.snapshot.value
                Unify.changeSet(snap, System.currentTimeMillis())?.let { repo.commit(it) }
                settings.update { it.copy(unifiedTasks = true) }
            }
            jxfw.ensureAlarm()
            // 回到前台才做网络同步与教务抓取；后台拉起的进程（小组件 tick / 开机）不碰网络
            launch {
                uiActive.collect { active ->
                    if (active) {
                        sync.requestSync(1000)
                        launch { jxfw.catchUp() }
                    }
                }
            }
            // 登录状态下排后台周期同步（MCP / 其他设备的写入由此到达本机），退出登录自动取消。
            // 登录状态只在真正变化时才触发（发布/订阅流的中间态不再反复 enqueue WorkManager）
            launch {
                sync.state
                    .map { it.account != null }
                    .distinctUntilChanged()
                    .collect { PeriodicSync.follow(this@MintCalApp, it) }
            }
            // 本地数据有变化（含从服务器写入的）后，登录状态下稍后同步一次；未登录时 requestSync 什么都不做。
            // 后台进程里改数据（小组件勾选等）不触发网络，等下次前台会话或 15 分钟周期同步兜底
            launch { repo.snapshot.collect { if (uiActive.value) sync.requestSync() } }
            // 数据或设置变化后重新排提醒并刷新小组件（防抖，避免连续编辑时反复调度）
            combine(repo.snapshot, settings.flow) { s, st -> s to st }
                .debounce(800)
                .collect { (snap, st) ->
                    reminders.reschedule(snap, st)
                    // 常驻快速添加通知跟随设置（重启 / 数据变化时自动恢复或撤掉）
                    if (st.quickAddNotif) Notifications.postQuickAdd(this@MintCalApp) else Notifications.cancelQuickAdd(this@MintCalApp)
                    // 自动备份：节流为每天一次，失败静默
                    launch(kotlinx.coroutines.Dispatchers.IO) { AutoBackup.maybeRun(this@MintCalApp, snap, st) }
                    // 小组件：RemoteViews 构建（含课程/待办展开）与 Binder 刷新移出主线程，
                    // 编辑后的防抖回调不再在 Main 上连做三次整套刷新
                    launch(Dispatchers.Default) {
                        TodayWidgetProvider.refreshAll(this@MintCalApp, snap, st)
                        TodoWidgetProvider.refreshAll(this@MintCalApp)
                        ScheduleWidgetProvider.refreshAll(this@MintCalApp, snap, st)
                    }
                }
        }
    }
}

val Context.mintApp: MintCalApp get() = applicationContext as MintCalApp
