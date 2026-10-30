@file:OptIn(ExperimentalMaterial3Api::class)

package com.shreenipane.orbit.ui.attendance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.AttendanceCalc
import com.shreenipane.orbit.core.calc.ExamPlanner
import com.shreenipane.orbit.core.model.AttendanceRecord
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyNote
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.RowDivider
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.Tag
import com.shreenipane.orbit.ui.components.ThinRing
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Good
import com.shreenipane.orbit.ui.theme.Warn
import com.shreenipane.orbit.ui.theme.attendanceColor
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ofPattern("EEE, d MMM")
private val WEEKDAY = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** What to tell the user about one course, given how many classes are really left. */
data class AttendancePlan(val tag: String, val color: Color, val line: String)

fun attendancePlan(a: AttendanceRecord, remaining: Int?): AttendancePlan {
    val adv = com.shreenipane.orbit.core.calc.AttendanceAdvice.of(a.attended, a.total, remaining)
    val color = when (adv.level) {
        com.shreenipane.orbit.core.calc.AttendanceAdvice.Level.GOOD -> Good
        com.shreenipane.orbit.core.calc.AttendanceAdvice.Level.WARN -> Warn
        com.shreenipane.orbit.core.calc.AttendanceAdvice.Level.BAD -> Bad
    }
    return AttendancePlan(adv.tag, color, adv.line)
}

@Composable
fun AttendanceScreen(snap: Snapshot, refreshing: Boolean, onRefresh: () -> Unit) {
    var open by remember { mutableStateOf<AttendanceRecord?>(null) }
    val now = LocalDateTime.now()
    val minute = now.hour * 60 + now.minute
    val overall = snap.attendance.let { a ->
        val att = a.sumOf { it.attended }; val tot = a.sumOf { it.total }
        if (tot == 0) null else AttendanceCalc.percent(att, tot)
    }

    // One pass over the calendar per sync, not one per course per frame.
    val remainingAll = remember(snap, now.toLocalDate()) {
        ExamPlanner.remainingAll(snap.calendar, snap.timetable.sessions, now.toLocalDate(), minute)
    }
    val rows = remember(snap, remainingAll) {
        snap.attendance
            .map { it to ExamPlanner.remainingFrom(remainingAll, it) }
            .sortedBy { AttendanceCalc.percent(it.first.attended, it.first.total) }
    }

    OrbitScreen(
        title = "Attendance",
        subtitle = overall?.let { "%.1f%% overall".format(it) },
        refreshing = refreshing,
        onRefresh = onRefresh,
    ) {
        if (snap.attendance.isEmpty()) {
            item { EmptyNote("No attendance yet", "VTOP hasn't posted attendance for this semester.") }
            return@OrbitScreen
        }
        val end = snap.calendar?.let(ExamPlanner::semesterEnd)?.minusDays(1)
        section(
            header = "Courses",
            footer = if (snap.calendar != null && end != null)
                "Classes left are counted from the ${snap.calendar.classGroupName} academic calendar up to the last instructional day (${end.format(DATE)}), including instructional Saturdays and Sundays and day-order swaps. Tap a course to plan."
            else "Sign in again to load the academic calendar; until then, counts assume classes continue indefinitely.",
        ) {
            Rows(rows, inset = 74.dp) { (a, remaining) ->
                val plan = attendancePlan(a, remaining)
                ListRow(
                    title = a.courseTitle,
                    subtitle = "${a.attended}/${a.total} · ${a.courseType.ifBlank { a.slot }}" +
                        (remaining?.let { "\n$it classes left" } ?: ""),
                    leading = { ThinRing(a.vtopPercent / 100f, attendanceColor(AttendanceCalc.percent(a.attended, a.total)), "${a.vtopPercent}") },
                    trailing = { Tag(plan.tag, plan.color) },
                    onClick = { open = a },
                )
            }
        }
    }

    open?.let { a -> PlannerSheet(a, snap, now, minute) { open = null } }
}

/**
 * Calculator for one course: every remaining class from the academic
 * calendar, each one toggled attend/miss, with the final percentage live.
 * You can't plan more classes than the calendar has left.
 */
@Composable
private fun PlannerSheet(a: AttendanceRecord, snap: Snapshot, now: LocalDateTime, minute: Int, onDismiss: () -> Unit) {
    val cal = snap.calendar
    val dates = remember(a, cal) {
        if (cal == null) emptyList()
        else ExamPlanner.classDates(cal, snap.timetable.sessions, a.courseCode, ExamPlanner.isLab(a), now.toLocalDate(), minute)
    }
    val missed = remember(a) { mutableStateListOf<Int>() }
    val attend = dates.size - missed.size
    val finalPct = AttendanceCalc.projected(a.attended, a.total, attend, missed.size)
    val plan = attendancePlan(a, if (cal == null) null else dates.size)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.92f), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(a.courseTitle, style = MaterialTheme.typography.titleLarge)
                    Text("${a.courseCode} · ${a.slot} · ${a.faculty}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThinRing((finalPct / 100).toFloat(), attendanceColor(finalPct), "%.1f".format(finalPct), size = 84.dp, stroke = 6.dp)
                        Spacer(Modifier.width(20.dp))
                        Column {
                            Text("Final attendance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${a.attended + attend}/${a.total + dates.size} classes", fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (cal == null) "Academic calendar not loaded" else "Attending $attend of ${dates.size} left",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(plan.line, color = plan.color, style = MaterialTheme.typography.bodyMedium)
                    val matrix = remember(a, cal) { ExamPlanner.matrix(cal, snap.exams, snap.timetable.sessions, now.toLocalDate(), minute) }
                    val row = matrix?.rows?.firstOrNull { it.courseCode == a.courseCode && it.isLab == ExamPlanner.isLab(a) }
                    if (matrix != null && row != null) {
                        val labels = ExamPlanner.EXAMS.toMap()
                        Text(
                            "Classes left · " + matrix.exams.joinToString("  ·  ") { (key, _) ->
                                "before ${labels[key]}: ${row.left[key]?.toString() ?: "done"}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (a.debarStatus.isNotBlank() && a.debarStatus != "-") {
                        Spacer(Modifier.height(6.dp))
                        Text("VTOP: ${a.debarStatus}", color = Bad, style = MaterialTheme.typography.bodySmall)
                    }
                    if (dates.isNotEmpty()) {
                        Row(Modifier.padding(top = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { missed.clear() }) { Text("Attend all") }
                            OutlinedButton(onClick = { missed.clear(); missed.addAll(dates.indices) }) { Text("Miss all") }
                        }
                    }
                }
            }
            if (cal != null && dates.isEmpty()) {
                item {
                    Text("No classes of this course left in the calendar.", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp))
                }
            }
            itemsIndexed(dates) { i, cd ->
                Column {
                    if (i > 0) RowDivider(72.dp)
                    val attending = i !in missed
                    Row(
                        Modifier.fillMaxWidth().clickable { if (attending) missed.add(i) else missed.remove(i) }
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = attending, onCheckedChange = { if (it) missed.remove(i) else missed.add(i) })
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text("${cd.date.format(DATE)} · ${cd.session.start}", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                listOfNotNull(
                                    cd.session.venue,
                                    cd.followsDay?.let { "${WEEKDAY[it - 1]} timetable" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(if (attending) "Attend" else "Miss", style = MaterialTheme.typography.labelLarge,
                            color = if (attending) Good else Bad)
                    }
                }
            }
        }
    }
}
