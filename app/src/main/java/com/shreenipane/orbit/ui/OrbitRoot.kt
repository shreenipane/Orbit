package com.shreenipane.orbit.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
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
            newLogin = vm::newLogin,
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
                HorizontalDivider(thickness = 2.dp, color = com.shreenipane.orbit.ui.theme.Pastel.Line)
                OrbitTabBar(tab) { i -> if (tab == i && Tab.entries[i] == Tab.More) more = null; tab = i }
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
                        onOpen = { tab = Tab.More.ordinal; more = it.name },
                        overrides = state.overrides, onToggleCancel = vm::toggleCancelled, onRemoveOverride = vm::removeOverride)
                    Tab.Timetable -> TimetableScreen(snap, state.refreshing, refresh,
                        overrides = state.overrides, onToggleCancel = vm::toggleCancelled,
                        onAddOverride = vm::addOverride, onRemoveOverride = vm::removeOverride)
                    Tab.Attendance -> AttendanceScreen(snap, state.refreshing, refresh, overrides = state.overrides)
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
        Text("Orbit is locked", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        com.shreenipane.orbit.ui.components.BrutalButton("Unlock", onUnlock)
    }
}

/**
 * Bottom tabs. Hand-built rather than Material's NavigationBar, which keeps
 * padding around each label and cut "Attendance" off on narrow phones and
 * with large system fonts. Each tab here gets its full fifth of the width,
 * and labels are sized in dp so the system font size can't overflow them.
 */
@Composable
private fun OrbitTabBar(selected: Int, onSelect: (Int) -> Unit) {
    val P = com.shreenipane.orbit.ui.theme.Pastel
    val labelSize = with(androidx.compose.ui.platform.LocalDensity.current) { 11.dp.toSp() }
    Row(
        Modifier.fillMaxWidth().background(P.CardTeal).navigationBarsPadding().height(68.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEachIndexed { i, t ->
            val on = i == selected
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(width = 56.dp, height = 30.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(15.dp))
                        .background(if (on) P.Accent else androidx.compose.ui.graphics.Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(t.icon, contentDescription = t.label, tint = if (on) P.Ink else P.InkMuted, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    t.label, maxLines = 1, softWrap = false,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = labelSize, letterSpacing = 0.sp,
                        fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
                    ),
                    color = if (on) P.Ink else P.InkMuted,
                )
            }
        }
    }
}
