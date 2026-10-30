package com.shreenipane.orbit.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.BunkPlanner
import com.shreenipane.orbit.core.model.ClassOverride
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyNote
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.Metric
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.Tag
import com.shreenipane.orbit.ui.components.brutal
import com.shreenipane.orbit.ui.components.labLabel
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Good
import com.shreenipane.orbit.ui.theme.Pastel
import com.shreenipane.orbit.ui.theme.Warn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DOW = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

/** Pick days you want off; see each course's attendance after missing every class on them. */
@Composable
fun BunkScreen(snap: Snapshot, overrides: List<ClassOverride>) {
    val today = LocalDate.now()
    val days = remember(today) { (0L..20L).map { today.plusDays(it) } }
    val counts = remember(snap, overrides, today) {
        days.associateWith { BunkPlanner.classesOn(snap.calendar, snap.timetable.sessions, it, overrides).size }
    }
    var picked by remember { mutableStateOf(setOf<LocalDate>()) }
    val result = remember(picked, snap, overrides) {
        BunkPlanner.project(snap.attendance, snap.timetable.sessions, snap.calendar, picked, overrides)
    }

    OrbitScreen(title = "Bunk planner", subtitle = "Pick the days you want off") {
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEach { d ->
                    val n = counts[d] ?: 0
                    val on = d in picked
                    Column(
                        Modifier.width(58.dp)
                            .brutal(if (on) Pastel.Accent else if (n == 0) Pastel.CardLight else Pastel.Card, radius = 12.dp,
                                shadow = if (on) 3.dp else 1.dp, border = 1.5.dp)
                            .clickable(enabled = n > 0) { picked = if (on) picked - d else picked + d }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(d.format(DOW), style = MaterialTheme.typography.labelMedium, color = Pastel.InkMuted)
                        Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleLarge, color = Pastel.Ink)
                        Text(if (n == 0) "off" else "$n cls", style = MaterialTheme.typography.labelSmall, color = Pastel.InkMuted)
                    }
                }
            }
        }

        if (picked.isEmpty()) {
            item { EmptyNote("Tap the days you want off", "Orbit shows where each course would land if you skipped every class on them.",
                Modifier.padding(top = 12.dp)) }
            return@OrbitScreen
        }

        val missed = result.sumOf { it.missed }
        val under = result.count { it.below }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("${picked.size}", if (picked.size == 1) "Day off" else "Days off", Modifier.weight(1f), fill = Pastel.Sky)
                Metric("$missed", "Classes missed", Modifier.weight(1f), fill = Pastel.Yellow)
                Metric("$under", "Under 75%", Modifier.weight(1f), fill = if (under > 0) Pastel.Salmon else Pastel.Green)
            }
        }
        section(header = "If you skip them") {
            Rows(result) { p ->
                ListRow(
                    p.record.courseTitle,
                    "${labLabel(com.shreenipane.orbit.core.calc.ExamPlanner.isLab(p.record))} · ${Math.round(p.before)}% → ${Math.round(p.after)}% · misses ${p.missed}",
                    trailing = {
                        Tag("${Math.round(p.after)}%", when {
                            p.dropsBelow -> Bad
                            p.below -> Warn
                            else -> Good
                        })
                    },
                )
            }
        }
    }
}
