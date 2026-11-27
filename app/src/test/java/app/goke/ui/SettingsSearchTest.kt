package app.goke.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import app.goke.GokeApp
import app.goke.R
import app.goke.SmokeTestApp
import app.goke.data.DarkMode
import app.goke.ui.icons.Palette
import app.goke.ui.screens.SETTINGS_SEARCH_SPECS
import app.goke.ui.screens.SettingsSearchItem
import app.goke.ui.screens.buildSettingsSearchCatalog
import app.goke.ui.screens.findHighlightRanges
import app.goke.ui.screens.searchSettings
import app.goke.ui.theme.GokeTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = SmokeTestApp::class)
class SettingsSearchTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()

    @Before
    fun setUp() {
        runBlocking {
            app.repo.load()
        }
    }

    @Test
    fun catalogHas46UniqueValidEntries() {
        assertEquals(46, SETTINGS_SEARCH_SPECS.size)
        val ids = SETTINGS_SEARCH_SPECS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)

        val catalog = buildSettingsSearchCatalog(app)
        assertEquals(46, catalog.size)
        for (item in catalog) {
            assertTrue("Blank id", item.id.isNotBlank())
            assertTrue("Blank title for ${item.id}", item.title.isNotBlank())
            assertTrue("Blank section for ${item.id}", item.section.isNotBlank())
            assertTrue("Empty keywords for ${item.id}", item.keywords.isNotEmpty())
            assertTrue("Blank keyword in ${item.id}", item.keywords.all { it.isNotBlank() })
        }
    }

    @Test
    fun rankingFollowsMatchTierPriority() {
        val icon = androidx.compose.material.icons.Icons.Rounded.Palette
        val synthetic =
            listOf(
                SettingsSearchItem("sec", "其他", "深色设置", null, listOf("other"), icon, Route.About, isTopLevel = true),
                SettingsSearchItem("sub", "外观", "常规", "包含深色调节", listOf("other"), icon, Route.About, isTopLevel = true),
                SettingsSearchItem("kw_sub", "主题", "常规", null, listOf("超深色"), icon, Route.About, isTopLevel = true),
                SettingsSearchItem("kw_pre", "颜色", "常规", null, listOf("深色模式"), icon, Route.About, isTopLevel = true),
                SettingsSearchItem("kw_exact", "样式", "常规", null, listOf("深色"), icon, Route.About, isTopLevel = true),
                SettingsSearchItem("title_sub", "夜间深色护眼", "常规", null, listOf("other"), icon, Route.About, isTopLevel = true),
                SettingsSearchItem("title_pre", "深色主题", "常规", null, listOf("other"), icon, Route.About, isTopLevel = true),
                SettingsSearchItem("title_exact", "深色", "常规", null, listOf("other"), icon, Route.About, isTopLevel = true),
            )

        val ranked = searchSettings("深色", synthetic).map { it.item.id }
        assertEquals(
            listOf("title_exact", "title_pre", "title_sub", "kw_exact", "kw_pre", "kw_sub", "sub", "sec"),
            ranked,
        )
    }

    @Test
    fun matchingSupportsMultiTokenCaseInsensitiveAndSynonyms() {
        val catalog = buildSettingsSearchCatalog(app)

        assertTrue(searchSettings("", catalog).isEmpty())
        assertTrue(searchSettings("   \t  ", catalog).isEmpty())

        // Case-insensitive English & acronyms
        assertEquals("mcp", searchSettings("mcp", catalog).first().item.id)
        assertEquals("mcp", searchSettings("MCP", catalog).first().item.id)
        assertTrue(searchSettings("ics", catalog).any { it.item.id == "export_ics" || it.item.id == "data" })
        assertTrue(searchSettings("ICS", catalog).any { it.item.id == "import_file" })
        assertTrue(searchSettings("DARK", catalog).any { it.item.id == "dark_mode" })
        assertEquals("jxfw", searchSettings("JXFW", catalog).first().item.id)

        // Synonyms
        val darkHits = searchSettings("暗黑", catalog).map { it.item.id }
        assertTrue(darkHits.contains("appearance") && darkHits.contains("dark_mode"))
        assertTrue(searchSettings("考试", catalog).any { it.item.id == "remind_exams" })
        assertEquals("grades", searchSettings("绩点", catalog).first().item.id)
        assertEquals("grades", searchSettings("挂科", catalog).first().item.id)
        assertEquals("parcel", searchSettings("取件码", catalog).first().item.id)

        // Multi-token AND matching
        val examRemind = searchSettings("提醒 考试", catalog).map { it.item.id }
        assertEquals(listOf("remind_exams"), examRemind)
        val teacherSchedule = searchSettings("课表 教师", catalog).map { it.item.id }
        assertTrue(teacherSchedule.contains("show_teacher"))
        assertFalse(teacherSchedule.contains("grades"))
    }

    @Test
    fun findHighlightRangesMergesOverlappingAdjacentAndCaseInsensitive() {
        assertEquals(emptyList<IntRange>(), findHighlightRanges("", listOf("a")))
        assertEquals(emptyList<IntRange>(), findHighlightRanges("abc", emptyList()))
        assertEquals(listOf(0..2), findHighlightRanges("abc", listOf("ab", "bc")))
        assertEquals(listOf(0..3), findHighlightRanges("abcd", listOf("ab", "cd")))
        assertEquals(listOf(0..2, 6..8), findHighlightRanges("MCP 和 mcp", listOf("mcp")))
    }

    @Test
    fun uiSearchNavigatesTopLevelAndDeepSubpagesAndHandlesClearAndBack() {
        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.ME) {}
            }
        }
        rule.waitForIdle()

        // Open search via header toggle
        rule.onNodeWithTag("settings_search_toggle").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_search_input").assertIsDisplayed()

        // Top-level query -> MCP -> navigate -> Back preserves search on Tab.ME
        rule.onNodeWithTag("settings_search_input").performTextInput("mcp")
        rule.waitForIdle()
        rule.onNodeWithTag("settings_search_result_mcp").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithContentDescription(app.getString(R.string.back)).fetchSemanticsNodes().size)

        rule.runOnUiThread {
            rule.activity.onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithContentDescription(app.getString(R.string.back)).fetchSemanticsNodes().size)
        rule.onNodeWithTag("settings_search_result_mcp").assertIsDisplayed()

        // Deep subpage query -> dark_mode -> Route.Appearance -> TopBar back
        rule.onNodeWithTag("settings_search_input").performTextReplacement("暗黑")
        rule.waitForIdle()
        rule.onNodeWithTag("settings_search_result_dark_mode").assertIsDisplayed().performClick()
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.theme_color)).assertIsDisplayed()

        rule.onNodeWithContentDescription(app.getString(R.string.back)).performClick()
        rule.waitForIdle()

        // Inline clear restores idle settings list while staying in search mode
        rule.onNodeWithTag("settings_search_clear").assertIsDisplayed().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_search_input").assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.me_hub_campus_section)).assertIsDisplayed()

        // No-match query shows empty state; empty action clears query
        rule.onNodeWithTag("settings_search_input").performTextInput("zzznomatch999")
        rule.waitForIdle()
        rule.onNodeWithTag("settings_search_empty").assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.settings_search_clear_action)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag("settings_search_empty").assertCountEquals(0)
        rule.onNodeWithText(app.getString(R.string.me_hub_campus_section)).assertIsDisplayed()

        // System Back exits search mode without popping Tab.ME
        rule.runOnUiThread {
            rule.activity.onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()
        rule.onAllNodesWithTag("settings_search_input").assertCountEquals(0)
        rule.onAllNodesWithText(app.getString(R.string.tab_me)).onFirst().assertIsDisplayed()
    }

    @Test
    fun captureLightAndDarkScreenshots() {
        rule.setContent {
            val st by app.settings.flow.collectAsState()
            GokeTheme(st) {
                GokeRoot(app, null, initialTab = Tab.ME) {}
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("settings_search_toggle").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_search_input").performTextInput("提醒")
        rule.waitForIdle()
        rule.onNodeWithTag("settings_search_result_reminders").assertIsDisplayed()
        shot("settings_search")

        try {
            app.settings.update { it.copy(darkMode = DarkMode.DARK) }
            rule.waitForIdle()
            shot("settings_search_dark")
        } finally {
            app.settings.update { it.copy(darkMode = DarkMode.SYSTEM) }
        }
    }

    private fun shot(name: String) {
        if (System.getenv("CI").equals("true", ignoreCase = true)) {
            rule.onRoot().captureToImage()
            return
        }
        val dir = File("build/shots").apply { mkdirs() }
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
