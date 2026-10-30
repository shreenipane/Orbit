package com.shreenipane.orbit.ui.home

import androidx.compose.foundation.clickable
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
    overrides: List<com.shreenipane.orbit.core.model.ClassOverride> = emptyList(),
    onToggleCancel: (java.time.LocalDate, ClassSession) -> Unit = { _, _ -> },
    onRemoveOverride: (com.shreenipane.orbit.core.model.ClassOverride) -> Unit = {},
) {
    var acting by remember { mutableStateOf<ClassSession?>(null) }
    // Re-evaluate "now" every 30 s so the Now/Next card moves on its own.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now() } }

    val today = now.toLocalDate()
    val minute = now.hour * 60 + now.minute
    val todayCal = snap.calendar?.days?.firstOrNull { it.date == today.toString() }
    val todayOrder = todayCal?.dayOrder
    // Holidays and exam days on the academic calendar have no classes.
    val dayOff = !com.shreenipane.orbit.core.calc.ClassDays.runs(snap.calendar, today)
    // Today's classes with your changes applied: cancelled ones stay listed (crossed out), extras are added.
    val dayClasses = if (dayOff) emptyList() else com.shreenipane.orbit.core.calc.Overrides.day(
        Schedule.forDay(snap.timetable.sessions, todayOrder ?: today.dayOfWeek.value), today, overrides)
    val active = dayClasses.filterNot { it.cancelled }.map { it.session }
    fun mins(t: String) = t.substring(0, 2).toInt() * 60 + t.substring(3, 5).toInt()
    val nextUp = active.firstOrNull { mins(it.start) > minute }
    val sched = Schedule.Now(
        ongoing = active.firstOrNull { minute >= mins(it.start) && minute < mins(it.end) },
        next = nextUp,
        today = active,
        minutesToNext = nextUp?.let { mins(it.start) - minute },
    )
    val remainingAll = remember(snap, today, overrides) { ExamPlanner.remainingAll(snap.calendar, snap.timetable.sessions, today, minute, overrides) }
    val low = snap.attendance.filter { AttendanceCalc.percent(it.attended, it.total) < 75.0 }
    // Walks the whole calendar, so it's cached: recomputed when data, the day or the minute changes, not on every redraw.
    val countdown = remember(snap, today, minute, overrides) {
        ExamPlanner.countdowns(snap.calendar, snap.exams, snap.timetable.sessions, today, minute, overrides).firstOrNull()
    }
    val nextBreak = remember(snap.calendar, today) { com.shreenipane.orbit.core.calc.Breaks.upcoming(snap.calendar, today).firstOrNull() }
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
        // Exam week: the next paper within 7 days gets the top spot.
        val paper = remember(snap.exams, now.toLocalDate(), now.hour) {
            ExamTime.upcoming(snap.exams, now).firstOrNull()?.takeIf { (_, at) -> !at.toLocalDate().isAfter(now.toLocalDate().plusDays(7)) }
        }
        if (paper != null) {
            item { ExamCard(paper.first, paper.second, now) }
        } else if (countdown != null && countdown.daysLeft <= 7) {
            item {
                Hero(Pastel.Salmon, Modifier.padding(bottom = 14.dp)) {
                    Tag(if (countdown.daysLeft == 0L) "${countdown.label} starts today" else "${countdown.label} in ${countdown.daysLeft} days",
                        com.shreenipane.orbit.ui.theme.Bad)
                    Spacer(Modifier.height(8.dp))
                    Text("Exam week is coming", style = MaterialTheme.typography.headlineMedium, color = Pastel.Ink)
                    Text("Paper dates appear here once VTOP publishes the schedule.", style = MaterialTheme.typography.bodyMedium, color = Pastel.Ink)
                }
            }
        }
        item { NowCard(sched) }

        item {
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(overall?.let { "${Math.round(it)}%" } ?: "—", "Attendance", Modifier.weight(1f),
                    fill = overall?.let { com.shreenipane.orbit.ui.theme.pastelFor(attendanceColor(it)) } ?: Pastel.Yellow)
                Metric(countdown?.let { "${it.daysLeft}d" } ?: "—", countdown?.let { "To ${it.label}" } ?: "Next exam", Modifier.weight(1f),
                    fill = Pastel.Sky)
            }
        }

        section(header = "Classes today", action = "Calendar" to { onOpen(MoreDestination.Calendar) }) {
            if (dayClasses.isEmpty()) {
                ListRow("No classes today", todayCal?.let { com.shreenipane.orbit.core.calc.Breaks.holidayName(it) })
            } else {
                Rows(dayClasses, inset = 82.dp) { dc ->
                    androidx.compose.foundation.layout.Box(Modifier.clickable { acting = dc.session }) {
                        ClassRow(dc.session, isNow = dc.session == sched.ongoing, struck = dc.cancelled,
                            trailing = if (dc.cancelled) "Cancelled" else if (dc.extra) "Extra" else null)
                    }
                }
            }
        }

        if (countdown != null || pendingDas.isNotEmpty() || nextBreak != null) {
            section(header = "Coming up", action = "See all" to { onOpen(MoreDestination.Exams) }) {
                if (countdown != null) {
                    ListRow(
                        title = "${countdown.label} in ${countdown.daysLeft} ${if (countdown.daysLeft == 1L) "day" else "days"}",
                        subtitle = "${countdown.start.format(SHORT)} · ${countdown.totalClasses} classes before it",
                        onClick = { onOpen(MoreDestination.Exams) },
                        chevron = true,
                    )
                }
                if (nextBreak != null) {
                    if (countdown != null) RowDivider()
                    ListRow(
                        title = nextBreak.title,
                        subtitle = com.shreenipane.orbit.ui.holidays.breakRange(nextBreak) + " · ${nextBreak.days} ${if (nextBreak.days == 1) "day" else "days"} off",
                        onClick = { onOpen(MoreDestination.Holidays) },
                        chevron = true,
                    )
                }
                pendingDas.take(3).forEachIndexed { i, (da, due) ->
                    if (i > 0 || countdown != null || nextBreak != null) RowDivider()
                    val days = ChronoUnit.DAYS.between(today, due)
                    ListRow(
                        title = da.title,
                        subtitle = da.courseTitle,
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
                        com.shreenipane.orbit.ui.attendance.attendancePlan(a, remaining).tag,
                        trailing = { Text("${a.vtopPercent}%", fontWeight = FontWeight.SemiBold, color = Bad) },
                    )
                }
            }
        }

        item {
            Text(
                "Synced ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(snap.fetchedAtMillis))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, start = 4.dp),
            )
        }
    }

    acting?.let { s ->
        com.shreenipane.orbit.ui.timetable.ClassActionSheet(s, today, overrides, onToggleCancel, onRemoveOverride) { acting = null }
    }
}

