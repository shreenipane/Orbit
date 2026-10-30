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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.shreenipane.orbit.core.model.DigitalAssignment
import com.shreenipane.orbit.core.model.Reminder
import com.shreenipane.orbit.ui.DaUpload
import com.shreenipane.orbit.ui.components.brutal
import com.shreenipane.orbit.core.model.ReminderKind
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.core.calc.ExamTime
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.Tag
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Good
import com.shreenipane.orbit.ui.theme.Warn
import java.time.temporal.ChronoUnit
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
    daUpload: DaUpload = DaUpload.Idle,
    onUpload: (DigitalAssignment, Uri) -> Unit = { _, _ -> },
    onOtp: (String, String) -> Unit = { _, _ -> },
    onResetUpload: () -> Unit = {},
) {
    var openDa by remember { mutableStateOf<DigitalAssignment?>(null) }
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var creating by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val today = LocalDate.now()
    val pending = reminders.filter { !it.done }
    val done = reminders.filter { it.done }
    val das = snap.assignments.map { it to ExamTime.parseVtopDate(it.dueDate) }
    val daOpen = das.filter { !it.first.submitted && (it.second == null || !it.second!!.isBefore(today)) }
        .sortedBy { it.second ?: LocalDate.MAX }
    val daDone = das.filter { it.first.submitted }
    val daMissed = das.filter { !it.first.submitted && it.second != null && it.second!!.isBefore(today) }

    Box(Modifier.fillMaxSize()) {
        OrbitScreen(title = "Deadlines", subtitle = snap.semester?.name, bottomPadding = 96.dp) {
            section(
                header = "Digital assignments",
                footer = snap.warnings.firstOrNull { it.startsWith("Digital assignments") }?.let { "Couldn't load DAs. Pull to refresh on Today." }
                    ?: if (snap.assignments.isEmpty()) "No DAs on VTOP for this semester yet." else
                    "${daDone.size} submitted · ${daOpen.size} open" + if (daMissed.isNotEmpty()) " · ${daMissed.size} past due" else "",
            ) {
                if (daOpen.isEmpty()) ListRow(if (snap.assignments.isEmpty()) "Nothing posted" else "All caught up", null)
                Rows(daOpen) { (da, due) ->
                    val days = due?.let { ChronoUnit.DAYS.between(today, it) }
                    ListRow(
                        title = "${da.title} · ${da.courseTitle}",
                        subtitle = listOfNotNull(due?.format(DAY), da.courseCode, da.maxMark.takeIf { it.isNotBlank() }?.let { "$it marks" },
                            if (da.canUpload) "tap to upload" else null)
                            .joinToString(" · "),
                        onClick = { onResetUpload(); openDa = da },
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    when (days) { null -> "—"; 0L -> "Today"; 1L -> "1 day"; else -> "$days days" },
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                    color = when { days == null -> MaterialTheme.colorScheme.onSurfaceVariant; days <= 2 -> Bad; days <= 5 -> Warn; else -> MaterialTheme.colorScheme.onSurface },
                                )
                                Text("left", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                }
            }
            if (daMissed.isNotEmpty()) {
                section(header = "Past due, not uploaded") {
                    Rows(daMissed) { (da, due) ->
                        ListRow("${da.title} · ${da.courseTitle}", "Was due ${due?.format(DAY)}", titleColor = Bad, onClick = { onResetUpload(); openDa = da })
                    }
                }
            }
            if (daDone.isNotEmpty()) {
                section(header = "Submitted") {
                    Rows(daDone) { (da, _) ->
                        ListRow("${da.title} · ${da.courseTitle}", da.status, trailing = { Tag("Done", Good) }, onClick = { onResetUpload(); openDa = da })
                    }
                }
            }

            section(header = "Your reminders", action = "Add" to { creating = true }) {
                if (pending.isEmpty()) ListRow("No reminders", "Tap Add to set one. Orbit notifies you when it's due.")
                Rows(pending, inset = 56.dp) { r -> ReminderRow(r, overdue = r.dueAtMillis < now, onToggle, onDelete) { editing = r } }
            }
            if (done.isNotEmpty()) {
                section(header = "Done") {
                    Rows(done.sortedByDescending { it.dueAtMillis }, inset = 56.dp) { r ->
                        ReminderRow(r, overdue = false, onToggle, onDelete) { editing = r }
                    }
                }
            }
        }
        com.shreenipane.orbit.ui.components.BrutalButton(
            text = "Add",
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp),
            fill = com.shreenipane.orbit.ui.theme.Pastel.Yellow,
            leading = { Icon(Icons.Rounded.Add, null, tint = com.shreenipane.orbit.ui.theme.Pastel.Ink) },
        )
    }

    openDa?.let { da -> DaSheet(da, daUpload, onUpload, onOtp) { openDa = null; onResetUpload() } }

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

private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM")

@Composable
private fun ReminderRow(r: Reminder, overdue: Boolean, onToggle: (Reminder) -> Unit, onDelete: (Reminder) -> Unit, onEdit: () -> Unit) {
    ListRow(
        title = r.title,
        subtitle = listOfNotNull(
            r.dueAtMillis.toLocal().format(WHEN),
            r.courseCode,
            if (r.kind == ReminderKind.ASSIGNMENT) "Assignment" else null,
        ).joinToString(" · "),
        titleColor = if (overdue) Bad else androidx.compose.ui.graphics.Color.Unspecified,
        leading = { Checkbox(checked = r.done, onCheckedChange = { onToggle(r) }, modifier = Modifier.size(26.dp)) },
        trailing = { IconButton(onClick = { onDelete(r) }) { Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
        onClick = onEdit,
    )
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

@Composable
private fun DaSheet(
    da: DigitalAssignment,
    state: DaUpload,
    onUpload: (DigitalAssignment, Uri) -> Unit,
    onOtp: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onUpload(da, uri) }
    var otp by remember { mutableStateOf("") }
    val due = ExamTime.parseVtopDate(da.dueDate)

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = com.shreenipane.orbit.ui.theme.Pastel.Page,
    ) {
        Column(Modifier.padding(start = 24.dp, end = 28.dp, bottom = 36.dp)) {
            Text(da.title, style = MaterialTheme.typography.headlineMedium)
            Text("${da.courseTitle} · ${da.courseCode}", style = MaterialTheme.typography.bodyMedium,
                color = com.shreenipane.orbit.ui.theme.Pastel.InkMuted)
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().brutal(com.shreenipane.orbit.ui.theme.Pastel.Card)) {
                ListRow("Due", due?.let { d ->
                    val days = ChronoUnit.DAYS.between(LocalDate.now(), d)
                    d.format(DAY) + when { days < 0 -> " · past due"; days == 0L -> " · today"; else -> " · in $days days" }
                } ?: da.dueDate.ifBlank { "Not set" })
                com.shreenipane.orbit.ui.components.RowDivider()
                ListRow("Marks", listOf(da.maxMark, da.weightage.takeIf { it.isNotBlank() }?.let { "weightage $it" })
                    .filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" })
                com.shreenipane.orbit.ui.components.RowDivider()
                ListRow("Status", da.status.ifBlank { "Not uploaded" }, titleColor = if (da.submitted) Good else Bad)
            }
            Spacer(Modifier.height(20.dp))

            when (state) {
                is DaUpload.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp,
                        color = com.shreenipane.orbit.ui.theme.Pastel.Ink)
                    Spacer(Modifier.width(12.dp))
                    Text("Uploading to VTOP…", style = MaterialTheme.typography.bodyLarge)
                }
                is DaUpload.NeedOtp -> {
                    Text("VTOP mailed an OTP to your VIT e-mail to confirm this upload.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(otp, { otp = it.filter(Char::isLetterOrDigit).take(8) }, label = { Text("OTP") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(14.dp))
                    com.shreenipane.orbit.ui.components.BrutalButton("Confirm upload", { onOtp(da.title, otp) }, Modifier.fillMaxWidth(),
                        fill = com.shreenipane.orbit.ui.theme.Pastel.Green, enabled = otp.isNotBlank())
                }
                is DaUpload.Done -> Text(state.message, color = Good, style = MaterialTheme.typography.titleMedium)
                is DaUpload.Failed -> Text(state.message, color = Bad, style = MaterialTheme.typography.bodyMedium)
                DaUpload.Idle -> Unit
            }

            if (state !is DaUpload.Working && state !is DaUpload.NeedOtp && state !is DaUpload.Done) {
                Spacer(Modifier.height(14.dp))
                if (da.canUpload) {
                    com.shreenipane.orbit.ui.components.BrutalButton(
                        if (da.submitted) "Replace uploaded file" else "Upload file",
                        {
                            picker.launch(arrayOf(
                                "application/pdf", "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            ))
                        },
                        Modifier.fillMaxWidth(),
                        fill = com.shreenipane.orbit.ui.theme.Pastel.Yellow,
                    )
                    Text("PDF, Word or Excel, up to 4 MB.", style = MaterialTheme.typography.bodySmall,
                        color = com.shreenipane.orbit.ui.theme.Pastel.InkMuted, modifier = Modifier.padding(top = 8.dp))
                } else {
                    Text("VTOP isn't taking uploads for this DA right now.",
                        style = MaterialTheme.typography.bodySmall, color = com.shreenipane.orbit.ui.theme.Pastel.InkMuted)
                }
            }
        }
    }
}
