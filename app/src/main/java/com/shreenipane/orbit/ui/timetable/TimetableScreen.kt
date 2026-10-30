package com.shreenipane.orbit.ui.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.AttendanceCalc
import com.shreenipane.orbit.core.calc.Overrides
import com.shreenipane.orbit.core.calc.Schedule
import com.shreenipane.orbit.core.model.ClassOverride
import com.shreenipane.orbit.core.model.ClassSession
import com.shreenipane.orbit.core.model.OverrideKind
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.BrutalButton
import com.shreenipane.orbit.ui.components.EmptyNote
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.Segmented
import com.shreenipane.orbit.ui.components.Tag
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.home.ClassRow
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Pastel
import com.shreenipane.orbit.ui.theme.Warn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val SHORT = listOf("M", "T", "W", "T", "F", "S", "S")
private val NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val DATE = DateTimeFormatter.ofPattern("EEE, d MMM")

@Composable
fun TimetableScreen(
    snap: Snapshot,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    overrides: List<ClassOverride> = emptyList(),
    onToggleCancel: (LocalDate, ClassSession) -> Unit = { _, _ -> },
    onAddOverride: (ClassOverride) -> Unit = {},
    onRemoveOverride: (ClassOverride) -> Unit = {},
) {
    val sessions = snap.timetable.sessions
    val days = (1..7).filter { it <= 5 || sessions.any { s -> s.day == it } }
    val today = LocalDate.now()
    var selected by rememberSaveable { mutableIntStateOf(days.indexOf(today.dayOfWeek.value).coerceAtLeast(0)) }
    val sel = selected.coerceIn(0, days.lastIndex)
    val day = days[sel]
    val date = nextDateFor(day, today)
    val list = Overrides.day(Schedule.forDay(sessions, day), date, overrides)
    var acting by remember { mutableStateOf<ClassSession?>(null) }
    var addingExtra by remember { mutableStateOf(false) }

    OrbitScreen(title = "Timetable", subtitle = snap.semester?.name, refreshing = refreshing, onRefresh = onRefresh) {
        if (sessions.isEmpty()) {
            item { EmptyNote("No timetable", "No timetable for this semester. Try another in Settings.") }
            return@OrbitScreen
        }
        item {
            Segmented(days.map { SHORT[it - 1] }, sel, { selected = it }, Modifier.padding(top = 4.dp))
        }
        section(
            header = NAMES[day - 1] + " · " + date.format(DATE) + if (date == today) " (today)" else "",
            footer = "Tap a class to cancel it.",
        ) {
            if (list.isEmpty()) ListRow("No classes", null)
            Rows(list, inset = 0.dp) { dc ->
                val s = dc.session
                val att = snap.attendance.firstOrNull { it.courseCode == s.courseCode && it.slot.contains(s.slot.substringBefore('+')) }
                androidx.compose.foundation.layout.Box(Modifier.clickable { acting = s }) {
                    ClassRow(
                        s, isNow = false,
                        trailing = when {
                            dc.cancelled -> "Cancelled"
                            dc.extra -> "Extra"
                            else -> att?.let { "%.0f%%".format(AttendanceCalc.percent(it.attended, it.total)) }
                        },
                        struck = dc.cancelled,
                    )
                }
            }
        }
        item {
            BrutalButton("+ Extra class", { addingExtra = true }, Modifier.padding(top = 16.dp), fill = Pastel.Yellow)
        }

        val upcoming = overrides.filter { it.date >= today.toString() }
        if (upcoming.isNotEmpty()) {
            section(header = "Your changes") {
                Rows(upcoming) { o ->
                    ListRow(
                        o.courseTitle + if (o.isLab) " (Lab)" else "",
                        "${LocalDate.parse(o.date).format(DATE)} · ${o.start}–${o.end}",
                        trailing = {
                            Tag(if (o.kind == OverrideKind.CANCELLED) "Cancelled" else "Extra",
                                if (o.kind == OverrideKind.CANCELLED) Bad else Warn)
                        },
                        onClick = { onRemoveOverride(o) },
                    )
                }
            }
            item {
                Text("Tap a change to undo it.", style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted,
                    modifier = Modifier.padding(top = 8.dp, start = 2.dp))
            }
        }

        section(header = "Courses", footer = "${snap.timetable.courses.sumOf { it.credits }.let { "%.0f".format(it) }} credits this semester") {
            Rows(snap.timetable.courses) { c ->
                ListRow(
                    c.title,
                    listOf(c.code, c.type, c.slot, c.venue).filter { it.isNotBlank() }.joinToString(" · "),
                    trailing = { Text("%.0f cr".format(c.credits), style = MaterialTheme.typography.bodySmall) },
                )
            }
        }
    }

    acting?.let { s -> ClassActionSheet(s, date, overrides, onToggleCancel, onRemoveOverride) { acting = null } }
    if (addingExtra) ExtraClassDialog(sessions, onAddOverride) { addingExtra = false }
}
