package app.goke.ui.screens

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.goke.R
import app.goke.ui.Route
import app.goke.ui.components.mintClickable
import app.goke.ui.icons.CalendarMonth
import app.goke.ui.icons.CalendarViewWeek
import app.goke.ui.icons.Checklist
import app.goke.ui.icons.Cloud
import app.goke.ui.icons.DeleteSweep
import app.goke.ui.icons.FitnessCenter
import app.goke.ui.icons.FolderOpen
import app.goke.ui.icons.Grade
import app.goke.ui.icons.History
import app.goke.ui.icons.LaptopMac
import app.goke.ui.icons.LocalShipping
import app.goke.ui.icons.MeetingRoom
import app.goke.ui.icons.NotificationsActive
import app.goke.ui.icons.Palette
import app.goke.ui.icons.Schedule
import app.goke.ui.icons.School
import app.goke.ui.icons.SwapVert
import app.goke.ui.icons.Terminal
import app.goke.ui.icons.Tune
import app.goke.ui.icons.WaterDrop
import app.goke.ui.icons.Widgets
import app.goke.ui.theme.MintTheme

private val SettingsSearchBarShape = RoundedCornerShape(14.dp)
private val WHITESPACE_REGEX = Regex("\\s+")

internal data class SettingsSearchSpec(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val sectionRes: Int,
    @StringRes val subtitleRes: Int? = null,
    @StringRes val keywordsRes: Int,
    val icon: ImageVector,
    val route: Route,
    val isTopLevel: Boolean = false,
)

internal data class SettingsSearchItem(
    val id: String,
    val title: String,
    val section: String,
    val subtitle: String?,
    val keywords: List<String>,
    val icon: ImageVector,
    val route: Route,
    val isTopLevel: Boolean = false,
)

internal data class SettingsSearchMatch(
    val item: SettingsSearchItem,
    val score: Int,
    val titleRanges: List<IntRange>,
)

