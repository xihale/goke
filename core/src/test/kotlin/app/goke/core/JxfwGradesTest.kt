package app.goke.core

import app.goke.core.jxfw.JxfwGrade
import app.goke.core.jxfw.JxfwParse
import app.goke.core.jxfw.JxfwSync
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JxfwGradesTest {
    @Test
    fun summarizeGradesComputesWeightedGpaAndCredits() {
        val emptySummary = JxfwParse.summarizeGrades(emptyList())
        assertEquals(0.0, emptySummary.totalCredits)
        assertEquals(0.0, emptySummary.gpaCredits)
        assertNull(emptySummary.weightedGpa)
        assertEquals(0, emptySummary.count)

        val grades =
            listOf(
                JxfwGrade(term = "202501", name = "高数", credit = 4.0, score = "90", gpa = 4.0),
                JxfwGrade(term = "202501", name = "线代", credit = 2.0, score = "80", gpa = 3.0),
                JxfwGrade(term = "202501", name = "军训", credit = 2.0, score = "优秀", gpa = null),
                JxfwGrade(term = "202501", name = "挂科选修", credit = 2.0, score = "45", gpa = 0.0),
                JxfwGrade(term = "202501", name = "无学分讲座", credit = 0.0, score = "合格", gpa = 4.0),
                JxfwGrade(term = "202501", name = "缺失学分", credit = null, score = "88", gpa = 3.8),
            )

        val summary = JxfwParse.summarizeGrades(grades)
        assertEquals(10.0, summary.totalCredits, 1e-9)
        assertEquals(8.0, summary.gpaCredits, 1e-9)
        assertEquals(2.75, summary.weightedGpa!!, 1e-9)
        assertEquals(6, summary.count)

        val noGpaSummary =
            JxfwParse.summarizeGrades(
                listOf(JxfwGrade(term = "202501", name = "体育", credit = 1.0, score = "良好", gpa = null)),
            )
        assertEquals(1.0, noGpaSummary.totalCredits, 1e-9)
        assertEquals(0.0, noGpaSummary.gpaCredits, 1e-9)
        assertNull(noGpaSummary.weightedGpa)
        assertEquals(1, noGpaSummary.count)
    }

    @Test
    fun termLabelFormatsStandardTermsAndFallsBackOtherwise() {
        assertEquals("2026-2027 第一学期", JxfwParse.termLabel("202601"))
        assertEquals("2026-2027 第二学期", JxfwParse.termLabel("202602"))
        assertEquals("2025-2026 第一学期", JxfwParse.termLabel(" 202501 "))
        assertEquals("202603", JxfwParse.termLabel("202603"))
        assertEquals("2026", JxfwParse.termLabel("2026"))
        assertEquals("abcd01", JxfwParse.termLabel("abcd01"))
        assertEquals("", JxfwParse.termLabel(""))
    }

    @Test
    fun jxfwGradeSerializesAndDeserializesRoundTrip() {
        val list =
            listOf(
                JxfwGrade(
                    term = "202501",
                    code = "CS101",
                    name = "程序设计",
                    credit = 3.5,
                    score = "89",
                    gpa = 3.9,
                    category = "专业必修",
                    studyMode = "初修",
                    examType = "正常考试",
                ),
            )
        val serializer = ListSerializer(JxfwGrade.serializer())
        val encoded = JxfwParse.json.encodeToString(serializer, list)
        val decoded = JxfwParse.json.decodeFromString(serializer, encoded)
        assertEquals(list, decoded)
    }

    @Test
    fun summarizeGradesSupportsFilterKeysCategoriesAndEarnedCredits() {
        val g1 = JxfwGrade(term = "202501", code = "CS101", name = "高数", credit = 4.0, score = "90", gpa = 4.0, category = "必修", studyMode = "初修")
        val g2 = JxfwGrade(term = "202501", code = "PE101", name = "体育", credit = 1.0, score = "优秀", gpa = null, category = "限选", studyMode = "初修")
        val g3 = JxfwGrade(term = "202501", code = "EN101", name = "英语", credit = 2.0, score = "45", gpa = 0.0, category = "必修", studyMode = "重修")
        val g4 = JxfwGrade(term = "202501", code = "AR101", name = "美育", credit = 1.0, score = "不及格", gpa = null, category = "通识", studyMode = "初修")
        val all = listOf(g1, g2, g3, g4)

        assertEquals("202501|CS101|高数", g1.filterKey)
        assertEquals(listOf("必修", "初修", "限选", "重修", "通识"), JxfwParse.gradeCategories(all))

        val full = JxfwParse.summarizeGrades(all)
        assertEquals(8.0, full.totalCredits, 1e-9)
        assertEquals(5.0, full.earnedCredits, 1e-9)
        assertEquals(6.0, full.gpaCredits, 1e-9)
        assertEquals(16.0 / 6.0, full.weightedGpa!!, 1e-9)
        assertEquals(4, full.count)

        val byKey = JxfwParse.summarizeGrades(all, excludedKeys = setOf(g3.filterKey))
        assertEquals(6.0, byKey.totalCredits, 1e-9)
        assertEquals(5.0, byKey.earnedCredits, 1e-9)
        assertEquals(4.0, byKey.gpaCredits, 1e-9)
        assertEquals(4.0, byKey.weightedGpa!!, 1e-9)
        assertEquals(3, byKey.count)

        val byCat = JxfwParse.summarizeGrades(all, excludedCategories = setOf("通识", "重修"))
        assertEquals(5.0, byCat.totalCredits, 1e-9)
        assertEquals(5.0, byCat.earnedCredits, 1e-9)
        assertEquals(4.0, byCat.gpaCredits, 1e-9)
        assertEquals(4.0, byCat.weightedGpa!!, 1e-9)
        assertEquals(2, byCat.count)
    }

    @Test
    fun summarizeGradesExcludesDeferredUngradedAndSupersededZeroRows() {
        val deferredZero =
            JxfwGrade(
                term = "202502",
                code = "EE201",
                name = "电路分析基础",
                credit = 2.5,
                score = "0",
                gpa = 0.0,
                examType = "正常考试",
            )
        val deferredPassed =
            JxfwGrade(
                term = "202601",
                code = "EE201",
                name = "电路分析基础",
                credit = 2.5,
                score = "64",
                gpa = 1.4,
                examType = "正常考试",
            )
        val explicitDeferred =
            JxfwGrade(
                term = "202601",
                code = "MA202",
                name = "复变函数",
                credit = 2.0,
                score = "0",
                gpa = 0.0,
                status = "缓考",
            )
        val ungraded =
            JxfwGrade(
                term = "202601",
                code = "MA203",
                name = "复变函数与积分变换",
                credit = 2.0,
                score = "",
                gpa = null,
            )

        val all = listOf(deferredZero, deferredPassed, explicitDeferred, ungraded)
        val superseded = JxfwParse.supersededGradeKeys(all)
        assertEquals(setOf(deferredZero.filterKey), superseded)

        val summary = JxfwParse.summarizeGrades(all)
        assertEquals(2.5, summary.totalCredits, 1e-9)
        assertEquals(2.5, summary.earnedCredits, 1e-9)
        assertEquals(2.5, summary.gpaCredits, 1e-9)
        assertEquals(1.4, summary.weightedGpa!!, 1e-9)
        assertEquals(4, summary.count)

        val termOnlySummary = JxfwParse.summarizeGrades(listOf(deferredZero), supersededKeys = superseded)
        assertEquals(0.0, termOnlySummary.totalCredits, 1e-9)
        assertEquals(0.0, termOnlySummary.gpaCredits, 1e-9)
        assertNull(termOnlySummary.weightedGpa)
    }

    @Test
    fun shouldClassifyCourseAsExamDetectsExamRemarksAndExcludesCourseTopics() {
        assertTrue(JxfwSync.shouldClassifyCourseAsExam("高等数学", "第8周：期中考试"))
        assertTrue(JxfwSync.shouldClassifyCourseAsExam("随堂测验", ""))
        assertFalse(JxfwSync.shouldClassifyCourseAsExam("软件测试", "第4周：黑盒测试"))
        assertFalse(JxfwSync.shouldClassifyCourseAsExam("操作系统", "第16周：考前复习与答疑"))
    }
}
