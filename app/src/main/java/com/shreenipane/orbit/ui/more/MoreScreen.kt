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
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Hotel
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
    Exams("Exams", "Classes left, papers, your study plan", Icons.Outlined.Event, "Academics"),
    Trends("Marks trends", "How each assessment went", Icons.AutoMirrored.Outlined.ShowChart, "Academics"),
    Cgpa("CGPA", "Calculator and grade history", Icons.Outlined.School, "Academics"),
    Results("Results", "Grades and grade ranges", Icons.Outlined.WorkspacePremium, "Academics"),
    Recap("Semester recap", "Your semester on one card", Icons.Outlined.AutoAwesome, "Academics"),
    Holidays("Holidays", "Next break and all breaks", Icons.Outlined.BeachAccess, "Academics"),
    Reminders("Deadlines", "DAs and reminders", Icons.AutoMirrored.Outlined.Assignment, "Plan"),
    Bunk("Bunk planner", "Pick days off, see your attendance", Icons.Outlined.EventBusy, "Plan"),
    FreeSlots("Free slots", "Your gaps and who's free now", Icons.Outlined.Schedule, "Plan"),
    Timer("Study timer", "Focus sessions, time per subject", Icons.Outlined.Timer, "Plan"),
    Notes("Notes", "Notes and photos per course", Icons.AutoMirrored.Outlined.StickyNote2, "Plan"),
    Planner("FFCS planner", "Check slot clashes", Icons.Outlined.GridView, "Plan"),
    Leave("Leave", "Apply for hostel leave", Icons.Outlined.Hotel, "Hostel"),
    Faculty("Faculty info", "Cabins and contacts on VTOP", Icons.Outlined.Badge, "VTOP pages"),
    Fees("Fees & payments", "Dues and receipts on VTOP", Icons.Outlined.Payments, "VTOP pages"),
    Friends("Friends", "Timetables, plans, free time", Icons.Outlined.Groups, "People"),
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
                MoreDestination.Exams -> ExamsScreen(snap, state.overrides, state.studyPlan, vm::upsertStudy, vm::removeStudy)
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
                MoreDestination.Leave -> com.shreenipane.orbit.ui.hostel.LeaveScreen()
                MoreDestination.Trends -> com.shreenipane.orbit.ui.tools.TrendsScreen(snap)
                MoreDestination.Recap -> com.shreenipane.orbit.ui.tools.RecapScreen(snap, state.settings.alias.ifBlank { snap.regNo })
                MoreDestination.Bunk -> com.shreenipane.orbit.ui.tools.BunkScreen(snap, state.overrides)
                MoreDestination.FreeSlots -> com.shreenipane.orbit.ui.tools.FreeSlotsScreen(snap, state.friends)
                MoreDestination.Timer -> com.shreenipane.orbit.ui.tools.TimerScreen(snap, state.studyPlan, state.studyLog, vm::logStudy)
                MoreDestination.Notes -> com.shreenipane.orbit.ui.tools.NotesScreen(snap, state.notes, vm)
                MoreDestination.Faculty -> com.shreenipane.orbit.ui.hostel.VtopLinkScreen("Faculty info",
                    "Look up a faculty member's cabin, e-mail and open hours.", Regex("faculty|employee", RegexOption.IGNORE_CASE))
                MoreDestination.Fees -> com.shreenipane.orbit.ui.hostel.VtopLinkScreen("Fees & payments",
                    "See fees due and download receipts.", Regex("payment|fees", RegexOption.IGNORE_CASE))
                MoreDestination.Friends -> FriendsScreen(snap, state.friends, vm::myShareCode, vm::importFriend, vm::removeFriend, state.settings.alias,
                    planCode = vm::myPlanCode, onImportPlan = vm::importPlan)
                MoreDestination.Settings -> SettingsScreen(state, canUseLock, vm)
            }
        }
    }
}
