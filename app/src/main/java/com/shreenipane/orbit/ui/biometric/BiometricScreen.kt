package com.shreenipane.orbit.ui.biometric

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.model.BiometricEntry
import com.shreenipane.orbit.ui.AppViewModel
import com.shreenipane.orbit.ui.components.BrutalButton
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.Metric
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.Pastel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM")

/** Your biometric punches at the academic blocks, from VTOP (Academics → Biometric Log), one day at a time. */
@Composable
fun BiometricScreen(vm: AppViewModel) {
    val today = LocalDate.now()
    var dateText by rememberSaveable { mutableStateOf(today.toString()) }
    val date = LocalDate.parse(dateText)
    var entries by remember { mutableStateOf<List<BiometricEntry>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dateText) {
        loading = true; error = null; entries = null
        vm.lookup { biometric(date) }
            .onSuccess { entries = it.sortedBy { e -> e.time } }
            .onFailure { error = it.message ?: "Couldn't load the log." }
        loading = false
    }

    OrbitScreen(title = "Biometric log", subtitle = "Academic blocks") {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BrutalButton("‹", { dateText = date.minusDays(1).toString() }, fill = Pastel.Card)
                Text(
                    if (date == today) "Today" else date.format(DAY),
                    style = MaterialTheme.typography.titleLarge, color = Pastel.Ink, maxLines = 1,
                    modifier = Modifier.weight(1f).clickable { dateText = today.toString() }.padding(horizontal = 14.dp),
                )
                BrutalButton("›", { dateText = date.plusDays(1).toString() }, fill = Pastel.Card, enabled = date.isBefore(today))
            }
        }

        val list = entries
        if (!list.isNullOrEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric(list.first().time.take(5), "First in", Modifier.weight(1f), fill = Pastel.Green)
                    Metric(list.last().time.take(5), "Last", Modifier.weight(1f), fill = Pastel.Sky)
                    Metric("${list.size}", "Punches", Modifier.weight(1f), fill = Pastel.Lavender)
                }
            }
        }

        section(header = "Punches") {
            when {
                loading -> ListRow("Loading…", null)
                error != null -> ListRow("Couldn't load the log", error)
                list.isNullOrEmpty() -> ListRow("No punches this day", null)
                else -> Rows(list) { e -> ListRow(e.time, e.location.ifBlank { null }) }
            }
        }
    }
}
