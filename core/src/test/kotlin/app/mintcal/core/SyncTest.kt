package app.mintcal.core

import app.mintcal.core.model.Course
import app.mintcal.core.model.FocusSession
import app.mintcal.core.model.Record
import app.mintcal.core.model.Snapshot
import app.mintcal.core.model.Task
import app.mintcal.core.sync.SyncCodec
import app.mintcal.core.sync.SyncPlanner
import app.mintcal.core.sync.WireRecord
import app.mintcal.core.util.MintJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncTest {
    private val sem = TestUtil.semester

    private fun course(
        id: String,
        name: String,
    ) = Course(id, sem.id, name, dayOfWeek = 1, startPeriod = 1, endPeriod = 2, weeks = setOf(1, 2))

    private val a = course("a", "高数").withMeta(100, null)
    private val b = course("b", "英语").withMeta(200, null)
    private val snap = Snapshot(semesters = listOf(sem), courses = listOf(a, b))

    @Test fun `wire record round trip keeps every record kind`() {
        val task = Task("t1", "交作业").withMeta(5, null)
        val focus = FocusSession("f1", "t1", 1000, 61_000, pausedMillis = 1000, plannedMinutes = 1).withMeta(6, null)
        for (r in listOf(sem, a, task, focus)) {
            val w = SyncCodec.encode(r)
            assertEquals(r::class.simpleName, w.kind)
            val text = MintJson.encodeToString(WireRecord.serializer(), w)
            val back = SyncCodec.decode(MintJson.decodeFromString(WireRecord.serializer(), text))
            assertEquals(r, back)
        }
        assertEquals(
            "focusSession",
            MintJson
                .encodeToJsonElement(Record.serializer(), focus)
                .jsonObject["_t"]!!
                .jsonPrimitive.content,
        )
    }

    @Test fun `pristine ignores focus sessions only when none exist`() {
        val focus = FocusSession("f1", null, 1000, 61_000).withMeta(6, null)
        assertFalse(SyncPlanner.isPristine(snap.put(focus)))
        assertTrue(SyncPlanner.isPristine(Snapshot(semesters = listOf(sem))))
    }

    @Test fun `tombstone keeps deletedAt on the wire`() {
        val gone = a.withMeta(300, 300)
        val w = SyncCodec.encode(gone)
        assertEquals(300L, w.deletedAt)
        assertEquals(gone, SyncCodec.decode(w))
    }

    @Test fun `unknown record type is skipped`() {
        val future = MintJson.parseToJsonElement("""{"type":"from-the-future","id":"x"}""").jsonObject
        val w = SyncCodec.encode(a).copy(data = future)
        assertNull(SyncCodec.decode(w))
    }

    @Test fun `pending returns records changed after watermark including tombstones`() {
        val withGone = snap.put(a.withMeta(300, 300))
        val ids = SyncPlanner.pending(withGone, 150).map { it.id }
        assertEquals(listOf("b", "a"), ids)
        assertTrue(SyncPlanner.pending(withGone, 300).isEmpty())
    }

    @Test fun `incoming applies only newer or missing records`() {
        val newerA = a.withMeta(150, null)
        val olderB = b.withMeta(100, null)
        val sameB = b
        val brandNew = course("c", "物理").withMeta(10, null)
        val out = SyncPlanner.incoming(snap, listOf(newerA, olderB, sameB, brandNew)).map { it.id }
        assertEquals(listOf("a", "c"), out)
    }

    @Test fun `pristine detection`() {
        assertTrue(SyncPlanner.isPristine(Snapshot(semesters = listOf(sem))))
        assertFalse(SyncPlanner.isPristine(snap))
        assertFalse(SyncPlanner.isPristine(Snapshot(semesters = listOf(sem, sem.copy(id = "other")))))
    }
}
