package com.shreenipane.orbit.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import com.shreenipane.orbit.ui.components.EmptyState
import com.shreenipane.orbit.ui.home.ClassRow
import java.time.LocalDate

private val NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
fun TimetableScreen(snap: Snapshot) {
    val sessions = snap.timetable.sessions
    // Weekdays always; weekends only when something is scheduled.
    val days = (1..7).filter { it <= 5 || sessions.any { s -> s.day == it } }
    val today = LocalDate.now().dayOfWeek.value
    var selected by rememberSaveable { mutableIntStateOf(days.indexOf(today).coerceAtLeast(0)) }

    if (sessions.isEmpty()) {
        EmptyState("🗓️", "No timetable yet", "VTOP didn't return a timetable for this semester. Try another semester from the top bar.")
        return
    }

    val sel = selected.coerceIn(0, days.lastIndex)
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = sel, edgePadding = 12.dp) {
            days.forEachIndexed { i, d ->
                Tab(
                    selected = i == sel,
                    onClick = { selected = i },
                    text = { Text(if (d == today) "${NAMES[d - 1]} •" else NAMES[d - 1]) },
                )
            }
        }
        val list = Schedule.forDay(sessions, days[sel])
        if (list.isEmpty()) {
            EmptyState("😴", "Nothing scheduled", "No classes on ${NAMES[days[sel] - 1]}.")
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list) { s ->
                    val att = snap.attendance.firstOrNull { it.courseCode == s.courseCode && it.slot.contains(s.slot.substringBefore('+')) }
                    ClassRow(s, isNow = false, attendance = att?.let { AttendanceCalc.percent(it.attended, it.total) })
                }
            }
        }
    }
}
