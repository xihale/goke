package app.mintcal.ui.screens

import app.mintcal.core.model.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 课程编辑画布的周次代数：画 / 搬 / 移除都是「画笔周」与「时间周集」的集合运算。 */
class CourseGridOpsTest {
    private fun draft(
        dow: Int,
        sp: Int,
        ep: Int,
        weeks: Set<Int>,
        base: String? = null,
    ) = PatternDraft(
        base?.let { Course(id = it, semesterId = "s", name = "n", dayOfWeek = dow, startPeriod = sp, endPeriod = ep, weeks = weeks) },
        dow,
        sp,
        ep,
        weeks,
        null,
    )

    /** base 的 Course 字段由 materialize 落值，代数只动 (位置, 周集)，按投影比较。 */
    private fun List<PatternDraft>.proj(): List<List<Any?>> = map { listOf(it.base?.id, it.dow, it.sp, it.ep, it.weeks.sorted()) }

    private fun List<PatternDraft>.assertProj(vararg expected: List<Any?>) = assertEquals(expected.toList(), proj())

    @Test
    fun drawOnEmptySlotCreatesDraftWithBrushWeeks() {
        PatternOps.draw(emptyList(), dow = 1, sp = 3, ep = 4, brush = setOf(1, 2, 3)).assertProj(listOf(null, 1, 3, 4, listOf(1, 2, 3)))
    }

    @Test
    fun drawExactSlotUnionsWeeks() {
        PatternOps.draw(listOf(draft(1, 3, 4, setOf(1, 2))), 1, 3, 4, brush = setOf(2, 3)).assertProj(listOf(null, 1, 3, 4, listOf(1, 2, 3)))
    }

    @Test
    fun drawOverlappingSlotTakesBrushWeeksAwayFromIt() {
        // 周一 3-4节 的 1-8 周 + 画 2-4节 的 9-16 周：旧时间保住 1-8，新时间 9-16
        val next = PatternOps.draw(listOf(draft(1, 3, 4, (1..8).toSet())), 1, 2, 4, brush = (9..16).toSet())
        next.assertProj(listOf(null, 1, 3, 4, (1..8).toList()), listOf(null, 1, 2, 4, (9..16).toList()))
    }

    @Test
    fun moveWholePatternRelocatesRecord() {
        val next = PatternOps.move(listOf(draft(1, 3, 4, (1..16).toSet(), base = "b1")), 0, dow = 3, sp = 5, ep = 6, brush = (1..16).toSet())
        next.assertProj(listOf("b1", 3, 5, 6, (1..16).toList()))
    }

    @Test
    fun moveSplitOfWeekCreatesNewDraftAndKeepsRest() {
        val next = PatternOps.move(listOf(draft(1, 3, 4, (1..16).toSet(), base = "b1")), 0, dow = 3, sp = 5, ep = 6, brush = setOf(9, 10))
        next.assertProj(listOf("b1", 1, 3, 4, (1..8).toList() + (11..16).toList()), listOf(null, 3, 5, 6, listOf(9, 10)))
    }

    @Test
    fun moveIntoExactSlotMergesInsteadOfStacking() {
        val base = listOf(draft(1, 3, 4, setOf(1, 2), base = "b1"), draft(3, 5, 6, setOf(3, 4)))
        val next = PatternOps.move(base, 1, dow = 1, sp = 3, ep = 4, brush = setOf(3, 4))
        // b1 是原本就在目标位置的那条：吸收搬来的周；被搬的草稿周空被丢弃
        next.assertProj(listOf("b1", 1, 3, 4, listOf(1, 2, 3, 4)))
    }

    @Test
    fun removeBrushWeeksDropsEmptyDraftsKeepsBase() {
        val base = listOf(draft(1, 3, 4, setOf(1, 2), base = "b1"), draft(2, 1, 2, setOf(5)))
        val next = PatternOps.removeWeeks(base, 0, brush = setOf(1, 2))
        next.assertProj(listOf("b1", 1, 3, 4, emptyList<Int>()), listOf(null, 2, 1, 2, listOf(5)))
    }

    @Test
    fun removeDropsWholePattern() {
        val base = listOf(draft(1, 3, 4, setOf(1)), draft(2, 1, 2, setOf(5)))
        PatternOps.remove(base, 0).assertProj(listOf(null, 2, 1, 2, listOf(5)))
    }

    @Test
    fun opsNeverLeaveEmptyDraftsBehind() {
        val next = PatternOps.draw(listOf(draft(1, 3, 4, setOf(1))), 1, 3, 4, brush = setOf(1))
        assertTrue(next.none { it.base == null && it.weeks.isEmpty() })
    }
}
