package app.mintcal.ui.screens

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
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mintcal.R
import app.mintcal.core.jxfw.JxfwGrade
import app.mintcal.core.jxfw.JxfwGradeSummary
import app.mintcal.core.jxfw.JxfwParse
import app.mintcal.jxfw.JxfwException
import app.mintcal.jxfw.JxfwGradesState
import app.mintcal.ui.LocalActions
import app.mintcal.ui.LocalApp
import app.mintcal.ui.LocalNav
import app.mintcal.ui.Route
import app.mintcal.ui.components.ChipPill
import app.mintcal.ui.components.EmptyState
import app.mintcal.ui.components.MintCard
import app.mintcal.ui.components.MintScreen
import app.mintcal.ui.components.SectionLabel
import app.mintcal.ui.theme.MintTheme
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
    var selectedTerm by rememberSaveable { mutableStateOf<String?>(null) }

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
    val displayedGrades =
        remember(gradesState.grades, activeTerm) {
            val base =
                if (activeTerm == null) {
                    gradesState.grades
                } else {
                    gradesState.grades.filter { it.term == activeTerm }
                }
            base.sortedByDescending { it.term }
        }
    val summary =
        remember(displayedGrades) {
            JxfwParse.summarizeGrades(displayedGrades)
        }

    val onRefresh: () -> Unit = {
        scope.launch {
            app.jxfw
                .refreshGrades()
                .onSuccess { list ->
                    actions.toast(ctx.getString(R.string.grades_refreshed_toast, list.size))
                }
        }
    }

    MintScreen(title = stringResource(R.string.grades_title), onBack = onBack) {
        if (!jxfwState.loggedIn) {
            EmptyState(
                title = stringResource(R.string.grades_not_logged_in_title),
                hint = stringResource(R.string.grades_not_logged_in_hint),
                icon = Icons.Rounded.School,
                actionLabel = stringResource(R.string.jxfw_card_bind_action),
                onAction = { nav.push(Route.Jxfw) },
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
                    onRefresh = onRefresh,
                )
            }

            if (gradesState.lastError != null) {
                item(key = "error") {
                    GradesErrorBanner(
                        gradesState = gradesState,
                        onOpenJxfw = { nav.push(Route.Jxfw) },
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
                    SectionLabel(
                        stringResource(R.string.grades_list_section, displayedGrades.size),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                itemsIndexed(
                    displayedGrades,
                    key = { index, g -> "$index|${g.term}|${g.code}|${g.name}" },
                ) { _, grade ->
                    GradeRow(
                        grade = grade,
                        showTerm = activeTerm == null,
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
    onRefresh: () -> Unit,
) {
    val dash = stringResource(R.string.grades_dash)
    val gpaDisplay =
        summary.weightedGpa?.let {
            String.format(Locale.US, "%.2f", it)
        } ?: dash
    val creditsText = stringResource(R.string.grades_total_credits, formatCompactDecimal(summary.totalCredits))
    val countText = stringResource(R.string.grades_course_count, summary.count)
    val fetchedText =
        gradesState.fetchedAt?.let {
            stringResource(R.string.grades_last_fetched, jxfwStamp(it))
        } ?: stringResource(R.string.grades_never_fetched)

    MintCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(if (filteredByTerm) R.string.grades_weighted_gpa_term else R.string.grades_weighted_gpa),
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
            "$creditsText · $countText · $fetchedText",
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        if (failed) {
            MintTheme.colors.removed
        } else {
            MaterialTheme.colorScheme.primary
        }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MintTheme.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    grade.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
