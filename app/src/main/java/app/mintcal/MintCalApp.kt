package app.mintcal

import android.app.Application
import android.content.Context
import app.mintcal.ai.AdvancedController
import app.mintcal.core.change.Unify
import app.mintcal.data.AutoBackup
import app.mintcal.data.MintDb
import app.mintcal.data.ParcelRuleStore
import app.mintcal.data.Repository
import app.mintcal.data.SecretStore
import app.mintcal.data.SettingsStore
import app.mintcal.jxfw.JxfwManager
import app.mintcal.notify.Notifications
import app.mintcal.notify.ReminderScheduler
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
import kotlinx.coroutines.launch

/** 应用级容器：数据仓库、设置、密钥、提醒调度、高级修改控制器。 */
class MintCalApp : Application() {
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

    /** 外部入口（通知 / 小组件 / 快捷方式 / 分享）待处理的动作，由根组合消费后清空。 */
    val links = MutableStateFlow<DeepLink?>(null)

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
            sync.requestSync(1000)
            jxfw.ensureAlarm()
            launch { jxfw.catchUp() }
            // 本地数据有变化（含从服务器写入的）后，登录状态下稍后同步一次；未登录时 requestSync 什么都不做
            launch { repo.snapshot.collect { sync.requestSync() } }
            // 数据或设置变化后重新排提醒并刷新小组件（防抖，避免连续编辑时反复调度）
            combine(repo.snapshot, settings.flow) { s, st -> s to st }
                .debounce(800)
                .collect { (snap, st) ->
                    reminders.reschedule(snap, st)
                    // 常驻快速添加通知跟随设置（重启 / 数据变化时自动恢复或撤掉）
                    if (st.quickAddNotif) Notifications.postQuickAdd(this@MintCalApp) else Notifications.cancelQuickAdd(this@MintCalApp)
                    // 自动备份：节流为每天一次，失败静默
                    launch(kotlinx.coroutines.Dispatchers.IO) { AutoBackup.maybeRun(this@MintCalApp, snap, st) }
                    TodayWidgetProvider.refreshAll(this@MintCalApp, snap, st)
                    TodoWidgetProvider.refreshAll(this@MintCalApp)
                    ScheduleWidgetProvider.refreshAll(this@MintCalApp, snap, st)
                }
        }
    }
}

val Context.mintApp: MintCalApp get() = applicationContext as MintCalApp