internal val SETTINGS_SEARCH_SPECS: List<SettingsSearchSpec> =
    listOf(
        // A. Top-level settings entries (19)
        SettingsSearchSpec(
            id = "jxfw",
            titleRes = R.string.me_hub_bind_jxfw,
            sectionRes = R.string.me_hub_campus_section,
            subtitleRes = R.string.me_hub_bind_jxfw_sub,
            keywordsRes = R.string.settings_search_kw_jxfw,
            icon = Icons.Rounded.School,
            route = Route.Jxfw,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "campus_resources",
            titleRes = R.string.droprelay_title,
            sectionRes = R.string.me_hub_campus_section,
            keywordsRes = R.string.droprelay_settings_kw,
            icon = Icons.Rounded.FolderOpen,
            route = Route.CampusResources(),
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "grades",
            titleRes = R.string.grades_title,
            sectionRes = R.string.me_hub_campus_section,
            keywordsRes = R.string.settings_search_kw_grades,
            icon = Icons.Rounded.Grade,
            route = Route.Grades,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "physical_test",
            titleRes = R.string.tzcs_title,
            sectionRes = R.string.me_hub_campus_section,
            keywordsRes = R.string.settings_search_kw_physical_test,
            icon = Icons.Rounded.FitnessCenter,
            route = Route.PhysicalTest,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "classroom",
            titleRes = R.string.classroom_title,
            sectionRes = R.string.me_hub_campus_section,
            keywordsRes = R.string.settings_search_kw_classroom,
            icon = Icons.Rounded.MeetingRoom,
            route = Route.Classroom,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "course_selection",
            titleRes = R.string.xk_title,
            sectionRes = R.string.me_hub_campus_section,
            keywordsRes = R.string.settings_search_kw_course_selection,
            icon = Icons.Rounded.Checklist,
            route = Route.CourseSelection,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "hotwater",
            titleRes = R.string.hotwater_title,
            sectionRes = R.string.me_hub_campus_section,
            keywordsRes = R.string.settings_search_kw_hotwater,
            icon = Icons.Rounded.WaterDrop,
            route = Route.HotWater,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "parcel",
            titleRes = R.string.parcel_title,
            sectionRes = R.string.me_hub_campus_section,
            keywordsRes = R.string.settings_search_kw_parcel,
            icon = Icons.Rounded.LocalShipping,
            route = Route.Parcel,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "appearance",
            titleRes = R.string.appearance,
            sectionRes = R.string.me_hub_prefs_section,
            keywordsRes = R.string.settings_search_kw_appearance,
            icon = Icons.Rounded.Palette,
            route = Route.Appearance,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "widgets",
            titleRes = R.string.widgets_title,
            sectionRes = R.string.me_hub_prefs_section,
            keywordsRes = R.string.settings_search_kw_widgets,
            icon = Icons.Rounded.Widgets,
            route = Route.Widgets,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "preferences",
            titleRes = R.string.prefs_title,
            sectionRes = R.string.me_hub_prefs_section,
            keywordsRes = R.string.settings_search_kw_preferences,
            icon = Icons.Rounded.Tune,
            route = Route.Preferences,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "reminders",
            titleRes = R.string.reminders_title,
            sectionRes = R.string.me_hub_prefs_section,
            keywordsRes = R.string.settings_search_kw_reminders,
            icon = Icons.Rounded.NotificationsActive,
            route = Route.Reminders,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "sync",
            titleRes = R.string.sync_title,
            sectionRes = R.string.me_hub_sync_section,
            keywordsRes = R.string.settings_search_kw_sync,
            icon = Icons.Rounded.Cloud,
            route = Route.Sync,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "calendar_sync",
            titleRes = R.string.cal_sync_title,
            sectionRes = R.string.me_hub_sync_section,
            keywordsRes = R.string.settings_search_kw_calendar_sync,
            icon = Icons.Rounded.CalendarMonth,
            route = Route.CalendarSync,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "data",
            titleRes = R.string.data_title,
            sectionRes = R.string.me_hub_sync_section,
            keywordsRes = R.string.settings_search_kw_data,
            icon = Icons.Rounded.SwapVert,
            route = Route.Data,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "trash",
            titleRes = R.string.trash_title,
            sectionRes = R.string.me_hub_sync_section,
            keywordsRes = R.string.settings_search_kw_trash,
            icon = Icons.Rounded.DeleteSweep,
            route = Route.Trash,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "history",
            titleRes = R.string.history_title,
            sectionRes = R.string.me_hub_sync_section,
            keywordsRes = R.string.settings_search_kw_history,
            icon = Icons.Rounded.History,
            route = Route.History,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "lan_web",
            titleRes = R.string.lan_web_title,
            sectionRes = R.string.section_more,
            keywordsRes = R.string.settings_search_kw_lan_web,
            icon = Icons.Rounded.LaptopMac,
            route = Route.LanWeb,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "mcp",
            titleRes = R.string.mcp_title,
            sectionRes = R.string.section_more,
            keywordsRes = R.string.settings_search_kw_mcp,
            icon = Icons.Rounded.Terminal,
            route = Route.Mcp,
            isTopLevel = true,
        ),
        SettingsSearchSpec(
            id = "about",
            titleRes = R.string.about_title,
            sectionRes = R.string.section_more,
            keywordsRes = R.string.settings_search_kw_about,
            icon = Icons.Rounded.Info,
            route = Route.About,
            isTopLevel = true,
        ),
        // B. Key sub-page deep entries (24)
        SettingsSearchSpec(
            id = "theme_color",
            titleRes = R.string.theme_color,
            sectionRes = R.string.appearance,
            keywordsRes = R.string.settings_search_kw_theme_color,
            icon = Icons.Rounded.Palette,
            route = Route.Appearance,
        ),
        SettingsSearchSpec(
            id = "dark_mode",
            titleRes = R.string.dark_mode,
            sectionRes = R.string.appearance,
            keywordsRes = R.string.settings_search_kw_dark_mode,
            icon = Icons.Rounded.Palette,
            route = Route.Appearance,
        ),
        SettingsSearchSpec(
            id = "show_teacher",
            titleRes = R.string.show_teacher,
            sectionRes = R.string.prefs_title,
            keywordsRes = R.string.settings_search_kw_show_teacher,
            icon = Icons.Rounded.Tune,
            route = Route.Preferences,
        ),
        SettingsSearchSpec(
            id = "week_starts_monday",
            titleRes = R.string.week_starts_monday,
            sectionRes = R.string.prefs_title,
            keywordsRes = R.string.settings_search_kw_week_starts_monday,
            icon = Icons.Rounded.Tune,
            route = Route.Preferences,
        ),
        SettingsSearchSpec(
            id = "default_block_minutes",
            titleRes = R.string.default_block_minutes,
            sectionRes = R.string.prefs_title,
            keywordsRes = R.string.settings_search_kw_default_block_minutes,
            icon = Icons.Rounded.Tune,
            route = Route.Preferences,
        ),
        SettingsSearchSpec(
            id = "swap_swipe",
            titleRes = R.string.swap_swipe,
            sectionRes = R.string.prefs_title,
            subtitleRes = R.string.swap_swipe_desc,
            keywordsRes = R.string.settings_search_kw_swap_swipe,
            icon = Icons.Rounded.Tune,
            route = Route.Preferences,
        ),
        SettingsSearchSpec(
            id = "haptic_feedback",
            titleRes = R.string.haptic_feedback,
            sectionRes = R.string.prefs_title,
            subtitleRes = R.string.haptic_feedback_desc,
            keywordsRes = R.string.settings_search_kw_haptic_feedback,
            icon = Icons.Rounded.Tune,
            route = Route.Preferences,
        ),
        SettingsSearchSpec(
            id = "sound_feedback",
            titleRes = R.string.sound_feedback,
            sectionRes = R.string.prefs_title,
            subtitleRes = R.string.sound_feedback_desc,
            keywordsRes = R.string.settings_search_kw_sound_feedback,
            icon = Icons.Rounded.Tune,
            route = Route.Preferences,
        ),
        SettingsSearchSpec(
            id = "remind_courses",
            titleRes = R.string.remind_courses,
            sectionRes = R.string.reminders_title,
            keywordsRes = R.string.settings_search_kw_remind_courses,
            icon = Icons.Rounded.NotificationsActive,
            route = Route.Reminders,
        ),
        SettingsSearchSpec(
            id = "remind_exams",
            titleRes = R.string.remind_exams,
            sectionRes = R.string.reminders_title,
            keywordsRes = R.string.settings_search_kw_remind_exams,
            icon = Icons.Rounded.NotificationsActive,
            route = Route.Reminders,
        ),
        SettingsSearchSpec(
            id = "remind_tasks",
            titleRes = R.string.remind_tasks,
            sectionRes = R.string.reminders_title,
            keywordsRes = R.string.settings_search_kw_remind_tasks,
            icon = Icons.Rounded.NotificationsActive,
            route = Route.Reminders,
        ),
        SettingsSearchSpec(
            id = "remind_events",
            titleRes = R.string.remind_events,
            sectionRes = R.string.reminders_title,
            keywordsRes = R.string.settings_search_kw_remind_events,
            icon = Icons.Rounded.NotificationsActive,
            route = Route.Reminders,
        ),
        SettingsSearchSpec(
            id = "quick_add_remind",
            titleRes = R.string.reminders_section_quick_add,
            sectionRes = R.string.reminders_title,
            keywordsRes = R.string.settings_search_kw_quick_add_remind,
            icon = Icons.Rounded.NotificationsActive,
            route = Route.Reminders,
        ),
        SettingsSearchSpec(
            id = "remind_digest",
            titleRes = R.string.remind_digest,
            sectionRes = R.string.reminders_title,
            keywordsRes = R.string.settings_search_kw_remind_digest,
            icon = Icons.Rounded.NotificationsActive,
            route = Route.Reminders,
        ),
        SettingsSearchSpec(
            id = "quick_notif",
            titleRes = R.string.quick_notif_row,
            sectionRes = R.string.reminders_title,
            keywordsRes = R.string.settings_search_kw_quick_notif,
            icon = Icons.Rounded.NotificationsActive,
            route = Route.Reminders,
        ),
        SettingsSearchSpec(
            id = "auto_mute",
            titleRes = R.string.auto_mute,
            sectionRes = R.string.reminders_title,
            keywordsRes = R.string.settings_search_kw_auto_mute,
            icon = Icons.Rounded.NotificationsActive,
            route = Route.Reminders,
        ),
        SettingsSearchSpec(
            id = "reliability",
            titleRes = R.string.reliability_title,
            sectionRes = R.string.reminders_title,
            keywordsRes = R.string.settings_search_kw_reliability,
            icon = Icons.Rounded.NotificationsActive,
            route = Route.Reliability,
        ),
        SettingsSearchSpec(
            id = "import_file",
            titleRes = R.string.import_file,
            sectionRes = R.string.data_title,
            keywordsRes = R.string.settings_search_kw_import_file,
            icon = Icons.Rounded.SwapVert,
            route = Route.Import(null),
        ),
        SettingsSearchSpec(
            id = "auto_backup",
            titleRes = R.string.auto_backup,
            sectionRes = R.string.data_title,
            keywordsRes = R.string.settings_search_kw_auto_backup,
            icon = Icons.Rounded.SwapVert,
            route = Route.Data,
        ),
        SettingsSearchSpec(
            id = "export_ics",
            titleRes = R.string.export_ics,
            sectionRes = R.string.data_title,
            keywordsRes = R.string.settings_search_kw_export_ics,
            icon = Icons.Rounded.SwapVert,
            route = Route.Data,
        ),
        SettingsSearchSpec(
            id = "restore_json",
            titleRes = R.string.restore_json,
            sectionRes = R.string.data_title,
            keywordsRes = R.string.settings_search_kw_restore_json,
            icon = Icons.Rounded.SwapVert,
            route = Route.Data,
        ),
        SettingsSearchSpec(
            id = "hotwater_bills",
            titleRes = R.string.hotwater_bills_title,
            sectionRes = R.string.hotwater_title,
            keywordsRes = R.string.settings_search_kw_hotwater_bills,
            icon = Icons.Rounded.WaterDrop,
            route = Route.HotWaterBills,
        ),
        SettingsSearchSpec(
            id = "parcel_rules",
            titleRes = R.string.parcel_rules_manage,
            sectionRes = R.string.parcel_title,
            keywordsRes = R.string.settings_search_kw_parcel_rules,
            icon = Icons.Rounded.LocalShipping,
            route = Route.Parcel,
        ),
        SettingsSearchSpec(
            id = "update_check",
            titleRes = R.string.update_check,
            sectionRes = R.string.about_title,
            keywordsRes = R.string.settings_search_kw_update_check,
            icon = Icons.Rounded.Info,
            route = Route.About,
        ),
        SettingsSearchSpec(
            id = "error_log",
            titleRes = R.string.error_log_title,
            sectionRes = R.string.about_title,
            keywordsRes = R.string.settings_search_kw_error_log,
            icon = Icons.Rounded.Info,
            route = Route.About,
        ),
        SettingsSearchSpec(
            id = "edit_timetable",
            titleRes = R.string.edit_timetable,
            sectionRes = R.string.tab_timetable,
            keywordsRes = R.string.settings_search_kw_edit_timetable,
            icon = Icons.Rounded.CalendarViewWeek,
            route = Route.EditTimetable,
        ),
        SettingsSearchSpec(
            id = "periods",
            titleRes = R.string.semester_schedule_label,
            sectionRes = R.string.tab_timetable,
            keywordsRes = R.string.settings_search_kw_periods,
            icon = Icons.Rounded.Schedule,
            route = Route.Periods,
        ),
    )

