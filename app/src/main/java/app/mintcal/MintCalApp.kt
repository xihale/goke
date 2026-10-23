package app.mintcal

import android.app.Application
import android.content.Context
import app.mintcal.ai.AdvancedController
import app.mintcal.data.MintDb
import app.mintcal.data.Repository
import app.mintcal.data.SecretStore
import app.mintcal.data.SettingsStore
import app.mintcal.notify.Notifications
import app.mintcal.notify.ReminderScheduler
import app.mintcal.widget.TodayWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
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
    lateinit var secrets: SecretStore
        private set
    lateinit var reminders: ReminderScheduler
        private set
    lateinit var advanced: AdvancedController
        private set

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        repo = Repository(MintDb(this))
        settings = SettingsStore(this)
        secrets = SecretStore(this)
        reminders = ReminderScheduler(this)
        advanced = AdvancedController(this)
        Notifications.ensureChannels(this)
        scope.launch {
            repo.load()
            // 数据或设置变化后重新排提醒并刷新小组件（防抖，避免连续编辑时反复调度）
            combine(repo.snapshot, settings.flow) { s, st -> s to st }
                .debounce(800)
                .collect { (snap, st) ->
                    reminders.reschedule(snap, st)
                    TodayWidgetProvider.refreshAll(this@MintCalApp, snap, st)
                }
        }
    }
}

val Context.mintApp: MintCalApp get() = applicationContext as MintCalApp
