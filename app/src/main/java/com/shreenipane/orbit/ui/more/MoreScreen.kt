package com.shreenipane.orbit.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.AppViewModel
import com.shreenipane.orbit.ui.UiState
import com.shreenipane.orbit.ui.cgpa.CgpaScreen
import com.shreenipane.orbit.ui.exams.ExamsScreen
import com.shreenipane.orbit.ui.friends.FriendsScreen
import com.shreenipane.orbit.ui.reminders.RemindersScreen
import com.shreenipane.orbit.ui.settings.SettingsScreen

enum class MoreDestination(val title: String, val subtitle: String, val icon: ImageVector) {
    Exams("Exams", "Schedule, venues, seats and countdowns", Icons.Rounded.Event),
    Cgpa("CGPA planner", "Where you stand and what you need", Icons.Rounded.School),
    Reminders("Reminders & assignments", "Your own deadlines, with alerts", Icons.Rounded.NotificationsActive),
    Friends("Friends", "Share timetables, find free time together", Icons.Rounded.Group),
    Settings("Settings", "Name, lock, alerts, sync, sign out", Icons.Rounded.Settings),
}

@Composable
fun MoreScreen(
    destination: MoreDestination?,
    onOpen: (MoreDestination) -> Unit,
    state: UiState,
    snap: Snapshot,
    vm: AppViewModel,
    canUseLock: Boolean,
) {
    when (destination) {
        MoreDestination.Exams -> ExamsScreen(snap)
        MoreDestination.Cgpa -> CgpaScreen(snap)
        MoreDestination.Reminders -> RemindersScreen(snap, state.reminders, vm::upsertReminder, vm::toggleDone, vm::deleteReminder)
        MoreDestination.Friends -> FriendsScreen(snap, state.friends, vm::myShareCode, vm::importFriend, vm::removeFriend, state.settings.alias)
        MoreDestination.Settings -> SettingsScreen(state, canUseLock, vm::updateSettings, vm::signOut)
        null -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(MoreDestination.entries) { d ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(d) }) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(d.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.title, style = MaterialTheme.typography.titleMedium)
                            Text(d.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }
}