internal fun buildSettingsSearchCatalog(ctx: Context): List<SettingsSearchItem> =
    SETTINGS_SEARCH_SPECS.map { spec ->
        SettingsSearchItem(
            id = spec.id,
            title = ctx.getString(spec.titleRes),
            section = ctx.getString(spec.sectionRes),
            subtitle = spec.subtitleRes?.let { ctx.getString(it) },
            keywords =
                ctx
                    .getString(spec.keywordsRes)
                    .trim()
                    .split(WHITESPACE_REGEX)
                    .filter { it.isNotEmpty() },
            icon = spec.icon,
            route = spec.route,
            isTopLevel = spec.isTopLevel,
        )
    }

private data class IndexedSearchMatch(
    val index: Int,
    val match: SettingsSearchMatch,
)

internal fun searchSettings(
    query: String,
    catalog: List<SettingsSearchItem>,
): List<SettingsSearchMatch> {
    val tokens =
        query
            .trim()
            .lowercase()
            .split(WHITESPACE_REGEX)
            .filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()

    val results = ArrayList<IndexedSearchMatch>()
    for ((index, item) in catalog.withIndex()) {
        val titleLower = item.title.lowercase()
        val sectionLower = item.section.lowercase()
        val subtitleLower = item.subtitle?.lowercase()
        val keywordsLower = item.keywords.map { it.lowercase() }

        var score = 0
        var allMatched = true
        for (token in tokens) {
            val tokenScore =
                when {
                    titleLower == token -> 120
                    titleLower.startsWith(token) -> 90
                    token in titleLower -> 70
                    keywordsLower.any { it == token } -> 55
                    keywordsLower.any { it.startsWith(token) } -> 45
                    keywordsLower.any { token in it } -> 35
                    subtitleLower != null && token in subtitleLower -> 25
                    token in sectionLower -> 15
                    else -> 0
                }
            if (tokenScore == 0) {
                allMatched = false
                break
            }
            score += tokenScore
        }
        if (!allMatched) continue

        if (item.isTopLevel) score += 5
        val titleRanges = findHighlightRanges(item.title, tokens)
        results += IndexedSearchMatch(index, SettingsSearchMatch(item, score, titleRanges))
    }

    return results
        .sortedWith(compareByDescending<IndexedSearchMatch> { it.match.score }.thenBy { it.index })
        .map { it.match }
}

