package com.shreenipane.orbit.ui.timetable

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.AttendanceCalc
import com.shreenipane.orbit.core.calc.Schedule
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyNote
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.Segmented
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.home.ClassRow
import java.time.LocalDate

private val SHORT = listOf("M", "T", "W", "T", "F", "S", "S")
private val NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@Composable
fun TimetableScreen(snap: Snapshot, refreshing: Boolean, onRefresh: () -> Unit) {
    val sessions = snap.timetable.sessions
    val days = (1..7).filter { it <= 5 || sessions.any { s -> s.day == it } }
    val today = LocalDate.now().dayOfWeek.value
    var selected by rememberSaveable { mutableIntStateOf(days.indexOf(today).coerceAtLeast(0)) }
    val sel = selected.coerceIn(0, days.lastIndex)
    val day = days[sel]
    val list = Schedule.forDay(sessions, day)

    OrbitScreen(title = "Timetable", subtitle = snap.semester?.name, refreshing = refreshing, onRefresh = onRefresh) {
        if (sessions.isEmpty()) {
            item { EmptyNote("No timetable", "VTOP didn't return a timetable for this semester. Pick another semester in Settings.") }
            return@OrbitScreen
        }
        item {
            Segmented(days.map { SHORT[it - 1] }, sel, { selected = it }, Modifier.padding(top = 4.dp))
        }
        section(header = NAMES[day - 1] + if (day == today) " · Today" else "") {
            if (list.isEmpty()) {
                ListRow("No classes", null)
            } else {
                Rows(list, inset = 82.dp) { s ->
                    val att = snap.attendance.firstOrNull { it.courseCode == s.courseCode && it.slot.contains(s.slot.substringBefore('+')) }
                    ClassRow(s, isNow = false, trailing = att?.let { "%.0f%%".format(AttendanceCalc.percent(it.attended, it.total)) })
                }
            }
        }
        section(header = "Courses", footer = "${snap.timetable.courses.sumOf { it.credits }.let { "%.0f".format(it) }} credits this semester") {
            Rows(snap.timetable.courses) { c ->
                ListRow(
                    c.title,
                    listOf(c.code, c.type, c.slot, c.venue).filter { it.isNotBlank() }.joinToString(" · "),
                    trailing = { androidx.compose.material3.Text("%.0f cr".format(c.credits), style = androidx.compose.material3.MaterialTheme.typography.bodySmall) },
                )
            }
        }
    }
}
