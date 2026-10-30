package com.shreenipane.orbit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.AttendanceCalc
import com.shreenipane.orbit.core.calc.ExamPlanner
import com.shreenipane.orbit.core.calc.ExamTime
import com.shreenipane.orbit.core.calc.GradeCalc
import com.shreenipane.orbit.core.calc.Schedule
import com.shreenipane.orbit.core.model.ClassSession
import com.shreenipane.orbit.core.model.Reminder
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.Dot
import com.shreenipane.orbit.ui.components.Hero
import com.shreenipane.orbit.ui.components.Tag
import com.shreenipane.orbit.ui.theme.Pastel
import com.shreenipane.orbit.ui.components.EmptyNote
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.Metric
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.RowDivider
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.TimeColumn
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.more.MoreDestination
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Warn
import com.shreenipane.orbit.ui.theme.attendanceColor
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val SHORT = DateTimeFormatter.ofPattern("EEE, d MMM")

@Composable
fun HomeScreen(
    snap: Snapshot,
    reminders: List<Reminder>,
    alias: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpen: (MoreDestination) -> Unit,
) {
    // Re-evaluate "now" every 30 s so the Now/Next card moves on its own.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now() } }

    val today = now.toLocalDate()
    val minute = now.hour * 60 + now.minute
    val todayCal = snap.calendar?.days?.firstOrNull { it.date == today.toString() }
    val todayOrder = todayCal?.dayOrder
    // Holidays and exam days on the academic calendar have no classes.
    val dayOff = todayCal != null && todayCal.kind in setOf(com.shreenipane.orbit.core.model.DayKind.HOLIDAY, com.shreenipane.orbit.core.model.DayKind.NO_INSTRUCTION, com.shreenipane.orbit.core.model.DayKind.EXAM)
    val sched = Schedule.now(if (dayOff) emptyList() else snap.timetable.sessions, todayOrder ?: today.dayOfWeek.value, minute)
    val remainingAll = remember(snap, today) { ExamPlanner.remainingAll(snap.calendar, snap.timetable.sessions, today, minute) }
    val low = snap.attendance.filter { AttendanceCalc.percent(it.attended, it.total) < 75.0 }
    val standing = snap.grades?.let(GradeCalc::standing)
    val countdown = ExamPlanner.countdowns(snap.calendar, snap.exams, snap.timetable.sessions, today, minute).firstOrNull()
    val nextBreak = com.shreenipane.orbit.core.calc.Breaks.upcoming(snap.calendar, today).firstOrNull()
    val pendingDas = snap.assignments.filter { !it.submitted }
        .mapNotNull { da -> ExamTime.parseVtopDate(da.dueDate)?.let { da to it } }
        .filter { !it.second.isBefore(today) }
        .sortedBy { it.second }
    val nowMillis = System.currentTimeMillis()
    val dueSoon = reminders.filter { !it.done && it.dueAtMillis < nowMillis + 3 * 86_400_000L }.take(3)
    val overall = snap.attendance.let { a ->
        val att = a.sumOf { it.attended }; val tot = a.sumOf { it.total }
        if (tot == 0) null else AttendanceCalc.percent(att, tot)
    }

    OrbitScreen(
        title = if (alias.isNotBlank()) "Hi, $alias" else "Today",
        subtitle = now.format(DATE),
        refreshing = refreshing,
        onRefresh = onRefresh,
    ) {
        item { NowCard(sched) }

        item {
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(overall?.let { "%.0f%%".format(it) } ?: "—", "Attendance", Modifier.weight(1f),
                    fill = overall?.let { com.shreenipane.orbit.ui.theme.pastelFor(attendanceColor(it)) } ?: Pastel.Yellow)
                Metric(standing?.let { "%.2f".format(it.cgpa) } ?: "—", "CGPA", Modifier.weight(1f), fill = Pastel.Lavender)
                Metric(countdown?.let { "${it.daysLeft}d" } ?: "—", countdown?.let { "To ${it.label}" } ?: "Next exam", Modifier.weight(1f),
                    fill = Pastel.Sky)
            }
        }

        section(header = "Classes today") {
            if (sched.today.isEmpty()) {
                ListRow("No classes today", todayCal?.label?.takeIf { dayOff && it.isNotBlank() } ?: "Pull down to refresh if that looks wrong.")
            } else {
                Rows(sched.today, inset = 82.dp) { s -> ClassRow(s, isNow = s == sched.ongoing) }
            }
        }

        if (countdown != null || pendingDas.isNotEmpty() || nextBreak != null) {
            section(header = "Coming up", action = "See all" to { onOpen(MoreDestination.Exams) }) {
                if (countdown != null) {
                    ListRow(
                        title = "${countdown.label} in ${countdown.daysLeft} ${if (countdown.daysLeft == 1L) "day" else "days"}",
                        subtitle = "${countdown.totalClasses} classes left · ${countdown.instructionalDaysLeft} instructional days · ${countdown.start.format(SHORT)}",
                        onClick = { onOpen(MoreDestination.Exams) },
                        chevron = true,
                    )
                }
                if (nextBreak != null) {
                    if (countdown != null) RowDivider()
                    ListRow(
                        title = "${nextBreak.title} · ${nextBreak.days} ${if (nextBreak.days == 1) "day" else "days"} off",
                        subtitle = com.shreenipane.orbit.ui.holidays.breakRange(nextBreak) + " · " +
                            com.shreenipane.orbit.ui.holidays.breakWhen(nextBreak, today),
                        onClick = { onOpen(MoreDestination.Holidays) },
                        chevron = true,
                    )
                }
                pendingDas.take(3).forEachIndexed { i, (da, due) ->
                    if (i > 0 || countdown != null || nextBreak != null) RowDivider()
                    val days = ChronoUnit.DAYS.between(today, due)
                    ListRow(
                        title = da.title,
                        subtitle = "${da.courseTitle} · due ${due.format(SHORT)}",
                        onClick = { onOpen(MoreDestination.Reminders) },
                        trailing = {
                            Text(
                                if (days == 0L) "Today" else "${days}d",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                color = if (days <= 2) Bad else if (days <= 5) Warn else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }

        if (dueSoon.isNotEmpty()) {
            section(header = "Your reminders") {
                Rows(dueSoon) { r ->
                    val overdue = r.dueAtMillis < nowMillis
                    ListRow(
                        r.title,
                        r.courseCode,
                        onClick = { onOpen(MoreDestination.Reminders) },
                        trailing = {
                            Text(
                                if (overdue) "Overdue" else java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(r.dueAtMillis)),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (overdue) Bad else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }

        if (low.isNotEmpty()) {
            section(header = "Below 75%") {
                Rows(low) { a ->
                    val remaining = ExamPlanner.remainingFrom(remainingAll, a)
                    ListRow(
                        a.courseTitle,
                        com.shreenipane.orbit.ui.attendance.attendancePlan(a, remaining).line,
                        trailing = { Text("${a.vtopPercent}%", fontWeight = FontWeight.SemiBold, color = Bad) },
                    )
                }
            }
        }

        item {
            Text(
                "${snap.regNo} · synced ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(snap.fetchedAtMillis))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun NowCard(s: Schedule.Now) {
    val ongoing = s.ongoing
    val next = s.next
    val fill = when {
        ongoing != null -> Pastel.Green
        next != null -> Pastel.Sky
        else -> Pastel.Lavender
    }
    Hero(fill) {
        val ink = Pastel.Ink
        when {
            ongoing != null -> {
                Tag("Now · until ${ongoing.end}", com.shreenipane.orbit.ui.theme.Good)
                Spacer(Modifier.height(10.dp))
                Text(ongoing.courseTitle, style = MaterialTheme.typography.headlineMedium, color = ink)
                Text("${ongoing.venue} · ${ongoing.slot}", style = MaterialTheme.typography.bodyMedium, color = ink)
                if (next != null) {
                    Spacer(Modifier.height(10.dp))
                    Text("Next: ${next.courseTitle} at ${next.start}", style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted)
                }
            }
            next != null -> {
                Tag("Next · in ${humanMinutes(s.minutesToNext ?: 0)}", com.shreenipane.orbit.ui.theme.Warn)
                Spacer(Modifier.height(10.dp))
                Text(next.courseTitle, style = MaterialTheme.typography.headlineMedium, color = ink)
                Text("${next.venue} · ${next.start}–${next.end}", style = MaterialTheme.typography.bodyMedium, color = ink)
            }
            s.today.isNotEmpty() -> {
                Text("Done for today!", style = MaterialTheme.typography.headlineMedium, color = ink)
                Text("${s.today.size} classes finished", style = MaterialTheme.typography.bodyMedium, color = ink)
            }
            else -> {
                Text("Free day!", style = MaterialTheme.typography.headlineMedium, color = ink)
                Text("No classes today", style = MaterialTheme.typography.bodyMedium, color = ink)
            }
        }
    }
}

private fun humanMinutes(m: Int) = if (m < 60) "$m min" else "${m / 60}h ${m % 60}m"

@Composable
fun ClassRow(s: ClassSession, isNow: Boolean, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        TimeColumn(s.start, s.end)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isNow) { Dot(com.shreenipane.orbit.ui.theme.Pastel.Green); Spacer(Modifier.width(6.dp)) }
                Text(s.courseTitle, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Text(
                listOf(s.venue, s.slot, if (s.isLab) "Lab" else null).filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
