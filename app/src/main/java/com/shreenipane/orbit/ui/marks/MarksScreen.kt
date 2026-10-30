package com.shreenipane.orbit.ui.marks

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.model.CourseMarks
import com.shreenipane.orbit.core.model.MarkComponent
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyNote
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.RowDivider
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.Segmented
import com.shreenipane.orbit.ui.components.section

@Composable
fun MarksScreen(snap: Snapshot, refreshing: Boolean, onRefresh: () -> Unit) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    OrbitScreen(title = "Marks", subtitle = snap.semester?.name, refreshing = refreshing, onRefresh = onRefresh) {
        if (snap.marks.isEmpty()) {
            item { EmptyNote("No marks yet", "Marks appear here once faculty post them on VTOP.") }
            return@OrbitScreen
        }
        item { Segmented(listOf("By course", "By exam"), mode, { mode = it }, Modifier.padding(top = 4.dp)) }
        if (mode == 0) {
            snap.marks.forEach { m -> section { CourseCard(m) } }
        } else {
            groupByExam(snap.marks).forEach { g ->
                val posted = g.rows.count { it.second.scored != null }
                section(header = g.title, footer = "$posted of ${g.rows.size} courses posted") {
                    Rows(g.rows) { (course, c) ->
                        ListRow(
                            course.courseTitle,
                            course.courseType,
                            trailing = { Text("${fmt(c.scored)}/${fmt(c.maxMark)}", fontWeight = FontWeight.SemiBold) },
                        )
                    }
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

private data class ExamGroup(val title: String, val rows: List<Pair<CourseMarks, MarkComponent>>)

/** "CAT-1", "CAT 1" and "cat1" are the same exam. */
private fun groupByExam(marks: List<CourseMarks>): List<ExamGroup> =
    marks.flatMap { c -> c.components.map { c to it } }
        .groupBy { it.second.title.uppercase().replace(Regex("[^A-Z0-9]"), "") }
        .map { (_, rows) -> ExamGroup(rows.first().second.title, rows) }

@Composable
private fun CourseCard(m: CourseMarks) {
    var expanded by rememberSaveable(m.courseCode + m.courseType) { mutableStateOf(false) }
    val soFar = m.weightageSoFar
    Column(Modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.courseTitle, style = MaterialTheme.typography.titleMedium)
                Text("${m.courseCode} · ${m.courseType}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fmt(m.weightedTotal), style = MaterialTheme.typography.headlineMedium)
                Text("of ${fmt(soFar)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (soFar > 0) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (m.weightedTotal / soFar).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            if (m.components.isEmpty()) Text("No components posted yet.", style = MaterialTheme.typography.bodySmall)
            m.components.forEachIndexed { i, c ->
                if (i > 0) RowDivider(0.dp)
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
                    Text(fmt(c.weighted), fontWeight = FontWeight.SemiBold)
                }
            }
            com.shreenipane.orbit.core.calc.FatNeeded.of(m)?.let { f ->
                Spacer(Modifier.height(10.dp))
                Text("In FAT (out of 100) you need", style = MaterialTheme.typography.labelLarge)
                Text(
                    f.lines.joinToString("  ·  ") { l ->
                        "${l.target}: " + when {
                            !l.possible -> "out of reach"
                            l.neededOutOf100 == 0 -> "done"
                            else -> "${l.neededOutOf100}"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Grading is relative, so treat these as rough targets.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            Text("Tap for details", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}
