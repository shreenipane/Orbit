package com.shreenipane.orbit.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.AppViewModel
import com.shreenipane.orbit.ui.UiState
import com.shreenipane.orbit.ui.cgpa.CgpaScreen
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.brutal
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.exams.ExamsScreen
import com.shreenipane.orbit.ui.friends.FriendsScreen
import com.shreenipane.orbit.ui.reminders.RemindersScreen
import com.shreenipane.orbit.ui.settings.SettingsScreen

enum class MoreDestination(val title: String, val subtitle: String, val icon: ImageVector, val group: String) {
    Calendar("Calendar", "Class days, holidays, exams", Icons.Outlined.CalendarMonth, "Academics"),
    Exams("Exams", "Classes left before each exam", Icons.Outlined.Event, "Academics"),
    Cgpa("CGPA", "Calculator and grade history", Icons.Outlined.School, "Academics"),
    Results("Results", "Grades and grade ranges", Icons.Outlined.WorkspacePremium, "Academics"),
    Holidays("Holidays", "Next break and all breaks", Icons.Outlined.BeachAccess, "Academics"),
    Reminders("Deadlines", "DAs and reminders", Icons.AutoMirrored.Outlined.Assignment, "Plan"),
    Planner("FFCS planner", "Check slot clashes", Icons.Outlined.GridView, "Plan"),
    Friends("Friends", "Swap timetables by QR", Icons.Outlined.Groups, "People"),
    Campus("Campus", "News, faculty, fees, hostel", Icons.Outlined.AccountBalance, "People"),
    Settings("Settings", "Theme, alerts, backup", Icons.Outlined.Settings, "App"),
}

@Composable
fun MoreScreen(
    destination: MoreDestination?,
    onOpen: (MoreDestination) -> Unit,
    onBack: () -> Unit,
    state: UiState,
    snap: Snapshot,
    vm: AppViewModel,
    canUseLock: Boolean,
) {
    if (destination == null) {
        OrbitScreen(title = "More") {
            MoreDestination.entries.groupBy { it.group }.forEach { (group, list) ->
            section(header = group) {
                Rows(list) { d ->
                    ListRow(
                        d.title, d.subtitle,
                        leading = {
                            Box(
                                Modifier.size(36.dp).brutal(
                                    com.shreenipane.orbit.ui.components.TILE_COLORS[d.ordinal % com.shreenipane.orbit.ui.components.TILE_COLORS.size],
                                    radius = 10.dp, shadow = 2.dp, border = 1.5.dp,
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(d.icon, null, tint = com.shreenipane.orbit.ui.theme.Pastel.Ink)
                            }
                        },
                        chevron = true,
                        onClick = { onOpen(d) },
                    )
                }
            }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 4.dp, top = 4.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = com.shreenipane.orbit.ui.theme.Pastel.Ink)
            Text("More", color = com.shreenipane.orbit.ui.theme.Pastel.Ink, style = MaterialTheme.typography.titleMedium)
        }
        Box(Modifier.weight(1f)) {
            when (destination) {
                MoreDestination.Exams -> ExamsScreen(snap, state.overrides)
                MoreDestination.Holidays -> com.shreenipane.orbit.ui.holidays.HolidaysScreen(snap)
                MoreDestination.Reminders -> RemindersScreen(
                    snap, state.reminders, vm::upsertReminder, vm::toggleDone, vm::deleteReminder,
                    daUpload = state.daUpload, onUpload = vm::uploadDa, onOtp = vm::submitDaOtp, onResetUpload = vm::resetDaUpload,
                )
                MoreDestination.Cgpa -> CgpaScreen(snap, vm, state.settings.creditTargets)
                MoreDestination.Calendar -> com.shreenipane.orbit.ui.calendar.CalendarScreen(
                    snap, state.overrides, vm::toggleCancelled, vm::removeOverride,
                )
                MoreDestination.Results -> com.shreenipane.orbit.ui.results.ResultsScreen(snap, vm)
                MoreDestination.Planner -> com.shreenipane.orbit.ui.planner.PlannerScreen(snap, state.planner, vm::upsertPlanned, vm::removePlanned)
                MoreDestination.Campus -> com.shreenipane.orbit.ui.campus.CampusScreen(state, vm)
                MoreDestination.Friends -> FriendsScreen(snap, state.friends, vm::myShareCode, vm::importFriend, vm::removeFriend, state.settings.alias)
                MoreDestination.Settings -> SettingsScreen(state, canUseLock, vm)
            }
        }
    }
}
