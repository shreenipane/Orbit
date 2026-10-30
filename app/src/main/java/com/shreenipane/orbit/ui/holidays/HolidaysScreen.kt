package com.shreenipane.orbit.ui.holidays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.Breaks
import com.shreenipane.orbit.core.calc.ExamPlanner
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyNote
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.Metric
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.section
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM")

/** "Sat, 7 Nov – Mon, 9 Nov" or a single day. */
fun breakRange(b: Breaks.Break): String =
    if (b.start == b.end) b.start.format(DAY) else "${b.start.format(DAY)} – ${b.end.format(DAY)}"

/** "in 8 days", "tomorrow", "on now · back Tue, 10 Nov". */
fun breakWhen(b: Breaks.Break, today: LocalDate): String {
    val until = ChronoUnit.DAYS.between(today, b.start)
    return when {
        until <= 0 -> "on now · back ${b.end.plusDays(1).format(DAY)}"
        until == 1L -> "starts tomorrow"
        else -> "in $until days"
    }
}

@Composable
fun HolidaysScreen(snap: Snapshot) {
    val today = LocalDate.now()
    val cal = snap.calendar
    val upcoming = Breaks.upcoming(cal, today)

    OrbitScreen(title = "Holidays", subtitle = cal?.classGroupName) {
        if (cal == null) {
            item { EmptyNote("Calendar not loaded", "Sign in again so Orbit can read VTOP's academic calendar.") }
            return@OrbitScreen
        }
        val next = upcoming.firstOrNull()
        if (next == null) {
            item { EmptyNote("No more holidays", "The academic calendar has no breaks left this semester.") }
        } else {
            item {
                com.shreenipane.orbit.ui.components.Hero(com.shreenipane.orbit.ui.theme.Pastel.Mint) {
                    com.shreenipane.orbit.ui.components.Tag("Next holiday · ${breakWhen(next, today)}", com.shreenipane.orbit.ui.theme.Good)
                    Spacer(Modifier.height(10.dp))
                    Text(next.title, style = MaterialTheme.typography.headlineMedium)
                    Text(breakRange(next), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("${next.days}", if (next.days == 1) "day off" else "days off", Modifier.weight(1f), fill = com.shreenipane.orbit.ui.theme.Pastel.Yellow)
                    Metric("${next.weekdaysOff}", "weekdays off", Modifier.weight(1f), fill = com.shreenipane.orbit.ui.theme.Pastel.Lavender)
                    Metric("${ChronoUnit.DAYS.between(today, next.start).coerceAtLeast(0)}", "days to go", Modifier.weight(1f), fill = com.shreenipane.orbit.ui.theme.Pastel.Sky)
                }
            }
        }

        if (upcoming.size > 1) {
            section(header = "Later this semester") {
                Rows(upcoming.drop(1)) { b ->
                    ListRow(
                        b.title,
                        "${breakRange(b)} · ${breakWhen(b, today)}",
                        trailing = { Text("${b.days}d", style = MaterialTheme.typography.titleMedium) },
                    )
                }
            }
        }

        val end = ExamPlanner.semesterEnd(cal)?.minusDays(1)
        if (end != null && !end.isBefore(today)) {
            section(footer = "Breaks group consecutive days without classes (holidays, no-instruction days and the weekends around them). A plain weekend isn't listed.") {
                ListRow("Last instructional day", "${end.format(DAY)} · in ${ChronoUnit.DAYS.between(today, end)} days")
            }
        }
    }
}