internal fun findHighlightRanges(
    text: String,
    tokens: List<String>,
): List<IntRange> {
    if (text.isEmpty() || tokens.isEmpty()) return emptyList()
    val raw = ArrayList<IntRange>()
    for (token in tokens) {
        if (token.isEmpty()) continue
        var startIndex = 0
        while (startIndex < text.length) {
            val found = text.indexOf(token, startIndex, ignoreCase = true)
            if (found < 0) break
            raw += found until (found + token.length)
            startIndex = found + token.length
        }
    }
    if (raw.isEmpty()) return emptyList()

    raw.sortWith(compareBy<IntRange>({ it.first }, { it.last }))
    val merged = ArrayList<IntRange>(raw.size)
    var current = raw[0]
    for (i in 1 until raw.size) {
        val next = raw[i]
        if (next.first <= current.last + 1) {
            current = current.first..maxOf(current.last, next.last)
        } else {
            merged += current
            current = next
        }
    }
    merged += current
    return merged
}

internal fun highlightMatches(
    title: String,
    ranges: List<IntRange>,
    primary: Color,
    primaryContainer: Color,
): AnnotatedString {
    if (ranges.isEmpty() || title.isEmpty()) return AnnotatedString(title)
    val highlightStyle =
        SpanStyle(
            color = primary,
            fontWeight = FontWeight.SemiBold,
            background = primaryContainer.copy(alpha = 0.55f),
        )
    return buildAnnotatedString {
        var cursor = 0
        for (range in ranges) {
            val start = range.first.coerceIn(cursor, title.length)
            val endExclusive = (range.last + 1).coerceIn(start, title.length)
            if (start > cursor) {
                append(title.substring(cursor, start))
            }
            if (endExclusive > start) {
                pushStyle(highlightStyle)
                append(title.substring(start, endExclusive))
                pop()
            }
            cursor = maxOf(cursor, endExclusive)
        }
        if (cursor < title.length) {
            append(title.substring(cursor))
        }
    }
}

