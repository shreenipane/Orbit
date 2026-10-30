package com.shreenipane.orbit.ui.marks

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.model.CourseMarks
import com.shreenipane.orbit.core.model.MarkComponent
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyState

@Composable
fun MarksScreen(snap: Snapshot) {
    if (snap.marks.isEmpty()) {
        EmptyState("📝", "No marks yet", "Marks show up here once faculty post them on VTOP.")
        return
    }
    var byExam by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!byExam, { byExam = false }, { Text("By course") })
                FilterChip(byExam, { byExam = true }, { Text("By exam") })
            }
        }
        if (byExam) {
            items(groupByExam(snap.marks)) { ExamGroupCard(it) }
        } else {
            items(snap.marks) { MarksCard(it) }
        }
    }
}

private data class ExamGroup(val title: String, val rows: List<Pair<CourseMarks, MarkComponent>>)

/** "CAT-1", "CAT 1" and "cat1" are the same exam. */
private fun groupByExam(marks: List<CourseMarks>): List<ExamGroup> =
    marks.flatMap { c -> c.components.map { c to it } }
        .groupBy { it.second.title.uppercase().replace(Regex("[^A-Z0-9]"), "") }
        .map { (_, rows) -> ExamGroup(rows.first().second.title, rows) }

@Composable
private fun ExamGroupCard(g: ExamGroup) {
    val scored = g.rows.filter { it.second.scored != null }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(g.title, style = MaterialTheme.typography.titleMedium)
            Text("${scored.size} of ${g.rows.size} courses posted", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            g.rows.forEach { (course, c) ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(course.courseTitle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${fmt(c.scored)}/${fmt(c.maxMark)}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun fmt(d: Double?) = when {
    d == null -> "—"
    d % 1.0 == 0.0 -> d.toInt().toString()
    else -> "%.2f".format(d).trimEnd('0').trimEnd('.')
}

@Composable
private fun MarksCard(m: CourseMarks) {
    var expanded by rememberSaveable(m.courseCode + m.courseType) { mutableStateOf(false) }
    val soFar = m.weightageSoFar
    Card(Modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.courseTitle, style = MaterialTheme.typography.titleMedium)
                    Text("${m.courseCode} · ${m.courseType}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmt(m.weightedTotal), style = MaterialTheme.typography.headlineMedium)
                    Text("of ${fmt(soFar)} so far", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (soFar > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (m.weightedTotal / soFar).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                m.components.forEach { c ->
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${fmt(c.scored)}/${fmt(c.maxMark)} · weight ${fmt(c.weightage)}" +
                                    if (c.remark.isNotBlank() && c.remark != "-") " · ${c.remark}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(fmt(c.weighted), fontWeight = FontWeight.Bold)
                    }
                }
                if (m.components.isEmpty()) Text("No components posted yet.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
