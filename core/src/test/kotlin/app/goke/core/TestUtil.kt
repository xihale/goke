package app.goke.core

import app.goke.core.model.Semester
import java.time.LocalDate

object TestUtil {
    fun resource(path: String): ByteArray = TestUtil::class.java.getResourceAsStream(path)!!.use { it.readBytes() }

    val semester = Semester(id = "sem1", name = "大一下", week1Monday = LocalDate.of(2026, 8, 31), totalWeeks = 20)
}