@Composable
internal fun SettingsSearchBar(
    visible: Boolean,
    queryText: String,
    searchFocus: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearchSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val onSurfaceVariant = scheme.onSurfaceVariant
    val panelEnter = expandVertically(MintTheme.spec()) + fadeIn(MintTheme.spec())
    val panelExit = shrinkVertically(MintTheme.spec()) + fadeOut(MintTheme.spec())

    AnimatedVisibility(
        visible = visible,
        enter = panelEnter,
        exit = panelExit,
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(SettingsSearchBarShape)
                .background(scheme.surfaceContainerLow)
                .heightIn(min = 48.dp)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = onSurfaceVariant)
            BasicTextField(
                value = queryText,
                onValueChange = onQueryChange,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                        .focusRequester(searchFocus)
                        .testTag("settings_search_input"),
                textStyle = typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                decorationBox = { inner ->
                    Box {
                        if (queryText.isEmpty()) {
                            Text(
                                stringResource(R.string.settings_search_hint),
                                style = typography.bodyLarge,
                                color = onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            if (queryText.isNotEmpty()) {
                IconButton(
                    onClick = onClearQuery,
                    modifier = Modifier.testTag("settings_search_clear"),
                ) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.settings_search_clear_cd))
                }
            }
        }
    }
}

@Composable
internal fun SettingsSearchResultRow(
    match: SettingsSearchMatch,
    onClick: () -> Unit,
) {
    val item = match.item
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val highlightedTitle = highlightMatches(item.title, match.titleRanges, scheme.primary, scheme.primaryContainer)
    val meta = if (item.subtitle != null) "${item.section} · ${item.subtitle}" else item.section

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .mintClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 10.dp)
            .testTag("settings_search_result_${item.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                highlightedTitle,
                style = typography.bodyLarge,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                meta,
                style = typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant)
    }
}
