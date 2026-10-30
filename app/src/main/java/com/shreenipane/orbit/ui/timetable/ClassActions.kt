@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.shreenipane.orbit.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.model.ClassOverride
import com.shreenipane.orbit.core.model.ClassSession
import com.shreenipane.orbit.core.model.OverrideKind
import com.shreenipane.orbit.ui.components.BrutalButton
import com.shreenipane.orbit.ui.theme.Pastel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ofPattern("EEE, d MMM")

/** Next date (today or later) whose weekday is [isoDay]. */
fun nextDateFor(isoDay: Int, from: LocalDate = LocalDate.now()): LocalDate {
    var d = from
    while (d.dayOfWeek.value != isoDay) d = d.plusDays(1)
    return d
}

/**
 * Tap-a-class sheet: mark this class cancelled on a given date (or undo), or
 * remove an extra class. Counts of classes left update straight away.
 */
@Composable
fun ClassActionSheet(
    session: ClassSession,
    date: LocalDate,
    overrides: List<ClassOverride>,
    onToggleCancel: (LocalDate, ClassSession) -> Unit,
    onRemove: (ClassOverride) -> Unit,
    onDismiss: () -> Unit,
) {
    // Offer this date and the next three occurrences of the same weekday.
    val dates = remember(date) { (0..3).map { date.plusWeeks(it.toLong()) } }
    val extra = overrides.firstOrNull {
        it.kind == OverrideKind.EXTRA && it.date == date.toString() && it.courseCode == session.courseCode && it.start == session.start
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Pastel.Page,
    ) {
        Column(Modifier.padding(start = 24.dp, end = 28.dp, bottom = 32.dp)) {
            Text(session.courseTitle, style = MaterialTheme.typography.headlineMedium)
            Text("${session.start}–${session.end} · ${session.venue} · ${session.slot}", style = MaterialTheme.typography.bodyMedium,
                color = Pastel.InkMuted)
            Spacer(Modifier.height(18.dp))
            if (extra != null) {
                Text("This is an extra class you added.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                BrutalButton("Remove extra class", { onRemove(extra); onDismiss() }, Modifier.fillMaxWidth(), fill = Pastel.Salmon)
            } else {
                Text("Class cancelled? Mark it so classes-left counts and alerts skip it.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                dates.forEach { d ->
                    val cancelled = overrides.any {
                        it.kind == OverrideKind.CANCELLED && it.date == d.toString() && it.courseCode == session.courseCode &&
                            it.isLab == session.isLab && it.start == session.start
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        BrutalButton(
                            (if (cancelled) "Undo cancel · " else "Cancelled on ") + d.format(DATE),
                            { onToggleCancel(d, session) },
                            Modifier.fillMaxWidth(),
                            fill = if (cancelled) Pastel.Salmon else Pastel.Card,
                        )
                    }
                }
            }
        }
    }
}

/** Add a one-off extra class (a makeup or extra lecture). */
@Composable
fun ExtraClassDialog(
    courses: List<ClassSession>,
    onSave: (ClassOverride) -> Unit,
    onDismiss: () -> Unit,
) {
    val distinct = remember(courses) { courses.distinctBy { it.courseCode to it.isLab } }
    var pick by remember { mutableStateOf(distinct.firstOrNull()) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var start by remember { mutableStateOf("17:00") }
    var end by remember { mutableStateOf("17:50") }
    var venue by remember { mutableStateOf(pick?.venue.orEmpty()) }
    val timeOk = Regex("^\\d{2}:\\d{2}$")
    val dateOk = runCatching { LocalDate.parse(date) }.isSuccess

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extra class") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    distinct.forEach { s ->
                        FilterChip(pick == s, { pick = s; venue = s.venue }, { Text(s.courseTitle.take(20) + if (s.isLab) " (Lab)" else "") })
                    }
                }
                OutlinedTextField(date, { date = it.trim() }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, isError = !dateOk)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it.trim() }, label = { Text("Start HH:mm") }, singleLine = true,
                        isError = !timeOk.matches(start), modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it.trim() }, label = { Text("End HH:mm") }, singleLine = true,
                        isError = !timeOk.matches(end), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(venue, { venue = it }, label = { Text("Room") }, singleLine = true)
                Row {
                    OutlinedButton(onClick = { date = LocalDate.now().toString() }) { Text("Today") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = { date = LocalDate.now().plusDays(1).toString() }) { Text("Tomorrow") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pick != null && dateOk && timeOk.matches(start) && timeOk.matches(end) && start < end,
                onClick = {
                    val s = pick ?: return@TextButton
                    onSave(ClassOverride(System.currentTimeMillis(), date, OverrideKind.EXTRA, s.courseCode, s.courseTitle, s.isLab, start, end, venue))
                    onDismiss()
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
