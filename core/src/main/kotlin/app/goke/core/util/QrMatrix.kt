package app.goke.core.util

/**
 * 纯 Kotlin 实现的紧凑型 QR Code 编码器（字节模式 + 高容错 H 级，支持 Version 1–6）。
 * 用于在端内零第三方依赖渲染主题自适应二维码（高容错支持中心放置品牌徽标）。
 */
object QrMatrix {
    private data class VersionSpec(
        val version: Int,
        val dataBytes: Int,
        val ecPerBlock: Int,
        val g1Blocks: Int,
        val g1Data: Int,
        val g2Blocks: Int,
        val g2Data: Int,
        val align: IntArray,
        val remainderBits: Int,
    ) {
        val size: Int get() = 17 + 4 * version
    }

    // ISO/IEC 18004 Error Correction Level H (30% 容错) 表
    private val VERSIONS =
        listOf(
            VersionSpec(1, 9, 17, 1, 9, 0, 0, intArrayOf(), 0),
            VersionSpec(2, 16, 28, 1, 16, 0, 0, intArrayOf(6, 18), 7),
            VersionSpec(3, 26, 22, 2, 13, 0, 0, intArrayOf(6, 22), 7),
            VersionSpec(4, 36, 16, 4, 9, 0, 0, intArrayOf(6, 26), 7),
            VersionSpec(5, 46, 22, 2, 11, 2, 12, intArrayOf(6, 30), 7),
            VersionSpec(6, 60, 28, 4, 15, 0, 0, intArrayOf(6, 20, 34), 7),
        )

    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) GF_EXP[i] = GF_EXP[i - 255]
    }

    private fun gfMul(
        a: Int,
        b: Int,
    ): Int = if (a == 0 || b == 0) 0 else GF_EXP[GF_LOG[a] + GF_LOG[b]]

    private fun rsGenerator(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            val root = GF_EXP[i]
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], root)
            }
            poly = next
        }
        return poly
    }

    private fun rsRemainder(
        data: IntArray,
        ecLen: Int,
    ): IntArray {
        val gen = rsGenerator(ecLen)
        val buf = IntArray(data.size + ecLen)
        data.copyInto(buf)
        for (i in data.indices) {
            val factor = buf[i]
            if (factor != 0) {
                for (j in 1 until gen.size) {
                    buf[i + j] = buf[i + j] xor gfMul(gen[j], factor)
                }
            }
        }
        return buf.copyOfRange(data.size, buf.size)
    }

    /** 将文本以 UTF-8 字节模式编码为 H 级容错矩阵（`true` 表示深色模块）。 */
    fun encode(text: String): Array<BooleanArray> {
        val bytes = text.encodeToByteArray()
        val spec =
            VERSIONS.firstOrNull { 2 + bytes.size <= it.dataBytes }
                ?: error("QR payload too long (${bytes.size} bytes)")

        val bits = ArrayList<Int>(spec.dataBytes * 8)

        fun pushBits(
            value: Int,
            count: Int,
        ) {
            for (i in count - 1 downTo 0) bits.add((value ushr i) and 1)
        }

        // Byte mode = 0100, char count = 8 bits (Version 1-9)
        pushBits(0b0100, 4)
        pushBits(bytes.size, 8)
        for (b in bytes) pushBits(b.toInt() and 0xFF, 8)

        val capacityBits = spec.dataBytes * 8
        val terminator = minOf(4, capacityBits - bits.size)
        pushBits(0, terminator)
        while (bits.size % 8 != 0) bits.add(0)
        var padIdx = 0
        while (bits.size < capacityBits) {
            pushBits(if (padIdx % 2 == 0) 0xEC else 0x11, 8)
            padIdx++
        }

        val dataCodewords =
            IntArray(spec.dataBytes) { i ->
                var v = 0
                for (b in 0 until 8) v = (v shl 1) or bits[i * 8 + b]
                v
            }

        val dataBlocks = mutableListOf<IntArray>()
        val ecBlocks = mutableListOf<IntArray>()
        var offset = 0
        repeat(spec.g1Blocks) {
            val blk = dataCodewords.copyOfRange(offset, offset + spec.g1Data)
            offset += spec.g1Data
            dataBlocks += blk
            ecBlocks += rsRemainder(blk, spec.ecPerBlock)
        }
        repeat(spec.g2Blocks) {
            val blk = dataCodewords.copyOfRange(offset, offset + spec.g2Data)
            offset += spec.g2Data
            dataBlocks += blk
            ecBlocks += rsRemainder(blk, spec.ecPerBlock)
        }

        val allBits = ArrayList<Int>(spec.size * spec.size)
        val maxDataLen = dataBlocks.maxOf { it.size }
        for (i in 0 until maxDataLen) {
            for (blk in dataBlocks) if (i < blk.size) pushIntBits(allBits, blk[i])
        }
        for (i in 0 until spec.ecPerBlock) {
            for (blk in ecBlocks) pushIntBits(allBits, blk[i])
        }
        repeat(spec.remainderBits) { allBits.add(0) }

        val n = spec.size
        val base = Array(n) { BooleanArray(n) }
        val reserved = Array(n) { BooleanArray(n) }

        fun placeFinder(
            top: Int,
            left: Int,
        ) {
            for (dr in -1..7) {
                for (dc in -1..7) {
                    val r = top + dr
                    val c = left + dc
                    if (r !in 0 until n || c !in 0 until n) continue
                    reserved[r][c] = true
                    val inBox = dr in 0..6 && dc in 0..6
                    val dark =
                        inBox &&
                            (dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4))
                    base[r][c] = dark
                }
            }
        }

        placeFinder(0, 0)
        placeFinder(0, n - 7)
        placeFinder(n - 7, 0)

        for (r0 in spec.align) {
            for (c0 in spec.align) {
                if (reserved[r0][c0]) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val r = r0 + dr
                        val c = c0 + dc
                        reserved[r][c] = true
                        base[r][c] = kotlin.math.max(kotlin.math.abs(dr), kotlin.math.abs(dc)) != 1
                    }
                }
            }
        }

        for (i in 8 until n - 8) {
            reserved[6][i] = true
            reserved[i][6] = true
            base[6][i] = i % 2 == 0
            base[i][6] = i % 2 == 0
        }

        // Format info slots + dark module
        for (i in 0..8) {
            if (i != 6) {
                reserved[8][i] = true
                reserved[i][8] = true
            }
        }
        for (i in 1..8) reserved[8][n - i] = true
        for (i in 1..7) reserved[n - i][8] = true
        reserved[n - 8][8] = true
        base[n - 8][8] = true

        // 放置数据位
        var bitIdx = 0
        var col = n - 1
        var upward = true
        while (col > 0) {
            if (col == 6) col--
            val rows = if (upward) (n - 1 downTo 0) else (0 until n)
            for (r in rows) {
                for (dc in 0..1) {
                    val c = col - dc
                    if (!reserved[r][c] && bitIdx < allBits.size) {
                        base[r][c] = allBits[bitIdx++] == 1
                    }
                }
            }
            col -= 2
            upward = !upward
        }

        var bestScore = Int.MAX_VALUE
        var bestMatrix = base
        for (mask in 0 until 8) {
            val candidate =
                Array(n) { r ->
                    BooleanArray(n) { c ->
                        if (reserved[r][c]) base[r][c] else base[r][c] xor maskBit(mask, r, c)
                    }
                }
            applyFormatInfo(candidate, mask)
            val score = penaltyScore(candidate)
            if (score < bestScore) {
                bestScore = score
                bestMatrix = candidate
            }
        }
        return bestMatrix
    }

    private fun pushIntBits(
        out: MutableList<Int>,
        value: Int,
    ) {
        for (i in 7 downTo 0) out.add((value ushr i) and 1)
    }

    private fun maskBit(
        mask: Int,
        r: Int,
        c: Int,
    ): Boolean =
        when (mask) {
            0 -> (r + c) % 2 == 0
            1 -> r % 2 == 0
            2 -> c % 3 == 0
            3 -> (r + c) % 3 == 0
            4 -> (r / 2 + c / 3) % 2 == 0
            5 -> (r * c) % 2 + (r * c) % 3 == 0
            6 -> ((r * c) % 2 + (r * c) % 3) % 2 == 0
            else -> ((r + c) % 2 + (r * c) % 3) % 2 == 0
        }

    private fun applyFormatInfo(
        m: Array<BooleanArray>,
        mask: Int,
    ) {
        val n = m.size
        // EC Level H = 10
        val data = (0b10 shl 3) or mask
        var rem = data shl 10
        for (i in 4 downTo 0) {
            if ((rem ushr (i + 10)) and 1 != 0) {
                rem = rem xor (0x537 shl i)
            }
        }
        val fmt = ((data shl 10) or rem) xor 0x5412

        fun bit(pos: Int): Boolean = ((fmt ushr pos) and 1) != 0

        for (i in 0..5) m[8][i] = bit(14 - i)
        m[8][7] = bit(8)
        m[8][8] = bit(7)
        m[7][8] = bit(6)
        for (i in 0..5) m[5 - i][8] = bit(5 - i)

        for (i in 0..6) m[n - 1 - i][8] = bit(14 - i)
        for (i in 0..7) m[8][n - 8 + i] = bit(7 - i)
    }

    private fun penaltyScore(m: Array<BooleanArray>): Int {
        val n = m.size
        var score = 0
        // N1: 连续同色 5+
        for (r in 0 until n) {
            var run = 1
            for (c in 1 until n) {
                if (m[r][c] == m[r][c - 1]) {
                    run++
                } else {
                    if (run >= 5) score += 3 + (run - 5)
                    run = 1
                }
            }
            if (run >= 5) score += 3 + (run - 5)
        }
        for (c in 0 until n) {
            var run = 1
            for (r in 1 until n) {
                if (m[r][c] == m[r - 1][c]) {
                    run++
                } else {
                    if (run >= 5) score += 3 + (run - 5)
                    run = 1
                }
            }
            if (run >= 5) score += 3 + (run - 5)
        }
        // N2: 2×2 同色块
        for (r in 0 until n - 1) {
            for (c in 0 until n - 1) {
                val v = m[r][c]
                if (m[r][c + 1] == v && m[r + 1][c] == v && m[r + 1][c + 1] == v) score += 3
            }
        }
        // N3: 1:1:3:1:1 finder-like 序列
        val pat1 = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
        val pat2 = booleanArrayOf(false, false, false, false, true, false, true, true, true, false, true)
        for (r in 0 until n) {
            for (c in 0..n - 11) {
                if ((0 until 11).all { m[r][c + it] == pat1[it] } || (0 until 11).all { m[r][c + it] == pat2[it] }) score += 40
                if ((0 until 11).all { m[c + it][r] == pat1[it] } || (0 until 11).all { m[c + it][r] == pat2[it] }) score += 40
            }
        }
        // N4: 深色比例偏离 50%
        var dark = 0
        for (r in 0 until n) for (c in 0 until n) if (m[r][c]) dark++
        val pct = (dark * 100) / (n * n)
        val prev5 = kotlin.math.abs(pct - 50) / 5
        score += prev5 * 10
        return score
    }
}
