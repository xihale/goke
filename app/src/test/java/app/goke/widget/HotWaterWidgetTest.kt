package app.goke.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.RectF
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.goke.R
import app.goke.SmokeTestApp
import app.goke.hotwater.HotWaterConfig
import app.goke.hotwater.HotWaterErrorCode
import app.goke.hotwater.HotWaterPhase
import app.goke.hotwater.HotWaterState
import app.goke.ui.screens.errorStringRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SmokeTestApp::class)
class HotWaterWidgetTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun optionsOf(
        w: Int,
        h: Int,
    ): Bundle =
        Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
        }

    private fun inflate(
        state: HotWaterState,
        w: Int,
        h: Int,
    ): Pair<View, android.widget.RemoteViews> {
        val rv = HotWaterWidgetProvider.build(ctx, state, optionsOf(w, h))
        val root = rv.apply(ctx, FrameLayout(ctx))
        return root to rv
    }

    private fun boundIdleState() =
        HotWaterState(
            isBound = true,
            config = HotWaterConfig(houseName = "紫荆2号楼", dormitoryNo = "408"),
            phase = HotWaterPhase.IDLE,
            canUseAmount = 1234,
            lastCheckAt = 1_700_000_000_000L,
        )

    @Test
    fun circleCompact1x1ShowsOnlyActionAndHidesRoomAndMeta() {
        val (root, rv) = inflate(boundIdleState(), 70, 70)
        assertEquals(R.layout.widget_hotwater_circle, rv.layoutId)
        assertEquals(ctx.getString(R.string.widget_hotwater_btn_open), root.findViewById<TextView>(R.id.hw_action_label).text.toString())
        assertEquals(View.GONE, root.findViewById<View>(R.id.hw_room).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.hw_meta).visibility)
        assertNotNull(Shadows.shadowOf(root.findViewById<View>(R.id.widget_root)).onClickListener)
    }

    @Test
    fun circleLarge2x2ShowsRoomAndBalanceMeta() {
        val (root, rv) = inflate(boundIdleState(), 160, 160)
        assertEquals(R.layout.widget_hotwater_circle, rv.layoutId)
        assertEquals(ctx.getString(R.string.widget_hotwater_btn_open), root.findViewById<TextView>(R.id.hw_action_label).text.toString())
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.hw_room).visibility)
        assertEquals("紫荆2号楼 408", root.findViewById<TextView>(R.id.hw_room).text.toString())
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.hw_meta).visibility)
        assertTrue(
            root
                .findViewById<TextView>(R.id.hw_meta)
                .text
                .toString()
                .contains("12.34"),
        )
    }

    @Test
    fun rectCompact2x1HidesRoomAndMeta() {
        val (root, rv) = inflate(boundIdleState().copy(phase = HotWaterPhase.VALVE_OPEN), 150, 64)
        assertEquals(R.layout.widget_hotwater, rv.layoutId)
        assertEquals(ctx.getString(R.string.widget_hotwater_btn_close), root.findViewById<TextView>(R.id.hw_action_label).text.toString())
        assertEquals(View.GONE, root.findViewById<View>(R.id.hw_room).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.hw_meta).visibility)
        assertNotNull(Shadows.shadowOf(root.findViewById<View>(R.id.widget_root)).onClickListener)
    }

    @Test
    fun rectLarge3x1ShowsRoomAndBalance() {
        val (root, rv) = inflate(boundIdleState(), 240, 72)
        assertEquals(R.layout.widget_hotwater, rv.layoutId)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.hw_room).visibility)
        assertEquals("紫荆2号楼 408", root.findViewById<TextView>(R.id.hw_room).text.toString())
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.hw_meta).visibility)
        assertTrue(
            root
                .findViewById<TextView>(R.id.hw_meta)
                .text
                .toString()
                .contains("12.34"),
        )
    }

    @Test
    fun unboundAndErrorStatesRenderExpectedLabelsAndClickTarget() {
        val (unboundRoot, _) = inflate(HotWaterState(isBound = false), 240, 72)
        assertEquals(ctx.getString(R.string.widget_hotwater_btn_bind), unboundRoot.findViewById<TextView>(R.id.hw_action_label).text.toString())
        assertEquals(ctx.getString(R.string.widget_hotwater_unbound_sub), unboundRoot.findViewById<TextView>(R.id.hw_meta).text.toString())
        assertNotNull(Shadows.shadowOf(unboundRoot.findViewById<View>(R.id.widget_root)).onClickListener)

        val errState =
            boundIdleState().copy(
                phase = HotWaterPhase.ERROR,
                errorCode = HotWaterErrorCode.DEVICE_NOT_FOUND,
            )
        val (errRoot, _) = inflate(errState, 240, 72)
        assertEquals(ctx.getString(R.string.widget_hotwater_btn_open), errRoot.findViewById<TextView>(R.id.hw_action_label).text.toString())
        assertEquals(ctx.getString(HotWaterErrorCode.DEVICE_NOT_FOUND.errorStringRes()), errRoot.findViewById<TextView>(R.id.hw_meta).text.toString())
        assertNotNull(Shadows.shadowOf(errRoot.findViewById<View>(R.id.widget_root)).onClickListener)
    }

    @Test
    fun circleRendersTrueOneToOneGeometryInNonSquareNxNSlots() {
        val slots =
            listOf(
                72 to 92, // HyperOS 1x1
                160 to 205, // 2x2
                250 to 310, // 3x3
                96 to 74, // landscape 1x1
            )
        val density = ctx.resources.displayMetrics.density
        for ((wDp, hDp) in slots) {
            val (root, rv) = inflate(boundIdleState(), wDp, hDp)
            assertEquals("Expected circle layout for ${wDp}x$hDp", R.layout.widget_hotwater_circle, rv.layoutId)
            val pxW = (wDp * density).roundToInt()
            val pxH = (hDp * density).roundToInt()
            root.measure(
                View.MeasureSpec.makeMeasureSpec(pxW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(pxH, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, pxW, pxH)

            val bg = root.findViewById<ImageView>(R.id.hw_circle_bg)
            assertEquals(ImageView.ScaleType.FIT_CENTER, bg.scaleType)
            val drawable = bg.drawable
            assertNotNull(drawable)
            assertTrue(drawable.intrinsicWidth > 0)
            assertEquals(drawable.intrinsicWidth, drawable.intrinsicHeight)

            val src = RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
            val mapped = RectF()
            bg.imageMatrix.mapRect(mapped, src)
            assertTrue(
                "Expected 1:1 circle bounds for ${wDp}x$hDp, got ${mapped.width()}x${mapped.height()}",
                abs(mapped.width() - mapped.height()) <= 0.5f,
            )

            val contentW = (bg.width - bg.paddingLeft - bg.paddingRight).toFloat()
            val contentH = (bg.height - bg.paddingTop - bg.paddingBottom).toFloat()
            val expectedDiameter = minOf(contentW, contentH)
            assertTrue(
                "Expected diameter ~$expectedDiameter for ${wDp}x$hDp, got ${mapped.width()}",
                abs(mapped.width() - expectedDiameter) <= 1f,
            )
            val viewCenterX = bg.paddingLeft + contentW / 2f
            val viewCenterY = bg.paddingTop + contentH / 2f
            assertTrue(abs(mapped.centerX() - viewCenterX) <= 1f)
            assertTrue(abs(mapped.centerY() - viewCenterY) <= 1f)
        }
    }

    @Test
    fun tallCellNxNDetectionAndOrientationAwareMaxHeight() {
        val portrait1x1 =
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 64)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 58)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 88)
            }
        val rv1x1 = HotWaterWidgetProvider.build(ctx, boundIdleState(), portrait1x1)
        assertEquals(R.layout.widget_hotwater_circle, rv1x1.layoutId)

        val portrait2x2 =
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 205)
            }
        val rv2x2 = HotWaterWidgetProvider.build(ctx, boundIdleState(), portrait2x2)
        assertEquals(R.layout.widget_hotwater_circle, rv2x2.layoutId)

        val (_, rv2x1) = inflate(boundIdleState(), 160, 92)
        assertEquals(R.layout.widget_hotwater, rv2x1.layoutId)

        val (root1x2, rv1x2) = inflate(boundIdleState(), 72, 192)
        assertEquals(R.layout.widget_hotwater_vertical, rv1x2.layoutId)
        assertEquals(View.VISIBLE, root1x2.findViewById<View>(R.id.hw_room).visibility)
        assertEquals(View.VISIBLE, root1x2.findViewById<View>(R.id.hw_meta).visibility)
    }

    @Test
    fun vertical2x3AndWide4x2ScaleProperly() {
        val (root1x2, _) = inflate(boundIdleState(), 72, 192)
        val (root2x3, rv2x3) = inflate(boundIdleState(), 150, 290)
        assertEquals(R.layout.widget_hotwater_vertical, rv2x3.layoutId)
        assertEquals(View.VISIBLE, root2x3.findViewById<View>(R.id.hw_room).visibility)
        assertEquals(View.VISIBLE, root2x3.findViewById<View>(R.id.hw_meta).visibility)
        assertTrue(
            root2x3.findViewById<TextView>(R.id.hw_action_label).textSize >
                root1x2.findViewById<TextView>(R.id.hw_action_label).textSize,
        )

        val (root3x1, _) = inflate(boundIdleState(), 240, 72)
        val (root4x2, rv4x2) = inflate(boundIdleState(), 320, 180)
        assertEquals(R.layout.widget_hotwater, rv4x2.layoutId)
        assertEquals(View.VISIBLE, root4x2.findViewById<View>(R.id.hw_info_group).visibility)
        assertTrue(
            root4x2.findViewById<TextView>(R.id.hw_action_label).textSize >
                root3x1.findViewById<TextView>(R.id.hw_action_label).textSize,
        )
    }

    @Test
    fun circle3x3ScalesTypographyAndContentPaddingAbove2x2() {
        val (root2x2, _) = inflate(boundIdleState(), 160, 160)
        val (root3x3, rv3x3) = inflate(boundIdleState(), 250, 310)
        assertEquals(R.layout.widget_hotwater_circle, rv3x3.layoutId)

        val label2x2 = root2x2.findViewById<TextView>(R.id.hw_action_label)
        val label3x3 = root3x3.findViewById<TextView>(R.id.hw_action_label)
        assertTrue(label3x3.textSize > label2x2.textSize)

        val content2x2 = root2x2.findViewById<View>(R.id.hw_content)
        val content3x3 = root3x3.findViewById<View>(R.id.hw_content)
        assertTrue(content3x3.paddingLeft > content2x2.paddingLeft)
    }

    @Test
    fun optionAppWidgetSizesSupportsSingleAndMultipleSizes() {
        val singleOptions =
            Bundle().apply {
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(72f, 92f)))
            }
        val singleRv = HotWaterWidgetProvider.build(ctx, boundIdleState(), singleOptions)
        assertEquals(R.layout.widget_hotwater_circle, singleRv.layoutId)

        val multiOptions =
            Bundle().apply {
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(SizeF(72f, 92f), SizeF(240f, 72f)),
                )
            }
        val multiRv = HotWaterWidgetProvider.build(ctx, boundIdleState(), multiOptions)
        val root = multiRv.apply(ctx, FrameLayout(ctx))
        assertEquals(
            ctx.getString(R.string.widget_hotwater_btn_open),
            root.findViewById<TextView>(R.id.hw_action_label).text.toString(),
        )
    }
}
