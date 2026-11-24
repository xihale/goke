package app.goke.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun gokeIcon(
    name: String,
    autoMirror: Boolean = false,
    block: ImageVector.Builder.() -> Unit,
): Lazy<ImageVector> =
    lazy {
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
                autoMirror = autoMirror,
            ).apply(block)
            .build()
    }

private inline fun ImageVector.Builder.solidPath(crossinline block: PathBuilder.() -> Unit) {
    path(fill = SolidColor(Color.Black), pathBuilder = block)
}

private val openInNewIcon by gokeIcon("AutoMirrored.Rounded.OpenInNew", autoMirror = true) {
    solidPath {
        moveTo(18.0f, 19.0f)
        horizontalLineTo(6.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        verticalLineTo(6.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        horizontalLineToRelative(5.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineTo(5.0f)
        curveToRelative(-1.11f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(14.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(14.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineToRelative(-6.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(5.0f)
        curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
        close()
        moveTo(14.0f, 4.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(2.59f)
        lineToRelative(-9.13f, 9.13f)
        curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
        curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
        lineTo(19.0f, 6.41f)
        verticalLineTo(9.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineTo(4.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineToRelative(-5.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        close()
    }
}

val Icons.AutoMirrored.Rounded.OpenInNew: ImageVector
    get() = openInNewIcon

private val undoIcon by gokeIcon("AutoMirrored.Rounded.Undo", autoMirror = true) {
    solidPath {
        moveTo(12.5f, 8.0f)
        curveToRelative(-2.65f, 0.0f, -5.05f, 0.99f, -6.9f, 2.6f)
        lineTo(3.71f, 8.71f)
        curveTo(3.08f, 8.08f, 2.0f, 8.52f, 2.0f, 9.41f)
        verticalLineTo(15.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(5.59f)
        curveToRelative(0.89f, 0.0f, 1.34f, -1.08f, 0.71f, -1.71f)
        lineToRelative(-1.91f, -1.91f)
        curveToRelative(1.39f, -1.16f, 3.16f, -1.88f, 5.12f, -1.88f)
        curveToRelative(3.16f, 0.0f, 5.89f, 1.84f, 7.19f, 4.5f)
        curveToRelative(0.27f, 0.56f, 0.91f, 0.84f, 1.5f, 0.64f)
        curveToRelative(0.71f, -0.23f, 1.07f, -1.04f, 0.75f, -1.72f)
        curveTo(20.23f, 10.42f, 16.65f, 8.0f, 12.5f, 8.0f)
        close()
    }
}

val Icons.AutoMirrored.Rounded.Undo: ImageVector
    get() = undoIcon

private val localOfferIcon by gokeIcon("Outlined.LocalOffer") {
    solidPath {
        moveTo(21.41f, 11.58f)
        lineToRelative(-9.0f, -9.0f)
        curveTo(12.05f, 2.22f, 11.55f, 2.0f, 11.0f, 2.0f)
        horizontalLineTo(4.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(7.0f)
        curveToRelative(0.0f, 0.55f, 0.22f, 1.05f, 0.59f, 1.42f)
        lineToRelative(9.0f, 9.0f)
        curveToRelative(0.36f, 0.36f, 0.86f, 0.58f, 1.41f, 0.58f)
        reflectiveCurveToRelative(1.05f, -0.22f, 1.41f, -0.59f)
        lineToRelative(7.0f, -7.0f)
        curveToRelative(0.37f, -0.36f, 0.59f, -0.86f, 0.59f, -1.41f)
        reflectiveCurveToRelative(-0.23f, -1.06f, -0.59f, -1.42f)
        close()
        moveTo(13.0f, 20.01f)
        lineTo(4.0f, 11.0f)
        verticalLineTo(4.0f)
        horizontalLineToRelative(7.0f)
        verticalLineToRelative(-0.01f)
        lineToRelative(9.0f, 9.0f)
        lineToRelative(-7.0f, 7.02f)
        close()
    }
    solidPath {
        moveTo(6.5f, 6.5f)
        moveToRelative(-1.5f, 0.0f)
        arcToRelative(1.5f, 1.5f, 0.0f, true, true, 3.0f, 0.0f)
        arcToRelative(1.5f, 1.5f, 0.0f, true, true, -3.0f, 0.0f)
    }
}

val Icons.Outlined.LocalOffer: ImageVector
    get() = localOfferIcon

private val alarmIcon by gokeIcon("Rounded.Alarm") {
    solidPath {
        moveTo(15.87f, 15.25f)
        lineToRelative(-3.37f, -2.0f)
        lineTo(12.5f, 8.72f)
        curveToRelative(0.0f, -0.4f, -0.32f, -0.72f, -0.72f, -0.72f)
        horizontalLineToRelative(-0.06f)
        curveToRelative(-0.4f, 0.0f, -0.72f, 0.32f, -0.72f, 0.72f)
        verticalLineToRelative(4.72f)
        curveToRelative(0.0f, 0.35f, 0.18f, 0.68f, 0.49f, 0.86f)
        lineToRelative(3.65f, 2.19f)
        curveToRelative(0.34f, 0.2f, 0.78f, 0.1f, 0.98f, -0.24f)
        curveToRelative(0.21f, -0.35f, 0.1f, -0.8f, -0.25f, -1.0f)
        close()
        moveTo(21.18f, 5.01f)
        lineTo(18.1f, 2.45f)
        curveToRelative(-0.42f, -0.35f, -1.05f, -0.3f, -1.41f, 0.13f)
        curveToRelative(-0.35f, 0.42f, -0.29f, 1.05f, 0.13f, 1.41f)
        lineToRelative(3.07f, 2.56f)
        curveToRelative(0.42f, 0.35f, 1.05f, 0.3f, 1.41f, -0.13f)
        curveToRelative(0.36f, -0.42f, 0.3f, -1.05f, -0.12f, -1.41f)
        close()
        moveTo(4.1f, 6.55f)
        lineToRelative(3.07f, -2.56f)
        curveToRelative(0.43f, -0.36f, 0.49f, -0.99f, 0.13f, -1.41f)
        curveToRelative(-0.35f, -0.43f, -0.98f, -0.48f, -1.4f, -0.13f)
        lineTo(2.82f, 5.01f)
        curveToRelative(-0.42f, 0.36f, -0.48f, 0.99f, -0.12f, 1.41f)
        curveToRelative(0.35f, 0.43f, 0.98f, 0.48f, 1.4f, 0.13f)
        close()
        moveTo(12.0f, 4.0f)
        curveToRelative(-4.97f, 0.0f, -9.0f, 4.03f, -9.0f, 9.0f)
        reflectiveCurveToRelative(4.03f, 9.0f, 9.0f, 9.0f)
        reflectiveCurveToRelative(9.0f, -4.03f, 9.0f, -9.0f)
        reflectiveCurveToRelative(-4.03f, -9.0f, -9.0f, -9.0f)
        close()
        moveTo(12.0f, 20.0f)
        curveToRelative(-3.86f, 0.0f, -7.0f, -3.14f, -7.0f, -7.0f)
        reflectiveCurveToRelative(3.14f, -7.0f, 7.0f, -7.0f)
        reflectiveCurveToRelative(7.0f, 3.14f, 7.0f, 7.0f)
        reflectiveCurveToRelative(-3.14f, 7.0f, -7.0f, 7.0f)
        close()
    }
}

val Icons.Rounded.Alarm: ImageVector
    get() = alarmIcon

private val batterySaverIcon by gokeIcon("Rounded.BatterySaver") {
    solidPath {
        moveTo(16.0f, 4.0f)
        horizontalLineToRelative(-2.0f)
        verticalLineTo(3.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineToRelative(-2.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(1.0f)
        horizontalLineTo(8.0f)
        curveTo(7.45f, 4.0f, 7.0f, 4.45f, 7.0f, 5.0f)
        verticalLineToRelative(16.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(8.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineTo(5.0f)
        curveTo(17.0f, 4.45f, 16.55f, 4.0f, 16.0f, 4.0f)
        close()
        moveTo(14.0f, 14.0f)
        horizontalLineToRelative(-1.0f)
        verticalLineToRelative(1.0f)
        curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
        horizontalLineToRelative(0.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        verticalLineToRelative(-1.0f)
        horizontalLineToRelative(-1.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        verticalLineToRelative(0.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        horizontalLineToRelative(1.0f)
        verticalLineToRelative(-1.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        horizontalLineToRelative(0.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
        verticalLineToRelative(1.0f)
        horizontalLineToRelative(1.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
        verticalLineToRelative(0.0f)
        curveTo(15.0f, 13.55f, 14.55f, 14.0f, 14.0f, 14.0f)
        close()
    }
}

val Icons.Rounded.BatterySaver: ImageVector
    get() = batterySaverIcon

private val bedtimeIcon by gokeIcon("Rounded.Bedtime") {
    solidPath {
        moveTo(11.65f, 3.46f)
        curveToRelative(0.27f, -0.71f, -0.36f, -1.45f, -1.12f, -1.34f)
        curveToRelative(-5.52f, 0.8f, -9.47f, 6.07f, -8.34f, 11.88f)
        curveToRelative(0.78f, 4.02f, 4.09f, 7.21f, 8.14f, 7.87f)
        curveToRelative(3.74f, 0.61f, 7.16f, -0.87f, 9.32f, -3.44f)
        curveToRelative(0.48f, -0.57f, 0.19f, -1.48f, -0.55f, -1.62f)
        curveTo(13.08f, 15.66f, 9.42f, 9.27f, 11.65f, 3.46f)
        close()
    }
}

val Icons.Rounded.Bedtime: ImageVector
    get() = bedtimeIcon

private val blockIcon by gokeIcon("Rounded.Block") {
    solidPath {
        moveTo(12.0f, 2.0f)
        curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
        reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
        reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
        reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
        close()
        moveTo(4.0f, 12.0f)
        curveToRelative(0.0f, -4.42f, 3.58f, -8.0f, 8.0f, -8.0f)
        curveToRelative(1.85f, 0.0f, 3.55f, 0.63f, 4.9f, 1.69f)
        lineTo(5.69f, 16.9f)
        curveTo(4.63f, 15.55f, 4.0f, 13.85f, 4.0f, 12.0f)
        close()
        moveTo(12.0f, 20.0f)
        curveToRelative(-1.85f, 0.0f, -3.55f, -0.63f, -4.9f, -1.69f)
        lineTo(18.31f, 7.1f)
        curveTo(19.37f, 8.45f, 20.0f, 10.15f, 20.0f, 12.0f)
        curveToRelative(0.0f, 4.42f, -3.58f, 8.0f, -8.0f, 8.0f)
        close()
    }
}

val Icons.Rounded.Block: ImageVector
    get() = blockIcon

private val calendarMonthIcon by gokeIcon("Rounded.CalendarMonth") {
    solidPath {
        moveTo(17.0f, 2.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(1.0f)
        horizontalLineTo(8.0f)
        verticalLineTo(3.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        reflectiveCurveTo(6.0f, 2.45f, 6.0f, 3.0f)
        verticalLineToRelative(1.0f)
        horizontalLineTo(5.0f)
        curveTo(3.89f, 4.0f, 3.01f, 4.9f, 3.01f, 6.0f)
        lineTo(3.0f, 20.0f)
        curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(14.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(6.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        horizontalLineToRelative(-1.0f)
        verticalLineTo(3.0f)
        curveTo(18.0f, 2.45f, 17.55f, 2.0f, 17.0f, 2.0f)
        close()
        moveTo(19.0f, 20.0f)
        horizontalLineTo(5.0f)
        verticalLineTo(10.0f)
        horizontalLineToRelative(14.0f)
        verticalLineTo(20.0f)
        close()
        moveTo(11.0f, 13.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
        reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
        reflectiveCurveTo(11.0f, 13.55f, 11.0f, 13.0f)
        close()
        moveTo(7.0f, 13.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
        reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
        reflectiveCurveTo(7.0f, 13.55f, 7.0f, 13.0f)
        close()
        moveTo(15.0f, 13.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
        reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
        reflectiveCurveTo(15.0f, 13.55f, 15.0f, 13.0f)
        close()
        moveTo(11.0f, 17.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
        reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
        reflectiveCurveTo(11.0f, 17.55f, 11.0f, 17.0f)
        close()
        moveTo(7.0f, 17.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
        reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
        reflectiveCurveTo(7.0f, 17.55f, 7.0f, 17.0f)
        close()
        moveTo(15.0f, 17.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
        reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
        reflectiveCurveTo(15.0f, 17.55f, 15.0f, 17.0f)
        close()
    }
}

val Icons.Rounded.CalendarMonth: ImageVector
    get() = calendarMonthIcon

private val calendarTodayIcon by gokeIcon("Rounded.CalendarToday") {
    solidPath {
        moveTo(20.0f, 3.0f)
        horizontalLineToRelative(-1.0f)
        lineTo(19.0f, 2.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(1.0f)
        lineTo(7.0f, 3.0f)
        lineTo(7.0f, 2.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(1.0f)
        lineTo(4.0f, 3.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(16.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(16.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        lineTo(22.0f, 5.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(19.0f, 21.0f)
        lineTo(5.0f, 21.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        lineTo(4.0f, 8.0f)
        horizontalLineToRelative(16.0f)
        verticalLineToRelative(12.0f)
        curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
        close()
    }
}

val Icons.Rounded.CalendarToday: ImageVector
    get() = calendarTodayIcon

private val calendarViewWeekIcon by gokeIcon("Rounded.CalendarViewWeek") {
    solidPath {
        moveTo(20.0f, 4.0f)
        horizontalLineTo(4.0f)
        curveTo(2.9f, 4.0f, 2.0f, 4.9f, 2.0f, 6.0f)
        verticalLineToRelative(12.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(16.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(6.0f)
        curveTo(22.0f, 4.9f, 21.1f, 4.0f, 20.0f, 4.0f)
        close()
        moveTo(13.0f, 6.0f)
        horizontalLineToRelative(2.5f)
        verticalLineToRelative(12.0f)
        horizontalLineTo(13.0f)
        verticalLineTo(6.0f)
        close()
        moveTo(11.0f, 18.0f)
        horizontalLineTo(8.5f)
        verticalLineTo(6.0f)
        horizontalLineTo(11.0f)
        verticalLineTo(18.0f)
        close()
        moveTo(4.0f, 6.0f)
        horizontalLineToRelative(2.5f)
        verticalLineToRelative(12.0f)
        horizontalLineTo(4.0f)
        verticalLineTo(6.0f)
        close()
        moveTo(20.0f, 18.0f)
        horizontalLineToRelative(-2.5f)
        verticalLineTo(6.0f)
        horizontalLineTo(20.0f)
        verticalLineTo(18.0f)
        close()
    }
}

val Icons.Rounded.CalendarViewWeek: ImageVector
    get() = calendarViewWeekIcon

private val checklistIcon by gokeIcon("Rounded.Checklist") {
    solidPath {
        moveTo(22.0f, 8.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineToRelative(-7.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(7.0f)
        curveTo(21.55f, 9.0f, 22.0f, 8.55f, 22.0f, 8.0f)
        close()
        moveTo(13.0f, 16.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(7.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineToRelative(-7.0f)
        curveTo(13.45f, 15.0f, 13.0f, 15.45f, 13.0f, 16.0f)
        close()
        moveTo(10.47f, 4.63f)
        curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0.0f, 1.41f)
        lineToRelative(-4.23f, 4.25f)
        curveToRelative(-0.39f, 0.39f, -1.02f, 0.39f, -1.42f, 0.0f)
        lineTo(2.7f, 8.16f)
        curveToRelative(-0.39f, -0.39f, -0.39f, -1.02f, 0.0f, -1.41f)
        curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0.0f)
        lineToRelative(1.42f, 1.42f)
        lineToRelative(3.54f, -3.54f)
        curveTo(9.45f, 4.25f, 10.09f, 4.25f, 10.47f, 4.63f)
        close()
        moveTo(10.48f, 12.64f)
        curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0.0f, 1.41f)
        lineToRelative(-4.23f, 4.25f)
        curveToRelative(-0.39f, 0.39f, -1.02f, 0.39f, -1.42f, 0.0f)
        lineTo(2.7f, 16.16f)
        curveToRelative(-0.39f, -0.39f, -0.39f, -1.02f, 0.0f, -1.41f)
        reflectiveCurveToRelative(1.02f, -0.39f, 1.41f, 0.0f)
        lineToRelative(1.42f, 1.42f)
        lineToRelative(3.54f, -3.54f)
        curveTo(9.45f, 12.25f, 10.09f, 12.25f, 10.48f, 12.64f)
        lineTo(10.48f, 12.64f)
        close()
    }
}

val Icons.Rounded.Checklist: ImageVector
    get() = checklistIcon

private val cloudIcon by gokeIcon("Rounded.Cloud") {
    solidPath {
        moveTo(19.35f, 10.04f)
        curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
        curveTo(9.11f, 4.0f, 6.6f, 5.64f, 5.35f, 8.04f)
        curveTo(2.34f, 8.36f, 0.0f, 10.91f, 0.0f, 14.0f)
        curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
        horizontalLineToRelative(13.0f)
        curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
        curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
        close()
    }
}

val Icons.Rounded.Cloud: ImageVector
    get() = cloudIcon

private val contentCopyIcon by gokeIcon("Rounded.ContentCopy") {
    solidPath {
        moveTo(15.0f, 20.0f)
        horizontalLineTo(5.0f)
        verticalLineTo(7.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineToRelative(0.0f)
        curveTo(3.45f, 6.0f, 3.0f, 6.45f, 3.0f, 7.0f)
        verticalLineToRelative(13.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(10.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineToRelative(0.0f)
        curveTo(16.0f, 20.45f, 15.55f, 20.0f, 15.0f, 20.0f)
        close()
        moveTo(20.0f, 16.0f)
        verticalLineTo(4.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        horizontalLineTo(9.0f)
        curveTo(7.9f, 2.0f, 7.0f, 2.9f, 7.0f, 4.0f)
        verticalLineToRelative(12.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(9.0f)
        curveTo(19.1f, 18.0f, 20.0f, 17.1f, 20.0f, 16.0f)
        close()
        moveTo(18.0f, 16.0f)
        horizontalLineTo(9.0f)
        verticalLineTo(4.0f)
        horizontalLineToRelative(9.0f)
        verticalLineTo(16.0f)
        close()
    }
}

val Icons.Rounded.ContentCopy: ImageVector
    get() = contentCopyIcon

private val deleteOutlineIcon by gokeIcon("Rounded.DeleteOutline") {
    solidPath {
        moveTo(6.0f, 19.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(8.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        lineTo(18.0f, 9.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        lineTo(8.0f, 7.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(10.0f)
        close()
        moveTo(9.0f, 9.0f)
        horizontalLineToRelative(6.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
        verticalLineToRelative(8.0f)
        curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
        lineTo(9.0f, 19.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        verticalLineToRelative(-8.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        close()
        moveTo(15.5f, 4.0f)
        lineToRelative(-0.71f, -0.71f)
        curveToRelative(-0.18f, -0.18f, -0.44f, -0.29f, -0.7f, -0.29f)
        lineTo(9.91f, 3.0f)
        curveToRelative(-0.26f, 0.0f, -0.52f, 0.11f, -0.7f, 0.29f)
        lineTo(8.5f, 4.0f)
        lineTo(6.0f, 4.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(12.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineToRelative(-2.5f)
        close()
    }
}

val Icons.Rounded.DeleteOutline: ImageVector
    get() = deleteOutlineIcon

private val deleteSweepIcon by gokeIcon("Rounded.DeleteSweep") {
    solidPath {
        moveTo(16.0f, 16.0f)
        horizontalLineToRelative(2.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
        reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
        horizontalLineToRelative(-2.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
        close()
        moveTo(16.0f, 8.0f)
        horizontalLineToRelative(5.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
        reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
        horizontalLineToRelative(-5.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
        close()
        moveTo(16.0f, 12.0f)
        horizontalLineToRelative(4.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
        reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
        horizontalLineToRelative(-4.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
        close()
        moveTo(3.0f, 18.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(6.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        lineTo(13.0f, 8.0f)
        lineTo(3.0f, 8.0f)
        verticalLineToRelative(10.0f)
        close()
        moveTo(13.0f, 5.0f)
        horizontalLineToRelative(-2.0f)
        lineToRelative(-0.71f, -0.71f)
        curveToRelative(-0.18f, -0.18f, -0.44f, -0.29f, -0.7f, -0.29f)
        lineTo(6.41f, 4.0f)
        curveToRelative(-0.26f, 0.0f, -0.52f, 0.11f, -0.7f, 0.29f)
        lineTo(5.0f, 5.0f)
        lineTo(3.0f, 5.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(10.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
        close()
    }
}

val Icons.Rounded.DeleteSweep: ImageVector
    get() = deleteSweepIcon

private val eventIcon by gokeIcon("Rounded.Event") {
    solidPath {
        moveTo(16.0f, 13.0f)
        horizontalLineToRelative(-3.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(3.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(3.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineToRelative(-3.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        close()
        moveTo(16.0f, 3.0f)
        verticalLineToRelative(1.0f)
        lineTo(8.0f, 4.0f)
        lineTo(8.0f, 3.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(1.0f)
        lineTo(5.0f, 4.0f)
        curveToRelative(-1.11f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
        lineTo(3.0f, 20.0f)
        curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(14.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        lineTo(21.0f, 6.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        horizontalLineToRelative(-1.0f)
        lineTo(18.0f, 3.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
        close()
        moveTo(18.0f, 20.0f)
        lineTo(6.0f, 20.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        lineTo(5.0f, 9.0f)
        horizontalLineToRelative(14.0f)
        verticalLineToRelative(10.0f)
        curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
        close()
    }
}

val Icons.Rounded.Event: ImageVector
    get() = eventIcon

private val expandLessIcon by gokeIcon("Rounded.ExpandLess") {
    solidPath {
        moveTo(11.29f, 8.71f)
        lineTo(6.7f, 13.3f)
        curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
        curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
        lineTo(12.0f, 10.83f)
        lineToRelative(3.88f, 3.88f)
        curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
        curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
        lineTo(12.7f, 8.71f)
        curveToRelative(-0.38f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
        close()
    }
}

val Icons.Rounded.ExpandLess: ImageVector
    get() = expandLessIcon

private val expandMoreIcon by gokeIcon("Rounded.ExpandMore") {
    solidPath {
        moveTo(15.88f, 9.29f)
        lineTo(12.0f, 13.17f)
        lineTo(8.12f, 9.29f)
        curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
        curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
        lineToRelative(4.59f, 4.59f)
        curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
        lineToRelative(4.59f, -4.59f)
        curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
        curveToRelative(-0.39f, -0.38f, -1.03f, -0.39f, -1.42f, 0.0f)
        close()
    }
}

val Icons.Rounded.ExpandMore: ImageVector
    get() = expandMoreIcon

private val fitnessCenterIcon by gokeIcon("Rounded.FitnessCenter") {
    solidPath {
        moveTo(20.57f, 14.86f)
        lineToRelative(0.72f, -0.72f)
        curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
        lineToRelative(-0.02f, -0.02f)
        curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
        lineTo(17.0f, 15.57f)
        lineTo(8.43f, 7.0f)
        lineToRelative(2.86f, -2.86f)
        curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
        lineToRelative(-0.02f, -0.02f)
        curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
        lineToRelative(-0.72f, 0.72f)
        lineToRelative(-0.72f, -0.72f)
        curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.42f, 0.0f)
        lineTo(5.57f, 4.14f)
        lineToRelative(-0.72f, -0.72f)
        curveToRelative(-0.39f, -0.39f, -1.04f, -0.39f, -1.43f, 0.0f)
        curveToRelative(-0.39f, 0.39f, -0.39f, 1.04f, 0.0f, 1.43f)
        lineToRelative(0.72f, 0.72f)
        lineTo(2.71f, 7.0f)
        curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
        lineToRelative(0.72f, 0.72f)
        lineToRelative(-0.72f, 0.73f)
        curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
        lineToRelative(0.02f, 0.02f)
        curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
        lineTo(7.0f, 8.43f)
        lineTo(15.57f, 17.0f)
        lineToRelative(-2.86f, 2.86f)
        curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
        lineToRelative(0.02f, 0.02f)
        curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
        lineToRelative(0.72f, -0.72f)
        lineToRelative(0.72f, 0.72f)
        curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
        lineToRelative(1.43f, -1.43f)
        lineToRelative(0.72f, 0.72f)
        curveToRelative(0.39f, 0.39f, 1.04f, 0.39f, 1.43f, 0.0f)
        curveToRelative(0.39f, -0.39f, 0.39f, -1.04f, 0.0f, -1.43f)
        lineToRelative(-0.72f, -0.72f)
        lineTo(21.29f, 17.0f)
        curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
        lineToRelative(-0.72f, -0.73f)
        close()
    }
}

val Icons.Rounded.FitnessCenter: ImageVector
    get() = fitnessCenterIcon

private val flagIcon by gokeIcon("Rounded.Flag") {
    solidPath {
        moveTo(14.4f, 6.0f)
        lineToRelative(-0.24f, -1.2f)
        curveToRelative(-0.09f, -0.46f, -0.5f, -0.8f, -0.98f, -0.8f)
        horizontalLineTo(6.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(15.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineToRelative(-6.0f)
        horizontalLineToRelative(5.6f)
        lineToRelative(0.24f, 1.2f)
        curveToRelative(0.09f, 0.47f, 0.5f, 0.8f, 0.98f, 0.8f)
        horizontalLineTo(19.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineTo(7.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineToRelative(-4.6f)
        close()
    }
}

val Icons.Rounded.Flag: ImageVector
    get() = flagIcon

private val folderOpenIcon by gokeIcon("Rounded.FolderOpen") {
    solidPath {
        moveTo(20.0f, 6.0f)
        horizontalLineToRelative(-8.0f)
        lineToRelative(-1.41f, -1.41f)
        curveTo(10.21f, 4.21f, 9.7f, 4.0f, 9.17f, 4.0f)
        lineTo(4.0f, 4.0f)
        curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
        lineTo(2.0f, 18.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(16.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        lineTo(22.0f, 8.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(19.0f, 18.0f)
        lineTo(5.0f, 18.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        lineTo(4.0f, 9.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        horizontalLineToRelative(14.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
        verticalLineToRelative(8.0f)
        curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
        close()
    }
}

val Icons.Rounded.FolderOpen: ImageVector
    get() = folderOpenIcon

private val gradeIcon by gokeIcon("Rounded.Grade") {
    solidPath {
        moveTo(12.0f, 17.27f)
        lineToRelative(5.17f, 3.12f)
        curveToRelative(0.38f, 0.23f, 0.85f, -0.11f, 0.75f, -0.54f)
        lineToRelative(-1.37f, -5.88f)
        lineToRelative(4.56f, -3.95f)
        curveToRelative(0.33f, -0.29f, 0.16f, -0.84f, -0.29f, -0.88f)
        lineToRelative(-6.01f, -0.51f)
        lineToRelative(-2.35f, -5.54f)
        curveToRelative(-0.17f, -0.41f, -0.75f, -0.41f, -0.92f, 0.0f)
        lineTo(9.19f, 8.63f)
        lineToRelative(-6.01f, 0.51f)
        curveToRelative(-0.44f, 0.04f, -0.62f, 0.59f, -0.28f, 0.88f)
        lineToRelative(4.56f, 3.95f)
        lineToRelative(-1.37f, 5.88f)
        curveToRelative(-0.1f, 0.43f, 0.37f, 0.77f, 0.75f, 0.54f)
        lineTo(12.0f, 17.27f)
        close()
    }
}

val Icons.Rounded.Grade: ImageVector
    get() = gradeIcon

private val gridViewIcon by gokeIcon("Rounded.GridView") {
    solidPath {
        moveTo(5.0f, 11.0f)
        horizontalLineToRelative(4.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(5.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        horizontalLineTo(5.0f)
        curveTo(3.9f, 3.0f, 3.0f, 3.9f, 3.0f, 5.0f)
        verticalLineToRelative(4.0f)
        curveTo(3.0f, 10.1f, 3.9f, 11.0f, 5.0f, 11.0f)
        close()
    }
    solidPath {
        moveTo(5.0f, 21.0f)
        horizontalLineToRelative(4.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineToRelative(-4.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        horizontalLineTo(5.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(4.0f)
        curveTo(3.0f, 20.1f, 3.9f, 21.0f, 5.0f, 21.0f)
        close()
    }
    solidPath {
        moveTo(13.0f, 5.0f)
        verticalLineToRelative(4.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(4.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(5.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        horizontalLineToRelative(-4.0f)
        curveTo(13.9f, 3.0f, 13.0f, 3.9f, 13.0f, 5.0f)
        close()
    }
    solidPath {
        moveTo(15.0f, 21.0f)
        horizontalLineToRelative(4.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineToRelative(-4.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        horizontalLineToRelative(-4.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(4.0f)
        curveTo(13.0f, 20.1f, 13.9f, 21.0f, 15.0f, 21.0f)
        close()
    }
}

val Icons.Rounded.GridView: ImageVector
    get() = gridViewIcon

private val historyIcon by gokeIcon("Rounded.History") {
    solidPath {
        moveTo(13.26f, 3.0f)
        curveTo(8.17f, 2.86f, 4.0f, 6.95f, 4.0f, 12.0f)
        lineTo(2.21f, 12.0f)
        curveToRelative(-0.45f, 0.0f, -0.67f, 0.54f, -0.35f, 0.85f)
        lineToRelative(2.79f, 2.8f)
        curveToRelative(0.2f, 0.2f, 0.51f, 0.2f, 0.71f, 0.0f)
        lineToRelative(2.79f, -2.8f)
        curveToRelative(0.31f, -0.31f, 0.09f, -0.85f, -0.36f, -0.85f)
        lineTo(6.0f, 12.0f)
        curveToRelative(0.0f, -3.9f, 3.18f, -7.05f, 7.1f, -7.0f)
        curveToRelative(3.72f, 0.05f, 6.85f, 3.18f, 6.9f, 6.9f)
        curveToRelative(0.05f, 3.91f, -3.1f, 7.1f, -7.0f, 7.1f)
        curveToRelative(-1.61f, 0.0f, -3.1f, -0.55f, -4.28f, -1.48f)
        curveToRelative(-0.4f, -0.31f, -0.96f, -0.28f, -1.32f, 0.08f)
        curveToRelative(-0.42f, 0.42f, -0.39f, 1.13f, 0.08f, 1.49f)
        curveTo(9.0f, 20.29f, 10.91f, 21.0f, 13.0f, 21.0f)
        curveToRelative(5.05f, 0.0f, 9.14f, -4.17f, 9.0f, -9.26f)
        curveToRelative(-0.13f, -4.69f, -4.05f, -8.61f, -8.74f, -8.74f)
        close()
        moveTo(12.75f, 8.0f)
        curveToRelative(-0.41f, 0.0f, -0.75f, 0.34f, -0.75f, 0.75f)
        verticalLineToRelative(3.68f)
        curveToRelative(0.0f, 0.35f, 0.19f, 0.68f, 0.49f, 0.86f)
        lineToRelative(3.12f, 1.85f)
        curveToRelative(0.36f, 0.21f, 0.82f, 0.09f, 1.03f, -0.26f)
        curveToRelative(0.21f, -0.36f, 0.09f, -0.82f, -0.26f, -1.03f)
        lineToRelative(-2.88f, -1.71f)
        verticalLineToRelative(-3.4f)
        curveToRelative(0.0f, -0.4f, -0.34f, -0.74f, -0.75f, -0.74f)
        close()
    }
}

val Icons.Rounded.History: ImageVector
    get() = historyIcon

private val imageIcon by gokeIcon("Rounded.Image") {
    solidPath {
        moveTo(21.0f, 19.0f)
        verticalLineTo(5.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        horizontalLineTo(5.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(14.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(14.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        close()
        moveTo(8.9f, 13.98f)
        lineToRelative(2.1f, 2.53f)
        lineToRelative(3.1f, -3.99f)
        curveToRelative(0.2f, -0.26f, 0.6f, -0.26f, 0.8f, 0.01f)
        lineToRelative(3.51f, 4.68f)
        curveToRelative(0.25f, 0.33f, 0.01f, 0.8f, -0.4f, 0.8f)
        horizontalLineTo(6.02f)
        curveToRelative(-0.42f, 0.0f, -0.65f, -0.48f, -0.39f, -0.81f)
        lineTo(8.12f, 14.0f)
        curveToRelative(0.19f, -0.26f, 0.57f, -0.27f, 0.78f, -0.02f)
        close()
    }
}

val Icons.Rounded.Image: ImageVector
    get() = imageIcon

private val inboxIcon by gokeIcon("Rounded.Inbox") {
    solidPath {
        moveTo(19.0f, 3.0f)
        horizontalLineTo(5.0f)
        curveTo(3.9f, 3.0f, 3.0f, 3.9f, 3.0f, 5.0f)
        verticalLineToRelative(14.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(14.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(5.0f)
        curveTo(21.0f, 3.9f, 20.1f, 3.0f, 19.0f, 3.0f)
        close()
        moveTo(19.0f, 5.0f)
        verticalLineToRelative(9.0f)
        horizontalLineToRelative(-3.56f)
        curveToRelative(-0.36f, 0.0f, -0.68f, 0.19f, -0.86f, 0.5f)
        curveTo(14.06f, 15.4f, 13.11f, 16.0f, 12.0f, 16.0f)
        reflectiveCurveToRelative(-2.06f, -0.6f, -2.58f, -1.5f)
        curveTo(9.24f, 14.19f, 8.91f, 14.0f, 8.56f, 14.0f)
        horizontalLineTo(5.0f)
        verticalLineTo(5.0f)
        horizontalLineTo(19.0f)
        close()
    }
}

val Icons.Rounded.Inbox: ImageVector
    get() = inboxIcon

private val inventory2Icon by gokeIcon("Rounded.Inventory2") {
    solidPath {
        moveTo(20.0f, 2.0f)
        horizontalLineTo(4.0f)
        curveTo(3.0f, 2.0f, 2.0f, 2.9f, 2.0f, 4.0f)
        verticalLineToRelative(3.01f)
        curveTo(2.0f, 7.73f, 2.43f, 8.35f, 3.0f, 8.7f)
        verticalLineTo(20.0f)
        curveToRelative(0.0f, 1.1f, 1.1f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(14.0f)
        curveToRelative(0.9f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(8.7f)
        curveToRelative(0.57f, -0.35f, 1.0f, -0.97f, 1.0f, -1.69f)
        verticalLineTo(4.0f)
        curveTo(22.0f, 2.9f, 21.0f, 2.0f, 20.0f, 2.0f)
        close()
        moveTo(14.0f, 14.0f)
        horizontalLineToRelative(-4.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        verticalLineToRelative(0.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        horizontalLineToRelative(4.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
        verticalLineToRelative(0.0f)
        curveTo(15.0f, 13.55f, 14.55f, 14.0f, 14.0f, 14.0f)
        close()
        moveTo(20.0f, 7.0f)
        horizontalLineTo(4.0f)
        verticalLineTo(4.0f)
        horizontalLineToRelative(16.0f)
        verticalLineTo(7.0f)
        close()
    }
}

val Icons.Rounded.Inventory2: ImageVector
    get() = inventory2Icon

private val iosShareIcon by gokeIcon("Rounded.IosShare") {
    solidPath {
        moveTo(18.0f, 8.0f)
        horizontalLineToRelative(-2.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(0.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(2.0f)
        verticalLineToRelative(11.0f)
        horizontalLineTo(6.0f)
        verticalLineTo(10.0f)
        horizontalLineToRelative(2.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineToRelative(0.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineTo(6.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(11.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(12.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(10.0f)
        curveTo(20.0f, 8.9f, 19.1f, 8.0f, 18.0f, 8.0f)
        close()
    }
    solidPath {
        moveTo(12.0f, 16.0f)
        lineTo(12.0f, 16.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineTo(5.0f)
        horizontalLineToRelative(1.79f)
        curveToRelative(0.45f, 0.0f, 0.67f, -0.54f, 0.35f, -0.85f)
        lineToRelative(-2.79f, -2.79f)
        curveToRelative(-0.2f, -0.2f, -0.51f, -0.2f, -0.71f, 0.0f)
        lineTo(8.85f, 4.15f)
        curveTo(8.54f, 4.46f, 8.76f, 5.0f, 9.21f, 5.0f)
        horizontalLineTo(11.0f)
        verticalLineToRelative(10.0f)
        curveTo(11.0f, 15.55f, 11.45f, 16.0f, 12.0f, 16.0f)
        close()
    }
}

val Icons.Rounded.IosShare: ImageVector
    get() = iosShareIcon

private val laptopMacIcon by gokeIcon("Rounded.LaptopMac") {
    solidPath {
        moveTo(20.0f, 18.0f)
        curveToRelative(1.1f, 0.0f, 1.99f, -0.9f, 1.99f, -2.0f)
        lineTo(22.0f, 5.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        lineTo(4.0f, 3.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(11.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        lineTo(0.0f, 18.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(20.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        horizontalLineToRelative(-4.0f)
        close()
        moveTo(5.0f, 5.0f)
        horizontalLineToRelative(14.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
        verticalLineToRelative(9.0f)
        curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
        lineTo(5.0f, 16.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        lineTo(4.0f, 6.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        close()
        moveTo(12.0f, 19.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
        reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
        reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
        close()
    }
}

val Icons.Rounded.LaptopMac: ImageVector
    get() = laptopMacIcon

private val localShippingIcon by gokeIcon("Rounded.LocalShipping") {
    solidPath {
        moveTo(19.5f, 8.0f)
        lineTo(17.0f, 8.0f)
        lineTo(17.0f, 6.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        lineTo(3.0f, 4.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(9.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
        reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
        horizontalLineToRelative(6.0f)
        curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
        reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
        horizontalLineToRelative(1.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineToRelative(-3.33f)
        curveToRelative(0.0f, -0.43f, -0.14f, -0.85f, -0.4f, -1.2f)
        lineTo(20.3f, 8.4f)
        curveToRelative(-0.19f, -0.25f, -0.49f, -0.4f, -0.8f, -0.4f)
        close()
        moveTo(6.0f, 18.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
        reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
        reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
        close()
        moveTo(19.5f, 9.5f)
        lineToRelative(1.96f, 2.5f)
        lineTo(17.0f, 12.0f)
        lineTo(17.0f, 9.5f)
        horizontalLineToRelative(2.5f)
        close()
        moveTo(18.0f, 18.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
        reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
        reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
        close()
    }
}

val Icons.Rounded.LocalShipping: ImageVector
    get() = localShippingIcon

private val meetingRoomIcon by gokeIcon("Rounded.MeetingRoom") {
    solidPath {
        moveTo(20.0f, 19.0f)
        horizontalLineToRelative(-1.0f)
        verticalLineTo(5.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineToRelative(-4.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineTo(6.0f)
        curveTo(5.45f, 3.0f, 5.0f, 3.45f, 5.0f, 4.0f)
        verticalLineToRelative(15.0f)
        horizontalLineTo(4.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(9.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineTo(6.0f)
        horizontalLineToRelative(3.0f)
        verticalLineToRelative(14.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(2.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        reflectiveCurveTo(20.55f, 19.0f, 20.0f, 19.0f)
        close()
        moveTo(11.0f, 13.0f)
        lineTo(11.0f, 13.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        verticalLineToRelative(0.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        horizontalLineToRelative(0.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
        verticalLineToRelative(0.0f)
        curveTo(12.0f, 12.55f, 11.55f, 13.0f, 11.0f, 13.0f)
        close()
    }
}

val Icons.Rounded.MeetingRoom: ImageVector
    get() = meetingRoomIcon

private val moreHorizIcon by gokeIcon("Rounded.MoreHoriz") {
    solidPath {
        moveTo(6.0f, 10.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
        reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
        reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(18.0f, 10.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
        reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
        reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(12.0f, 10.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
        reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
        reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
        close()
    }
}

val Icons.Rounded.MoreHoriz: ImageVector
    get() = moreHorizIcon

private val notificationsActiveIcon by gokeIcon("Rounded.NotificationsActive") {
    solidPath {
        moveTo(18.0f, 16.0f)
        verticalLineToRelative(-5.0f)
        curveToRelative(0.0f, -3.07f, -1.64f, -5.64f, -4.5f, -6.32f)
        lineTo(13.5f, 4.0f)
        curveToRelative(0.0f, -0.83f, -0.68f, -1.5f, -1.51f, -1.5f)
        reflectiveCurveTo(10.5f, 3.17f, 10.5f, 4.0f)
        verticalLineToRelative(0.68f)
        curveTo(7.63f, 5.36f, 6.0f, 7.92f, 6.0f, 11.0f)
        verticalLineToRelative(5.0f)
        lineToRelative(-1.3f, 1.29f)
        curveToRelative(-0.63f, 0.63f, -0.19f, 1.71f, 0.7f, 1.71f)
        horizontalLineToRelative(13.17f)
        curveToRelative(0.89f, 0.0f, 1.34f, -1.08f, 0.71f, -1.71f)
        lineTo(18.0f, 16.0f)
        close()
        moveTo(11.99f, 22.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        horizontalLineToRelative(-4.0f)
        curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
        close()
        moveTo(6.77f, 4.73f)
        curveToRelative(0.42f, -0.38f, 0.43f, -1.03f, 0.03f, -1.43f)
        curveToRelative(-0.38f, -0.38f, -1.0f, -0.39f, -1.39f, -0.02f)
        curveTo(3.7f, 4.84f, 2.52f, 6.96f, 2.14f, 9.34f)
        curveToRelative(-0.09f, 0.61f, 0.38f, 1.16f, 1.0f, 1.16f)
        curveToRelative(0.48f, 0.0f, 0.9f, -0.35f, 0.98f, -0.83f)
        curveToRelative(0.3f, -1.94f, 1.26f, -3.67f, 2.65f, -4.94f)
        close()
        moveTo(18.6f, 3.28f)
        curveToRelative(-0.4f, -0.37f, -1.02f, -0.36f, -1.4f, 0.02f)
        curveToRelative(-0.4f, 0.4f, -0.38f, 1.04f, 0.03f, 1.42f)
        curveToRelative(1.38f, 1.27f, 2.35f, 3.0f, 2.65f, 4.94f)
        curveToRelative(0.07f, 0.48f, 0.49f, 0.83f, 0.98f, 0.83f)
        curveToRelative(0.61f, 0.0f, 1.09f, -0.55f, 0.99f, -1.16f)
        curveToRelative(-0.38f, -2.37f, -1.55f, -4.48f, -3.25f, -6.05f)
        close()
    }
}

val Icons.Rounded.NotificationsActive: ImageVector
    get() = notificationsActiveIcon

private val notificationsNoneIcon by gokeIcon("Rounded.NotificationsNone") {
    solidPath {
        moveTo(19.29f, 17.29f)
        lineTo(18.0f, 16.0f)
        verticalLineToRelative(-5.0f)
        curveToRelative(0.0f, -3.07f, -1.64f, -5.64f, -4.5f, -6.32f)
        lineTo(13.5f, 4.0f)
        curveToRelative(0.0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
        reflectiveCurveToRelative(-1.5f, 0.67f, -1.5f, 1.5f)
        verticalLineToRelative(0.68f)
        curveTo(7.63f, 5.36f, 6.0f, 7.92f, 6.0f, 11.0f)
        verticalLineToRelative(5.0f)
        lineToRelative(-1.29f, 1.29f)
        curveToRelative(-0.63f, 0.63f, -0.19f, 1.71f, 0.7f, 1.71f)
        horizontalLineToRelative(13.17f)
        curveToRelative(0.9f, 0.0f, 1.34f, -1.08f, 0.71f, -1.71f)
        close()
        moveTo(16.0f, 17.0f)
        lineTo(8.0f, 17.0f)
        verticalLineToRelative(-6.0f)
        curveToRelative(0.0f, -2.48f, 1.51f, -4.5f, 4.0f, -4.5f)
        reflectiveCurveToRelative(4.0f, 2.02f, 4.0f, 4.5f)
        verticalLineToRelative(6.0f)
        close()
        moveTo(12.0f, 22.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        horizontalLineToRelative(-4.0f)
        curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
        close()
    }
}

val Icons.Rounded.NotificationsNone: ImageVector
    get() = notificationsNoneIcon

private val openInFullIcon by gokeIcon("Rounded.OpenInFull") {
    solidPath {
        moveTo(21.0f, 8.59f)
        verticalLineTo(4.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineToRelative(-4.59f)
        curveToRelative(-0.89f, 0.0f, -1.34f, 1.08f, -0.71f, 1.71f)
        lineToRelative(1.59f, 1.59f)
        lineToRelative(-10.0f, 10.0f)
        lineToRelative(-1.59f, -1.59f)
        curveTo(4.08f, 14.08f, 3.0f, 14.52f, 3.0f, 15.41f)
        verticalLineTo(20.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(4.59f)
        curveToRelative(0.89f, 0.0f, 1.34f, -1.08f, 0.71f, -1.71f)
        lineToRelative(-1.59f, -1.59f)
        lineToRelative(10.0f, -10.0f)
        lineToRelative(1.59f, 1.59f)
        curveTo(19.92f, 9.92f, 21.0f, 9.48f, 21.0f, 8.59f)
        close()
    }
}

val Icons.Rounded.OpenInFull: ImageVector
    get() = openInFullIcon

private val paletteIcon by gokeIcon("Rounded.Palette") {
    solidPath {
        moveTo(12.0f, 2.0f)
        curveTo(6.49f, 2.0f, 2.0f, 6.49f, 2.0f, 12.0f)
        reflectiveCurveToRelative(4.49f, 10.0f, 10.0f, 10.0f)
        curveToRelative(1.38f, 0.0f, 2.5f, -1.12f, 2.5f, -2.5f)
        curveToRelative(0.0f, -0.61f, -0.23f, -1.2f, -0.64f, -1.67f)
        curveToRelative(-0.08f, -0.1f, -0.13f, -0.21f, -0.13f, -0.33f)
        curveToRelative(0.0f, -0.28f, 0.22f, -0.5f, 0.5f, -0.5f)
        horizontalLineTo(16.0f)
        curveToRelative(3.31f, 0.0f, 6.0f, -2.69f, 6.0f, -6.0f)
        curveTo(22.0f, 6.04f, 17.51f, 2.0f, 12.0f, 2.0f)
        close()
        moveTo(17.5f, 13.0f)
        curveToRelative(-0.83f, 0.0f, -1.5f, -0.67f, -1.5f, -1.5f)
        curveToRelative(0.0f, -0.83f, 0.67f, -1.5f, 1.5f, -1.5f)
        reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
        curveTo(19.0f, 12.33f, 18.33f, 13.0f, 17.5f, 13.0f)
        close()
        moveTo(14.5f, 9.0f)
        curveTo(13.67f, 9.0f, 13.0f, 8.33f, 13.0f, 7.5f)
        curveTo(13.0f, 6.67f, 13.67f, 6.0f, 14.5f, 6.0f)
        reflectiveCurveTo(16.0f, 6.67f, 16.0f, 7.5f)
        curveTo(16.0f, 8.33f, 15.33f, 9.0f, 14.5f, 9.0f)
        close()
        moveTo(5.0f, 11.5f)
        curveTo(5.0f, 10.67f, 5.67f, 10.0f, 6.5f, 10.0f)
        reflectiveCurveTo(8.0f, 10.67f, 8.0f, 11.5f)
        curveTo(8.0f, 12.33f, 7.33f, 13.0f, 6.5f, 13.0f)
        reflectiveCurveTo(5.0f, 12.33f, 5.0f, 11.5f)
        close()
        moveTo(11.0f, 7.5f)
        curveTo(11.0f, 8.33f, 10.33f, 9.0f, 9.5f, 9.0f)
        reflectiveCurveTo(8.0f, 8.33f, 8.0f, 7.5f)
        curveTo(8.0f, 6.67f, 8.67f, 6.0f, 9.5f, 6.0f)
        reflectiveCurveTo(11.0f, 6.67f, 11.0f, 7.5f)
        close()
    }
}

val Icons.Rounded.Palette: ImageVector
    get() = paletteIcon

private val radioButtonUncheckedIcon by gokeIcon("Rounded.RadioButtonUnchecked") {
    solidPath {
        moveTo(12.0f, 2.0f)
        curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
        reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
        reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
        reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
        close()
        moveTo(12.0f, 20.0f)
        curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
        reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
        reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
        reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
        close()
    }
}

val Icons.Rounded.RadioButtonUnchecked: ImageVector
    get() = radioButtonUncheckedIcon

private val removeIcon by gokeIcon("Rounded.Remove") {
    solidPath {
        moveTo(18.0f, 13.0f)
        horizontalLineTo(6.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
        horizontalLineToRelative(12.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
        reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
        close()
    }
}

val Icons.Rounded.Remove: ImageVector
    get() = removeIcon

private val repeatIcon by gokeIcon("Rounded.Repeat") {
    solidPath {
        moveTo(7.0f, 7.0f)
        horizontalLineToRelative(10.0f)
        verticalLineToRelative(1.79f)
        curveToRelative(0.0f, 0.45f, 0.54f, 0.67f, 0.85f, 0.35f)
        lineToRelative(2.79f, -2.79f)
        curveToRelative(0.2f, -0.2f, 0.2f, -0.51f, 0.0f, -0.71f)
        lineToRelative(-2.79f, -2.79f)
        curveToRelative(-0.31f, -0.31f, -0.85f, -0.09f, -0.85f, 0.36f)
        lineTo(17.0f, 5.0f)
        lineTo(6.0f, 5.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(4.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
        lineTo(7.0f, 7.0f)
        close()
        moveTo(17.0f, 17.0f)
        lineTo(7.0f, 17.0f)
        verticalLineToRelative(-1.79f)
        curveToRelative(0.0f, -0.45f, -0.54f, -0.67f, -0.85f, -0.35f)
        lineToRelative(-2.79f, 2.79f)
        curveToRelative(-0.2f, 0.2f, -0.2f, 0.51f, 0.0f, 0.71f)
        lineToRelative(2.79f, 2.79f)
        curveToRelative(0.31f, 0.31f, 0.85f, 0.09f, 0.85f, -0.36f)
        lineTo(7.0f, 19.0f)
        horizontalLineToRelative(11.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineToRelative(-4.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(3.0f)
        close()
    }
}

val Icons.Rounded.Repeat: ImageVector
    get() = repeatIcon

private val restartAltIcon by gokeIcon("Rounded.RestartAlt") {
    solidPath {
        moveTo(12.0f, 5.0f)
        verticalLineTo(3.21f)
        curveToRelative(0.0f, -0.45f, -0.54f, -0.67f, -0.85f, -0.35f)
        lineTo(8.35f, 5.65f)
        curveToRelative(-0.2f, 0.2f, -0.2f, 0.51f, 0.0f, 0.71f)
        lineToRelative(2.79f, 2.79f)
        curveTo(11.46f, 9.46f, 12.0f, 9.24f, 12.0f, 8.79f)
        verticalLineTo(7.0f)
        curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
        curveToRelative(0.0f, 2.72f, -1.83f, 5.02f, -4.31f, 5.75f)
        curveTo(13.27f, 18.87f, 13.0f, 19.27f, 13.0f, 19.7f)
        verticalLineToRelative(0.0f)
        curveToRelative(0.0f, 0.65f, 0.62f, 1.16f, 1.25f, 0.97f)
        curveTo(17.57f, 19.7f, 20.0f, 16.64f, 20.0f, 13.0f)
        curveTo(20.0f, 8.58f, 16.42f, 5.0f, 12.0f, 5.0f)
        close()
    }
    solidPath {
        moveTo(6.0f, 13.0f)
        curveToRelative(0.0f, -1.34f, 0.44f, -2.58f, 1.19f, -3.59f)
        curveToRelative(0.3f, -0.4f, 0.26f, -0.95f, -0.09f, -1.31f)
        lineToRelative(0.0f, 0.0f)
        curveTo(6.68f, 7.68f, 5.96f, 7.72f, 5.6f, 8.2f)
        curveTo(4.6f, 9.54f, 4.0f, 11.2f, 4.0f, 13.0f)
        curveToRelative(0.0f, 3.64f, 2.43f, 6.7f, 5.75f, 7.67f)
        curveTo(10.38f, 20.86f, 11.0f, 20.35f, 11.0f, 19.7f)
        verticalLineToRelative(0.0f)
        curveToRelative(0.0f, -0.43f, -0.27f, -0.83f, -0.69f, -0.95f)
        curveTo(7.83f, 18.02f, 6.0f, 15.72f, 6.0f, 13.0f)
        close()
    }
}

val Icons.Rounded.RestartAlt: ImageVector
    get() = restartAltIcon

private val restoreIcon by gokeIcon("Rounded.Restore") {
    solidPath {
        moveTo(13.25f, 3.0f)
        curveToRelative(-5.09f, -0.14f, -9.26f, 3.94f, -9.26f, 9.0f)
        lineTo(2.2f, 12.0f)
        curveToRelative(-0.45f, 0.0f, -0.67f, 0.54f, -0.35f, 0.85f)
        lineToRelative(2.79f, 2.8f)
        curveToRelative(0.2f, 0.2f, 0.51f, 0.2f, 0.71f, 0.0f)
        lineToRelative(2.79f, -2.8f)
        curveToRelative(0.32f, -0.31f, 0.09f, -0.85f, -0.35f, -0.85f)
        horizontalLineToRelative(-1.8f)
        curveToRelative(0.0f, -3.9f, 3.18f, -7.05f, 7.1f, -7.0f)
        curveToRelative(3.72f, 0.05f, 6.85f, 3.18f, 6.9f, 6.9f)
        curveToRelative(0.05f, 3.91f, -3.1f, 7.1f, -7.0f, 7.1f)
        curveToRelative(-1.61f, 0.0f, -3.1f, -0.55f, -4.28f, -1.48f)
        curveToRelative(-0.4f, -0.31f, -0.96f, -0.28f, -1.32f, 0.08f)
        curveToRelative(-0.42f, 0.43f, -0.39f, 1.13f, 0.08f, 1.5f)
        curveToRelative(1.52f, 1.19f, 3.44f, 1.9f, 5.52f, 1.9f)
        curveToRelative(5.05f, 0.0f, 9.14f, -4.17f, 9.0f, -9.26f)
        curveToRelative(-0.13f, -4.69f, -4.05f, -8.61f, -8.74f, -8.74f)
        close()
        moveTo(12.74f, 8.0f)
        curveToRelative(-0.41f, 0.0f, -0.75f, 0.34f, -0.75f, 0.75f)
        verticalLineToRelative(3.68f)
        curveToRelative(0.0f, 0.35f, 0.19f, 0.68f, 0.49f, 0.86f)
        lineToRelative(3.12f, 1.85f)
        curveToRelative(0.36f, 0.21f, 0.82f, 0.09f, 1.03f, -0.26f)
        curveToRelative(0.21f, -0.36f, 0.09f, -0.82f, -0.26f, -1.03f)
        lineToRelative(-2.88f, -1.71f)
        verticalLineToRelative(-3.4f)
        curveToRelative(0.0f, -0.4f, -0.33f, -0.74f, -0.75f, -0.74f)
        close()
    }
}

val Icons.Rounded.Restore: ImageVector
    get() = restoreIcon

private val scheduleIcon by gokeIcon("Rounded.Schedule") {
    solidPath {
        moveTo(11.99f, 2.0f)
        curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
        reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
        curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
        reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
        close()
        moveTo(12.0f, 20.0f)
        curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
        reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
        reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
        reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
        close()
        moveTo(11.78f, 7.0f)
        horizontalLineToRelative(-0.06f)
        curveToRelative(-0.4f, 0.0f, -0.72f, 0.32f, -0.72f, 0.72f)
        verticalLineToRelative(4.72f)
        curveToRelative(0.0f, 0.35f, 0.18f, 0.68f, 0.49f, 0.86f)
        lineToRelative(4.15f, 2.49f)
        curveToRelative(0.34f, 0.2f, 0.78f, 0.1f, 0.98f, -0.24f)
        curveToRelative(0.21f, -0.34f, 0.1f, -0.79f, -0.25f, -0.99f)
        lineToRelative(-3.87f, -2.3f)
        lineTo(12.5f, 7.72f)
        curveToRelative(0.0f, -0.4f, -0.32f, -0.72f, -0.72f, -0.72f)
        close()
    }
}

val Icons.Rounded.Schedule: ImageVector
    get() = scheduleIcon

private val schoolIcon by gokeIcon("Rounded.School") {
    solidPath {
        moveTo(5.0f, 13.18f)
        verticalLineToRelative(2.81f)
        curveToRelative(0.0f, 0.73f, 0.4f, 1.41f, 1.04f, 1.76f)
        lineToRelative(5.0f, 2.73f)
        curveToRelative(0.6f, 0.33f, 1.32f, 0.33f, 1.92f, 0.0f)
        lineToRelative(5.0f, -2.73f)
        curveToRelative(0.64f, -0.35f, 1.04f, -1.03f, 1.04f, -1.76f)
        verticalLineToRelative(-2.81f)
        lineToRelative(-6.04f, 3.3f)
        curveToRelative(-0.6f, 0.33f, -1.32f, 0.33f, -1.92f, 0.0f)
        lineTo(5.0f, 13.18f)
        close()
        moveTo(11.04f, 3.52f)
        lineToRelative(-8.43f, 4.6f)
        curveToRelative(-0.69f, 0.38f, -0.69f, 1.38f, 0.0f, 1.76f)
        lineToRelative(8.43f, 4.6f)
        curveToRelative(0.6f, 0.33f, 1.32f, 0.33f, 1.92f, 0.0f)
        lineTo(21.0f, 10.09f)
        lineTo(21.0f, 16.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
        lineTo(23.0f, 9.59f)
        curveToRelative(0.0f, -0.37f, -0.2f, -0.7f, -0.52f, -0.88f)
        lineToRelative(-9.52f, -5.19f)
        curveToRelative(-0.6f, -0.32f, -1.32f, -0.32f, -1.92f, 0.0f)
        close()
    }
}

val Icons.Rounded.School: ImageVector
    get() = schoolIcon

private val swapHorizIcon by gokeIcon("Rounded.SwapHoriz") {
    solidPath {
        moveTo(6.14f, 11.86f)
        lineToRelative(-2.78f, 2.79f)
        curveToRelative(-0.19f, 0.2f, -0.19f, 0.51f, 0.0f, 0.71f)
        lineToRelative(2.78f, 2.79f)
        curveToRelative(0.31f, 0.32f, 0.85f, 0.09f, 0.85f, -0.35f)
        lineTo(6.99f, 16.0f)
        lineTo(13.0f, 16.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
        lineTo(6.99f, 14.0f)
        verticalLineToRelative(-1.79f)
        curveToRelative(0.0f, -0.45f, -0.54f, -0.67f, -0.85f, -0.35f)
        close()
        moveTo(20.65f, 8.65f)
        lineToRelative(-2.78f, -2.79f)
        curveToRelative(-0.31f, -0.32f, -0.85f, -0.09f, -0.85f, 0.35f)
        lineTo(17.02f, 8.0f)
        lineTo(11.0f, 8.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(6.01f)
        verticalLineToRelative(1.79f)
        curveToRelative(0.0f, 0.45f, 0.54f, 0.67f, 0.85f, 0.35f)
        lineToRelative(2.78f, -2.79f)
        curveToRelative(0.2f, -0.19f, 0.2f, -0.51f, 0.01f, -0.7f)
        close()
    }
}

val Icons.Rounded.SwapHoriz: ImageVector
    get() = swapHorizIcon

private val swapVertIcon by gokeIcon("Rounded.SwapVert") {
    solidPath {
        moveTo(16.0f, 17.01f)
        verticalLineTo(11.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(6.01f)
        horizontalLineToRelative(-1.79f)
        curveToRelative(-0.45f, 0.0f, -0.67f, 0.54f, -0.35f, 0.85f)
        lineToRelative(2.79f, 2.78f)
        curveToRelative(0.2f, 0.19f, 0.51f, 0.19f, 0.71f, 0.0f)
        lineToRelative(2.79f, -2.78f)
        curveToRelative(0.32f, -0.31f, 0.09f, -0.85f, -0.35f, -0.85f)
        horizontalLineTo(16.0f)
        close()
        moveTo(8.65f, 3.35f)
        lineTo(5.86f, 6.14f)
        curveToRelative(-0.32f, 0.31f, -0.1f, 0.85f, 0.35f, 0.85f)
        horizontalLineTo(8.0f)
        verticalLineTo(13.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineTo(6.99f)
        horizontalLineToRelative(1.79f)
        curveToRelative(0.45f, 0.0f, 0.67f, -0.54f, 0.35f, -0.85f)
        lineTo(9.35f, 3.35f)
        curveToRelative(-0.19f, -0.19f, -0.51f, -0.19f, -0.7f, 0.0f)
        close()
    }
}

val Icons.Rounded.SwapVert: ImageVector
    get() = swapVertIcon

private val syncIcon by gokeIcon("Rounded.Sync") {
    solidPath {
        moveTo(12.0f, 4.0f)
        lineTo(12.0f, 2.21f)
        curveToRelative(0.0f, -0.45f, -0.54f, -0.67f, -0.85f, -0.35f)
        lineToRelative(-2.8f, 2.79f)
        curveToRelative(-0.2f, 0.2f, -0.2f, 0.51f, 0.0f, 0.71f)
        lineToRelative(2.79f, 2.79f)
        curveToRelative(0.32f, 0.31f, 0.86f, 0.09f, 0.86f, -0.36f)
        lineTo(12.0f, 6.0f)
        curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
        curveToRelative(0.0f, 0.79f, -0.15f, 1.56f, -0.44f, 2.25f)
        curveToRelative(-0.15f, 0.36f, -0.04f, 0.77f, 0.23f, 1.04f)
        curveToRelative(0.51f, 0.51f, 1.37f, 0.33f, 1.64f, -0.34f)
        curveToRelative(0.37f, -0.91f, 0.57f, -1.91f, 0.57f, -2.95f)
        curveToRelative(0.0f, -4.42f, -3.58f, -8.0f, -8.0f, -8.0f)
        close()
        moveTo(12.0f, 18.0f)
        curveToRelative(-3.31f, 0.0f, -6.0f, -2.69f, -6.0f, -6.0f)
        curveToRelative(0.0f, -0.79f, 0.15f, -1.56f, 0.44f, -2.25f)
        curveToRelative(0.15f, -0.36f, 0.04f, -0.77f, -0.23f, -1.04f)
        curveToRelative(-0.51f, -0.51f, -1.37f, -0.33f, -1.64f, 0.34f)
        curveTo(4.2f, 9.96f, 4.0f, 10.96f, 4.0f, 12.0f)
        curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
        verticalLineToRelative(1.79f)
        curveToRelative(0.0f, 0.45f, 0.54f, 0.67f, 0.85f, 0.35f)
        lineToRelative(2.79f, -2.79f)
        curveToRelative(0.2f, -0.2f, 0.2f, -0.51f, 0.0f, -0.71f)
        lineToRelative(-2.79f, -2.79f)
        curveToRelative(-0.31f, -0.31f, -0.85f, -0.09f, -0.85f, 0.36f)
        lineTo(12.0f, 18.0f)
        close()
    }
}

val Icons.Rounded.Sync: ImageVector
    get() = syncIcon

private val terminalIcon by gokeIcon("Rounded.Terminal") {
    solidPath {
        moveTo(20.0f, 4.0f)
        horizontalLineTo(4.0f)
        curveTo(2.89f, 4.0f, 2.0f, 4.9f, 2.0f, 6.0f)
        verticalLineToRelative(12.0f)
        curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(16.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(6.0f)
        curveTo(22.0f, 4.9f, 21.11f, 4.0f, 20.0f, 4.0f)
        close()
        moveTo(20.0f, 18.0f)
        horizontalLineTo(4.0f)
        verticalLineTo(8.0f)
        horizontalLineToRelative(16.0f)
        verticalLineTo(18.0f)
        close()
        moveTo(12.0f, 16.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        horizontalLineToRelative(4.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
        curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
        horizontalLineToRelative(-4.0f)
        curveTo(12.45f, 17.0f, 12.0f, 16.55f, 12.0f, 16.0f)
        close()
        moveTo(6.79f, 9.71f)
        curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0.0f)
        lineToRelative(2.59f, 2.59f)
        curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0.0f, 1.41f)
        lineToRelative(-2.59f, 2.59f)
        curveToRelative(-0.39f, 0.39f, -1.02f, 0.39f, -1.41f, 0.0f)
        curveToRelative(-0.39f, -0.39f, -0.39f, -1.02f, 0.0f, -1.41f)
        lineTo(8.67f, 13.0f)
        lineToRelative(-1.88f, -1.88f)
        curveTo(6.4f, 10.73f, 6.4f, 10.1f, 6.79f, 9.71f)
        close()
    }
}

val Icons.Rounded.Terminal: ImageVector
    get() = terminalIcon

private val timerIcon by gokeIcon("Rounded.Timer") {
    solidPath {
        moveTo(10.0f, 3.0f)
        horizontalLineToRelative(4.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineToRelative(-4.0f)
        curveTo(9.45f, 1.0f, 9.0f, 1.45f, 9.0f, 2.0f)
        curveTo(9.0f, 2.55f, 9.45f, 3.0f, 10.0f, 3.0f)
        close()
    }
    solidPath {
        moveTo(19.03f, 7.39f)
        lineToRelative(0.75f, -0.75f)
        curveToRelative(0.38f, -0.38f, 0.39f, -1.01f, 0.0f, -1.4f)
        curveToRelative(0.0f, 0.0f, -0.01f, -0.01f, -0.01f, -0.01f)
        curveToRelative(-0.39f, -0.39f, -1.01f, -0.38f, -1.4f, 0.0f)
        lineToRelative(-0.75f, 0.75f)
        curveTo(16.07f, 4.74f, 14.12f, 4.0f, 12.0f, 4.0f)
        curveToRelative(-4.8f, 0.0f, -8.88f, 3.96f, -9.0f, 8.76f)
        curveTo(2.87f, 17.84f, 6.94f, 22.0f, 12.0f, 22.0f)
        curveToRelative(4.98f, 0.0f, 9.0f, -4.03f, 9.0f, -9.0f)
        curveTo(21.0f, 10.88f, 20.26f, 8.93f, 19.03f, 7.39f)
        close()
        moveTo(13.0f, 13.0f)
        curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
        reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
        verticalLineTo(9.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
        verticalLineTo(13.0f)
        close()
    }
}

val Icons.Rounded.Timer: ImageVector
    get() = timerIcon

private val todayIcon by gokeIcon("Rounded.Today") {
    solidPath {
        moveTo(19.0f, 3.0f)
        horizontalLineToRelative(-1.0f)
        lineTo(18.0f, 2.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(1.0f)
        lineTo(8.0f, 3.0f)
        lineTo(8.0f, 2.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(1.0f)
        lineTo(5.0f, 3.0f)
        curveToRelative(-1.11f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
        lineTo(3.0f, 19.0f)
        curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(14.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        lineTo(21.0f, 5.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(18.0f, 19.0f)
        lineTo(6.0f, 19.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        lineTo(5.0f, 8.0f)
        horizontalLineToRelative(14.0f)
        verticalLineToRelative(10.0f)
        curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
        close()
        moveTo(8.0f, 10.0f)
        horizontalLineToRelative(3.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
        verticalLineToRelative(3.0f)
        curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
        lineTo(8.0f, 15.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        verticalLineToRelative(-3.0f)
        curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
        close()
    }
}

val Icons.Rounded.Today: ImageVector
    get() = todayIcon

private val tuneIcon by gokeIcon("Rounded.Tune") {
    solidPath {
        moveTo(3.0f, 18.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(5.0f)
        verticalLineToRelative(-2.0f)
        lineTo(4.0f, 17.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        close()
        moveTo(3.0f, 6.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(9.0f)
        lineTo(13.0f, 5.0f)
        lineTo(4.0f, 5.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        close()
        moveTo(13.0f, 20.0f)
        verticalLineToRelative(-1.0f)
        horizontalLineToRelative(7.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineToRelative(-7.0f)
        verticalLineToRelative(-1.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(4.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
        close()
        moveTo(7.0f, 10.0f)
        verticalLineToRelative(1.0f)
        lineTo(4.0f, 11.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(3.0f)
        verticalLineToRelative(1.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineToRelative(-4.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
        close()
        moveTo(21.0f, 12.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineToRelative(-9.0f)
        verticalLineToRelative(2.0f)
        horizontalLineToRelative(9.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        close()
        moveTo(16.0f, 9.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        lineTo(17.0f, 7.0f)
        horizontalLineToRelative(3.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineToRelative(-3.0f)
        lineTo(17.0f, 4.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(4.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        close()
    }
}

val Icons.Rounded.Tune: ImageVector
    get() = tuneIcon

private val upcomingIcon by gokeIcon("Rounded.Upcoming") {
    solidPath {
        moveTo(20.45f, 6.55f)
        lineTo(20.45f, 6.55f)
        curveToRelative(-0.38f, -0.38f, -1.01f, -0.38f, -1.39f, 0.0f)
        lineTo(16.89f, 8.7f)
        curveToRelative(-0.39f, 0.38f, -0.39f, 1.01f, 0.0f, 1.39f)
        lineToRelative(0.01f, 0.01f)
        curveToRelative(0.39f, 0.39f, 1.01f, 0.39f, 1.4f, 0.0f)
        curveToRelative(0.62f, -0.63f, 1.52f, -1.54f, 2.15f, -2.17f)
        curveTo(20.83f, 7.55f, 20.83f, 6.93f, 20.45f, 6.55f)
        close()
    }
    solidPath {
        moveTo(12.02f, 3.0f)
        horizontalLineToRelative(-0.03f)
        curveTo(11.44f, 3.0f, 11.0f, 3.44f, 11.0f, 3.98f)
        verticalLineToRelative(3.03f)
        curveTo(11.0f, 7.56f, 11.44f, 8.0f, 11.98f, 8.0f)
        horizontalLineToRelative(0.03f)
        curveTo(12.56f, 8.0f, 13.0f, 7.56f, 13.0f, 7.02f)
        verticalLineTo(3.98f)
        curveTo(13.0f, 3.44f, 12.56f, 3.0f, 12.02f, 3.0f)
        close()
    }
    solidPath {
        moveTo(7.1f, 10.11f)
        lineToRelative(0.01f, -0.01f)
        curveToRelative(0.38f, -0.38f, 0.38f, -1.01f, 0.0f, -1.39f)
        lineTo(4.96f, 6.54f)
        curveToRelative(-0.38f, -0.39f, -1.01f, -0.39f, -1.39f, 0.0f)
        lineTo(3.55f, 6.55f)
        curveToRelative(-0.39f, 0.39f, -0.39f, 1.01f, 0.0f, 1.39f)
        curveToRelative(0.63f, 0.62f, 1.53f, 1.54f, 2.15f, 2.17f)
        curveTo(6.09f, 10.49f, 6.72f, 10.49f, 7.1f, 10.11f)
        close()
    }
    solidPath {
        moveTo(12.0f, 15.0f)
        curveToRelative(-1.24f, 0.0f, -2.31f, -0.75f, -2.76f, -1.83f)
        curveTo(8.92f, 12.43f, 8.14f, 12.0f, 7.34f, 12.0f)
        lineTo(4.0f, 12.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        lineToRelative(0.0f, 5.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(16.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineToRelative(-5.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        lineToRelative(-3.34f, 0.0f)
        curveToRelative(-0.8f, 0.0f, -1.58f, 0.43f, -1.9f, 1.17f)
        curveTo(14.31f, 14.25f, 13.24f, 15.0f, 12.0f, 15.0f)
    }
}

val Icons.Rounded.Upcoming: ImageVector
    get() = upcomingIcon

private val uploadFileIcon by gokeIcon("Rounded.UploadFile") {
    solidPath {
        moveTo(19.41f, 7.41f)
        lineToRelative(-4.83f, -4.83f)
        curveTo(14.21f, 2.21f, 13.7f, 2.0f, 13.17f, 2.0f)
        horizontalLineTo(6.0f)
        curveTo(4.9f, 2.0f, 4.01f, 2.9f, 4.01f, 4.0f)
        lineTo(4.0f, 20.0f)
        curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 1.99f, 2.0f)
        horizontalLineTo(18.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(8.83f)
        curveTo(20.0f, 8.3f, 19.79f, 7.79f, 19.41f, 7.41f)
        close()
        moveTo(14.8f, 15.0f)
        horizontalLineTo(13.0f)
        verticalLineToRelative(3.0f)
        curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
        reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
        verticalLineToRelative(-3.0f)
        horizontalLineTo(9.21f)
        curveToRelative(-0.45f, 0.0f, -0.67f, -0.54f, -0.35f, -0.85f)
        lineToRelative(2.8f, -2.79f)
        curveToRelative(0.2f, -0.19f, 0.51f, -0.19f, 0.71f, 0.0f)
        lineToRelative(2.79f, 2.79f)
        curveTo(15.46f, 14.46f, 15.24f, 15.0f, 14.8f, 15.0f)
        close()
        moveTo(14.0f, 9.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
        verticalLineTo(3.5f)
        lineTo(18.5f, 9.0f)
        horizontalLineTo(14.0f)
        close()
    }
}

val Icons.Rounded.UploadFile: ImageVector
    get() = uploadFileIcon

private val visibilityIcon by gokeIcon("Rounded.Visibility") {
    solidPath {
        moveTo(12.0f, 4.0f)
        curveTo(7.0f, 4.0f, 2.73f, 7.11f, 1.0f, 11.5f)
        curveTo(2.73f, 15.89f, 7.0f, 19.0f, 12.0f, 19.0f)
        reflectiveCurveToRelative(9.27f, -3.11f, 11.0f, -7.5f)
        curveTo(21.27f, 7.11f, 17.0f, 4.0f, 12.0f, 4.0f)
        close()
        moveTo(12.0f, 16.5f)
        curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
        reflectiveCurveToRelative(2.24f, -5.0f, 5.0f, -5.0f)
        reflectiveCurveToRelative(5.0f, 2.24f, 5.0f, 5.0f)
        reflectiveCurveToRelative(-2.24f, 5.0f, -5.0f, 5.0f)
        close()
        moveTo(12.0f, 8.5f)
        curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
        reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
        reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
        reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
        close()
    }
}

val Icons.Rounded.Visibility: ImageVector
    get() = visibilityIcon

private val visibilityOffIcon by gokeIcon("Rounded.VisibilityOff") {
    solidPath {
        moveTo(12.0f, 6.5f)
        curveToRelative(2.76f, 0.0f, 5.0f, 2.24f, 5.0f, 5.0f)
        curveToRelative(0.0f, 0.51f, -0.1f, 1.0f, -0.24f, 1.46f)
        lineToRelative(3.06f, 3.06f)
        curveToRelative(1.39f, -1.23f, 2.49f, -2.77f, 3.18f, -4.53f)
        curveTo(21.27f, 7.11f, 17.0f, 4.0f, 12.0f, 4.0f)
        curveToRelative(-1.27f, 0.0f, -2.49f, 0.2f, -3.64f, 0.57f)
        lineToRelative(2.17f, 2.17f)
        curveToRelative(0.47f, -0.14f, 0.96f, -0.24f, 1.47f, -0.24f)
        close()
        moveTo(2.71f, 3.16f)
        curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
        lineToRelative(1.97f, 1.97f)
        curveTo(3.06f, 7.83f, 1.77f, 9.53f, 1.0f, 11.5f)
        curveTo(2.73f, 15.89f, 7.0f, 19.0f, 12.0f, 19.0f)
        curveToRelative(1.52f, 0.0f, 2.97f, -0.3f, 4.31f, -0.82f)
        lineToRelative(2.72f, 2.72f)
        curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
        curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
        lineTo(4.13f, 3.16f)
        curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.42f, 0.0f)
        close()
        moveTo(12.0f, 16.5f)
        curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
        curveToRelative(0.0f, -0.77f, 0.18f, -1.5f, 0.49f, -2.14f)
        lineToRelative(1.57f, 1.57f)
        curveToRelative(-0.03f, 0.18f, -0.06f, 0.37f, -0.06f, 0.57f)
        curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
        curveToRelative(0.2f, 0.0f, 0.38f, -0.03f, 0.57f, -0.07f)
        lineTo(14.14f, 16.0f)
        curveToRelative(-0.65f, 0.32f, -1.37f, 0.5f, -2.14f, 0.5f)
        close()
        moveTo(14.97f, 11.17f)
        curveToRelative(-0.15f, -1.4f, -1.25f, -2.49f, -2.64f, -2.64f)
        lineToRelative(2.64f, 2.64f)
        close()
    }
}

val Icons.Rounded.VisibilityOff: ImageVector
    get() = visibilityOffIcon

private val wbSunnyIcon by gokeIcon("Rounded.WbSunny") {
    solidPath {
        moveTo(6.05f, 4.14f)
        lineToRelative(-0.39f, -0.39f)
        curveToRelative(-0.39f, -0.39f, -1.02f, -0.38f, -1.4f, 0.0f)
        lineToRelative(-0.01f, 0.01f)
        curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.4f)
        lineToRelative(0.39f, 0.39f)
        curveToRelative(0.39f, 0.39f, 1.01f, 0.39f, 1.4f, 0.0f)
        lineToRelative(0.01f, -0.01f)
        curveToRelative(0.39f, -0.38f, 0.39f, -1.02f, 0.0f, -1.4f)
        close()
        moveTo(3.01f, 10.5f)
        lineTo(1.99f, 10.5f)
        curveToRelative(-0.55f, 0.0f, -0.99f, 0.44f, -0.99f, 0.99f)
        verticalLineToRelative(0.01f)
        curveToRelative(0.0f, 0.55f, 0.44f, 0.99f, 0.99f, 0.99f)
        lineTo(3.0f, 12.49f)
        curveToRelative(0.56f, 0.01f, 1.0f, -0.43f, 1.0f, -0.98f)
        verticalLineToRelative(-0.01f)
        curveToRelative(0.0f, -0.56f, -0.44f, -1.0f, -0.99f, -1.0f)
        close()
        moveTo(12.01f, 0.55f)
        lineTo(12.0f, 0.55f)
        curveToRelative(-0.56f, 0.0f, -1.0f, 0.44f, -1.0f, 0.99f)
        verticalLineToRelative(0.96f)
        curveToRelative(0.0f, 0.55f, 0.44f, 0.99f, 0.99f, 0.99f)
        lineTo(12.0f, 3.49f)
        curveToRelative(0.56f, 0.01f, 1.0f, -0.43f, 1.0f, -0.98f)
        verticalLineToRelative(-0.97f)
        curveToRelative(0.0f, -0.55f, -0.44f, -0.99f, -0.99f, -0.99f)
        close()
        moveTo(19.75f, 3.76f)
        curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, -0.01f)
        lineToRelative(-0.39f, 0.39f)
        curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.4f)
        lineToRelative(0.01f, 0.01f)
        curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.4f, 0.0f)
        lineToRelative(0.39f, -0.39f)
        curveToRelative(0.39f, -0.39f, 0.39f, -1.01f, 0.0f, -1.4f)
        close()
        moveTo(17.94f, 18.86f)
        lineToRelative(0.39f, 0.39f)
        curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
        curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
        lineToRelative(-0.39f, -0.39f)
        curveToRelative(-0.39f, -0.39f, -1.02f, -0.38f, -1.4f, 0.0f)
        curveToRelative(-0.4f, 0.4f, -0.4f, 1.02f, -0.01f, 1.41f)
        close()
        moveTo(20.0f, 11.49f)
        verticalLineToRelative(0.01f)
        curveToRelative(0.0f, 0.55f, 0.44f, 0.99f, 0.99f, 0.99f)
        lineTo(22.0f, 12.49f)
        curveToRelative(0.55f, 0.0f, 0.99f, -0.44f, 0.99f, -0.99f)
        verticalLineToRelative(-0.01f)
        curveToRelative(0.0f, -0.55f, -0.44f, -0.99f, -0.99f, -0.99f)
        horizontalLineToRelative(-1.01f)
        curveToRelative(-0.55f, 0.0f, -0.99f, 0.44f, -0.99f, 0.99f)
        close()
        moveTo(12.0f, 5.5f)
        curveToRelative(-3.31f, 0.0f, -6.0f, 2.69f, -6.0f, 6.0f)
        reflectiveCurveToRelative(2.69f, 6.0f, 6.0f, 6.0f)
        reflectiveCurveToRelative(6.0f, -2.69f, 6.0f, -6.0f)
        reflectiveCurveToRelative(-2.69f, -6.0f, -6.0f, -6.0f)
        close()
        moveTo(11.99f, 22.45f)
        lineTo(12.0f, 22.45f)
        curveToRelative(0.55f, 0.0f, 0.99f, -0.44f, 0.99f, -0.99f)
        verticalLineToRelative(-0.96f)
        curveToRelative(0.0f, -0.55f, -0.44f, -0.99f, -0.99f, -0.99f)
        horizontalLineToRelative(-0.01f)
        curveToRelative(-0.55f, 0.0f, -0.99f, 0.44f, -0.99f, 0.99f)
        verticalLineToRelative(0.96f)
        curveToRelative(0.0f, 0.55f, 0.44f, 0.99f, 0.99f, 0.99f)
        close()
        moveTo(4.25f, 19.24f)
        curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
        lineToRelative(0.39f, -0.39f)
        curveToRelative(0.39f, -0.39f, 0.38f, -1.02f, 0.0f, -1.4f)
        lineToRelative(-0.01f, -0.01f)
        curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
        lineToRelative(-0.39f, 0.39f)
        curveToRelative(-0.38f, 0.4f, -0.38f, 1.02f, 0.01f, 1.41f)
        close()
    }
}

val Icons.Rounded.WbSunny: ImageVector
    get() = wbSunnyIcon

private val weekendIcon by gokeIcon("Rounded.Weekend") {
    solidPath {
        moveTo(21.0f, 10.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(3.0f)
        lineTo(5.0f, 15.0f)
        verticalLineToRelative(-3.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        reflectiveCurveToRelative(-2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(5.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(18.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineToRelative(-5.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(18.0f, 5.0f)
        lineTo(6.0f, 5.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(2.15f)
        curveToRelative(1.16f, 0.41f, 2.0f, 1.51f, 2.0f, 2.82f)
        lineTo(6.0f, 14.0f)
        horizontalLineToRelative(12.0f)
        verticalLineToRelative(-2.03f)
        curveToRelative(0.0f, -1.3f, 0.84f, -2.4f, 2.0f, -2.82f)
        lineTo(20.0f, 7.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        close()
    }
}

val Icons.Rounded.Weekend: ImageVector
    get() = weekendIcon

private val widgetsIcon by gokeIcon("Rounded.Widgets") {
    solidPath {
        moveTo(13.0f, 14.0f)
        verticalLineToRelative(6.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(6.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineToRelative(-6.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineToRelative(-6.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        close()
        moveTo(4.0f, 21.0f)
        horizontalLineToRelative(6.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineToRelative(-6.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        lineTo(4.0f, 13.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        verticalLineToRelative(6.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        close()
        moveTo(3.0f, 4.0f)
        verticalLineToRelative(6.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        horizontalLineToRelative(6.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        lineTo(11.0f, 4.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        lineTo(4.0f, 3.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        close()
        moveTo(15.95f, 2.4f)
        lineTo(11.7f, 6.64f)
        curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
        lineToRelative(4.25f, 4.25f)
        curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
        lineToRelative(4.25f, -4.25f)
        curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
        lineTo(17.37f, 2.4f)
        curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.42f, 0.0f)
        close()
    }
}

val Icons.Rounded.Widgets: ImageVector
    get() = widgetsIcon

private val wifiIcon by gokeIcon("Rounded.Wifi") {
    solidPath {
        moveTo(2.06f, 10.06f)
        curveToRelative(0.51f, 0.51f, 1.32f, 0.56f, 1.87f, 0.1f)
        curveToRelative(4.67f, -3.84f, 11.45f, -3.84f, 16.13f, -0.01f)
        curveToRelative(0.56f, 0.46f, 1.38f, 0.42f, 1.89f, -0.09f)
        curveToRelative(0.59f, -0.59f, 0.55f, -1.57f, -0.1f, -2.1f)
        curveToRelative(-5.71f, -4.67f, -13.97f, -4.67f, -19.69f, 0.0f)
        curveToRelative(-0.65f, 0.52f, -0.7f, 1.5f, -0.1f, 2.1f)
        close()
        moveTo(9.82f, 17.82f)
        lineToRelative(1.47f, 1.47f)
        curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
        lineToRelative(1.47f, -1.47f)
        curveToRelative(0.47f, -0.47f, 0.37f, -1.28f, -0.23f, -1.59f)
        curveToRelative(-1.22f, -0.63f, -2.68f, -0.63f, -3.91f, 0.0f)
        curveToRelative(-0.57f, 0.31f, -0.68f, 1.12f, -0.21f, 1.59f)
        close()
        moveTo(6.09f, 14.09f)
        curveToRelative(0.49f, 0.49f, 1.26f, 0.54f, 1.83f, 0.13f)
        curveToRelative(2.44f, -1.73f, 5.72f, -1.73f, 8.16f, 0.0f)
        curveToRelative(0.57f, 0.4f, 1.34f, 0.36f, 1.83f, -0.13f)
        lineToRelative(0.01f, -0.01f)
        curveToRelative(0.6f, -0.6f, 0.56f, -1.62f, -0.13f, -2.11f)
        curveToRelative(-3.44f, -2.49f, -8.13f, -2.49f, -11.58f, 0.0f)
        curveToRelative(-0.69f, 0.5f, -0.73f, 1.51f, -0.12f, 2.12f)
        close()
    }
}

val Icons.Rounded.Wifi: ImageVector
    get() = wifiIcon

private val notesIcon by gokeIcon("Rounded.Notes") {
    solidPath {
        moveTo(4.0f, 8.0f)
        horizontalLineTo(20.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineTo(4.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
        close()
        moveTo(4.0f, 13.0f)
        horizontalLineTo(20.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineTo(4.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
        close()
        moveTo(4.0f, 18.0f)
        horizontalLineTo(14.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineTo(4.0f)
        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
        reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
        close()
    }
}

val Icons.Rounded.Notes: ImageVector
    get() = notesIcon
