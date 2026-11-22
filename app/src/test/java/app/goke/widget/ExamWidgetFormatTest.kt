package app.goke.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.goke.R
import app.goke.SmokeTestApp
import app.goke.core.model.Event
import app.goke.core.model.EventKind
import app.goke.core.model.Snapshot
import app.goke.core.model.Source
import app.goke.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SmokeTestApp::class)
class ExamWidgetFormatTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun examEvent(
        id: String = "exam_1",
        title: String = "高等数学",
        location: String = "教3-208",
        note: String = "大学城 · 学院停课考 · 座位号 34",
        kind: EventKind = EventKind.EXAM,
        start: LocalDateTime = LocalDateTime.of(2026, 6, 15, 9, 0),
        end: LocalDateTime = LocalDateTime.of(2026, 6, 15, 11, 0),
    ) = Event(
        id = id,
        title = title,
        start = start,
        end = end,
        location = location,
        note = note,
        kind = kind,
        source = Source.MANUAL,
    )

    @Test
    fun eventLocationFormatsExamSeatAndFallsBackCleanly() {
        assertEquals("教3-208 · 34座", WidgetKit.eventLocation(ctx, examEvent()))
        assertEquals("教3-208", WidgetKit.eventLocation(ctx, examEvent(note = "大学城 · 学院停课考")))
        assertEquals("34座", WidgetKit.eventLocation(ctx, examEvent(location = "")))
        assertNull(WidgetKit.eventLocation(ctx, examEvent(location = "", note = "")))
        assertEquals("图书馆", WidgetKit.eventLocation(ctx, examEvent(location = "图书馆", kind = EventKind.NORMAL)))
    }

    @Test
    fun todayWidgetSurfacesExamRoomAndSeat() {
        val snap =
            Snapshot(
                events =
                    listOf(
                        examEvent(id = "e_seat"),
                        examEvent(id = "e_noseat", location = "教4-101", note = "停课考"),
                        examEvent(id = "e_normal", location = "活动中心", note = "座位号 99", kind = EventKind.NORMAL),
                    ),
            )
        val now = LocalDateTime.of(2026, 6, 15, 8, 0)
        val rows = TodayWidgetProvider.buildRows(ctx, snap, Settings(), now)
        assertEquals("教3-208 · 34座", rows.first { it.eventId == "e_seat" }.sub)
        assertEquals("教4-101", rows.first { it.eventId == "e_noseat" }.sub)
        assertEquals("活动中心", rows.first { it.eventId == "e_normal" }.sub)
    }

    @Test
    fun nextWidgetSurfacesUpcomingAndOngoingExam() {
        val examWithSeat = examEvent(id = "e_seat")
        val snapWithSeat = Snapshot(events = listOf(examWithSeat))

        val upcoming = NextWidgetProvider.pickSummary(ctx, snapWithSeat, Settings(), LocalDateTime.of(2026, 6, 15, 8, 30))
        assertEquals("09:00", upcoming.badge)
        assertEquals("教3-208 · 34座", upcoming.sub)
        assertEquals("高等数学", upcoming.title)
        assertEquals("e_seat", upcoming.eventId)

        val ongoing = NextWidgetProvider.pickSummary(ctx, snapWithSeat, Settings(), LocalDateTime.of(2026, 6, 15, 9, 30))
        assertEquals(ctx.getString(R.string.widget_ongoing), ongoing.badge)
        assertEquals("09:00–11:00 · 教3-208 · 34座", ongoing.sub)
        assertEquals("e_seat", ongoing.eventId)

        val examNoSeat = examEvent(id = "e_noseat", note = "大学城")
        val fallback =
            NextWidgetProvider.pickSummary(
                ctx,
                Snapshot(events = listOf(examNoSeat)),
                Settings(),
                LocalDateTime.of(2026, 6, 15, 8, 30),
            )
        assertEquals("教3-208", fallback.sub)
    }
}
