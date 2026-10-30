@file:OptIn(ExperimentalMaterial3Api::class)

package com.shreenipane.orbit.ui.attendance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.AttendanceCalc
import com.shreenipane.orbit.core.calc.ExamPlanner
import com.shreenipane.orbit.core.model.AttendanceRecord
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyNote
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.Tag
import com.shreenipane.orbit.ui.components.ThinRing
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Good
import com.shreenipane.orbit.ui.theme.Warn
import com.shreenipane.orbit.ui.theme.attendanceColor
import java.time.LocalDateTime

@Composable
fun AttendanceScreen(snap: Snapshot, refreshing: Boolean, onRefresh: () -> Unit) {
    var open by remember { mutableStateOf<Pair<AttendanceRecord, Int?>?>(null) }
    val now = LocalDateTime.now()
    val minute = now.hour * 60 + now.minute
    val overall = snap.attendance.let { a ->
        val att = a.sumOf { it.attended }; val tot = a.sumOf { it.total }
        if (tot == 0) null else AttendanceCalc.percent(att, tot)
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
        val rows = snap.attendance
            .map { it to ExamPlanner.remainingFor(it, snap.calendar, snap.timetable.sessions, now.toLocalDate(), minute) }
            .sortedBy { AttendanceCalc.percent(it.first.attended, it.first.total) }
        section(
            header = "Courses",
            footer = "Counts use the 75% rule. " + if (snap.calendar != null)
                "\"Left\" counts instructional days in the ${snap.calendar.classGroupName} calendar, one class per timetable block."
            else "Sign in again to load the academic calendar for classes-left counts.",
        ) {
            Rows(rows, inset = 74.dp) { (a, remaining) -> AttendanceRow(a, remaining) { open = a to remaining } }
        }
    }

    open?.let { (a, remaining) -> PlannerSheet(a, remaining) { open = null } }
}

@Composable
private fun AttendanceRow(a: AttendanceRecord, remaining: Int?, onClick: () -> Unit) {
    val pct = AttendanceCalc.percent(a.attended, a.total)
    val skip = AttendanceCalc.skippable(a.attended, a.total)
    val need = AttendanceCalc.neededInARow(a.attended, a.total)
    ListRow(
        title = a.courseTitle,
        subtitle = buildString {
            append("${a.attended}/${a.total} · ${a.courseType.ifBlank { a.slot }}")
            if (remaining != null) {
                val miss = AttendanceCalc.missableOfRemaining(a.attended, a.total, remaining)
                append("\n$remaining left this semester · ")
                append(if (miss < 0) "75% out of reach" else "can miss $miss")
            }
        },
        leading = { ThinRing(a.vtopPercent / 100f, attendanceColor(pct), "${a.vtopPercent}") },
        trailing = {
            when {
                need > 0 -> Tag("Attend $need", Bad)
                skip > 0 -> Tag("Skip $skip", Good)
                else -> Tag("On the line", Warn)
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun PlannerSheet(a: AttendanceRecord, remaining: Int?, onDismiss: () -> Unit) {
    var present by remember { mutableIntStateOf(0) }
    var absent by remember { mutableIntStateOf(0) }
    val projected = AttendanceCalc.projected(a.attended, a.total, present, absent)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(a.courseTitle, style = MaterialTheme.typography.titleLarge)
            Text("${a.courseCode} · ${a.slot} · ${a.faculty}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThinRing((projected / 100).toFloat(), attendanceColor(projected), "%.1f".format(projected), size = 84.dp, stroke = 6.dp)
                Spacer(Modifier.width(20.dp))
                Column {
                    Text("Attend $present, miss $absent", style = MaterialTheme.typography.bodyMedium)
                    Text("${a.attended + present}/${a.total + present + absent} classes", fontWeight = FontWeight.SemiBold)
                    if (remaining != null) {
                        Text("$remaining classes left this semester", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Stepper("Attend", present) { present = it }
            Stepper("Miss", absent) { absent = it }
            if (a.debarStatus.isNotBlank() && a.debarStatus != "-") {
                Spacer(Modifier.height(12.dp))
                Text("VTOP debar status: ${a.debarStatus}", color = Bad)
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { if (value > 0) onChange(value - 1) }) { Icon(Icons.Rounded.Remove, "Less") }
        Text("$value", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
        FilledTonalIconButton(onClick = { onChange(value + 1) }) { Icon(Icons.Rounded.Add, "More") }
    }
}
