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
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    Home("Today", Icons.Outlined.WbSunny),
    Timetable("Timetable", Icons.Outlined.CalendarMonth),
    Attendance("Attendance", Icons.Outlined.CheckCircle),
    Marks("Marks", Icons.Outlined.BarChart),
    More("More", Icons.Outlined.GridView),
}

@Composable
fun OrbitRoot(vm: AppViewModel, canUseLock: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 3.dp) }
        return
    }
    if (state.showLogin || state.snapshot == null) {
        LoginScreen(
            campus = state.campus,
            onCampus = vm::setCampus,
            onSignedIn = vm::onSignedIn,
            onCancel = if (state.snapshot != null) vm::cancelSignIn else null,
            busy = state.refreshing,
            vault = vm.vault,
            onLockerChanged = vm::lockerChanged,
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
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Tab.entries.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { if (tab == i && t == Tab.More) more = null; tab = i },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (state.sessionExpired) {
                Banner("VTOP session ended · showing saved data", "Sign in", vm::requestSignIn)
            }
            val refresh = { vm.refresh() }
            Box(Modifier.weight(1f)) {
                when (Tab.entries[tab]) {
                    Tab.Home -> HomeScreen(snap, state.reminders, state.settings.alias, state.refreshing, refresh,
                        onOpen = { tab = Tab.More.ordinal; more = it.name })
                    Tab.Timetable -> TimetableScreen(snap, state.refreshing, refresh)
                    Tab.Attendance -> AttendanceScreen(snap, state.refreshing, refresh)
                    Tab.Marks -> MarksScreen(snap, state.refreshing, refresh)
                    Tab.More -> MoreScreen(
                        destination = moreDest,
                        onOpen = { more = it.name },
                        onBack = { more = null },
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
fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Orbit is locked", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlock, shape = MaterialTheme.shapes.medium) { Text("Unlock") }
    }
}
