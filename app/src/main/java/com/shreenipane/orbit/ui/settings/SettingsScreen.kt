package com.shreenipane.orbit.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    fun toast(msg: String) = android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val text = vm.backupJson()
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("no stream")
                }
            }.isSuccess
            toast(if (ok) "Backup saved" else "Couldn't save the backup")
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
            }.getOrNull()
            if (text.isNullOrBlank()) toast("Couldn't read that file") else vm.restore(text)
        }
    }

    OrbitScreen(title = "Settings") {
        section(header = "Profile", footer = "${snap?.regNo.orEmpty()} · ${state.campus.label}") {
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

        section(header = "Appearance") {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                com.shreenipane.orbit.ui.components.Segmented(
                    listOf("System", "Light", "Dark"), s.themeMode.coerceIn(0, 2),
                    { i -> vm.updateSettings { it.copy(themeMode = i) } },
                )
            }
        }

        section(header = "Academics", footer = null) {
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
            footer = "Your login is encrypted and unlocked only by your fingerprint.",
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
            Toggle(
                "Stay signed in",
                "Keeps your VTOP session from timing out",
                s.keepSignedIn,
            ) { v -> vm.updateSettings { it.copy(keepSignedIn = v) } }
            RowDivider()
            Toggle("Background sync", "New marks, absences and DAs every 6 h", s.backgroundSync) { v ->
                vm.updateSettings { it.copy(backgroundSync = v) }
            }
            RowDivider()
            Toggle("Weekly report", "Sunday evening: classes attended and how each subject moved", s.weeklyReport) { v ->
                vm.updateSettings { it.copy(weeklyReport = v) }
            }
            RowDivider()
            Toggle("Fee reminders", "Two days before a fee is due, and on the day", s.feeReminders) { v ->
                vm.updateSettings { it.copy(feeReminders = v) }
            }
        }

        section(
            header = "Your data",
            footer = null,
        ) {
            ListRow("Export to calendar", "Google Calendar, Outlook… (.ics)", chevron = true, onClick = {
                scope.launch {
                    val file = vm.icsFile()
                    if (file == null) { toast("Sync once first"); return@launch }
                    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/calendar")
                        .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { ctx.startActivity(android.content.Intent.createChooser(send, "Add to calendar")) }
                        .onFailure { toast("No app can open calendar files") }
                }
            })
            RowDivider()
            ListRow("Back up", "Save a file you can restore later", chevron = true, onClick = {
                runCatching { saveBackup.launch("orbit-backup-${java.time.LocalDate.now()}.json") }.onFailure { toast("No file picker on this phone") }
            })
            RowDivider()
            ListRow("Restore", "Merge a backup file into this phone", chevron = true, onClick = {
                runCatching { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                    .onFailure { toast("No file picker on this phone") }
            })
        }

        section(
            header = "Help",
            footer = "Send this if a screen is empty or wrong.",
        ) {
            ListRow("Send debug report", "Share what VTOP returned, so parsing can be fixed", chevron = true, onClick = {
                val file = vm.debugReport()
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
                ctx.startActivity(
                    android.content.Intent.createChooser(
                        android.content.Intent(android.content.Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        "Send debug report",
                    ),
                )
            })
        }

        section(footer = "Orbit is unofficial and not affiliated with VIT.") {
            ListRow("Sign out", "Clears VTOP data and the saved login", titleColor = Bad, onClick = { confirmSignOut = true })
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Removes your VTOP data and saved login from this phone.") },
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
