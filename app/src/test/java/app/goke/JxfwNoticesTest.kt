package app.goke

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.goke.core.jxfw.JxfwNotice
import app.goke.core.jxfw.JxfwNoticeSource
import app.goke.jxfw.JxfwNoticesState
import app.goke.notify.Notifications
import app.goke.ui.screens.JxfwNoticesContent
import app.goke.ui.theme.GokeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = SmokeTestApp::class)
class JxfwNoticesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()

    @Test
    fun deepLinkParsesJxfwNoticesUri() {
        val uri = DeepLinks.jxfwNoticesUri()
        assertEquals("goke://jxfw-notices", uri.toString())
        assertEquals(DeepLink.JxfwNotices, DeepLinks.parseUri(uri))
    }

    @Test
    fun notificationChannelAndAggregatedPostUseExpectedIdAndImportance() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.ensureChannels(app)
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(Notifications.CHANNEL_JXFW_NOTICE)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)

        val notices =
            listOf(
                JxfwNotice(id = "info/1216/1.htm", title = "选课安排通知", date = "2026-09-10"),
                JxfwNotice(id = "jxfw:2", title = "成绩已公布", source = JxfwNoticeSource.INTERNAL),
            )
        Notifications.postJxfwNotices(app, notices)
        val posted = shadowOf(nm).getNotification(Notifications.NOTIFY_JXFW_NOTICE_ID)
        assertNotNull(posted)
        assertEquals(Notifications.CHANNEL_JXFW_NOTICE, posted.channelId)

        nm.cancel(Notifications.NOTIFY_JXFW_NOTICE_ID)
        Notifications.postJxfwNotices(
            ctx = app,
            newNotices = listOf(JxfwNotice(id = "info/1216/2.htm", title = "公网公告", source = JxfwNoticeSource.PUBLIC)),
            publicEnabled = false,
            internalEnabled = true,
        )
        org.junit.Assert.assertNull(shadowOf(nm).getNotification(Notifications.NOTIFY_JXFW_NOTICE_ID))

        Notifications.postJxfwNotices(
            ctx = app,
            newNotices = notices,
            publicEnabled = false,
            internalEnabled = true,
        )
        val internalOnly = shadowOf(nm).getNotification(Notifications.NOTIFY_JXFW_NOTICE_ID)
        assertNotNull(internalOnly)
        assertEquals(
            app.getString(R.string.jxfw_notice_notif_internal_title),
            internalOnly.extras.getString(android.app.Notification.EXTRA_TITLE),
        )
        assertEquals(
            "成绩已公布",
            internalOnly.extras.getString(android.app.Notification.EXTRA_TEXT),
        )
    }

    @Test
    fun noticesScreenDefaultsToInternalWhenPublicDisabledAndShowsDisabledState() {
        val state =
            JxfwNoticesState(
                notices =
                    listOf(
                        JxfwNotice(
                            id = "jxfw:1",
                            title = "缓考申请已通过",
                            source = JxfwNoticeSource.INTERNAL,
                        ),
                        JxfwNotice(
                            id = "info/1216/4597.htm",
                            title = "公网测试公告",
                            date = "2026-09-10",
                            source = JxfwNoticeSource.PUBLIC,
                        ),
                    ),
                unreadIds = setOf("jxfw:1", "info/1216/4597.htm"),
                publicEnabled = false,
                internalEnabled = true,
            )

        rule.setContent {
            GokeTheme(app.settings.value) {
                JxfwNoticesContent(
                    state = state,
                    onBack = {},
                    onRefresh = {},
                    onMarkAllRead = {},
                    onNoticeClick = {},
                )
            }
        }

        rule.onNodeWithText("缓考申请已通过").assertIsDisplayed()
        rule.onNodeWithText("公网测试公告").assertDoesNotExist()
        rule.onNodeWithText(app.getString(R.string.jxfw_notices_filter_public)).performClick()
        rule.onNodeWithText(app.getString(R.string.jxfw_notices_disabled_public)).assertIsDisplayed()
    }

    @Test
    fun noticesScreenRendersItemsAndHandlesMarkAllRead() {
        var markAllCalled = false
        var clickedId: String? = null
        val state =
            JxfwNoticesState(
                notices =
                    listOf(
                        JxfwNotice(
                            id = "jxfw:1",
                            title = "未选中《大学体育》，请关注下一轮选课",
                            source = JxfwNoticeSource.INTERNAL,
                        ),
                        JxfwNotice(
                            id = "info/1216/4597.htm",
                            title = "关于2026年春季学期结业换证领取证书的通知",
                            date = "2026-09-10",
                            url = "https://jwc.gdut.edu.cn/info/1216/4597.htm",
                            source = JxfwNoticeSource.PUBLIC,
                        ),
                    ),
                unreadIds = setOf("jxfw:1"),
                fetchedAt = 1_760_000_000_000L,
            )

        rule.setContent {
            GokeTheme(app.settings.value) {
                JxfwNoticesContent(
                    state = state,
                    onBack = {},
                    onRefresh = {},
                    onMarkAllRead = { markAllCalled = true },
                    onNoticeClick = { clickedId = it.id },
                )
            }
        }

        rule.onNodeWithText(app.getString(R.string.jxfw_notices_title)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.jxfw_notices_internal_tag)).assertIsDisplayed()
        rule.onNodeWithText("2026-09-10").assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.jxfw_notices_mark_all_read)).performClick()
        assertTrue(markAllCalled)

        rule.onNodeWithText("关于2026年春季学期结业换证领取证书的通知").performClick()
        assertEquals("info/1216/4597.htm", clickedId)
    }
}
