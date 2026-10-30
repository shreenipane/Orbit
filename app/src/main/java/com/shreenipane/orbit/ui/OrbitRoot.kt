@file:OptIn(ExperimentalMaterial3Api::class)

package com.shreenipane.orbit.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.data.Notifier
import com.shreenipane.orbit.ui.attendance.AttendanceScreen
import com.shreenipane.orbit.ui.components.Banner
import com.shreenipane.orbit.ui.home.HomeScreen
import com.shreenipane.orbit.ui.login.LoginScreen
import com.shreenipane.orbit.ui.marks.MarksScreen
import com.shreenipane.orbit.ui.more.MoreDestination
import com.shreenipane.orbit.ui.more.MoreScreen
import com.shreenipane.orbit.ui.timetable.TimetableScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Today", Icons.Rounded.Home),
    Timetable("Timetable", Icons.Rounded.CalendarMonth),
    Attendance("Attendance", Icons.Rounded.CheckCircle),
    Marks("Marks", Icons.Rounded.BarChart),
    More("More", Icons.Rounded.Menu),
}

@Composable
fun OrbitRoot(vm: AppViewModel, canUseLock: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.showLogin || state.snapshot == null) {
        LoginScreen(
            campus = state.campus,
            onCampus = vm::setCampus,
            onSignedIn = vm::onSignedIn,
            onCancel = if (state.snapshot != null) vm::cancelSignIn else null,
            busy = state.refreshing,
        )
        return
    }
    NotificationPermissionAsk()
    MainScaffold(state, state.snapshot!!, vm, canUseLock)
}

/** Android 13+ needs a runtime grant before any alert can show. Asked once, after sign-in. */
@Composable
private fun NotificationPermissionAsk() {
    if (Build.VERSION.SDK_INT < 33) return
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences("orbit", 0)
        if (!Notifier.canPost(ctx) && !prefs.getBoolean("asked_notif", false)) {
            prefs.edit().putBoolean("asked_notif", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun MainScaffold(state: UiState, snap: Snapshot, vm: AppViewModel, canUseLock: Boolean) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var more by rememberSaveable { mutableStateOf<String?>(null) }
    val moreDest = more?.let { runCatching { MoreDestination.valueOf(it) }.getOrNull() }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() }
    }
    BackHandler(enabled = moreDest != null || tab != 0) {
        if (moreDest != null) more = null else tab = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (moreDest != null) IconButton(onClick = { more = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                title = {
                    if (moreDest != null) Text(moreDest.title) else SemesterPicker(snap, vm::selectSemester)
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }, enabled = !state.refreshing) {
                        Icon(Icons.Rounded.Refresh, "Refresh from VTOP")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { if (tab == i && t == Tab.More) more = null; tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val refresh = { vm.refresh() }
            Column {
                if (state.sessionExpired) {
                    Banner(
                        text = "Your VTOP session ended. Showing saved data.",
                        action = "Sign in",
                        onAction = vm::requestSignIn,
                    )
                }
                when (Tab.entries[tab]) {
                    Tab.Home -> HomeScreen(snap, state.reminders, state.settings.alias, state.refreshing, refresh)
                    Tab.Timetable -> TimetableScreen(snap)
                    Tab.Attendance -> AttendanceScreen(snap, state.refreshing, refresh)
                    Tab.Marks -> MarksScreen(snap)
                    Tab.More -> MoreScreen(
                        destination = moreDest,
                        onOpen = { more = it.name },
                        state = state,
                        snap = snap,
                        vm = vm,
                        canUseLock = canUseLock,
                    )
                }
            }
        }
    }
}

@Composable
private fun SemesterPicker(snap: Snapshot, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Text(
            snap.semester?.name ?: "Pick a semester",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Change semester")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        snap.semesters.forEach { s ->
            DropdownMenuItem(text = { Text(s.name) }, onClick = { open = false; onPick(s.id) })
        }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.padding(8.dp))
        Text("🪐", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text("Orbit is locked", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlock) { Text("Unlock") }
    }
}
