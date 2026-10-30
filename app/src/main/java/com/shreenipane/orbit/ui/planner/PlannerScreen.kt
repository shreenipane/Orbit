package com.shreenipane.orbit.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.FfcsPlanner
import com.shreenipane.orbit.core.model.PlannedCourse
import com.shreenipane.orbit.core.model.SlotTime
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.BrutalButton
import com.shreenipane.orbit.ui.components.Dot
import com.shreenipane.orbit.ui.components.EmptyNote
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.Metric
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.TILE_COLORS
import com.shreenipane.orbit.ui.components.Tag
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Good
import com.shreenipane.orbit.ui.theme.Pastel
import com.shreenipane.orbit.ui.theme.Warn
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Plan next semester's registration: type each course's slot combo
 * (e.g. "A1+TA1" or "L31+L32") and see clashes and the week laid out on
 * your current timetable's slot grid. Saved on the phone only.
 */
@Composable
fun PlannerScreen(
    snap: Snapshot,
    planned: List<PlannedCourse>,
    onSave: (PlannedCourse) -> Unit,
    onRemove: (PlannedCourse) -> Unit,
) {
    val grid = snap.timetable.slotGrid
    var editing by remember { mutableStateOf<PlannedCourse?>(null) }
    val clashes = remember(planned, grid) { FfcsPlanner.clashes(planned, grid) }
    val placements = remember(planned, grid) { planned.associateWith { FfcsPlanner.place(it, grid) } }
    val credits = planned.sumOf { it.credits }
    val colorOf = remember(planned) { planned.mapIndexed { i, c -> c.id to TILE_COLORS[i % TILE_COLORS.size] }.toMap() }

    OrbitScreen(
        title = "Planner",
        subtitle = "Try slot combos before registration",
        trailing = { BrutalButton("+ Course", { editing = PlannedCourse(System.currentTimeMillis(), "", "") }, fill = Pastel.Yellow) },
    ) {
        if (grid.isEmpty()) {
            item {
                EmptyNote("No slot grid yet", "Sync once to load the slot grid.",
                    Modifier.padding(top = 8.dp))
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric(planned.size.toString(), "Courses", Modifier.weight(1f), fill = Pastel.Sky)
                Metric(fmt(credits), "Credits", Modifier.weight(1f), fill = Pastel.Mint)
                Metric(clashes.size.toString(), "Clashes", Modifier.weight(1f),
                    fill = if (clashes.isEmpty()) Pastel.Green else Pastel.Salmon)
            }
        }

        section(header = "Courses", footer = "Slots like A1+TA1. Tap to edit.") {
            if (planned.isEmpty()) ListRow("Nothing planned yet", "Tap + Course to add one.")
            Rows(planned) { c ->
                val unknown = placements[c]?.second.orEmpty()
                val inClash = clashes.any { it.a.id == c.id || it.b.id == c.id }
                ListRow(
                    c.title.ifBlank { "Untitled" },
                    c.slots + (if (c.credits > 0) " · ${fmt(c.credits)} cr" else "") +
                        (if (unknown.isNotEmpty()) " · unknown: ${unknown.joinToString()}" else ""),
                    leading = { Dot(colorOf[c.id] ?: Pastel.Card, 14.dp) },
                    trailing = {
                        when {
                            inClash -> Tag("Clash", Bad)
                            unknown.isNotEmpty() -> Tag("Check", Warn)
                            else -> Tag("OK", Good)
                        }
                    },
                    onClick = { editing = c },
                )
            }
        }

        if (clashes.isNotEmpty()) {
            section(header = "Clashes", fill = Pastel.Salmon) {
                Rows(clashes) { k ->
                    ListRow("${k.a.title.ifBlank { k.a.slots }} × ${k.b.title.ifBlank { k.b.slots }}",
                        "${dayName(k.where.day)} ${k.where.start}–${k.where.end} (${k.where.slot})")
                }
            }
        }

        if (planned.isNotEmpty() && grid.isNotEmpty()) {
            val byDay = placements.flatMap { (c, p) -> p.first.map { it to c } }
                .groupBy { it.first.day }.toSortedMap()
            byDay.forEach { (day, list) ->
                section(header = dayName(day)) {
                    Rows(list.sortedBy { it.first.start }) { (t, c) -> PlacedRow(t, c, colorOf[c.id] ?: Pastel.Card) }
                }
            }
        }
    }

    editing?.let { c ->
        EditDialog(
            initial = c,
            isNew = planned.none { it.id == c.id },
            onSave = { onSave(it); editing = null },
            onRemove = { onRemove(c); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun PlacedRow(t: SlotTime, c: PlannedCourse, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(64.dp)) {
            Text(t.start, style = MaterialTheme.typography.labelLarge, color = Pastel.Ink)
            Text(t.end, style = MaterialTheme.typography.labelMedium, color = Pastel.InkMuted)
        }
        Dot(color, 12.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title.ifBlank { c.slots }, style = MaterialTheme.typography.bodyLarge)
            Text(t.slot, style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted)
        }
    }
}

@Composable
private fun EditDialog(
    initial: PlannedCourse,
    isNew: Boolean,
    onSave: (PlannedCourse) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var slots by remember { mutableStateOf(initial.slots) }
    var credits by remember { mutableStateOf(if (initial.credits > 0) fmt(initial.credits) else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pastel.Page,
        title = { Text(if (isNew) "Add course" else "Edit course") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Course (e.g. CSE2001 DSA)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(slots, { slots = it.uppercase() }, label = { Text("Slots (e.g. A1+TA1)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(credits, { v -> credits = v.filter { it.isDigit() || it == '.' }.take(4) }, label = { Text("Credits") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = slots.isNotBlank(), onClick = {
                onSave(initial.copy(title = title.trim(), slots = slots.trim(), credits = credits.toDoubleOrNull() ?: 0.0))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onRemove) { Text("Remove", color = Bad) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private fun dayName(d: Int): String =
    if (d in 1..7) DayOfWeek.of(d).getDisplayName(TextStyle.FULL, Locale.ENGLISH) else "Day $d"

private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.US, "%.1f", d)
