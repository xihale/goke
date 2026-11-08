package app.mintcal.core

import app.mintcal.core.parcel.parcelWindow
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 取件时段落块：当天下午/晚上，过结束时刻排明天；配置无效不排块。 */
class ParcelWindowTest {
    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 5, 10, 0)

    @Test
    fun `白天到达排当天时段`() {
        val (start, minutes) = parcelWindow(now, 14 * 60, 22 * 60)!!
        assertEquals(LocalDateTime.of(2026, 11, 5, 14, 0), start)
        assertEquals(8 * 60, minutes)
    }

    @Test
    fun `已过结束时刻排到明天`() {
        val late = LocalDateTime.of(2026, 11, 5, 22, 30)
        val (start, _) = parcelWindow(late, 14 * 60, 22 * 60)!!
        assertEquals(LocalDateTime.of(2026, 11, 6, 14, 0), start)
    }

    @Test
    fun `恰好在结束时刻排到明天`() {
        val edge = LocalDateTime.of(2026, 11, 5, 22, 0)
        val (start, _) = parcelWindow(edge, 14 * 60, 22 * 60)!!
        assertEquals(LocalDateTime.of(2026, 11, 6, 14, 0), start)
    }

    @Test
    fun `结束不晚于开始视为无效`() {
        assertNull(parcelWindow(now, 22 * 60, 22 * 60))
        assertNull(parcelWindow(now, 22 * 60, 14 * 60))
    }
}
