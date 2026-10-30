@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import com.shreenipane.orbit.ui.theme.Pastel
import com.shreenipane.orbit.ui.theme.pastelFor
import com.shreenipane.orbit.ui.components.Metric
import com.shreenipane.orbit.ui.components.labLabel
import androidx.compose.ui.text.style.TextOverflow
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
fun AttendanceScreen(
    snap: Snapshot, refreshing: Boolean, onRefresh: () -> Unit,
    overrides: List<com.shreenipane.orbit.core.model.ClassOverride> = emptyList(),
) {
    var open by remember { mutableStateOf<AttendanceRecord?>(null) }
    val now = LocalDateTime.now()
    val minute = now.hour * 60 + now.minute
    val overall = snap.attendance.let { a ->
        val att = a.sumOf { it.attended }; val tot = a.sumOf { it.total }
        if (tot == 0) null else AttendanceCalc.percent(att, tot)
    }

    // One pass over the calendar per sync, not one per course per frame.
    val remainingAll = remember(snap, now.toLocalDate(), overrides) {
        ExamPlanner.remainingAll(snap.calendar, snap.timetable.sessions, now.toLocalDate(), minute, overrides)
    }
    val rows = remember(snap, remainingAll) {
        snap.attendance
            .map { it to ExamPlanner.remainingFrom(remainingAll, it) }
            .sortedBy { AttendanceCalc.percent(it.first.attended, it.first.total) }
    }

    val plans = remember(rows) { rows.map { (a, rem) -> Triple(a, rem, attendancePlan(a, rem)) } }
    val safe = plans.count { it.third.color == Good }
    val atRisk = plans.size - safe
    val end = snap.calendar?.let(ExamPlanner::semesterEnd)?.minusDays(1)

    OrbitScreen(title = "Attendance", refreshing = refreshing, onRefresh = onRefresh) {
        if (snap.attendance.isEmpty()) {
            item { EmptyNote("No attendance yet", "VTOP hasn't posted any for this semester.") }
            return@OrbitScreen
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric(overall?.let { "${Math.round(it)}%" } ?: "–", "Overall", Modifier.weight(1f),
                    fill = overall?.let { pastelFor(attendanceColor(it)) } ?: Pastel.Yellow)
                Metric("$safe", "Safe", Modifier.weight(1f), fill = Pastel.Green)
                Metric("$atRisk", "Need care", Modifier.weight(1f), fill = if (atRisk > 0) Pastel.Salmon else Pastel.Card)
            }
        }
        section(header = "Courses") {
            Rows(plans, inset = 72.dp) { (a, remaining, plan) ->
                CourseRow(a, remaining, plan) { open = a }
            }
        }
        if (end != null) {
            item {
                Text("Classes left are counted to ${end.format(DATE)}", style = MaterialTheme.typography.bodySmall,
                    color = Pastel.InkMuted, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
            }
        }
    }

    open?.let { a -> PlannerSheet(a, snap, now, minute, overrides) { open = null } }
}

/** One course: ring, name on one line, "32/44 · Lab · 12 left", and a short verdict. */
@Composable
private fun CourseRow(a: AttendanceRecord, remaining: Int?, plan: AttendancePlan, onClick: () -> Unit) {
    val pct = AttendanceCalc.percent(a.attended, a.total)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThinRing(a.vtopPercent / 100f, attendanceColor(pct), "${a.vtopPercent}", size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.courseTitle, style = MaterialTheme.typography.bodyLarge, color = Pastel.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull("${a.attended}/${a.total}", labLabel(ExamPlanner.isLab(a)), remaining?.takeIf { it > 0 }?.let { "$it left" })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted, maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Tag(plan.tag, plan.color)
    }
}

/**
 * Calculator for one course: every remaining class from the academic
 * calendar, each one toggled attend/miss, with the final percentage live.
 * You can't plan more classes than the calendar has left.
 */
@Composable
private fun PlannerSheet(
    a: AttendanceRecord, snap: Snapshot, now: LocalDateTime, minute: Int,
    overrides: List<com.shreenipane.orbit.core.model.ClassOverride>, onDismiss: () -> Unit,
) {
    val cal = snap.calendar
    val dates = remember(a, cal) {
        if (cal == null) emptyList()
        else ExamPlanner.classDates(cal, snap.timetable.sessions, a.courseCode, ExamPlanner.isLab(a), now.toLocalDate(), minute, overrides = overrides)
    }
    val missed = remember(a) { mutableStateListOf<Int>() }
    val attend = dates.size - missed.size
    val finalPct = AttendanceCalc.projected(a.attended, a.total, attend, missed.size)
    val plan = attendancePlan(a, if (cal == null) null else dates.size)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Pastel.Page,
    ) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.92f), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(a.courseTitle, style = MaterialTheme.typography.titleLarge)
                    Text("${labLabel(ExamPlanner.isLab(a))} · ${a.slot}", style = MaterialTheme.typography.bodySmall,
                        color = Pastel.InkMuted)
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThinRing((finalPct / 100).toFloat(), attendanceColor(finalPct), "%.1f".format(finalPct), size = 84.dp, stroke = 6.dp)
                        Spacer(Modifier.width(20.dp))
                        Column {
                            Text("By the end", style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted)
                            Text("${a.attended + attend}/${a.total + dates.size} classes", fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium)
                            if (cal != null && dates.isNotEmpty()) {
                                Text("Attending $attend of ${dates.size}", style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(plan.line, color = Pastel.Ink, style = MaterialTheme.typography.bodyMedium)
                    val matrix = remember(a, cal) { ExamPlanner.matrix(cal, snap.exams, snap.timetable.sessions, now.toLocalDate(), minute, overrides) }
                    val row = matrix?.rows?.firstOrNull { it.courseCode == a.courseCode && it.isLab == ExamPlanner.isLab(a) }
                    if (matrix != null && row != null) {
                        val labels = ExamPlanner.EXAMS.toMap()
                        val upcoming = matrix.exams.mapNotNull { (key, _) -> row.left[key]?.let { (labels[key] ?: key) to it } }
                        if (upcoming.isNotEmpty()) {
                            androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                upcoming.forEach { (label, n) -> Tag("$n before $label", Pastel.InkMuted) }
                            }
                        }
                    }
                    if (a.debarStatus.isNotBlank() && a.debarStatus != "-") {
                        Spacer(Modifier.height(6.dp))
                        Text("VTOP: ${a.debarStatus}", color = Bad, style = MaterialTheme.typography.bodySmall)
                    }
                    if (dates.isNotEmpty()) {
                        Row(Modifier.padding(top = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.shreenipane.orbit.ui.components.BrutalButton("Attend all", { missed.clear() }, fill = Pastel.Green)
                            com.shreenipane.orbit.ui.components.BrutalButton("Miss all", { missed.clear(); missed.addAll(dates.indices) }, fill = Pastel.Salmon)
                        }
                    }
                }
            }
            if (cal != null && dates.isEmpty()) {
                item {
                    Text("No classes left.", style = MaterialTheme.typography.bodyMedium,
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
