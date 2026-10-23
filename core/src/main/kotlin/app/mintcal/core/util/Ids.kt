package app.mintcal.core.util

import java.util.UUID

/** ID 生成器，可在测试里替换为确定性的实现。 */
fun interface IdGenerator { fun next(): String }

object Ids : IdGenerator {
    override fun next(): String = UUID.randomUUID().toString()
}

class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var n = 0
    override fun next(): String = "$prefix-${++n}"
}
