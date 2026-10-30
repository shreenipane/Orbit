@file:OptIn(ExperimentalMaterial3Api::class)

package com.shreenipane.orbit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.shreenipane.orbit.core.calc.ExamTime
import com.shreenipane.orbit.core.model.Reminder
import com.shreenipane.orbit.core.calc.GradeCalc
import com.shreenipane.orbit.core.calc.Schedule
import com.shreenipane.orbit.core.model.ClassSession
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyState
import com.shreenipane.orbit.ui.components.Pill
import com.shreenipane.orbit.ui.components.SectionTitle
import com.shreenipane.orbit.ui.components.Stat
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.attendanceColor
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.time.LocalDateTime
import java.util.Date

@Composable
fun HomeScreen(snap: Snapshot, reminders: List<Reminder>, alias: String, refreshing: Boolean, onRefresh: () -> Unit) {
    // Re-evaluate "now" every 30 s so the ongoing/next card moves on its own.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now() } }

    val day = now.dayOfWeek.value
    val minute = now.hour * 60 + now.minute
    val sched = Schedule.now(snap.timetable.sessions, day, minute)
    val low = snap.attendance.filter { AttendanceCalc.percent(it.attended, it.total) < 75.0 }
    val standing = snap.grades?.let(GradeCalc::standing)
    val nextExam = ExamTime.upcoming(snap.exams, now).firstOrNull()
    val nowMillis = System.currentTimeMillis()
    val dueSoon = reminders.filter { !it.done && it.dueAtMillis < nowMillis + 3 * 86_400_000L }.take(3)

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column {
                    Text(greeting(now.hour) + if (alias.isNotBlank()) ", $alias" else "", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${snap.regNo} · synced ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(snap.fetchedAtMillis))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { NowCard(sched) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(Modifier.weight(1f)) {
                        Stat(standing?.let { "%.2f".format(it.cgpa) } ?: "—", "CGPA", Modifier.padding(16.dp))
                    }
                    Card(Modifier.weight(1f)) {
                        val overall = snap.attendance.let { a ->
                            val att = a.sumOf { it.attended }; val tot = a.sumOf { it.total }
                            if (tot == 0) "—" else "%.0f%%".format(AttendanceCalc.percent(att, tot))
                        }
                        Stat(overall, "Overall attendance", Modifier.padding(16.dp))
                    }
                }
            }

            if (nextExam != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${nextExam.first.examType} · ${nextExam.first.courseTitle}", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    nextExam.second.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a")) +
                                        nextExam.first.venue.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(ExamTime.countdown(nextExam.second, now), fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            if (dueSoon.isNotEmpty()) {
                item { SectionTitle("Due soon") }
                items(dueSoon) { r ->
                    val overdue = r.dueAtMillis < nowMillis
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title, style = MaterialTheme.typography.titleMedium)
                                r.courseCode?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            Text(
                                if (overdue) "Overdue" else java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(Date(r.dueAtMillis)),
                                color = if (overdue) Bad else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (low.isNotEmpty()) {
                item { SectionTitle("Below 75%") }
                items(low) { a ->
                    val need = AttendanceCalc.neededInARow(a.attended, a.total)
                    Card(colors = CardDefaults.cardColors(containerColor = Bad.copy(alpha = 0.10f))) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.courseTitle, style = MaterialTheme.typography.titleMedium)
                                Text("Attend the next $need in a row to get back to 75%", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${a.vtopPercent}%", fontWeight = FontWeight.Black, color = Bad)
                        }
                    }
                }
            }

            item { SectionTitle("Today") }
            if (sched.today.isEmpty()) {
                item { EmptyState("🌤️", "No classes today", "Enjoy it. Pull down to refresh if that looks wrong.") }
            } else {
                items(sched.today) { s ->
                    val att = snap.attendance.firstOrNull { it.courseCode == s.courseCode && it.slot.contains(s.slot.substringBefore('+')) }
                    ClassRow(s, isNow = s == sched.ongoing, attendance = att?.let { AttendanceCalc.percent(it.attended, it.total) })
                }
            }
        }
    }
}

private fun greeting(hour: Int) = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun NowCard(s: Schedule.Now) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            val ongoing = s.ongoing
            val next = s.next
            when {
                ongoing != null -> {
                    Pill("NOW", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(4.dp))
                    Text(ongoing.courseTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                    Text("${ongoing.venue} · until ${ongoing.end}", style = MaterialTheme.typography.bodyMedium)
                    if (next != null) Text("Then ${next.courseTitle} at ${next.start}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
                next != null -> {
                    Pill("NEXT · in ${humanMinutes(s.minutesToNext ?: 0)}", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                    Text(next.courseTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                    Text("${next.venue} · ${next.start}–${next.end}", style = MaterialTheme.typography.bodyMedium)
                }
                s.today.isNotEmpty() -> {
                    Text("Done for the day 🎉", style = MaterialTheme.typography.titleLarge)
                    Text("${s.today.size} classes today", style = MaterialTheme.typography.bodyMedium)
                }
                else -> Text("Free day ✨", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

private fun humanMinutes(m: Int) = if (m < 60) "$m min" else "${m / 60}h ${m % 60}m"

@Composable
fun ClassRow(s: ClassSession, isNow: Boolean, attendance: Double?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isNow) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(64.dp)) {
                Text(s.start, fontWeight = FontWeight.Bold)
                Text(s.end, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(s.courseTitle, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${s.courseCode} · ${s.slot} · ${s.venue}" + if (s.isLab) " · Lab" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (attendance != null) {
                Text("%.0f%%".format(attendance), fontWeight = FontWeight.Bold, color = attendanceColor(attendance))
            }
        }
    }
}