@Composable
private fun ExamCard(e: com.shreenipane.orbit.core.model.Exam, at: LocalDateTime, now: LocalDateTime) {
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())
    Hero(Pastel.Salmon, Modifier.padding(bottom = 14.dp)) {
        Tag("${e.examType} · " + when (days) { 0L -> "today"; 1L -> "tomorrow"; else -> "in $days days" }, com.shreenipane.orbit.ui.theme.Bad)
        Spacer(Modifier.height(8.dp))
        Text(e.courseTitle, style = MaterialTheme.typography.headlineMedium, color = Pastel.Ink)
        Text(at.format(DateTimeFormatter.ofPattern("EEE d MMM · h:mm a")), style = MaterialTheme.typography.bodyMedium, color = Pastel.Ink)
        val where = listOf(e.venue, e.seatLocation.takeIf { it.isNotBlank() }?.let { "Seat $it" }).filterNotNull().filter { it.isNotBlank() }
        if (where.isNotEmpty()) Text(where.joinToString(" · "), style = MaterialTheme.typography.titleMedium, color = Pastel.Ink)
        if (e.reportingTime.isNotBlank()) {
            Text("Report by ${e.reportingTime}", style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted)
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
                    Text("Then ${next.courseTitle} at ${next.start}", style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted)
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
fun ClassRow(s: ClassSession, isNow: Boolean, trailing: String? = null, struck: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        TimeColumn(s.start, s.end)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isNow) { Dot(com.shreenipane.orbit.ui.theme.Pastel.Green); Spacer(Modifier.width(6.dp)) }
                Text(s.courseTitle, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textDecoration = if (struck) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    color = if (struck) Pastel.InkMuted else Pastel.Ink)
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
