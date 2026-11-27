package app.goke.core

import app.goke.core.todo.QuickAdd
import app.goke.core.todo.QuickAddConfig
import app.goke.core.todo.QuickToken
import app.goke.core.todo.QuickTokenKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class QuickAddReminderAndTimeTest {
    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 26, 8, 0)

    @Test
    fun standaloneRemindAndBang() {
        val normal = QuickAdd.parse("明天下午3点 开会 提醒", now)
        assertEquals("开会", normal.title)
        assertEquals(listOf(0, 15), normal.remindMinutesList)
        assertFalse(normal.remindPersistent)
        assertTrue(normal.tokens.contains(QuickToken("提醒", QuickTokenKind.REMIND)))

        val bangAscii = QuickAdd.parse("明天下午3点 开会 提醒!", now)
        assertEquals("开会", bangAscii.title)
        assertEquals(listOf(0, 15), bangAscii.remindMinutesList)
        assertTrue(bangAscii.remindPersistent)
        assertTrue(bangAscii.tokens.contains(QuickToken("提醒!", QuickTokenKind.REMIND)))

        val bangFull = QuickAdd.parse("明天下午3点 开会 提醒！", now)
        assertEquals("开会", bangFull.title)
        assertEquals(listOf(0, 15), bangFull.remindMinutesList)
        assertTrue(bangFull.remindPersistent)
        assertTrue(bangFull.tokens.contains(QuickToken("提醒！", QuickTokenKind.REMIND)))
    }

    @Test
    fun customQuickAddConfig() {
        val cfg =
            QuickAddConfig(
                remindKeyword = " 闹钟 ",
                remindMinutesList = listOf(30, 5, 5),
                persistentRemindMinutesList = listOf(10, 0),
                bangPersistent = false,
            )
        val r1 = QuickAdd.parse("交作业 闹钟", now, cfg)
        assertEquals("交作业", r1.title)
        assertEquals(listOf(5, 30), r1.remindMinutesList)
        assertFalse(r1.remindPersistent)

        val r2 = QuickAdd.parse("交作业 闹钟!", now, cfg)
        assertEquals("交作业", r2.title)
        assertEquals(listOf(0, 10), r2.remindMinutesList)
        assertFalse(r2.remindPersistent)

        val disabled = QuickAddConfig(remindKeyword = "   ")
        val r3 = QuickAdd.parse("交作业 提醒 提醒!", now, disabled)
        assertEquals("交作业 提醒 提醒!", r3.title)
        assertTrue(r3.remindMinutesList.isEmpty())
        assertFalse(r3.remindPersistent)
    }

    @Test
    fun explicitOffsetShorthandsAndMultipleTokens() {
        val r1 = QuickAdd.parse("交作业 提醒30分钟", now)
        assertEquals("交作业", r1.title)
        assertEquals(listOf(30), r1.remindMinutesList)
        assertFalse(r1.remindPersistent)

        val r2 = QuickAdd.parse("交作业 提醒30分钟!", now)
        assertEquals("交作业", r2.title)
        assertEquals(listOf(30), r2.remindMinutesList)
        assertTrue(r2.remindPersistent)

        val r3 = QuickAdd.parse("提前15分钟提醒! 交作业", now)
        assertEquals("交作业", r3.title)
        assertEquals(listOf(15), r3.remindMinutesList)
        assertTrue(r3.remindPersistent)

        val r4 = QuickAdd.parse("提前半小时提醒 交作业", now)
        assertEquals("交作业", r4.title)
        assertEquals(listOf(30), r4.remindMinutesList)
        assertFalse(r4.remindPersistent)

        val r5 = QuickAdd.parse("交作业 提醒1小时!", now)
        assertEquals("交作业", r5.title)
        assertEquals(listOf(60), r5.remindMinutesList)
        assertTrue(r5.remindPersistent)

        val r6 = QuickAdd.parse("交作业 提醒15m!", now)
        assertEquals("交作业", r6.title)
        assertEquals(listOf(15), r6.remindMinutesList)
        assertTrue(r6.remindPersistent)

        val r7 = QuickAdd.parse("交作业 提醒0分钟", now)
        assertEquals("交作业", r7.title)
        assertEquals(listOf(0), r7.remindMinutesList)
        assertFalse(r7.remindPersistent)

        val multi = QuickAdd.parse("交作业 提醒30分钟 提醒5分钟!", now)
        assertEquals("交作业", multi.title)
        assertEquals(listOf(5, 30), multi.remindMinutesList)
        assertTrue(multi.remindPersistent)
    }

    @Test
    fun strictBoundariesPreventFalsePositives() {
        val r1 = QuickAdd.parse("提醒小明交作业", now)
        assertEquals("提醒小明交作业", r1.title)
        assertTrue(r1.remindMinutesList.isEmpty())
        assertFalse(r1.remindPersistent)

        val r2 = QuickAdd.parse("设置提醒功能", now)
        assertEquals("设置提醒功能", r2.title)
        assertTrue(r2.remindMinutesList.isEmpty())

        val r3 = QuickAdd.parse("设置提醒", now)
        assertEquals("设置提醒", r3.title)
        assertTrue(r3.remindMinutesList.isEmpty())

        val r4 = QuickAdd.parse("提醒小明交作业 提醒", now)
        assertEquals("提醒小明交作业", r4.title)
        assertEquals(listOf(0, 15), r4.remindMinutesList)
        assertFalse(r4.remindPersistent)
    }

    @Test
    fun priorityCoexistenceWithoutCollision() {
        val r1 = QuickAdd.parse("买牛奶 提醒! !高", now)
        assertEquals("买牛奶", r1.title)
        assertEquals(3, r1.priority)
        assertEquals(listOf(0, 15), r1.remindMinutesList)
        assertTrue(r1.remindPersistent)

        val r2 = QuickAdd.parse("买牛奶 提醒!高", now)
        assertEquals("买牛奶", r2.title)
        assertEquals(3, r2.priority)
        assertEquals(listOf(0, 15), r2.remindMinutesList)
        assertFalse(r2.remindPersistent)

        val r3 = QuickAdd.parse("买牛奶 提醒!!高", now)
        assertEquals("买牛奶", r3.title)
        assertEquals(3, r3.priority)
        assertEquals(listOf(0, 15), r3.remindMinutesList)
        assertTrue(r3.remindPersistent)

        val r4 = QuickAdd.parse("买牛奶 提醒30分钟 !1", now)
        assertEquals("买牛奶", r4.title)
        assertEquals(1, r4.priority)
        assertEquals(listOf(30), r4.remindMinutesList)
        assertFalse(r4.remindPersistent)

        val r5 = QuickAdd.parse("买牛奶 !3 提醒!", now)
        assertEquals("买牛奶", r5.title)
        assertEquals(3, r5.priority)
        assertEquals(listOf(0, 15), r5.remindMinutesList)
        assertTrue(r5.remindPersistent)
    }

    @Test
    fun remindWithoutDueDateDoesNotFabricateDue() {
        val r = QuickAdd.parse("买牛奶 提醒!", now)
        assertEquals("买牛奶", r.title)
        assertNull(r.due)
        assertFalse(r.dueHasTime)
        assertEquals(listOf(0, 15), r.remindMinutesList)
        assertTrue(r.remindPersistent)
        assertEquals(listOf(QuickToken("提醒!", QuickTokenKind.REMIND)), r.tokens)
    }

    @Test
    fun chineseAndMixedTimeVariants() {
        fun timeOf(expr: String): Pair<Int, Int> {
            val r = QuickAdd.parse("明天 $expr 开会", now)
            assertEquals("开会", r.title, "title mismatch for: $expr")
            assertTrue(r.dueHasTime, "dueHasTime should be true for: $expr")
            val due = r.due!!
            return due.hour to due.minute
        }

        assertEquals(12 to 30, timeOf("十二点三十"))
        assertEquals(12 to 30, timeOf("十二点三十分"))
        assertEquals(10 to 5, timeOf("十点零五"))
        assertEquals(10 to 5, timeOf("十点零五分"))
        assertEquals(10 to 5, timeOf("十点〇五"))
        assertEquals(2 to 30, timeOf("两点半"))
        assertEquals(8 to 15, timeOf("八点一刻"))
        assertEquals(8 to 30, timeOf("八点两刻"))
        assertEquals(9 to 45, timeOf("九点三刻"))
        assertEquals(15 to 45, timeOf("下午三点四十五"))
        assertEquals(23 to 20, timeOf("晚上十一点二十"))
        assertEquals(12 to 30, timeOf("12点三十"))
        assertEquals(12 to 30, timeOf("十二点30"))
        assertEquals(12 to 30, timeOf("12点30分"))
        assertEquals(8 to 45, timeOf("差一刻九点"))
        assertEquals(7 to 50, timeOf("差十分八点"))
        assertEquals(14 to 45, timeOf("下午差一刻三点"))
        assertEquals(8 to 55, timeOf("差5分到9点"))
        assertEquals(8 to 0, timeOf("八点整"))
        assertEquals(8 to 0, timeOf("八点钟"))
        assertEquals(0 to 0, timeOf("凌晨12点"))
        assertEquals(0 to 0, timeOf("晚上12点"))
        assertEquals(6 to 0, timeOf("清晨6点"))
        assertEquals(7 to 30, timeOf("早晨7点半"))
        assertEquals(14 to 0, timeOf("午后2点"))
        assertEquals(20 to 0, timeOf("晚间8点"))
        assertEquals(22 to 0, timeOf("夜间10点"))
        assertEquals(23 to 0, timeOf("夜里11点"))
        assertEquals(0 to 0, timeOf("半夜12点"))
        assertEquals(1 to 0, timeOf("深夜1点"))
        assertEquals(23 to 0, timeOf("深夜11点"))
    }

    @Test
    fun parseLinesForwardsConfigToSubtasks() {
        val cfg = QuickAddConfig(remindKeyword = "闹钟", remindMinutesList = listOf(10))
        val items =
            QuickAdd.parseLines(
                """
                准备周会 闹钟
                  - 打印资料 闹钟!
                """.trimIndent(),
                now,
                cfg,
            )
        assertEquals(2, items.size)
        assertNull(items[0].parentIndex)
        assertEquals(listOf(10), items[0].result.remindMinutesList)
        assertFalse(items[0].result.remindPersistent)

        assertEquals(0, items[1].parentIndex)
        assertEquals(listOf(0, 15), items[1].result.remindMinutesList)
        assertTrue(items[1].result.remindPersistent)
    }
}
