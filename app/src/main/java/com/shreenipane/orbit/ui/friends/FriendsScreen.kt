@file:OptIn(ExperimentalLayoutApi::class)

package com.shreenipane.orbit.ui.friends

import android.content.Intent
import com.shreenipane.orbit.core.calc.Buddies
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.shreenipane.orbit.ui.components.brutal
import com.shreenipane.orbit.ui.theme.Pastel
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
    planCode: () -> String? = { null },
    onImportPlan: (String) -> String? = { "Study plans aren't supported here." },
) {
    // Friends included in "Everyone free"; all of them unless you untick some.
    var excluded by remember { mutableStateOf(setOf<String>()) }
    var planMessage by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var day by remember { mutableIntStateOf(LocalDate.now().dayOfWeek.value.coerceAtMost(6)) }
    var showQr by remember { mutableStateOf<QrMode?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        // The same scanner takes a friend's study plan as well as their timetable.
        if (text.contains("ORBITPLAN1:")) {
            val e = onImportPlan(text)
            scanError = e
            planMessage = if (e == null) "Study plan added to Exams → My study plan." else null
            return@rememberLauncherForActivityResult
        }
        val err = onImport(text)
        scanError = err
        // Hand the phone back with my code showing, so the friend can add me too.
        if (err == null) showQr = QrMode.AfterScan(ShareCode.decode(text)?.name.orEmpty())
    }
    fun launchScan() {
        scanError = null
        scanner.launch(
            ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Point at your friend's Orbit QR")
                .setBeepEnabled(false)
                .setOrientationLocked(true),
        )
    }

    OrbitScreen(title = "Friends", subtitle = "Timetables") {
        section(footer = "Both of you scan once to swap timetables.") {
            ListRow("Show my QR", "Let a friend scan you", chevron = true, onClick = { showQr = QrMode.Mine })
            RowDivider()
            ListRow("Scan a friend's QR", "Opens the camera", chevron = true, onClick = { launchScan() })
            RowDivider()
            ListRow("Share as text", "For WhatsApp or Telegram", chevron = true, onClick = {
                val code = myCode() ?: return@ListRow
                val name = alias.ifBlank { snap.regNo }
                val text = ShareCode.plainText(name, snap.timetable.sessions) + "\nAdd me in Orbit:\n$code"
                ctx.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                    "Share timetable",
                ))
            })
            RowDivider()
            ListRow("Paste a code", null, chevron = true, onClick = { adding = true })
            RowDivider()
            ListRow("Share my study plan", "As a QR your friend scans here", chevron = true, onClick = {
                if (planCode() == null) planMessage = "Your study plan is empty. Add sessions in Exams → My study plan."
                else showQr = QrMode.Plan
            })
        }
        planMessage?.let { msg ->
            item { Text(msg, color = Pastel.Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
        }
        scanError?.let { msg ->
            item { Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
        }

        if (friends.isEmpty()) {
            item { EmptyNote("No friends yet", "Scan a friend's QR, or paste a code they sent you.") }
            return@OrbitScreen
        }

        item { Segmented(DAYS, day - 1, { day = it + 1 }, Modifier.padding(top = 20.dp)) }

        // Group free time: you plus whichever friends are ticked.
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                friends.forEach { f ->
                    val on = f.name !in excluded
                    Text(f.name, style = MaterialTheme.typography.labelLarge, color = Pastel.Ink, maxLines = 1,
                        modifier = Modifier.brutal(if (on) Pastel.Accent else Pastel.Card, radius = 10.dp, shadow = if (on) 3.dp else 1.dp, border = 1.5.dp)
                            .clickable { excluded = if (on) excluded + f.name else excluded - f.name }
                            .padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
        }
        val group = friends.filter { it.name !in excluded }
        val gaps = FreeTime.common(listOf(snap.timetable.sessions) + group.map { it.sessions }, day)
        section(header = if (group.size == friends.size) "Everyone free on ${FULL[day - 1]}" else "You + ${group.size} free on ${FULL[day - 1]}") {
            if (gaps.isEmpty()) ListRow("No common gap", "Nothing of 30 minutes or more.")
            Rows(gaps) { g -> ListRow("${g.start} – ${g.end}", "${g.minutes / 60}h ${g.minutes % 60}m") }
        }

        // Class buddies: which friends share each of your courses.
        val buddies = friends.flatMap { f -> Buddies.shared(snap.timetable.sessions, f.sessions).map { it to f.name } }
            .groupBy({ it.first.code }, { it })
        if (buddies.isNotEmpty()) {
            section(header = "Class buddies") {
                Rows(buddies.values.toList()) { list ->
                    ListRow(list.first().first.title,
                        list.joinToString { (sh, name) -> if (sh.sameClass) "$name (same class)" else name })
                }
            }
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
    showQr?.let { mode ->
        MyQrDialog(if (mode is QrMode.Plan) planCode() else myCode(), alias.ifBlank { snap.regNo }, mode,
            onScan = { showQr = null; launchScan() }) { showQr = null }
    }
}

private sealed class QrMode {
    data object Mine : QrMode()
    data object Plan : QrMode()
    data class AfterScan(val friend: String) : QrMode()
}

@Composable
private fun MyQrDialog(code: String?, name: String, mode: QrMode, onScan: () -> Unit, onClose: () -> Unit) {
    val bitmap = remember(code) { code?.let { runCatching { qrBitmap(it) }.getOrNull() } }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Pastel.Page,
        title = {
            Text(when (mode) {
                is QrMode.AfterScan -> "Added ${mode.friend.ifBlank { "your friend" }}"
                QrMode.Plan -> "My study plan"
                QrMode.Mine -> "My timetable"
            })
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (mode is QrMode.AfterScan) {
                    Text("Now let them scan yours so they get your timetable too.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                if (mode is QrMode.Plan) {
                    Text("Your friend scans this in Friends → Scan a friend's QR.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                if (bitmap == null) {
                    Text(if (mode is QrMode.Plan) "Your study plan is empty." else "Sync your timetable first.", color = MaterialTheme.colorScheme.error)
                } else {
                    // QR codes need dark-on-light, so the code sits on white even in dark mode.
                    Box(Modifier.brutal(Color.White, radius = 14.dp).padding(12.dp)) {
                        Image(bitmap, contentDescription = "QR code with your timetable",
                            filterQuality = FilterQuality.None, modifier = Modifier.size(248.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(name, style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
        dismissButton = {
            if (mode is QrMode.Mine) TextButton(onClick = onScan) { Text("Scan theirs") }
        },
    )
}

/** Renders [text] as a QR code, one pixel per module (scaled up crisply by the Image). */
private fun qrBitmap(text: String): ImageBitmap {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2)
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    val w = m.width; val h = m.height
    val px = IntArray(w * h) { i -> if (m.get(i % w, i / w)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
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
                Text("Paste the message your friend sent.")
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
