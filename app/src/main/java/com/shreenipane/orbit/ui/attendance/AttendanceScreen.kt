@file:OptIn(ExperimentalMaterial3Api::class)

package com.shreenipane.orbit.ui.attendance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.AttendanceCalc
import com.shreenipane.orbit.core.model.AttendanceRecord
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyState
import com.shreenipane.orbit.ui.components.Pill
import com.shreenipane.orbit.ui.components.Ring
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Good
import com.shreenipane.orbit.ui.theme.attendanceColor

@Composable
fun AttendanceScreen(snap: Snapshot, refreshing: Boolean, onRefresh: () -> Unit) {
    var open by remember { mutableStateOf<AttendanceRecord?>(null) }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        if (snap.attendance.isEmpty()) {
            EmptyState("📋", "No attendance yet", "VTOP hasn't posted attendance for this semester.")
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(snap.attendance.sortedBy { AttendanceCalc.percent(it.attended, it.total) }) { a ->
                    AttendanceCard(a) { open = a }
                }
                item {
                    Text(
                        "Skip and catch-up counts use the 75% rule, in classes as VTOP counts them. VTOP's own percentage (shown on the ring) may round differently.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }

    open?.let { PlannerSheet(it) { open = null } }
}

@Composable
private fun AttendanceCard(a: AttendanceRecord, onClick: () -> Unit) {
    val pct = AttendanceCalc.percent(a.attended, a.total)
    val skip = AttendanceCalc.skippable(a.attended, a.total)
    val need = AttendanceCalc.neededInARow(a.attended, a.total)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Ring(fraction = (a.vtopPercent / 100f), color = attendanceColor(pct), label = "${a.vtopPercent}%")
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(a.courseTitle, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${a.courseCode} · ${a.courseType.ifBlank { a.slot }} · ${a.attended}/${a.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                when {
                    need > 0 -> Pill("Attend next $need", Bad.copy(alpha = 0.15f), Bad)
                    skip > 0 -> Pill("Can skip $skip", Good.copy(alpha = 0.15f), Good)
                    else -> Pill("On the line, don't skip", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun PlannerSheet(a: AttendanceRecord, onDismiss: () -> Unit) {
    var present by remember { mutableIntStateOf(0) }
    var absent by remember { mutableIntStateOf(0) }
    val projected = AttendanceCalc.projected(a.attended, a.total, present, absent)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(a.courseTitle, style = MaterialTheme.typography.titleLarge)
            Text("${a.courseCode} · ${a.slot} · ${a.faculty}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(fraction = (projected / 100).toFloat(), color = attendanceColor(projected),
                    label = "%.1f%%".format(projected), size = 96.dp, stroke = 10.dp)
                Spacer(Modifier.width(20.dp))
                Column {
                    Text("If you attend $present and miss $absent more", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${a.attended + present}/${a.total + present + absent} classes",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
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
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { if (value > 0) onChange(value - 1) }) { Icon(Icons.Rounded.Remove, "Less") }
        Text("$value", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        FilledTonalIconButton(onClick = { onChange(value + 1) }) { Icon(Icons.Rounded.Add, "More") }
    }
}
