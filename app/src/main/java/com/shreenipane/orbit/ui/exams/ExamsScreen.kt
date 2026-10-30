package com.shreenipane.orbit.ui.exams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.shreenipane.orbit.core.model.StudyItem
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.ExamPlanner
import com.shreenipane.orbit.core.calc.ExamTime
import com.shreenipane.orbit.core.model.Exam
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyNote
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.RowDivider
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.Segmented
import com.shreenipane.orbit.ui.components.labLabel
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.NumberStyle
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM")
private val DAY_TIME = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a")

@Composable
fun ExamsScreen(
    snap: Snapshot,
    overrides: List<com.shreenipane.orbit.core.model.ClassOverride> = emptyList(),
    studyPlan: List<StudyItem> = emptyList(),
    onSaveStudy: (StudyItem) -> Unit = {},
    onRemoveStudy: (StudyItem) -> Unit = {},
) {
    var papersFor by remember { mutableStateOf<com.shreenipane.orbit.core.model.RegisteredCourse?>(null) }
    var editing by remember { mutableStateOf<StudyItem?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val now = LocalDateTime.now()
    val minute = now.hour * 60 + now.minute
    val countdowns = ExamPlanner.countdowns(snap.calendar, snap.exams, snap.timetable.sessions, now.toLocalDate(), minute, overrides)
    var pick by rememberSaveable { mutableIntStateOf(0) }

    OrbitScreen(title = "Exams", subtitle = snap.calendar?.classGroupName ?: snap.semester?.name) {
        if (countdowns.isEmpty() && snap.exams.isEmpty()) {
            item {
                EmptyNote(
                    "No exam dates yet",
                    if (snap.calendar == null) "Sign in again so Orbit can read the academic calendar."
                    else "The academic calendar doesn't show any upcoming CAT or FAT.",
                )
            }
        }

        if (countdowns.isNotEmpty()) {
            val cd = countdowns.first()
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("${cd.daysLeft}", "days to ${cd.label}", Modifier.weight(1f), com.shreenipane.orbit.ui.theme.Pastel.Salmon)
                    Stat("${cd.instructionalDaysLeft}", "teaching days", Modifier.weight(1f), com.shreenipane.orbit.ui.theme.Pastel.Yellow)
                    Stat("${cd.totalClasses}", "classes left", Modifier.weight(1f), com.shreenipane.orbit.ui.theme.Pastel.Green)
                }
            }
        }

        val matrix = ExamPlanner.matrix(snap.calendar, snap.exams, snap.timetable.sessions, now.toLocalDate(), minute, overrides)
        if (matrix != null) {
            section(
                header = "Classes left per subject",
                footer = "A lab block counts as one class.",
            ) { MatrixTable(matrix) }
        }

        // Your own revision plan: what to study on which day.
        section(header = "My study plan", action = "+ Add" to {
            editing = StudyItem(System.currentTimeMillis(), now.toLocalDate().toString(), "")
        }) {
            val today = now.toLocalDate().toString()
            val upcoming = studyPlan.filter { !it.done || it.date >= today }
            if (upcoming.isEmpty()) ListRow("Nothing planned", "Tap + Add to plan what to revise and when.")
            Rows(upcoming) { item -> StudyRow(item, today, { onSaveStudy(item.copy(done = !item.done)) }) { editing = item } }
        }

        // Previous-year papers from CodeChef-VIT's archive, by subject.
        val theory = snap.timetable.courses.filterNot { it.isLab }.distinctBy { it.code }
        if (theory.isNotEmpty()) {
            section(header = "Past papers", footer = "From papers.codechefvit.com") {
                Rows(theory) { c -> ListRow(c.title, c.code, chevron = true, onClick = { papersFor = c }) }
            }
        }

        snap.exams.groupBy { it.examType.ifBlank { "Exams" } }.forEach { (type, list) ->
            section(header = "$type schedule") {
                Rows(list.sortedBy { ExamTime.start(it) ?: LocalDateTime.MAX }) { e -> ExamRow(e, now) }
            }
        }
    }

    papersFor?.let { c -> PapersSheet(c.title, c.code) { papersFor = null } }
    editing?.let { item ->
        StudyDialog(
            item, isNew = studyPlan.none { it.id == item.id },
            subjects = snap.timetable.courses.map { it.title }.distinct(),
            onSave = { onSaveStudy(it); editing = null },
            onRemove = { onRemoveStudy(item); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun StudyRow(item: StudyItem, today: String, onToggle: () -> Unit, onEdit: () -> Unit) {
    val date = runCatching { java.time.LocalDate.parse(item.date) }.getOrNull()
    val whenText = when {
        item.date == today -> "Today"
        date != null && date == java.time.LocalDate.parse(today).plusDays(1) -> "Tomorrow"
        date != null -> date.format(DAY)
        else -> item.date
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.done, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                item.subject.ifBlank { "Revision" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textDecoration = if (item.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                color = if (item.done) com.shreenipane.orbit.ui.theme.Pastel.InkMuted else com.shreenipane.orbit.ui.theme.Pastel.Ink,
            )
            Text(listOf(whenText, item.topic).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = com.shreenipane.orbit.ui.theme.Pastel.InkMuted, maxLines = 2)
        }
    }
}

@Composable
private fun StudyDialog(
    initial: StudyItem, isNew: Boolean, subjects: List<String>,
    onSave: (StudyItem) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit,
) {
    val P = com.shreenipane.orbit.ui.theme.Pastel
    var subject by remember { mutableStateOf(initial.subject) }
    var topic by remember { mutableStateOf(initial.topic) }
    var dateText by remember { mutableStateOf(initial.date) }
    val date = runCatching { java.time.LocalDate.parse(dateText) }.getOrDefault(java.time.LocalDate.now())
    var pickSubject by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Page,
        title = { Text(if (isNew) "Plan a session" else "Edit plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.foundation.layout.Box {
                    OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (subjects.isNotEmpty()) TextButton(onClick = { pickSubject = true }) { Text("Pick") }
                        })
                    DropdownMenu(expanded = pickSubject, onDismissRequest = { pickSubject = false }) {
                        subjects.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { subject = t; pickSubject = false }) }
                    }
                }
                OutlinedTextField(topic, { topic = it }, label = { Text("Topics (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.shreenipane.orbit.ui.components.BrutalButton("‹", { dateText = date.minusDays(1).toString() }, fill = P.Card)
                    Text(date.format(DAY), style = MaterialTheme.typography.titleMedium, color = P.Ink,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    com.shreenipane.orbit.ui.components.BrutalButton("›", { dateText = date.plusDays(1).toString() }, fill = P.Card)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = subject.isNotBlank(), onClick = {
                onSave(initial.copy(subject = subject.trim(), topic = topic.trim(), date = date.toString()))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onRemove) { Text("Delete", color = com.shreenipane.orbit.ui.theme.Bad) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun MatrixTable(m: ExamPlanner.Matrix) {
    val labels = ExamPlanner.EXAMS.toMap()
    val colW = 64.dp
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text("Subject", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            m.exams.forEach { (key, start) ->
                Column(Modifier.width(colW), horizontalAlignment = Alignment.End) {
                    Text(labels[key] ?: key, style = MaterialTheme.typography.labelLarge)
                    Text(start.format(SHORT), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        m.rows.forEach { r ->
            RowDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.courseTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("${r.courseCode} · ${labLabel(r.isLab)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                m.exams.forEach { (key, _) ->
                    val v = r.left[key]
                    Text(
                        v?.toString() ?: "Done",
                        modifier = Modifier.width(colW),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        style = if (v == null) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
                        color = if (v == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private val SHORT = DateTimeFormatter.ofPattern("d MMM")

@Composable
private fun Stat(value: String, label: String, modifier: Modifier, fill: androidx.compose.ui.graphics.Color) {
    com.shreenipane.orbit.ui.components.Metric(value, label, modifier, fill = fill)
}

@Composable
private fun ExamRow(e: Exam, now: LocalDateTime) {
    val start = ExamTime.start(e)
    val past = start != null && start.isBefore(now)
    val where = listOf(e.venue, e.seatLocation, if (e.seatNumber.isNotBlank()) "Seat ${e.seatNumber}" else "")
        .filter { it.isNotBlank() && it != "-" }.joinToString(" · ")
    ListRow(
        title = e.courseTitle,
        subtitle = listOfNotNull(
            start?.format(DAY_TIME) ?: "Date not announced",
            where.ifBlank { null },
            e.reportingTime.takeIf { it.isNotBlank() && it != "-" }?.let { "report $it" },
        ).joinToString("\n"),
        titleColor = if (past) MaterialTheme.colorScheme.onSurfaceVariant else androidx.compose.ui.graphics.Color.Unspecified,
        trailing = {
            when {
                start == null -> Text("TBA", style = MaterialTheme.typography.bodySmall)
                past -> Text("Done", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Text(ExamTime.countdown(start, now), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}
