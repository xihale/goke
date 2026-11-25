package app.goke.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.goke.GokeApp
import app.goke.R
import app.goke.SmokeTestApp
import app.goke.core.model.Attachment
import app.goke.data.Attachments
import app.goke.ui.Actions
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.LocalNav
import app.goke.ui.rememberNav
import app.goke.ui.theme.GokeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = SmokeTestApp::class)
class AttachmentEditorTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<GokeApp>()

    @Test
    fun snapColumnsAndMagneticWidthBehaveSmoothly() {
        val container = 900f
        val gap = 24f
        val w1 = tileWidthForColumns(container, 1, gap)
        val w2 = tileWidthForColumns(container, 2, gap)
        val w3 = tileWidthForColumns(container, 3, gap)

        assertEquals(900f, w1, 0.1f)
        assertEquals(438f, w2, 0.1f)
        assertEquals(284f, w3, 0.1f)

        assertEquals(1, snapColumnsForWidth(w1, container, 1, gap))
        assertEquals(2, snapColumnsForWidth(w2, container, 1, gap))
        assertEquals(3, snapColumnsForWidth(w3, container, 2, gap))

        // 滞回区间：刚过中点一点点时保持原列数，避免边界抖动
        val mid12 = (w1 + w2) / 2f
        assertEquals(1, snapColumnsForWidth(mid12 - 5f, container, 1, gap))
        assertEquals(2, snapColumnsForWidth(mid12 - 50f, container, 1, gap))

        // 磁吸：靠近 2x2 目标宽度时向 snapW 拉近
        val nearW2 = w2 + 30f
        val magnetized = magneticDisplayWidth(nearW2, container, 2, gap)
        assertTrue(magnetized < nearW2 && magnetized > w2)
    }

    @Test
    fun expandedImageGridOpensViewerAndSupportsActions() {
        val dir = Attachments.dir(app)
        val img1 = createTempPng(File(dir, "img1.png"), 200, 150)
        val img2 = createTempPng(File(dir, "img2.png"), 150, 200)
        val docFile = File(dir, "doc1.pdf").apply { writeText("pdf") }

        var latestItems: List<Attachment> =
            listOf(
                Attachment("photo1.png", "image/png", img1.name),
                Attachment("photo2.png", "image/png", img2.name),
                Attachment("doc.pdf", "application/pdf", docFile.name),
            )

        rule.setContent {
            val nav = rememberNav()
            val scope = rememberCoroutineScope()
            val actions = remember { Actions(app, scope, nav) }
            var items by remember { mutableStateOf(latestItems) }
            GokeTheme(app.settings.flow.value) {
                CompositionLocalProvider(
                    LocalApp provides app,
                    LocalNav provides nav,
                    LocalActions provides actions,
                ) {
                    AttachmentEditor(
                        items = items,
                        onChange = {
                            items = it
                            latestItems = it
                        },
                    )
                }
            }
        }
        rule.waitForIdle()

        // 图片不以文件名卡片展示，非图片文件保持胶囊芯片展示
        rule.onAllNodesWithText("photo1.png").assertCountEquals(0)
        rule.onAllNodesWithText("photo2.png").assertCountEquals(0)
        rule.onNodeWithText("doc.pdf").assertIsDisplayed()

        // 点开图片后弹出全屏查看器并展示保存到相册/分享/删除操作
        rule.onNodeWithContentDescription("photo1.png").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("1 / 2").assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.attachment_save_gallery)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.attachment_share)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.delete)).assertIsDisplayed()

        // 在查看器内删除第一张图片
        rule.onNodeWithText(app.getString(R.string.delete)).performClick()
        rule.waitForIdle()

        assertEquals(2, latestItems.size)
        assertEquals("photo2.png", latestItems.first().name)
    }

    @Test
    fun longPressDragInsideVerticalScrollSnapsColumnsAndShowsGuide() {
        val dir = Attachments.dir(app)
        val img1 = createTempPng(File(dir, "drag_img1.png"), 300, 200)
        val img2 = createTempPng(File(dir, "drag_img2.png"), 300, 200)
        val img3 = createTempPng(File(dir, "drag_img3.png"), 300, 200)
        val items =
            listOf(
                Attachment("drag1.png", "image/png", img1.name),
                Attachment("drag2.png", "image/png", img2.name),
                Attachment("drag3.png", "image/png", img3.name),
            )
        var recordedCols: Int? = 1

        rule.setContent {
            val nav = rememberNav()
            val scope = rememberCoroutineScope()
            val actions = remember { Actions(app, scope, nav) }
            var cols by remember { mutableStateOf<Int?>(1) }
            GokeTheme(app.settings.flow.value) {
                CompositionLocalProvider(
                    LocalApp provides app,
                    LocalNav provides nav,
                    LocalActions provides actions,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                    ) {
                        AttachmentEditor(
                            items = items,
                            onChange = {},
                            imageColumns = cols,
                            onImageColumnsChange = {
                                cols = it
                                recordedCols = it
                            },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()

        val node = rule.onNodeWithContentDescription("drag1.png")
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val start = Offset(bounds.width * 0.85f, bounds.height * 0.5f)
        val mid = Offset(bounds.width * 0.5f, bounds.height * 0.5f)
        val end = Offset(bounds.width * 0.15f, bounds.height * 0.5f)
        node.performTouchInput {
            down(start)
            advanceEventTime(650L)
            moveTo(start)
        }
        rule.waitForIdle()

        node.performTouchInput {
            advanceEventTime(32L)
            moveTo(mid)
            advanceEventTime(32L)
            moveTo(end)
            advanceEventTime(32L)
            up()
        }
        rule.waitForIdle()

        assertTrue("Expected snapped columns to shrink from 1 to 2 or 3, got $recordedCols", (recordedCols ?: 1) >= 2)
    }

    private fun createTempPng(
        file: File,
        width: Int,
        height: Int,
    ): File {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()
        return file
    }
}
