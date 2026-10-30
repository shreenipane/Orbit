@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.shreenipane.orbit.ui.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.model.Reminder
import com.shreenipane.orbit.core.model.ReminderKind
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyState
import com.shreenipane.orbit.ui.components.Pill
import com.shreenipane.orbit.ui.components.SectionTitle
import com.shreenipane.orbit.ui.theme.Bad
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val WHEN = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a")
private fun Long.toLocal(): LocalDateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

@Composable
fun RemindersScreen(
    snap: Snapshot,
    reminders: List<Reminder>,
    onSave: (Reminder) -> Unit,
    onToggle: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
) {
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var creating by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val pending = reminders.filter { !it.done }
    val done = reminders.filter { it.done }

    Box(Modifier.fillMaxSize()) {
        if (reminders.isEmpty()) {
            EmptyState("⏰", "Nothing to remember yet", "Add assignments and reminders here. Orbit will notify you when they're due.")
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pending.isNotEmpty()) item { SectionTitle("Coming up") }
                items(pending) { r -> ReminderRow(r, overdue = r.dueAtMillis < now, onToggle, onDelete) { editing = r } }
                if (done.isNotEmpty()) item { SectionTitle("Done") }
                items(done.sortedByDescending { it.dueAtMillis }) { r -> ReminderRow(r, overdue = false, onToggle, onDelete) { editing = r } }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Add") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    if (creating || editing != null) {
        val courseCodes = snap.timetable.courses.map { it.code to it.title }.distinctBy { it.first }
        EditDialog(
            initial = editing,
            courses = courseCodes,
            onDismiss = { creating = false; editing = null },
            onSave = { onSave(it); creating = false; editing = null },
        )
    }
}

@Composable
private fun ReminderRow(r: Reminder, overdue: Boolean, onToggle: (Reminder) -> Unit, onDelete: (Reminder) -> Unit, onEdit: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = r.done, onCheckedChange = { onToggle(r) })
            Column(Modifier.weight(1f)) {
                Text(
                    r.title, style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (r.done) TextDecoration.LineThrough else null,
                )
                Text(
                    listOfNotNull(r.dueAtMillis.toLocal().format(WHEN), r.courseCode).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) Bad else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (r.kind == ReminderKind.ASSIGNMENT) {
                    Spacer(Modifier.height(4.dp))
                    Pill("Assignment", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onSurface)
                }
            }
            IconButton(onClick = { onDelete(r) }) { Icon(Icons.Rounded.Delete, "Delete") }
        }
    }
}

@Composable
private fun EditDialog(
    initial: Reminder?,
    courses: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
) {
    val start = initial?.dueAtMillis?.toLocal() ?: LocalDate.now().plusDays(1).atTime(18, 0)
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.kind ?: ReminderKind.ASSIGNMENT) }
    var course by remember { mutableStateOf(initial?.courseCode) }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(start.toLocalTime()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New reminder" else "Edit reminder") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(kind == ReminderKind.ASSIGNMENT, { kind = ReminderKind.ASSIGNMENT }, { Text("Assignment") })
                    FilterChip(kind == ReminderKind.REMINDER, { kind = ReminderKind.REMINDER }, { Text("Reminder") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate = true }) { Text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))) }
                    OutlinedButton(onClick = { pickTime = true }) { Text(time.format(DateTimeFormatter.ofPattern("h:mm a"))) }
                }
                if (courses.isNotEmpty()) {
                    Text("Course", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(course == null, { course = null }, { Text("None") })
                        courses.forEach { (code, name) ->
                            FilterChip(course == code, { course = code }, { Text(name.take(22)) })
                        }
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val due = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    onSave(Reminder(
                        id = initial?.id ?: System.currentTimeMillis(),
                        title = title.trim(), kind = kind, courseCode = course,
                        dueAtMillis = due, notes = notes.trim(), done = initial?.done ?: false,
                    ))
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (pickTime) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = { time = LocalTime.of(state.hour, state.minute); pickTime = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
        )
    }
}
