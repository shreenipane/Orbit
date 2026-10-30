package com.shreenipane.orbit.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.Breaks
import com.shreenipane.orbit.core.calc.ExamPlanner
import com.shreenipane.orbit.core.calc.Overrides
import com.shreenipane.orbit.core.calc.Schedule
import com.shreenipane.orbit.core.model.CalendarDay
import com.shreenipane.orbit.core.model.ClassOverride
import com.shreenipane.orbit.core.model.ClassSession
import com.shreenipane.orbit.core.model.DayKind
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.BrutalButton
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.brutal
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.home.ClassRow
import com.shreenipane.orbit.ui.theme.Pastel
import com.shreenipane.orbit.ui.timetable.ClassActionSheet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val WEEK = listOf("S", "M", "T", "W", "T", "F", "S")
private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

private fun kindColor(k: DayKind?): Color? = when (k) {
    DayKind.INSTRUCTIONAL -> Pastel.Green
    DayKind.HOLIDAY -> Pastel.Salmon
    DayKind.EXAM -> Pastel.Sky
    DayKind.NO_INSTRUCTION -> Pastel.Lavender
    else -> null
}

/** The academic calendar as a month grid; tap a day to see what's on. */
@Composable
fun CalendarScreen(
    snap: Snapshot,
    overrides: List<ClassOverride>,
    onToggleCancel: (LocalDate, ClassSession) -> Unit,
    onRemoveOverride: (ClassOverride) -> Unit,
) {
    val today = LocalDate.now()
    val cal = snap.calendar
    val byDate = remember(cal) { cal?.days?.associateBy { it.date }.orEmpty() }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    var selectedText by rememberSaveable { mutableStateOf(today.toString()) }
    val selected = LocalDate.parse(selectedText)
    var acting by remember { mutableStateOf<ClassSession?>(null) }

    OrbitScreen(title = "Calendar", subtitle = cal?.classGroupName) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BrutalButton("‹", { monthText = month.minusMonths(1).toString() }, fill = Pastel.Card)
                Text(month.format(MONTH), style = MaterialTheme.typography.titleLarge, color = Pastel.Ink,
                    modifier = Modifier.weight(1f).clickable {
                        monthText = YearMonth.from(today).toString(); selectedText = today.toString()
                    }.padding(horizontal = 12.dp), maxLines = 1)
                BrutalButton("›", { monthText = month.plusMonths(1).toString() }, fill = Pastel.Card)
            }
        }

        item {
            MonthGrid(month, today, selected, byDate) { selectedText = it.toString() }
        }

        item { Legend() }

        val cd = byDate[selected.toString()]
        val dayOff = !com.shreenipane.orbit.core.calc.ClassDays.runs(snap.calendar, selected)
        val classes = if (dayOff) emptyList()
        else Overrides.day(Schedule.forDay(snap.timetable.sessions, cd?.dayOrder ?: selected.dayOfWeek.value), selected, overrides)

        section(header = selected.format(DAY)) {
            // Only say what the day is when VTOP's calendar actually says something.
            val known = cd != null && cd.kind != DayKind.UNKNOWN
            if (known) ListRow(describe(cd, selected), dayNote(cd, selected))
            if (classes.isEmpty() && !known) ListRow("No classes", null)
            if (classes.isNotEmpty()) {
                if (known) com.shreenipane.orbit.ui.components.RowDivider()
                Rows(classes, inset = 82.dp) { dc ->
                    Box(Modifier.clickable { acting = dc.session }) {
                        ClassRow(dc.session, isNow = false, struck = dc.cancelled,
                            trailing = if (dc.cancelled) "Cancelled" else if (dc.extra) "Extra" else null)
                    }
                }
            }
        }

        if (cal == null) {
            item {
                Text("Sync to load VTOP's academic calendar.", style = MaterialTheme.typography.bodySmall,
                    color = Pastel.InkMuted, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
            }
        }
    }

    acting?.let { s -> ClassActionSheet(s, selected, overrides, onToggleCancel, onRemoveOverride) { acting = null } }
}

@Composable
private fun MonthGrid(month: YearMonth, today: LocalDate, selected: LocalDate, byDate: Map<String, CalendarDay>, onPick: (LocalDate) -> Unit) {
    // Weeks start on Sunday, like VTOP's calendar.
    val lead = month.atDay(1).dayOfWeek.value % 7
    val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(Modifier.fillMaxWidth().brutal(Pastel.Card).padding(horizontal = 8.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEK.forEach { d ->
                Text(d, style = MaterialTheme.typography.labelLarge, color = Pastel.InkMuted,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(Modifier.height(6.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val date = week.getOrNull(i)
                    Box(Modifier.weight(1f).height(52.dp), contentAlignment = Alignment.Center) {
                        if (date != null) DayCell(date, date == today, date == selected, byDate[date.toString()]?.kind) { onPick(date) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, isToday: Boolean, isSelected: Boolean, kind: DayKind?, onClick: () -> Unit) {
    Column(
        Modifier.size(44.dp).clip(CircleShape)
            .then(if (isToday) Modifier.background(Pastel.Yellow) else Modifier)
            .then(if (isSelected) Modifier.border(2.dp, Pastel.Line, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${date.dayOfMonth}", style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (kind == null && date.dayOfWeek == DayOfWeek.SUNDAY) Pastel.InkMuted else Pastel.Ink,
        )
        val c = kindColor(kind)
        Box(Modifier.padding(top = 2.dp).size(7.dp).clip(CircleShape)
            .then(if (c != null) Modifier.background(c).border(1.dp, Pastel.Line, CircleShape) else Modifier))
    }
}

@Composable
private fun Legend() {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(DayKind.INSTRUCTIONAL to "Classes", DayKind.EXAM to "Exam", DayKind.HOLIDAY to "Holiday", DayKind.NO_INSTRUCTION to "No class")
            .forEach { (k, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(kindColor(k) ?: Pastel.Card).border(1.dp, Pastel.Line, CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Pastel.InkMuted)
                }
            }
    }
}

private fun describe(cd: CalendarDay?, date: LocalDate): String {
    if (cd == null) return if (date.dayOfWeek == DayOfWeek.SUNDAY) "Sunday" else "Not on the calendar"
    return when (cd.kind) {
        DayKind.INSTRUCTIONAL -> "Class day"
        DayKind.EXAM -> ExamPlanner.EXAMS.toMap()[cd.exam] ?: "Exam"
        DayKind.HOLIDAY -> Breaks.holidayName(cd) ?: "Holiday"
        DayKind.NO_INSTRUCTION -> Breaks.holidayName(cd) ?: "No classes"
        DayKind.UNKNOWN -> if (date.dayOfWeek == DayOfWeek.SUNDAY) "Sunday" else "Not on the calendar"
    }
}

private fun dayNote(cd: CalendarDay?, date: LocalDate): String? {
    val order = cd?.dayOrder ?: return null
    return if (order != date.dayOfWeek.value) "Follows ${DAY_NAMES[order - 1]}'s timetable" else null
}
