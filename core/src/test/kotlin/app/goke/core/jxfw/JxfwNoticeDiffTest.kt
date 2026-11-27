package app.goke.core.jxfw

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JxfwNoticeDiffTest {
    @Test
    fun initialBaselineSeedsSeenWithoutTriggeringNewNotices() {
        val incoming =
            listOf(
                JxfwNotice(id = "info/1216/2320.htm", title = "Pinned 2021 notice", date = "2021-03-29"),
                JxfwNotice(id = "info/1216/4597.htm", title = "Latest notice", date = "2026-09-10"),
            )

        val res =
            JxfwNoticeDiff.diffNew(
                incoming = incoming,
                seenIds = emptyList(),
                isInitialBaseline = true,
            )

        assertTrue(res.newNotices.isEmpty())
        assertEquals(listOf("info/1216/2320.htm", "info/1216/4597.htm"), res.updatedSeenIds)
    }

    @Test
    fun incrementalDiffHandlesPinnedOutOfOrderItemWithoutFalsePositive() {
        val seen = listOf("info/1216/2320.htm", "info/1216/4590.htm")
        val incoming =
            listOf(
                JxfwNotice(id = "info/1216/2320.htm", title = "Pinned 2021 notice", date = "2021-03-29"),
                JxfwNotice(id = "info/1216/4597.htm", title = "New notice", date = "2026-09-10"),
                JxfwNotice(id = "info/1216/4590.htm", title = "Previous notice", date = "2026-09-04"),
            )

        val res =
            JxfwNoticeDiff.diffNew(
                incoming = incoming,
                seenIds = seen,
                isInitialBaseline = false,
            )

        assertEquals(listOf("info/1216/4597.htm"), res.newNotices.map { it.id })
        assertEquals(
            listOf("info/1216/2320.htm", "info/1216/4597.htm", "info/1216/4590.htm"),
            res.updatedSeenIds,
        )
    }

    @Test
    fun fifoTrimmingRetainsPinnedAndActiveIncomingIds() {
        val seen = (1..10).map { "old-$it" }
        val incoming =
            listOf(
                JxfwNotice(id = "pinned-0", title = "Pinned"),
                JxfwNotice(id = "new-1", title = "New 1"),
            )

        val res =
            JxfwNoticeDiff.diffNew(
                incoming = incoming,
                seenIds = seen,
                isInitialBaseline = false,
                maxSeenIds = 5,
            )

        assertEquals(listOf("pinned-0", "new-1", "old-1", "old-2", "old-3"), res.updatedSeenIds)
    }

    @Test
    fun mergeSourcesPrioritizesInternalAndDeduplicates() {
        val pub =
            listOf(
                JxfwNotice(id = "info/1216/1.htm", title = "Pub 1", source = JxfwNoticeSource.PUBLIC),
                JxfwNotice(id = "info/1216/2.htm", title = "Pub 2", source = JxfwNoticeSource.PUBLIC),
            )
        val internal =
            listOf(
                JxfwNotice(id = "jxfw:100", title = "Internal 1", source = JxfwNoticeSource.INTERNAL),
                JxfwNotice(id = "jxfw:100", title = "Internal dup", source = JxfwNoticeSource.INTERNAL),
            )

        val merged = JxfwNoticeDiff.mergeSources(publicNotices = pub, internalNotices = internal, maxItems = 2)

        assertEquals(listOf("jxfw:100", "info/1216/1.htm"), merged.map { it.id })
        assertEquals(JxfwNoticeSource.INTERNAL, merged[0].source)
        assertEquals(JxfwNoticeSource.PUBLIC, merged[1].source)
    }
}
