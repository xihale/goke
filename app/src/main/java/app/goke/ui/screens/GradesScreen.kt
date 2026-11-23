package app.goke.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.goke.R
import app.goke.core.jxfw.JxfwGrade
import app.goke.core.jxfw.JxfwGradeSummary
import app.goke.core.jxfw.JxfwParse
import app.goke.jxfw.JxfwException
import app.goke.jxfw.JxfwGradesState
import app.goke.ui.LocalActions
import app.goke.ui.LocalApp
import app.goke.ui.LocalNav
import app.goke.ui.Route
import app.goke.ui.components.ChipPill
import app.goke.ui.components.EmptyState
import app.goke.ui.components.MintCard
import app.goke.ui.components.MintScreen
import app.goke.ui.components.SectionLabel
import app.goke.ui.components.mintClickable
import app.goke.ui.icons.Grade
import app.goke.ui.icons.School
import app.goke.ui.theme.MintTheme
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** 「成绩与绩点」：本地只读查询教务成绩、按学期筛选并汇总加权均绩。 */
@Composable
fun GradesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val nav = LocalNav.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()

    val jxfwState by app.jxfw.state.collectAsState()
    val gradesState by app.jxfw.gradesState.collectAsState()
    LaunchedEffect(Unit) { app.jxfw.hydrateGrades() }

    val onRefresh: () -> Unit = {
        scope.launch {
            app.jxfw
                .refreshGrades()
                .onSuccess { list ->
                    actions.toast(ctx.getString(R.string.grades_refreshed_toast, list.size))
                }
        }
    }

    GradesContent(
        loggedIn = jxfwState.loggedIn,
        gradesState = gradesState,
        onBack = onBack,
        onOpenJxfw = { nav.push(Route.Jxfw) },
        onRefresh = onRefresh,
    )
}

