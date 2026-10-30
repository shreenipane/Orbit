package com.shreenipane.orbit.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.ui.AppViewModel
import com.shreenipane.orbit.ui.UiState
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.RowDivider
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.Bad

private val CLASS_GROUPS = listOf(null to "Automatic (General Flexible)", "General (Flexible)" to "General (Flexible)", "General (Semester)" to "General (Semester)")

@Composable
fun SettingsScreen(state: UiState, canUseLock: Boolean, vm: AppViewModel) {
    val s = state.settings
    val snap = state.snapshot
    var alias by remember { mutableStateOf(s.alias) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmRemoveLogin by remember { mutableStateOf(false) }
    var semMenu by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }

    OrbitScreen(title = "Settings") {
        section(header = "Profile", footer = "${snap?.regNo.orEmpty()} · ${state.campus.label}. Your name is used in greetings and shared timetables.") {
            TextField(
                value = alias,
                onValueChange = { alias = it.take(24); vm.updateSettings { st -> st.copy(alias = alias) } },
                placeholder = { Text("Your name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        section(header = "Academics", footer = "Classes-left counts read B.Tech entries in this class group's academic calendar.") {
            Box {
                ListRow("Semester", snap?.semester?.name ?: "Not picked", chevron = true, onClick = { semMenu = true })
                DropdownMenu(expanded = semMenu, onDismissRequest = { semMenu = false }) {
                    snap?.semesters.orEmpty().forEach { sem ->
                        DropdownMenuItem(text = { Text(sem.name) }, onClick = { semMenu = false; vm.selectSemester(sem.id) })
                    }
                }
            }
            RowDivider()
            Box {
                ListRow(
                    "Calendar class group",
                    CLASS_GROUPS.firstOrNull { it.first == state.classGroup }?.second
                        ?: state.classGroup ?: "Automatic",
                    chevron = true, onClick = { groupMenu = true },
                )
                DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                    CLASS_GROUPS.forEach { (id, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { groupMenu = false; vm.setClassGroup(id) })
                    }
                }
            }
            snap?.calendar?.let {
                RowDivider()
                ListRow("Using", it.classGroupName)
            }
        }

        section(
            header = "Security",
            footer = "The locker keeps your VTOP login encrypted (AES-256-GCM) with a key held in Android Keystore that only your fingerprint can unlock. It stays until you sign out.",
        ) {
            Toggle(
                "App lock",
                if (canUseLock) "Ask for fingerprint or screen lock when opening Orbit" else "Set up a screen lock on this phone first",
                s.biometricLock && canUseLock, enabled = canUseLock,
            ) { v -> vm.updateSettings { it.copy(biometricLock = v) } }
            RowDivider()
            val saved = state.savedLogin
            if (saved != null) {
                ListRow(
                    "Fingerprint login", "Saved for $saved",
                    trailing = { TextButton(onClick = { confirmRemoveLogin = true }) { Text("Remove", color = Bad) } },
                )
            } else {
                ListRow("Fingerprint login", "Not saved. Sign in with your password once and choose Save.")
            }
        }

        section(header = "Alerts") {
            Toggle("Class alerts", "Before each class", s.classAlerts) { v -> vm.updateSettings { it.copy(classAlerts = v) } }
            if (s.classAlerts) {
                RowDivider()
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    com.shreenipane.orbit.ui.components.Segmented(
                        listOf("5 min", "10 min", "15 min", "30 min"),
                        listOf(5, 10, 15, 30).indexOf(s.classAlertLeadMinutes).coerceAtLeast(0),
                        { i -> vm.updateSettings { it.copy(classAlertLeadMinutes = listOf(5, 10, 15, 30)[i]) } },
                    )
                }
            }
            RowDivider()
            Toggle("Exam and DA alerts", "Evening before and on the day", s.examAlerts) { v -> vm.updateSettings { it.copy(examAlerts = v) } }
            RowDivider()
            Toggle("Background sync", "Every 6 h while your VTOP session lasts: new marks, absences, DAs", s.backgroundSync) { v ->
                vm.updateSettings { it.copy(backgroundSync = v) }
            }
        }

        section(footer = "Reminders and friends stay on this phone. Orbit is unofficial and not affiliated with VIT.") {
            ListRow("Sign out", "Clears VTOP data and the saved login", titleColor = Bad, onClick = { confirmSignOut = true })
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("This removes your VTOP data and the saved fingerprint login from this phone.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text("Sign out", color = Bad) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
    if (confirmRemoveLogin) {
        AlertDialog(
            onDismissRequest = { confirmRemoveLogin = false },
            title = { Text("Remove saved login?") },
            text = { Text("You'll need your password and captcha next time.") },
            confirmButton = { TextButton(onClick = { confirmRemoveLogin = false; vm.removeSavedLogin() }) { Text("Remove", color = Bad) } },
            dismissButton = { TextButton(onClick = { confirmRemoveLogin = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListRow(title, subtitle, trailing = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) })
}
