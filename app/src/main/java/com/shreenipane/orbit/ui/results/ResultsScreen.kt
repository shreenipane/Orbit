package com.shreenipane.orbit.ui.results

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.model.GradeResult
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.AppViewModel
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.brutal
import com.shreenipane.orbit.ui.components.Metric
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.Tag
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Good
import com.shreenipane.orbit.ui.theme.Pastel
import com.shreenipane.orbit.ui.theme.Warn
import java.util.Locale

/** Semester results (grades as published) and credits earned per curriculum bucket. */
@Composable
fun ResultsScreen(snap: Snapshot, vm: AppViewModel) {
    val sems = snap.semesters
    var semId by remember { mutableStateOf(snap.semester?.id ?: sems.firstOrNull()?.id) }
    var results by remember { mutableStateOf<List<GradeResult>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<GradeResult?>(null) }
    var detail by remember { mutableStateOf<GradeDetail>(GradeDetail.Loading) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(semId) {
        val id = semId ?: return@LaunchedEffect
        loading = true; error = null; results = null
        vm.lookup { gradeView(id) }.onSuccess { results = it }.onFailure { error = it.message }
        loading = false
    }

    OrbitScreen(title = "Results", subtitle = "Grades by semester") {
        snap.grades?.let { g ->
            item {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric(g.cgpa?.let { String.format(Locale.US, "%.2f", it) } ?: "–", "CGPA", Modifier.weight(1f), fill = Pastel.Yellow)
                    Metric(g.creditsEarned?.let { fmt(it) } ?: "–", "Credits earned", Modifier.weight(1f), fill = Pastel.Mint)
                    Metric(g.creditsRegistered?.let { fmt(it) } ?: "–", "Registered", Modifier.weight(1f), fill = Pastel.Sky)
                }
            }
        }

        if (sems.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sems.forEach { s ->
                        val on = s.id == semId
                        Text(
                            s.name, style = MaterialTheme.typography.labelLarge, color = Pastel.Ink,
                            modifier = Modifier
                                .brutal(if (on) Pastel.Yellow else Pastel.Card, radius = 10.dp, shadow = if (on) 3.dp else 1.dp, border = 1.5.dp)
                                .clickable { semId = s.id }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }

        section(header = "Grades", footer = "Tap a course for its grade ranges.") {
            val list = results
            when {
                loading -> ListRow("Loading…", null)
                error != null -> ListRow("Couldn't load results", error)
                list.isNullOrEmpty() -> ListRow("No grades published", "VTOP shows grades once results are out.")
                else -> Rows(list) { r ->
                    ListRow(r.title, "${r.code} · ${r.type}",
                        trailing = { Tag(r.grade.ifBlank { "–" }, gradeColor(r.grade)) },
                        onClick = {
                            val id = semId
                            open = r
                            detail = GradeDetail.Loading
                            if (id == null || r.courseId.isBlank()) {
                                detail = GradeDetail.Failed("VTOP doesn't show grade ranges for this course.")
                            } else scope.launch {
                                vm.lookup { gradeStats(id, r.courseId) }
                                    .onSuccess { st -> detail = st?.let { GradeDetail.Loaded(it) } ?: GradeDetail.Failed("VTOP doesn't show grade ranges for this course.") }
                                    .onFailure { e -> detail = GradeDetail.Failed(e.message ?: "Couldn't load grade ranges.") }
                            }
                        })
                }
            }
        }

    }

    open?.let { r ->
        GradeSheet(r.title, "${r.code} · ${r.type}", r.grade, r.total, detail) { open = null }
    }
}

private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.US, "%.1f", d)
