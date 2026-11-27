package app.goke.core.campus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DropRelayLogicTest {
    @Test
    fun parseIndexJsonSupportsV1ObjectAndLegacyManifestArray() {
        val v1 =
            """
            {
              "version": 1,
              "generatedAt": 1789200417,
              "totalBytes": 3072,
              "files": [
                {"path": "课本/数据结构.pdf", "name": "数据结构.pdf", "size": 2048, "mtime": 1789200417, "ih": "abc"},
                {"path": "课件/算法.zip", "size": 1024, "mtime": 1789200400}
              ]
            }
            """.trimIndent()
        val parsed = DropRelayLogic.parseIndexJson(v1)
        assertEquals(1, parsed.version)
        assertEquals(3072L, parsed.totalBytes)
        assertEquals(2, parsed.files.size)
        assertEquals("算法.zip", parsed.files[1].displayName)

        val legacy =
            """
            [
              {"path": "课本/高数.pdf", "name": "高数.pdf", "size": 512, "mtime": 100, "torrent": "AAAA=="}
            ]
            """.trimIndent()
        val legacyParsed = DropRelayLogic.parseIndexJson(legacy)
        assertEquals(1, legacyParsed.files.size)
        assertEquals(512L, legacyParsed.totalBytes)
        assertEquals("高数.pdf", legacyParsed.files.first().displayName)
    }

    @Test
    fun listDirectoryGroupsSubdirsAndFiles() {
        val files =
            listOf(
                DropRelayFile("课本/文字/数据结构.pdf", "数据结构.pdf", 1000L),
                DropRelayFile("课本/文字/操作系统.pdf", "操作系统.pdf", 2000L),
                DropRelayFile("课本/导读.txt", "导读.txt", 100L),
                DropRelayFile("README.md", "README.md", 50L),
            )
        val root = DropRelayLogic.listDirectory(files, "")
        assertEquals(2, root.size)
        val dir = root[0] as ShareBrowseEntry.Dir
        assertEquals("课本", dir.name)
        assertEquals(3, dir.fileCount)
        assertEquals(3100L, dir.totalBytes)
        assertEquals("README.md", (root[1] as ShareBrowseEntry.FileItem).file.displayName)

        val sub = DropRelayLogic.listDirectory(files, "课本")
        assertEquals(2, sub.size)
        assertEquals("文字", (sub[0] as ShareBrowseEntry.Dir).name)
        assertEquals("导读.txt", (sub[1] as ShareBrowseEntry.FileItem).file.displayName)
    }

    @Test
    fun matchCourseResourcesHandlesSuffixesAndParens() {
        val files =
            listOf(
                DropRelayFile("课本/文字精排件/数据结构与算法.pdf", "数据结构与算法.pdf", 1024L),
                DropRelayFile("课本/扫描件/高等数学/同济高数.pdf", "同济高数.pdf", 2048L),
                DropRelayFile("EDA/Libero.zip", "Libero.zip", 3000L),
            )
        assertEquals(1, DropRelayLogic.matchCourseResources(files, "数据结构(A)").size)
        assertEquals(1, DropRelayLogic.matchCourseResources(files, "高等数学（上）").size)
        assertTrue(DropRelayLogic.matchCourseResources(files, "体育").isEmpty())
    }

    @Test
    fun extractDropCodeRecognizesRawCodeAndUrls() {
        assertEquals("AB23CD", DropRelayLogic.extractDropCode("ab23cd"))
        assertEquals("AB23CD", DropRelayLogic.extractDropCode("https://drop.example.com/ab23cd"))
        assertEquals("AB23CD", DropRelayLogic.extractDropCodeFromUrl("看看这个 https://drop.example.com/AB23CD", ""))
        assertEquals("AB23CD", DropRelayLogic.extractDropCodeFromUrl("https://files.campus.edu/AB23CD", "https://files.campus.edu"))
        assertNull(DropRelayLogic.extractDropCodeFromUrl("普通待办：晚上写作业 AB23CD", ""))
        assertFalse(DropRelayLogic.isValidDropCode("O0I1L2"))
    }

    @Test
    fun shouldPreferBrowserP2pRespectsSizeAndRouterWait() {
        val idle = DropRelayHealth(ok = true, p2pWaitMs = 0L)
        val busy = DropRelayHealth(ok = true, p2pWaitMs = 1500L)
        assertFalse(DropRelayLogic.shouldPreferBrowserP2p(100L * 1024 * 1024, idle))
        assertTrue(DropRelayLogic.shouldPreferBrowserP2p(201L * 1024 * 1024, idle))
        assertTrue(DropRelayLogic.shouldPreferBrowserP2p(10L * 1024 * 1024, busy))
    }

    @Test
    fun encodeUrlsProperly() {
        assertEquals(
            "https://share.example.com/%E8%AF%BE%E6%9C%AC/a%20b.pdf",
            DropRelayLogic.fileWebseedUrl("https://share.example.com/", "课本/a b.pdf"),
        )
        assertEquals(
            "https://share.example.com/#/%E8%AF%BE%E6%9C%AC/a%20b.pdf",
            DropRelayLogic.fileBrowserUrl("https://share.example.com", "课本/a b.pdf"),
        )
        assertEquals(
            "https://share.example.com/%E8%AF%BE%E6%9C%AC?zip=1",
            DropRelayLogic.dirZipUrl("https://share.example.com", "课本"),
        )
    }
}
