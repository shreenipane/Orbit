package com.shreenipane.orbit.ui.exams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
            val sel = pick.coerceIn(0, countdowns.lastIndex)
            val cd = countdowns[sel]
            if (countdowns.size > 1) {
                item { Segmented(countdowns.map { it.label }, sel, { pick = it }, Modifier.padding(top = 4.dp)) }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("${cd.daysLeft}", if (cd.daysLeft == 1L) "day to go" else "days to go", Modifier.weight(1f))
                    Stat("${cd.instructionalDaysLeft}", "teaching days", Modifier.weight(1f))
                    Stat("${cd.totalClasses}", "classes left", Modifier.weight(1f))
                }
            }
            section(
                header = "Classes before ${cd.label}",
                footer = "${cd.label} starts ${cd.start.format(DAY)}. Counted from the ${snap.calendar?.classGroupName ?: "academic"} calendar: " +
                    "instructional days only, holidays skipped, Saturday day orders followed. A lab block counts as one class.",
            ) {
                val courses = cd.courses
                if (courses.isEmpty()) ListRow("No timetable", "Your timetable is empty for this semester.")
                Rows(courses) { c ->
                    ListRow(
                        c.courseTitle,
                        "${c.courseCode} · ${labLabel(c.isLab)}",
                        trailing = { Text("${c.classes}", style = MaterialTheme.typography.titleMedium) },
                    )
                }
            }
        }

        snap.exams.groupBy { it.examType.ifBlank { "Exams" } }.forEach { (type, list) ->
            section(header = "$type schedule") {
                Rows(list.sortedBy { ExamTime.start(it) ?: LocalDateTime.MAX }) { e -> ExamRow(e, now) }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    com.shreenipane.orbit.ui.components.Metric(value, label, modifier)
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
