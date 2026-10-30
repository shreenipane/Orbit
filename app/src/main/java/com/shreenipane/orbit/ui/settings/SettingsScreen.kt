package com.shreenipane.orbit.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.data.AppSettings
import com.shreenipane.orbit.ui.UiState
import com.shreenipane.orbit.ui.components.SectionTitle

@Composable
fun SettingsScreen(
    state: UiState,
    canUseLock: Boolean,
    update: ((AppSettings) -> AppSettings) -> Unit,
    onSignOut: () -> Unit,
) {
    val s = state.settings
    var alias by remember { mutableStateOf(s.alias) }
    var confirmSignOut by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item { SectionTitle("You") }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it.take(24); update { st -> st.copy(alias = alias) } },
                        label = { Text("What should Orbit call you?") },
                        supportingText = { Text("Used in greetings and when you share your timetable") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${state.snapshot?.regNo.orEmpty()} · ${state.campus.label}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { SectionTitle("Privacy") }
        item {
            Card {
                Toggle(
                    "App lock",
                    if (canUseLock) "Fingerprint, face or screen lock when opening Orbit"
                    else "Set up a screen lock or fingerprint on this phone first",
                    s.biometricLock && canUseLock, enabled = canUseLock,
                ) { v -> update { it.copy(biometricLock = v) } }
            }
        }

        item { SectionTitle("Alerts") }
        item {
            Card {
                Column {
                    Toggle("Class alerts", "A heads-up before each class", s.classAlerts) { v -> update { it.copy(classAlerts = v) } }
                    if (s.classAlerts) {
                        Row(Modifier.padding(start = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 15, 30).forEach { m ->
                                FilterChip(s.classAlertLeadMinutes == m, { update { it.copy(classAlertLeadMinutes = m) } }, { Text("$m min") })
                            }
                        }
                    }
                    Toggle("Exam alerts", "The evening before and an hour before", s.examAlerts) { v -> update { it.copy(examAlerts = v) } }
                    Toggle(
                        "Background sync",
                        "Every 6 hours while your VTOP session is valid. Notifies you about new marks, absences and exam schedules.",
                        s.backgroundSync,
                    ) { v -> update { it.copy(backgroundSync = v) } }
                }
            }
        }

        item { SectionTitle("Account") }
        item {
            Column {
                OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) { Text("Sign out and clear VTOP data") }
                Text(
                    "Reminders and friends stay on this phone. Orbit never stores your VTOP password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        item {
            Text(
                "Orbit 0.2 · unofficial, not affiliated with VIT.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("This removes your saved VTOP data from this phone. You can sign in again any time.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
