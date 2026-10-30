package com.shreenipane.orbit.ui.cgpa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.GradeCalc
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyNote
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.Metric
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.RowDivider
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.Segmented
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Good
import kotlin.math.roundToInt

private val GRADES = listOf("S", "A", "B", "C", "D", "E", "F")

@Composable
fun CgpaScreen(snap: Snapshot) {
    val history = snap.grades
    val standing = history?.let(GradeCalc::standing)
    val current = snap.timetable.courses.filter { it.credits > 0 }
    val semCredits = current.sumOf { it.credits }

    var target by rememberSaveable { mutableFloatStateOf((((standing?.cgpa ?: 8.0) + 0.2).coerceAtMost(10.0) * 10).roundToInt() / 10f) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val picks = remember(snap.semester?.id) { mutableStateMapOf<String, String>() }

    OrbitScreen(title = "CGPA", subtitle = "Planner") {
        if (history == null || standing == null) {
            item { EmptyNote("No grade history", "VTOP didn't return your grade history. Pull to refresh on Today and try again.") }
            return@OrbitScreen
        }
        val predicted = GradeCalc.sgpa(current.map { it.credits to (picks[it.code + it.type] ?: "A") })
        val required = GradeCalc.requiredSgpa(standing, semCredits, target.toDouble())

        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("%.2f".format(standing.cgpa), "Current CGPA", Modifier.weight(1f))
                Metric(history.creditsEarned?.let { "%.0f".format(it) } ?: "%.0f".format(standing.gradedCredits), "Credits earned", Modifier.weight(1f))
                Metric("%.0f".format(semCredits), "This semester", Modifier.weight(1f))
            }
        }

        section(header = "Target") {
            Column(Modifier.padding(16.dp)) {
                Text("Reach %.1f".format(target), style = MaterialTheme.typography.titleLarge)
                Slider(value = target, onValueChange = { target = (it * 10).roundToInt() / 10f }, valueRange = 5f..10f, steps = 49)
                when {
                    semCredits == 0.0 -> Text("No credited courses in this semester's timetable.", style = MaterialTheme.typography.bodyMedium)
                    required.isNaN() -> Text("—")
                    required > 10 -> Text("Needs %.2f SGPA, out of reach this semester. Spread it over two.".format(required), color = Bad,
                        style = MaterialTheme.typography.bodyMedium)
                    required <= 0 -> Text("Already there. Any passing semester keeps you above it.", color = Good, style = MaterialTheme.typography.bodyMedium)
                    else -> Text("Needs an SGPA of %.2f across %.0f credits.".format(required, semCredits),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (current.isNotEmpty()) {
            section(header = "What if", footer = "Pick the grade you expect in each course.") {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("%.2f".format(predicted), style = MaterialTheme.typography.headlineMedium)
                        Text("SGPA", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("%.2f".format(GradeCalc.cgpaAfter(standing, semCredits, predicted)), style = MaterialTheme.typography.headlineMedium)
                        Text("New CGPA", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                current.forEach { c ->
                    RowDivider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(c.title, style = MaterialTheme.typography.bodyLarge)
                        Text("${c.code} · ${c.type} · ${"%.0f".format(c.credits)} credits", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                        val key = c.code + c.type
                        Segmented(GRADES, GRADES.indexOf(picks[key] ?: "A"), { picks[key] = GRADES[it] })
                    }
                }
            }
        }

        section(header = "Grade history", action = (if (showHistory) "Hide" else "Show") to { showHistory = !showHistory }) {
            if (!showHistory) {
                ListRow("${history.courses.size} graded courses", "Tap Show to list them")
            } else {
                Rows(history.courses) { g ->
                    ListRow(
                        g.title, "${g.code} · ${g.examMonth} · ${"%.1f".format(g.credits)} cr",
                        trailing = { Text(g.grade, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
                    )
                }
            }
        }
    }
}
