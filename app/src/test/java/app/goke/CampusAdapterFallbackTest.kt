package app.goke

import app.goke.core.campus.CampusHost
import app.goke.core.campus.CampusRepoPort
import app.goke.core.campus.CampusVault
import app.goke.core.campus.JxfwStrings
import app.goke.core.campus.TzcsStrings
import app.goke.core.campus.UnavailableCampusAdapterFactory
import app.goke.core.change.ChangeSet
import app.goke.core.jxfw.ClassroomBuilding
import app.goke.core.jxfw.JxfwNotice
import app.goke.core.jxfw.XkActionResult
import app.goke.core.jxfw.XkCourseItem
import app.goke.core.model.Snapshot
import app.goke.hotwater.HotWaterErrorCode
import app.goke.hotwater.HotWaterShowerRecord
import app.goke.hotwater.HotWaterTaskCopy
import app.goke.jxfw.JxfwException
import app.goke.jxfw.JxfwState
import app.goke.jxfw.TzcsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CampusAdapterFallbackTest {
    private val stubHost =
        object : CampusHost {
            override val vault: CampusVault =
                object : CampusVault {
                    override fun jxfwPassword(): String? = null

                    override fun setJxfwPassword(plain: String) = Unit

                    override fun tzcsPassword(): String? = null

                    override fun setTzcsPassword(plain: String) = Unit

                    override fun tzcsCookies(): String? = null

                    override fun setTzcsCookies(plain: String) = Unit

                    override fun hotWaterUserId(): String? = null

                    override fun setHotWaterUserId(plain: String) = Unit

                    override fun hotWaterUuid(): String? = null

                    override fun setHotWaterUuid(plain: String) = Unit
                }

            override val repo: CampusRepoPort =
                object : CampusRepoPort {
                    override val snapshot: StateFlow<Snapshot> = MutableStateFlow(Snapshot())

                    override suspend fun awaitReady() = Unit

                    override suspend fun loadIfEmpty() = Unit

                    override suspend fun ensureDefaultSemester() = Unit

                    override suspend fun commit(changeSet: ChangeSet) = Unit
                }

            override val jxfwStrings: JxfwStrings =
                object : JxfwStrings {
                    override val progressLogin = ""
                    override val progressSaving = ""
                    override val resultSemesterOver = ""
                    override val notifyTitle = ""
                    override val errNoCredentials = ""
                    override val errNetwork = ""
                    override val errUnsupported = "jxfw-unsupported"

                    override fun progressCourses(
                        done: Int,
                        total: Int,
                    ) = ""

                    override fun progressRefineWeeks(
                        done: Int,
                        total: Int,
                    ) = ""

                    override fun progressWeek(week: Int) = ""

                    override fun resultFull(
                        courses: Int,
                        exams: Int,
                    ) = ""

                    override fun resultNoChange(week: Int) = ""

                    override fun resultChanged(
                        week: Int,
                        changes: Int,
                    ) = ""
                }

            override val tzcsStrings: TzcsStrings =
                object : TzcsStrings {
                    override val errUnknown = ""
                    override val errNetwork = ""
                    override val sessionExpired = ""
                    override val errUnsupported = "tzcs-unsupported"
                }

            override fun recordError(
                tag: String,
                throwable: Throwable,
            ) = Unit

            override fun postWeeklyJxfwNotification(
                title: String,
                body: String,
            ) = Unit

            override fun postJxfwNoticeNotification(newNotices: List<JxfwNotice>) = Unit

            override fun updateWeeklyJxfwAlarm(enabled: Boolean) = Unit

            override fun followHotWaterArrearsWorker(enabled: Boolean) = Unit

            override fun hotWaterTaskCopy(arrearsYuan: Double) = HotWaterTaskCopy("", "", "")

            override fun onHotWaterWidgetRefresh() = Unit

            override fun onHotWaterShowerCompleted(record: HotWaterShowerRecord) = Unit
        }

    @Test
    fun unavailableAdaptersFailCleanlyWhileAllowingUiSeedForTests() =
        runBlocking {
            val jxfw = UnavailableCampusAdapterFactory.createJxfw(Unit, stubHost)
            assertFalse(jxfw.isSupported)
            assertEquals("", jxfw.portalHost)

            val loginErr = runCatching { jxfw.login("u", "p") }.exceptionOrNull() as? JxfwException
            assertEquals(JxfwException.Kind.UNSUPPORTED, loginErr?.kind)

            val enrollRes = jxfw.enrollCourse(XkCourseItem(kcrwdm = "1", name = "Course")).getOrThrow()
            assertTrue(enrollRes is XkActionResult.Failure)

            jxfw.seedStateForTest(
                state = JxfwState(username = "demo"),
                buildings = listOf(ClassroomBuilding(code = "01", name = "B1")),
                noticesState =
                    app.goke.jxfw.JxfwNoticesState(
                        notices =
                            listOf(
                                JxfwNotice(id = "pub-1", title = "Pub", source = app.goke.core.jxfw.JxfwNoticeSource.PUBLIC),
                                JxfwNotice(id = "jxfw:1", title = "Int", source = app.goke.core.jxfw.JxfwNoticeSource.INTERNAL),
                            ),
                        unreadIds = setOf("pub-1", "jxfw:1"),
                    ),
            )
            assertEquals(1, jxfw.fetchClassroomBuildings().size)
            assertTrue(jxfw.noticesState.value.publicEnabled)
            assertTrue(jxfw.noticesState.value.internalEnabled)
            jxfw.setNoticePublicEnabled(false)
            assertFalse(jxfw.noticesState.value.publicEnabled)
            assertEquals(setOf("jxfw:1"), jxfw.noticesState.value.unreadIds)
            assertEquals(setOf("jxfw:1"), jxfw.noticesState.value.activeUnreadIds)
            jxfw.setNoticeInternalEnabled(false)
            assertFalse(jxfw.noticesState.value.internalEnabled)
            assertTrue(
                jxfw.noticesState.value.unreadIds
                    .isEmpty(),
            )
            assertTrue(
                jxfw.noticesState.value.activeUnreadIds
                    .isEmpty(),
            )

            val tzcs = UnavailableCampusAdapterFactory.createTzcs(Unit, stubHost)
            assertFalse(tzcs.isSupported)
            val tzcsErr = tzcs.loadCaptcha().exceptionOrNull() as? TzcsException
            assertEquals(TzcsException.Kind.UNSUPPORTED, tzcsErr?.kind)

            val hw = UnavailableCampusAdapterFactory.createHotWater(Unit, stubHost)
            assertFalse(hw.isSupported)
            val hwRes = hw.toggleValve()
            assertFalse(hwRes.ok)
            assertEquals(HotWaterErrorCode.UNSUPPORTED, hwRes.errorCode)
            val ordersRes = hw.fetchOrders(all = false)
            assertTrue(ordersRes is app.goke.hotwater.HotWaterApiResult.Err)

            val dropDefaults = UnavailableCampusAdapterFactory.dropRelayDefaults()
            assertEquals("", dropDefaults.shareUrl)
            assertEquals("", dropDefaults.dropUrl)
        }
}
