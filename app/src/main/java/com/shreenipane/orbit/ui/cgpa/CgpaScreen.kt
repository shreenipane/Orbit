package com.shreenipane.orbit.ui.cgpa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.Curriculum
import com.shreenipane.orbit.core.calc.GradeCalc
import com.shreenipane.orbit.core.calc.SemesterWindow
import com.shreenipane.orbit.core.model.GradedCourse
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.AppViewModel
import com.shreenipane.orbit.ui.components.EmptyNote
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.Metric
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.RowDivider
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.Segmented
import com.shreenipane.orbit.ui.components.Tag
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.results.GradeDetail
import com.shreenipane.orbit.ui.results.GradeSheet
import com.shreenipane.orbit.ui.results.gradeColor
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Good
import com.shreenipane.orbit.ui.theme.Pastel
import com.shreenipane.orbit.ui.theme.Warn
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private val GRADES = listOf("S", "A", "B", "C", "D", "E", "F")
private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

private fun f2(d: Double) = String.format(Locale.US, "%.2f", d)
private fun f0(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.US, "%.1f", d)

/** "Nov-2025" → 202511, for sorting newest first. */
private fun monthKey(s: String): Int {
    val m = Regex("([A-Za-z]{3})[A-Za-z]*\\W*(\\d{4})").find(s) ?: return 0
    return m.groupValues[2].toInt() * 100 + MONTHS.indexOf(m.groupValues[1].uppercase()) + 1
}

@Composable
fun CgpaScreen(snap: Snapshot, vm: AppViewModel, targets: Map<String, Double>) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val history = snap.grades
    val standing = history?.let(GradeCalc::standing)

    OrbitScreen(title = "CGPA") {
        if (history == null || standing == null) {
            item { EmptyNote("No grade history", "VTOP didn't send it. Pull to refresh on Today.") }
            return@OrbitScreen
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric(f2(standing.cgpa), "CGPA", Modifier.weight(1f), fill = Pastel.Lavender)
                Metric(f0(history.creditsEarned ?: standing.gradedCredits), "Credits", Modifier.weight(1f), fill = Pastel.Green)
                Metric("${history.courses.size}", "Courses", Modifier.weight(1f), fill = Pastel.Sky)
            }
        }
        item { Segmented(listOf("Calculator", "Grade history"), tab, { tab = it }, Modifier.padding(top = 16.dp)) }
        if (tab == 0) calculator(snap, standing) else gradeHistory(snap, vm, targets)
    }
}

// ---------- calculator ----------

