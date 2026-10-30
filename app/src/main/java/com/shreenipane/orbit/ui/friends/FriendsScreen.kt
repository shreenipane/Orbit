@file:OptIn(ExperimentalLayoutApi::class)

package com.shreenipane.orbit.ui.friends

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.ShareCode
import com.shreenipane.orbit.core.calc.FreeTime
import com.shreenipane.orbit.core.calc.Schedule
import com.shreenipane.orbit.core.model.Friend
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyState
import com.shreenipane.orbit.ui.components.SectionTitle
import java.time.LocalDate

private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@Composable
fun FriendsScreen(
    snap: Snapshot,
    friends: List<Friend>,
    myCode: () -> String?,
    onImport: (String) -> String?,
    onRemove: (Friend) -> Unit,
    alias: String,
) {
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var day by remember { mutableIntStateOf(LocalDate.now().dayOfWeek.value.coerceAtMost(6)) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp).fillMaxWidth()) {
                    Text("Share your timetable", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Sends a code your friends paste into Orbit, plus a readable copy for anyone else. Nothing goes through a server.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val code = myCode() ?: return@Button
                            val name = alias.ifBlank { snap.regNo }
                            val text = ShareCode.plainText(name, snap.timetable.sessions) + "\nAdd me in Orbit:\n$code"
                            ctx.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                                "Share timetable",
                            ))
                        }) { Text("Share") }
                        FilledTonalButton(onClick = { adding = true }) { Text("Add a friend") }
                    }
                }
            }
        }

        if (friends.isEmpty()) {
            item { EmptyState("👋", "No friends added yet", "Ask a friend to share their Orbit code, then tap Add a friend.") }
            return@LazyColumn
        }

        item {
            Column {
                SectionTitle("Free together")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAYS.forEachIndexed { i, d -> FilterChip(day == i + 1, { day = i + 1 }, { Text(d) }) }
                }
            }
        }
        item {
            val gaps = FreeTime.common(listOf(snap.timetable.sessions) + friends.map { it.sessions }, day)
            Card {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Everyone's free on ${DAYS[day - 1]}", style = MaterialTheme.typography.titleMedium)
                    if (gaps.isEmpty()) Text("No common gap of 30 minutes or more.", style = MaterialTheme.typography.bodySmall)
                    gaps.forEach { Text("${it.start} – ${it.end}  (${it.minutes / 60}h ${it.minutes % 60}m)") }
                }
            }
        }

        item { SectionTitle("Friends") }
        items(friends) { f ->
            val list = Schedule.forDay(f.sessions, day)
            val withMe = FreeTime.common(listOf(snap.timetable.sessions, f.sessions), day)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.titleMedium)
                            Text(f.semesterName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onRemove(f) }) { Icon(Icons.Rounded.Delete, "Remove") }
                    }
                    if (list.isEmpty()) Text("No classes on ${DAYS[day - 1]}")
                    list.forEach { Text("${it.start}–${it.end}  ${it.courseTitle} · ${it.venue}", style = MaterialTheme.typography.bodySmall) }
                    if (withMe.isNotEmpty()) {
                        Text(
                            "Free with you: " + withMe.joinToString { "${it.start}–${it.end}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }

    if (adding) AddFriendDialog(onImport) { adding = false }
}

@Composable
private fun AddFriendDialog(onImport: (String) -> String?, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add a friend") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paste the message your friend shared. It contains a code starting with ORBIT1.")
                OutlinedTextField(text, { text = it; error = null }, label = { Text("Share code") }, minLines = 3,
                    isError = error != null, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { clipboard.getText()?.text?.let { text = it } }) { Text("Paste from clipboard") }
            }
        },
        confirmButton = {
            TextButton(onClick = { error = onImport(text); if (error == null) onClose() }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
