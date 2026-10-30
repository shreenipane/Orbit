package com.shreenipane.orbit.ui.exams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.ExamTime
import com.shreenipane.orbit.core.model.Exam
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyState
import com.shreenipane.orbit.ui.components.Pill
import com.shreenipane.orbit.ui.components.SectionTitle
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a")

@Composable
fun ExamsScreen(snap: Snapshot) {
    if (snap.exams.isEmpty()) {
        EmptyState("🗓️", "No exams scheduled", "Exam dates show up here once VTOP publishes the schedule.")
        return
    }
    val now = LocalDateTime.now()
    val next = ExamTime.upcoming(snap.exams, now).firstOrNull()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        if (next != null) {
            item {
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )) {
                    Column(Modifier.padding(20.dp).fillMaxWidth()) {
                        Pill("NEXT · ${ExamTime.countdown(next.second, now).uppercase()}", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                        Text(next.first.courseTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                        Text("${next.first.examType} · ${next.second.format(DAY)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        snap.exams.groupBy { it.examType.ifBlank { "Exams" } }.forEach { (type, list) ->
            item { SectionTitle(type) }
            items(list.sortedBy { ExamTime.start(it) ?: LocalDateTime.MAX }) { ExamCard(it, now) }
        }
    }
}

@Composable
private fun ExamCard(e: Exam, now: LocalDateTime) {
    val start = ExamTime.start(e)
    val past = start != null && start.isBefore(now)
    Card(colors = CardDefaults.cardColors(
        containerColor = if (past) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    )) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.courseTitle, style = MaterialTheme.typography.titleMedium)
                    Text("${e.courseCode} · ${e.slot}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    start == null -> Text("TBA", fontWeight = FontWeight.Bold)
                    past -> Text("Done", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Text(ExamTime.countdown(start, now), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (start != null) {
                Text(start.format(DAY), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                val where = listOf(e.venue, e.seatLocation, if (e.seatNumber.isNotBlank()) "Seat ${e.seatNumber}" else "")
                    .filter { it.isNotBlank() && it != "-" }.joinToString(" · ")
                if (where.isNotBlank()) Text(where, style = MaterialTheme.typography.bodySmall)
                if (e.reportingTime.isNotBlank()) Text("Report by ${e.reportingTime}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
