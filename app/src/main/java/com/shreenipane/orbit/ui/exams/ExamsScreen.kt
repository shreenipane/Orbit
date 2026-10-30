package com.shreenipane.orbit.ui.exams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.ExamPlanner
import com.shreenipane.orbit.core.calc.ExamTime
import com.shreenipane.orbit.core.model.Exam
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyNote
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.RowDivider
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.Segmented
import com.shreenipane.orbit.ui.components.labLabel
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.NumberStyle
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM")
private val DAY_TIME = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a")

@Composable
fun ExamsScreen(snap: Snapshot) {
    val now = LocalDateTime.now()
    val minute = now.hour * 60 + now.minute
    val countdowns = ExamPlanner.countdowns(snap.calendar, snap.exams, snap.timetable.sessions, now.toLocalDate(), minute)
    var pick by rememberSaveable { mutableIntStateOf(0) }

    OrbitScreen(title = "Exams", subtitle = snap.calendar?.classGroupName ?: snap.semester?.name) {
        if (countdowns.isEmpty() && snap.exams.isEmpty()) {
            item {
                EmptyNote(
                    "No exam dates yet",
                    if (snap.calendar == null) "Sign in again so Orbit can read the academic calendar."
                    else "The academic calendar doesn't show any upcoming CAT or FAT.",
                )
            }
            return@OrbitScreen
        }

        if (countdowns.isNotEmpty()) {
            val cd = countdowns.first()
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("${cd.daysLeft}", "days to ${cd.label}", Modifier.weight(1f), com.shreenipane.orbit.ui.theme.Pastel.Salmon)
                    Stat("${cd.instructionalDaysLeft}", "teaching days", Modifier.weight(1f), com.shreenipane.orbit.ui.theme.Pastel.Yellow)
                    Stat("${cd.totalClasses}", "classes left", Modifier.weight(1f), com.shreenipane.orbit.ui.theme.Pastel.Green)
                }
            }
        }

        val matrix = ExamPlanner.matrix(snap.calendar, snap.exams, snap.timetable.sessions, now.toLocalDate(), minute)
        if (matrix != null) {
            section(
                header = "Classes left per subject",
                footer = "Counted from the ${snap.calendar?.classGroupName ?: "academic"} calendar: instructional days only (Saturdays and Sundays too when VTOP marks them), " +
                    "holidays skipped, day-order swaps followed. A lab block counts as one class. \"Done\" means that exam has started or passed.",
            ) { MatrixTable(matrix) }
        }

        snap.exams.groupBy { it.examType.ifBlank { "Exams" } }.forEach { (type, list) ->
            section(header = "$type schedule") {
                Rows(list.sortedBy { ExamTime.start(it) ?: LocalDateTime.MAX }) { e -> ExamRow(e, now) }
            }
        }
    }
}

@Composable
private fun MatrixTable(m: ExamPlanner.Matrix) {
    val labels = ExamPlanner.EXAMS.toMap()
    val colW = 64.dp
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text("Subject", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            m.exams.forEach { (key, start) ->
                Column(Modifier.width(colW), horizontalAlignment = Alignment.End) {
                    Text(labels[key] ?: key, style = MaterialTheme.typography.labelLarge)
                    Text(start.format(SHORT), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        m.rows.forEach { r ->
            RowDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.courseTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("${r.courseCode} · ${labLabel(r.isLab)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                m.exams.forEach { (key, _) ->
                    val v = r.left[key]
                    Text(
                        v?.toString() ?: "Done",
                        modifier = Modifier.width(colW),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        style = if (v == null) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
                        color = if (v == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private val SHORT = DateTimeFormatter.ofPattern("d MMM")

@Composable
private fun Stat(value: String, label: String, modifier: Modifier, fill: androidx.compose.ui.graphics.Color) {
    com.shreenipane.orbit.ui.components.Metric(value, label, modifier, fill = fill)
}

@Composable
private fun ExamRow(e: Exam, now: LocalDateTime) {
    val start = ExamTime.start(e)
    val past = start != null && start.isBefore(now)
    val where = listOf(e.venue, e.seatLocation, if (e.seatNumber.isNotBlank()) "Seat ${e.seatNumber}" else "")
        .filter { it.isNotBlank() && it != "-" }.joinToString(" · ")
    ListRow(
        title = e.courseTitle,
        subtitle = listOfNotNull(
            start?.format(DAY_TIME) ?: "Date not announced",
            where.ifBlank { null },
            e.reportingTime.takeIf { it.isNotBlank() && it != "-" }?.let { "report $it" },
        ).joinToString("\n"),
        titleColor = if (past) MaterialTheme.colorScheme.onSurfaceVariant else androidx.compose.ui.graphics.Color.Unspecified,
        trailing = {
            when {
                start == null -> Text("TBA", style = MaterialTheme.typography.bodySmall)
                past -> Text("Done", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Text(ExamTime.countdown(start, now), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}
