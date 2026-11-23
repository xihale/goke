package app.goke.core

import app.goke.core.util.QrMatrix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QrMatrixTest {
    @Test
    fun encodesQqGroupUrlAsVersion4SquareMatrixWithValidFinders() {
        val m = QrMatrix.encode("https://qm.qq.com/q/VWUh0uEhKY")
        // 28 字节 + 2 字节头部 = 30 字节 -> H 级容错落在 Version 4（33×33）
        assertEquals(33, m.size)
        assertTrue(m.all { it.size == 33 })
        // 三个角标外框 7×7 与中心 3×3 为深色
        assertTrue(m[0][0] && m[0][6] && m[6][0] && m[6][6] && m[3][3])
        assertTrue(m[0][26] && m[0][32] && m[6][26] && m[6][32] && m[3][29])
        assertTrue(m[26][0] && m[26][6] && m[32][0] && m[32][6] && m[29][3])
    }
}