@Composable
internal fun GradesContent(
    loggedIn: Boolean,
    gradesState: JxfwGradesState,
    onBack: () -> Unit,
    onOpenJxfw: () -> Unit,
    onRefresh: () -> Unit,
) {
    var selectedTerm by rememberSaveable { mutableStateOf<String?>(null) }
    var excludedKeys by remember { mutableStateOf(emptySet<String>()) }
    var excludedCategories by remember { mutableStateOf(emptySet<String>()) }

    val terms =
        remember(gradesState.grades) {
            gradesState.grades
                .asSequence()
                .map { it.term }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedDescending()
                .toList()
        }
    val activeTerm = selectedTerm?.takeIf { it in terms }
    val termGrades =
        remember(gradesState.grades, activeTerm) {
            val base =
                if (activeTerm == null) {
                    gradesState.grades
                } else {
                    gradesState.grades.filter { it.term == activeTerm }
                }
            base.sortedByDescending { it.term }
        }
    val categories = remember(termGrades) { JxfwParse.gradeCategories(termGrades) }
    val activeExcludedCategories =
        remember(excludedCategories, categories) {
            excludedCategories.intersect(categories.toSet())
        }
    val activeExcludedKeys =
        remember(excludedKeys, termGrades) {
            val validKeys = termGrades.mapTo(HashSet()) { it.filterKey }
            excludedKeys.intersect(validKeys)
        }
    val visibleGrades =
        remember(termGrades, activeExcludedCategories) {
            JxfwParse.filterGrades(termGrades, excludedCategories = activeExcludedCategories)
        }
    val summary =
        remember(termGrades, activeExcludedKeys, activeExcludedCategories) {
            JxfwParse.summarizeGrades(
                termGrades,
                excludedKeys = activeExcludedKeys,
                excludedCategories = activeExcludedCategories,
            )
        }
    val excludedCount = termGrades.size - summary.count
    val hasFilter = activeExcludedKeys.isNotEmpty() || activeExcludedCategories.isNotEmpty()

    MintScreen(title = stringResource(R.string.grades_title), onBack = onBack) {
        if (!loggedIn) {
            EmptyState(
                title = stringResource(R.string.grades_not_logged_in_title),
                hint = stringResource(R.string.grades_not_logged_in_hint),
                icon = Icons.Rounded.School,
                actionLabel = stringResource(R.string.jxfw_card_bind_action),
                onAction = onOpenJxfw,
                modifier = Modifier.padding(top = 48.dp, start = 24.dp, end = 24.dp),
            )
            return@MintScreen
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary") {
                GradesSummaryCard(
                    summary = summary,
                    gradesState = gradesState,
                    filteredByTerm = activeTerm != null,
                    hasFilter = hasFilter,
                    excludedCount = excludedCount,
                    onResetFilter = {
                        excludedKeys = emptySet()
                        excludedCategories = emptySet()
                    },
                    onRefresh = onRefresh,
                )
            }

            if (gradesState.lastError != null) {
                item(key = "error") {
                    GradesErrorBanner(
                        gradesState = gradesState,
                        onOpenJxfw = onOpenJxfw,
                    )
                }
            }

            if (terms.isNotEmpty()) {
                item(key = "terms") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "term-all") {
                            ChipPill(
                                label = stringResource(R.string.grades_all_terms),
                                on = activeTerm == null,
                                onClick = { selectedTerm = null },
                            )
                        }
                        items(terms, key = { it }) { term ->
                            ChipPill(
                                label = JxfwParse.termLabel(term),
                                on = activeTerm == term,
                                onClick = { selectedTerm = term },
                            )
                        }
                    }
                }
            }

            if (categories.isNotEmpty()) {
                item(key = "categories-header") {
                    SectionLabel(
                        stringResource(R.string.grades_filter_section),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                item(key = "categories") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "category-all") {
                            ChipPill(
                                label = stringResource(R.string.grades_filter_all),
                                on = activeExcludedCategories.isEmpty(),
                                role = Role.Checkbox,
                                onClick = { excludedCategories = emptySet() },
                            )
                        }
                        items(categories, key = { it }) { category ->
                            val included = category !in activeExcludedCategories
                            ChipPill(
                                label = category,
                                on = included,
                                role = Role.Checkbox,
                                onClick = {
                                    excludedCategories =
                                        if (category in excludedCategories) {
                                            excludedCategories - category
                                        } else {
                                            excludedCategories + category
                                        }
                                },
                            )
                        }
                    }
                }
            }

            if (gradesState.grades.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.grades_empty_title),
                        hint = stringResource(R.string.grades_empty_hint),
                        icon = Icons.Rounded.Grade,
                        actionLabel = if (gradesState.busy) null else stringResource(R.string.grades_refresh),
                        onAction = if (gradesState.busy) null else onRefresh,
                        modifier = Modifier.padding(top = 20.dp, start = 8.dp, end = 8.dp),
                    )
                }
            } else {
                item(key = "list-header") {
                    Column(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        SectionLabel(stringResource(R.string.grades_list_section, visibleGrades.size))
                        Text(
                            stringResource(R.string.grades_filter_toggle_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(
                    visibleGrades,
                    key = { index, g -> "$index|${g.filterKey}" },
                ) { _, grade ->
                    val excluded = grade.filterKey in activeExcludedKeys
                    GradeRow(
                        grade = grade,
                        showTerm = activeTerm == null,
                        excluded = excluded,
                        onToggleExclude = {
                            val key = grade.filterKey
                            excludedKeys =
                                if (key in excludedKeys) {
                                    excludedKeys - key
                                } else {
                                    excludedKeys + key
                                }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GradesSummaryCard(
    summary: JxfwGradeSummary,
    gradesState: JxfwGradesState,
    filteredByTerm: Boolean,
    hasFilter: Boolean,
    excludedCount: Int,
    onResetFilter: () -> Unit,
    onRefresh: () -> Unit,
) {
    val dash = stringResource(R.string.grades_dash)
    val gpaDisplay =
        summary.weightedGpa?.let {
            String.format(Locale.US, "%.2f", it)
        } ?: dash
    val earnedCreditsText = stringResource(R.string.grades_earned_credits, formatCompactDecimal(summary.earnedCredits))
    val creditsText = stringResource(R.string.grades_total_credits, formatCompactDecimal(summary.totalCredits))
    val countText = stringResource(R.string.grades_course_count, summary.count)
    val fetchedText =
        gradesState.fetchedAt?.let {
            stringResource(R.string.grades_last_fetched, jxfwStamp(it))
        } ?: stringResource(R.string.grades_never_fetched)
    val gpaTitleRes =
        when {
            hasFilter -> R.string.grades_filtered_gpa
            filteredByTerm -> R.string.grades_weighted_gpa_term
            else -> R.string.grades_weighted_gpa
        }

    MintCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(gpaTitleRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    gpaDisplay,
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            FilledTonalButton(
                enabled = !gradesState.busy,
                onClick = onRefresh,
                modifier = Modifier.heightIn(min = 48.dp),
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                if (gradesState.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.grades_refreshing), maxLines = 1)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.grades_refresh), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "$earnedCreditsText · $creditsText · $countText · $fetchedText",
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasFilter) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.grades_filter_excluded_count, excludedCount),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.primary,
                )
                ChipPill(
                    label = stringResource(R.string.grades_filter_reset),
                    on = null,
                    onClick = onResetFilter,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.grades_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GradesErrorBanner(
    gradesState: JxfwGradesState,
    onOpenJxfw: () -> Unit,
) {
    MintCard(color = MintTheme.colors.conflictBg) {
        JxfwErrorGuidance(gradesState.lastErrorKind, gradesState.lastError)
        if (gradesState.lastErrorKind == JxfwException.Kind.CREDENTIALS) {
            TextButton(
                onClick = onOpenJxfw,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.grades_relogin_jxfw))
            }
        }
    }
}

@Composable
private fun GradeRow(
    grade: JxfwGrade,
    showTerm: Boolean,
    excluded: Boolean,
    onToggleExclude: () -> Unit,
) {
    val ctx = LocalContext.current
    val dash = stringResource(R.string.grades_dash)
    val metaParts =
        remember(grade, ctx) {
            buildList {
                grade.credit?.takeIf { it > 0.0 }?.let {
                    add(ctx.getString(R.string.grades_credit_value, formatCompactDecimal(it)))
                }
                if (grade.category.isNotBlank()) add(grade.category)
                if (grade.studyMode.isNotBlank()) add(grade.studyMode)
                if (grade.examType.isNotBlank()) add(grade.examType)
            }.joinToString(" · ")
        }
    val scoreText = grade.score.ifBlank { dash }
    val failed = grade.gpa == 0.0 || grade.score.toDoubleOrNull()?.let { it < 60.0 } == true
    val scoreColor =
        when {
            excluded -> MaterialTheme.colorScheme.onSurfaceVariant
            failed -> MintTheme.colors.removed
            else -> MaterialTheme.colorScheme.primary
        }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (excluded) MaterialTheme.colorScheme.surfaceContainerLow else MintTheme.card,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    toggleableState = if (excluded) ToggleableState.Off else ToggleableState.On
                }.mintClickable(pressedScale = 0.98f, role = Role.Checkbox, onClick = onToggleExclude),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .alpha(if (excluded) 0.55f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    grade.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (excluded) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (metaParts.isNotBlank()) {
                    Text(
                        metaParts,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showTerm && grade.term.isNotBlank()) {
                    Text(
                        JxfwParse.termLabel(grade.term),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    scoreText,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = scoreColor,
                    maxLines = 1,
                )
                grade.gpa?.let { gpa ->
                    Text(
                        stringResource(R.string.grades_gpa_value, formatCompactDecimal(gpa)),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun formatCompactDecimal(value: Double): String = DecimalFormat("0.##", DecimalFormatSymbols(Locale.US)).format(value)
