package app.goke

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import app.goke.advanced.AdvancedController
import app.goke.calendar.CalendarSyncStore
import app.goke.calendar.SystemCalendarSync
import app.goke.core.campus.CampusAdapterFactory
import app.goke.core.campus.CampusHost
import app.goke.core.campus.CampusRepoPort
import app.goke.core.campus.CampusVault
import app.goke.core.campus.HotWaterAdapter
import app.goke.core.campus.JxfwAdapter
import app.goke.core.campus.JxfwStrings
import app.goke.core.campus.TzcsAdapter
import app.goke.core.campus.TzcsStrings
import app.goke.core.campus.UnavailableCampusAdapterFactory
import app.goke.core.change.ChangeSet
import app.goke.core.change.Unify
import app.goke.core.jxfw.JxfwNotice
import app.goke.core.jxfw.JxfwSync
import app.goke.core.model.Snapshot
import app.goke.data.AutoBackup
import app.goke.data.ErrorReporter
import app.goke.data.MintDb
import app.goke.data.ParcelRuleStore
import app.goke.data.Repository
import app.goke.data.SecretStore
import app.goke.data.SettingsStore
import app.goke.data.UpdateChecker
import app.goke.debug.DebugHooks
import app.goke.droprelay.DropRelayManager
import app.goke.hotwater.HotWaterArrearsWorker
import app.goke.hotwater.HotWaterShowerRecord
import app.goke.hotwater.HotWaterTaskCopy
import app.goke.jxfw.JxfwReceiver
import app.goke.notify.Notifications
import app.goke.notify.ParcelCapture
import app.goke.notify.ParcelListenerService
import app.goke.notify.ReminderScheduler
import app.goke.share.TimetableShareManager
import app.goke.share.TimetableShareStore
import app.goke.sync.PeriodicSync
import app.goke.widget.HotWaterWidgetProvider
import app.goke.widget.NextWidgetProvider
import app.goke.widget.ParcelWidgetProvider
import app.goke.widget.ScheduleWidgetProvider
import app.goke.widget.TodayWidgetProvider
import app.goke.widget.TodoWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** 应用级容器：数据仓库、设置、密钥、提醒调度、本地 MCP 与高级修改预览控制器。open 仅为测试子类（SmokeTestApp）服务。 */
open class GokeApp : Application() {
    val errors = ErrorReporter()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + errors.coroutineExceptionHandler)
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
    lateinit var calendarSync: SystemCalendarSync
        private set
    lateinit var jxfw: JxfwAdapter
        private set
    lateinit var tzcs: TzcsAdapter
        private set
    lateinit var hotWater: HotWaterAdapter
        private set
    lateinit var timetableShare: TimetableShareManager
        private set
    lateinit var dropRelay: DropRelayManager
        private set
    lateinit var updateChecker: UpdateChecker
        private set
    lateinit var feedback: app.goke.ui.FeedbackEngine
        private set

    /** 外部入口（通知 / 小组件 / 快捷方式 / 分享）待处理的动作，由根组合消费后清空。 */
    val links = MutableStateFlow<DeepLink?>(null)

    /** 有 Activity 在前台时为 true。小组件 tick / 开机 / 通知监听拉起的进程为 false：
     *  这些进程只需要读库刷新小组件与提醒，不需要网络同步和教务抓取。 */
    val uiActive = MutableStateFlow(false)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        DebugHooks.init(this)
        errors.attach(this)
        repo = Repository(MintDb(this))
        settings = SettingsStore(this)
        feedback = app.goke.ui.FeedbackEngine(this) { settings.value }
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
                JxfwSync.migrateExamTasksToCourses(repo.snapshot.value, System.currentTimeMillis())?.let { repo.commit(it) }
            }
        parcelRules = ParcelRuleStore(this)
        secrets = SecretStore(this)
        reminders = ReminderScheduler(this)
        advanced = AdvancedController(this)
        calendarSync = SystemCalendarSync(this, repo, settings, CalendarSyncStore(this), scope, errors = errors)

        val campusHost = createCampusHost()
        val adapterFactory = loadCampusAdapterFactory()
        jxfw = adapterFactory.createJxfw(this, campusHost)
        tzcs = adapterFactory.createTzcs(this, campusHost)
        hotWater = adapterFactory.createHotWater(this, campusHost)
        dropRelay = DropRelayManager(this, settings, adapterFactory.dropRelayDefaults(), errors)

        timetableShare =
            TimetableShareManager(
                repo = repo,
                store = TimetableShareStore(this, secrets),
                jxfw = jxfw,
                defaultServerUrlProvider = { TimetableShareManager.DEFAULT_SERVER_URL },
                errors = errors,
            )

        updateChecker = UpdateChecker(settings)
        scope.launch {
            loadJob.join()
            // 通知渠道 / 快捷方式 / 规则预热 / Keystore 校验与过期缓存清理放到首屏读库之后，不与首帧抢 IO 和 Binder
            launch(Dispatchers.IO) {
                parcelRules.preload()
                jxfw.hydrate()
                tzcs.hydrate()
                hotWater.hydrate()
                dropRelay.hydrate()
                PeriodicSync.follow(this@GokeApp, false)
                updateChecker.clearStalePendingUpdate(cacheDir)
                Notifications.ensureChannels(this@GokeApp)
                DeepLinks.publishShortcuts(this@GokeApp)
            }
            jxfw.ensureAlarm()
            // 首次进入前台时按周期静默检测更新（小组件 / 闹钟拉起的后台进程不碰网络）
            launch(Dispatchers.IO) {
                uiActive.first { it }
                runCatching { updateChecker.maybeAutoCheck() }
            }
            // 回到前台才做系统日历监听与教务抓取；后台拉起的进程（小组件 tick / 开机）不碰网络
            launch {
                uiActive.collect { active ->
                    calendarSync.updateObserving(active)
                    if (active) {
                        calendarSync.requestSync(1200)
                        launch { jxfw.catchUp() }
                        if (settings.value.parcelEnabled) {
                            launch(Dispatchers.IO) {
                                ParcelCapture.scanRecentSmsInbox(this@GokeApp)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                                    !ParcelListenerService.isConnected &&
                                    NotificationManagerCompat.getEnabledListenerPackages(this@GokeApp).contains(packageName)
                                ) {
                                    runCatching {
                                        NotificationListenerService.requestRebind(
                                            ComponentName(this@GokeApp, ParcelListenerService::class.java),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            launch {
                calendarSync.state
                    .map { it.prefs.enabled }
                    .distinctUntilChanged()
                    .collect {
                        calendarSync.updateObserving(uiActive.value)
                        PeriodicSync.followCalendar(this@GokeApp, it)
                    }
            }
            launch {
                hotWater.state
                    .map { it.isBound && it.config.arrearsTodoEnabled }
                    .distinctUntilChanged()
                    .collect { HotWaterArrearsWorker.follow(this@GokeApp, it) }
            }
            // 本地数据有变化后，稍后触发系统日历对账
            launch {
                repo.snapshot.collect { snap ->
                    if (uiActive.value) {
                        calendarSync.onLocalSnapshotChanged(snap)
                    }
                }
            }
            // 数据或设置变化后重新排提醒并刷新全部 5 款桌面小组件（防抖，避免连续编辑时反复调度）
            combine(repo.snapshot, settings.flow) { s, st -> s to st }
                .debounce(800)
                .collect { (snap, st) ->
                    reminders.reschedule(snap, st)
                    if (st.quickAddNotif) Notifications.postQuickAdd(this@GokeApp) else Notifications.cancelQuickAdd(this@GokeApp)
                    launch(Dispatchers.IO) { AutoBackup.maybeRun(this@GokeApp, snap, st) }
                    launch(Dispatchers.Default) {
                        TodayWidgetProvider.refreshAll(this@GokeApp, snap, st)
                        TodoWidgetProvider.refreshAll(this@GokeApp)
                        ParcelWidgetProvider.refreshAll(this@GokeApp)
                        ScheduleWidgetProvider.refreshAll(this@GokeApp, snap, st)
                        NextWidgetProvider.refreshAll(this@GokeApp, snap, st)
                    }
                }
        }
    }

    private fun loadCampusAdapterFactory(): CampusAdapterFactory =
        runCatching {
            Class
                .forName(PRIVATE_ADAPTER_FACTORY)
                .getDeclaredConstructor()
                .newInstance() as CampusAdapterFactory
        }.getOrElse { UnavailableCampusAdapterFactory }

    private fun createCampusHost(): CampusHost =
        object : CampusHost {
            override val vault: CampusVault get() = secrets
            override val repo: CampusRepoPort =
                object : CampusRepoPort {
                    override val snapshot: StateFlow<Snapshot> get() = this@GokeApp.repo.snapshot

                    override suspend fun awaitReady() {
                        this@GokeApp.repo.ready.first { it }
                    }

                    override suspend fun loadIfEmpty() {
                        if (this@GokeApp
                                .repo.snapshot.value.semesters
                                .isEmpty()
                        ) {
                            this@GokeApp.repo.load()
                        }
                    }

                    override suspend fun ensureDefaultSemester() {
                        this@GokeApp.repo.ensureDefaultSemester()
                    }

                    override suspend fun commit(changeSet: ChangeSet) {
                        this@GokeApp.repo.commit(changeSet)
                    }
                }

            override val jxfwStrings: JxfwStrings =
                object : JxfwStrings {
                    override val progressLogin: String get() = getString(R.string.jxfw_progress_login)
                    override val progressSaving: String get() = getString(R.string.jxfw_progress_saving)
                    override val resultSemesterOver: String get() = getString(R.string.jxfw_result_semester_over)
                    override val notifyTitle: String get() = getString(R.string.jxfw_notify_title)
                    override val errNoCredentials: String get() = getString(R.string.jxfw_err_no_credentials)
                    override val errNetwork: String get() = getString(R.string.jxfw_err_network)
                    override val errUnsupported: String get() = getString(R.string.jxfw_err_unsupported)

                    override fun progressCourses(
                        done: Int,
                        total: Int,
                    ): String = getString(R.string.jxfw_progress_courses, done, total)

                    override fun progressRefineWeeks(
                        done: Int,
                        total: Int,
                    ): String = getString(R.string.jxfw_progress_refine_weeks, done, total)

                    override fun progressWeek(week: Int): String = getString(R.string.jxfw_progress_week, week)

                    override fun resultFull(
                        courses: Int,
                        exams: Int,
                    ): String = getString(R.string.jxfw_result_full, courses, exams)

                    override fun resultNoChange(week: Int): String = getString(R.string.jxfw_result_nochange, week)

                    override fun resultChanged(
                        week: Int,
                        changes: Int,
                    ): String = getString(R.string.jxfw_result_changed, week, changes)
                }

            override val tzcsStrings: TzcsStrings =
                object : TzcsStrings {
                    override val errUnknown: String get() = getString(R.string.tzcs_err_unknown)
                    override val errNetwork: String get() = getString(R.string.tzcs_err_network)
                    override val sessionExpired: String get() = getString(R.string.tzcs_session_expired)
                    override val errUnsupported: String get() = getString(R.string.tzcs_err_unsupported)
                }

            override fun recordError(
                tag: String,
                throwable: Throwable,
            ) {
                errors.record(tag, throwable)
            }

            override fun recordBreadcrumb(
                tag: String,
                message: String,
            ) {
                errors.breadcrumb(tag, message)
            }

            override fun postWeeklyJxfwNotification(
                title: String,
                body: String,
            ) {
                Notifications.post(this@GokeApp, JXFW_NOTIFY_ID, Notifications.CHANNEL_REMINDER, title, body)
            }

            override fun postJxfwNoticeNotification(newNotices: List<JxfwNotice>) {
                Notifications.postJxfwNotices(this@GokeApp, newNotices)
            }

            override fun updateWeeklyJxfwAlarm(enabled: Boolean) {
                val am = getSystemService(AlarmManager::class.java) ?: return
                if (!enabled) {
                    PendingIntent
                        .getBroadcast(
                            this@GokeApp,
                            JXFW_ALARM_CODE,
                            Intent(this@GokeApp, JxfwReceiver::class.java),
                            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                        )?.let {
                            am.cancel(it)
                            it.cancel()
                        }
                    return
                }
                val pi =
                    PendingIntent.getBroadcast(
                        this@GokeApp,
                        JXFW_ALARM_CODE,
                        Intent(this@GokeApp, JxfwReceiver::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ) ?: return
                val zone = ZoneId.systemDefault()
                var at = LocalDateTime.of(LocalDate.now(zone).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)), LocalTime.of(21, 0))
                if (at.isBefore(LocalDateTime.now(zone))) at = at.plusWeeks(1)
                am.setInexactRepeating(AlarmManager.RTC_WAKEUP, at.atZone(zone).toInstant().toEpochMilli(), AlarmManager.INTERVAL_DAY * 7, pi)
            }

            override fun followHotWaterArrearsWorker(enabled: Boolean) {
                HotWaterArrearsWorker.follow(this@GokeApp, enabled)
            }

            override fun hotWaterTaskCopy(arrearsYuan: Double): HotWaterTaskCopy =
                HotWaterTaskCopy(
                    title = getString(R.string.hw_arrears_task_title, arrearsYuan),
                    note = getString(R.string.hw_arrears_task_note, arrearsYuan),
                    opDesc = getString(R.string.hw_arrears_task_op_desc),
                )

            override fun onHotWaterWidgetRefresh() {
                HotWaterWidgetProvider.refreshAll(this@GokeApp)
            }

            override fun onHotWaterShowerCompleted(record: HotWaterShowerRecord) {
                Notifications.postHotWaterSummary(this@GokeApp, record)
            }
        }

    companion object {
        private const val PRIVATE_ADAPTER_FACTORY = "app.goke.adapters.PrivateCampusAdapterFactory"
        private const val JXFW_ALARM_CODE = 7421
        private const val JXFW_NOTIFY_ID = 7422
    }
}

val Context.gokeApp: GokeApp get() = applicationContext as GokeApp
