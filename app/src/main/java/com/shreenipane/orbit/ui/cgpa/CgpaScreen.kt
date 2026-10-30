@file:OptIn(ExperimentalLayoutApi::class)

package com.shreenipane.orbit.ui.cgpa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.GradeCalc
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyState
import com.shreenipane.orbit.ui.components.SectionTitle
import com.shreenipane.orbit.ui.components.Stat
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Good
import kotlin.math.roundToInt

private val GRADES = listOf("S", "A", "B", "C", "D", "E", "F")

@Composable
fun CgpaScreen(snap: Snapshot) {
    val history = snap.grades
    if (history == null) {
        EmptyState("🎓", "No grade history", "VTOP didn't return your grade history. Pull to refresh on Today and try again.")
        return
    }
    val standing = GradeCalc.standing(history)
    val current = snap.timetable.courses.filter { it.credits > 0 }
    val semCredits = current.sumOf { it.credits }

    var target by rememberSaveable { mutableFloatStateOf(((standing.cgpa + 0.2).coerceAtMost(10.0) * 10).roundToInt() / 10f) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    // Expected grade per current course, default A.
    val picks = remember(snap.semester?.id) { mutableStateMapOf<String, String>() }

    val predicted = GradeCalc.sgpa(current.map { it.credits to (picks[it.code + it.type] ?: "A") })
    val required = GradeCalc.requiredSgpa(standing, semCredits, target.toDouble())

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("%.2f".format(standing.cgpa), "Current CGPA")
                    Stat(history.creditsEarned?.let { "%.0f".format(it) } ?: "%.0f".format(standing.gradedCredits), "Credits earned")
                    Stat("%.0f".format(semCredits), "This semester")
                }
            }
        }

        item { SectionTitle("Target") }
        item {
            Card {
                Column(Modifier.padding(20.dp)) {
                    Text("Reach %.1f CGPA".format(target), style = MaterialTheme.typography.titleLarge)
                    Slider(value = target, onValueChange = { target = (it * 10).roundToInt() / 10f }, valueRange = 5f..10f, steps = 49)
                    when {
                        semCredits == 0.0 -> Text("No credited courses found in this semester's timetable.")
                        required.isNaN() -> Text("—")
                        required > 10 -> Text("Out of reach this semester: you'd need %.2f SGPA. Aim for it over two semesters.".format(required), color = Bad)
                        required <= 0 -> Text("You're already there. Any passing semester keeps you above it.", color = Good)
                        else -> Text(
                            "You need an SGPA of %.2f across %.0f credits this semester.".format(required, semCredits),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        if (current.isNotEmpty()) {
            item { SectionTitle("What if I get…") }
            item {
                Card {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Stat("%.2f".format(predicted), "SGPA", Modifier.weight(1f))
                            Stat("%.2f".format(GradeCalc.cgpaAfter(standing, semCredits, predicted)), "New CGPA", Modifier.weight(1f))
                        }
                    }
                }
            }
            items(current) { c ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(c.title, style = MaterialTheme.typography.titleMedium)
                        Text("${c.code} · ${c.type} · ${"%.0f".format(c.credits)} credits", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GRADES.forEach { g ->
                                val sel = (picks[c.code + c.type] ?: "A") == g
                                FilterChip(selected = sel, onClick = { picks[c.code + c.type] = g }, label = { Text(g) })
                            }
                        }
                    }
                }
            }
        }

        item {
            TextButton(onClick = { showHistory = !showHistory }) {
                Text(if (showHistory) "Hide grade history" else "Show grade history (${history.courses.size} courses)")
            }
        }
        if (showHistory) {
            items(history.courses) { g ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(g.title, style = MaterialTheme.typography.bodyMedium)
                        Text("${g.code} · ${g.examMonth} · ${"%.1f".format(g.credits)} cr", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(g.grade, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
