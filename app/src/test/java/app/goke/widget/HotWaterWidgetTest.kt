package app.goke.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
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
}
