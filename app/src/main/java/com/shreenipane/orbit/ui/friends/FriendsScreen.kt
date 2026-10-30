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
import com.shreenipane.orbit.ui.components.EmptyNote
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.RowDivider
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.Segmented
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.home.ClassRow
import java.time.LocalDate

private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val FULL = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

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

    OrbitScreen(title = "Friends", subtitle = "Timetables") {
        section(footer = "Shares a code friends paste into Orbit, plus a readable copy. Nothing goes through a server.") {
            ListRow("Share my timetable", null, chevron = true, titleColor = MaterialTheme.colorScheme.primary, onClick = {
                val code = myCode() ?: return@ListRow
                val name = alias.ifBlank { snap.regNo }
                val text = ShareCode.plainText(name, snap.timetable.sessions) + "\nAdd me in Orbit:\n$code"
                ctx.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                    "Share timetable",
                ))
            })
            RowDivider()
            ListRow("Add a friend", null, chevron = true, titleColor = MaterialTheme.colorScheme.primary, onClick = { adding = true })
        }

        if (friends.isEmpty()) {
            item { EmptyNote("No friends yet", "Ask a friend to share their Orbit code, then tap Add a friend.") }
            return@OrbitScreen
        }

        item { Segmented(DAYS, day - 1, { day = it + 1 }, Modifier.padding(top = 20.dp)) }

        val gaps = FreeTime.common(listOf(snap.timetable.sessions) + friends.map { it.sessions }, day)
        section(header = "Everyone free on ${FULL[day - 1]}") {
            if (gaps.isEmpty()) ListRow("No common gap", "Nothing of 30 minutes or more.")
            Rows(gaps) { g -> ListRow("${g.start} – ${g.end}", "${g.minutes / 60}h ${g.minutes % 60}m") }
        }

        friends.forEach { f ->
            val list = Schedule.forDay(f.sessions, day)
            val withMe = FreeTime.common(listOf(snap.timetable.sessions, f.sessions), day)
            section(
                header = f.name,
                action = "Remove" to { onRemove(f) },
                footer = if (withMe.isNotEmpty()) "Free with you: " + withMe.joinToString { "${it.start}–${it.end}" } else null,
            ) {
                if (list.isEmpty()) ListRow("No classes", f.semesterName)
                Rows(list, inset = 82.dp) { s -> ClassRow(s, isNow = false) }
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