private fun androidx.compose.foundation.lazy.LazyListScope.calculator(snap: Snapshot, standing: GradeCalc.Standing) {
    val current = snap.timetable.courses.filter { it.credits > 0 }
    val semCredits = current.sumOf { it.credits }
    section(header = "Target") {
        var target by rememberSaveable { mutableFloatStateOf(((standing.cgpa + 0.2).coerceAtMost(10.0) * 10).roundToInt() / 10f) }
        val required = GradeCalc.requiredSgpa(standing, semCredits, target.toDouble())
        Column(Modifier.padding(16.dp)) {
            Text("Reach ${String.format(Locale.US, "%.1f", target)}", style = MaterialTheme.typography.titleLarge)
            Slider(value = target, onValueChange = { target = (it * 10).roundToInt() / 10f }, valueRange = 5f..10f, steps = 49)
            when {
                semCredits == 0.0 -> Text("No credited courses this semester.", style = MaterialTheme.typography.bodyMedium)
                required.isNaN() -> Text("–")
                required > 10 -> Text("Needs ${f2(required)} SGPA. Out of reach this semester.", color = Bad, style = MaterialTheme.typography.bodyMedium)
                required <= 0 -> Text("You're already there.", color = Good, style = MaterialTheme.typography.bodyMedium)
                else -> Text("Needs ${f2(required)} SGPA this semester.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (current.isNotEmpty()) {
        section(header = "What if") {
            val picks = remember(snap.semester?.id) { mutableStateMapOf<String, String>() }
            val predicted = GradeCalc.sgpa(current.map { it.credits to (picks[it.code + it.type] ?: "A") })
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(f2(predicted), style = MaterialTheme.typography.headlineMedium)
                    Text("SGPA", style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted)
                }
                Column(Modifier.weight(1f)) {
                    Text(f2(GradeCalc.cgpaAfter(standing, semCredits, predicted)), style = MaterialTheme.typography.headlineMedium)
                    Text("New CGPA", style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted)
                }
            }
            current.forEach { c ->
                RowDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(c.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("${f0(c.credits)} credits", style = MaterialTheme.typography.bodySmall,
                        color = Pastel.InkMuted, modifier = Modifier.padding(bottom = 8.dp))
                    val key = c.code + c.type
                    Segmented(GRADES, GRADES.indexOf(picks[key] ?: "A"), { picks[key] = GRADES[it] })
                }
            }
        }
    }
}

// ---------- grade history ----------

private fun androidx.compose.foundation.lazy.LazyListScope.gradeHistory(snap: Snapshot, vm: AppViewModel, targets: Map<String, Double>) {
    val history = snap.grades ?: return
    val progress = Curriculum.progress(history, targets)
    val fromVtop = history.requirements.isNotEmpty()

    section(
        header = "Still to complete",
        footer = if (fromVtop) null else "Tap a category to enter how many it needs (from your curriculum). NGCR counts courses, not credits.",
    ) {
        var editing by remember { mutableStateOf<Curriculum.Progress?>(null) }
        if (progress.isEmpty()) ListRow("No categories in your grade history yet", null)
        Rows(progress) { p ->
            val unit = if (p.byCourses) "courses" else "credits"
            val req = p.required
            ListRow(
                p.name,
                if (req != null) "${f0(p.earned)} of ${f0(req)} $unit" else "${f0(p.earned)} $unit so far",
                trailing = {
                    val left = p.left
                    when {
                        left == null -> Tag("Set", Pastel.InkMuted)
                        left == 0.0 -> Tag("Done", Good)
                        else -> Tag("${f0(left)} left", Warn)
                    }
                },
                onClick = if (fromVtop) null else ({ editing = p }),
            )
        }
        editing?.let { p ->
            TargetDialog(p, onSave = { v ->
                vm.updateSettings { s ->
                    s.copy(creditTargets = if (v == null) s.creditTargets - p.key else s.creditTargets + (p.key to v))
                }
                editing = null
            }, onDismiss = { editing = null })
        }
    }

    val bySemester = history.courses.groupBy { it.examMonth }.toList().sortedByDescending { monthKey(it.first) }
    bySemester.forEach { (month, courses) ->
        section(header = month.ifBlank { "Other" }) {
            val scope = rememberCoroutineScope()
            var open by remember { mutableStateOf<GradedCourse?>(null) }
            var detail by remember { mutableStateOf<GradeDetail>(GradeDetail.Loading) }
            Rows(courses) { g ->
                ListRow(
                    g.title,
                    listOfNotNull(g.code, if (g.credits > 0) "${f0(g.credits)} cr" else null, g.distribution.takeIf { it.isNotBlank() })
                        .joinToString(" · "),
                    trailing = { Tag(g.grade.ifBlank { "–" }, gradeColor(g.grade)) },
                    onClick = {
                        open = g
                        detail = GradeDetail.Loading
                        val sem = SemesterWindow.semesterFor(g.examMonth, snap.semesters)
                        if (sem == null) {
                            detail = GradeDetail.Failed("Couldn't tell which semester this was.")
                        } else scope.launch {
                            vm.lookup { courseGradeStats(sem.id, g.code) }
                                .onSuccess { st -> detail = st?.let { GradeDetail.Loaded(it) } ?: GradeDetail.Failed("VTOP doesn't show grade ranges for this course.") }
                                .onFailure { e -> detail = GradeDetail.Failed(e.message ?: "Couldn't load grade ranges.") }
                        }
                    },
                )
            }
            open?.let { g ->
                GradeSheet(g.title, listOf(g.code, g.examMonth).joinToString(" · "), g.grade, null, detail) { open = null }
            }
        }
    }
}

@Composable
private fun TargetDialog(p: Curriculum.Progress, onSave: (Double?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(p.required?.let(::f0).orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pastel.Page,
        title = { Text(p.name) },
        text = {
            OutlinedTextField(
                text, { v -> text = v.filter { it.isDigit() || it == '.' }.take(5) },
                label = { Text(if (p.byCourses) "Courses needed" else "Credits needed") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.toDoubleOrNull()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
