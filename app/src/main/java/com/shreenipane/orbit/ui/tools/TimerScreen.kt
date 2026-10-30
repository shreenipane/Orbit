package com.shreenipane.orbit.ui.tools

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreenipane.orbit.core.calc.StudyLog
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.core.model.StudyItem
import com.shreenipane.orbit.core.model.StudySession
import com.shreenipane.orbit.ui.components.BrutalButton
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.Segmented
import com.shreenipane.orbit.ui.components.brutal
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.Pastel
import kotlinx.coroutines.delay
import java.time.LocalDate

private val LENGTHS = listOf(25, 50, 90)

private fun hm(min: Int) = if (min < 60) "${min}m" else "${min / 60}h" + if (min % 60 > 0) " ${min % 60}m" else ""

/**
 * A focus timer (25/50/90 min) tied to a subject. Finished or stopped sessions
 * are logged per subject; the week's totals show below. The screen stays on
 * while it runs.
 */
@Composable
fun TimerScreen(snap: Snapshot, plan: List<StudyItem>, log: List<StudySession>, onLog: (String, Int) -> Unit) {
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val planned = plan.filter { it.date == today.toString() && !it.done }.map { it.subject }
    val subjects = (planned + snap.timetable.courses.filterNot { it.isLab }.map { it.title }).distinct()

    var subject by rememberSaveable { mutableStateOf(planned.firstOrNull() ?: subjects.firstOrNull().orEmpty()) }
    var lengthIdx by rememberSaveable { mutableIntStateOf(0) }
    // Elapsed time survives rotation: banked seconds plus the running stretch since startedAt.
    var banked by rememberSaveable { mutableLongStateOf(0L) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var tick by rememberSaveable { mutableLongStateOf(0L) }
    val running = startedAt > 0L
    val total = LENGTHS[lengthIdx] * 60L
    val elapsed = banked + if (running) (tick - startedAt) / 1000 else 0L
    val left = (total - elapsed).coerceAtLeast(0L)

    fun stop(save: Boolean) {
        val mins = (elapsed / 60).toInt()
        if (save && mins >= 1) {
            onLog(subject, mins)
            Toast.makeText(ctx, "Logged ${hm(mins)} of $subject", Toast.LENGTH_SHORT).show()
        }
        banked = 0L; startedAt = 0L
    }

    LaunchedEffect(running) {
        while (startedAt > 0L) { tick = SystemClock.elapsedRealtime(); delay(500) }
    }
    LaunchedEffect(left, running) { if (running && left == 0L) stop(save = true) }
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    OrbitScreen(title = "Study timer") {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).brutal(if (running) Pastel.Green else Pastel.Card, radius = 18.dp, shadow = 5.dp)
                .padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%02d:%02d".format(left / 60, left % 60), fontSize = 64.sp, color = Pastel.Ink, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displayMedium)
                Text(subject.ifBlank { "Pick a subject" }, style = MaterialTheme.typography.titleMedium, color = Pastel.Ink, maxLines = 1)
            }
        }
        item { Segmented(LENGTHS.map { "$it min" }, lengthIdx, { if (!running && banked == 0L) lengthIdx = it }, Modifier.padding(top = 16.dp)) }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrutalButton(
                    if (running) "Pause" else if (banked > 0) "Resume" else "Start",
                    {
                        if (running) { banked = elapsed; startedAt = 0L }
                        else { val nowT = SystemClock.elapsedRealtime(); tick = nowT; startedAt = nowT }
                    },
                    Modifier.weight(1f), enabled = subject.isNotBlank(),
                )
                BrutalButton("Stop & save", { stop(save = true) }, Modifier.weight(1f), fill = Pastel.Card, enabled = running || banked > 0)
            }
        }
        if (subjects.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    subjects.forEach { s ->
                        val on = s == subject
                        Text(s, style = MaterialTheme.typography.labelLarge, color = Pastel.Ink, maxLines = 1,
                            modifier = Modifier.brutal(if (on) Pastel.Accent else Pastel.Card, radius = 10.dp, shadow = if (on) 3.dp else 1.dp, border = 1.5.dp)
                                .clickable(enabled = !running) { subject = s }.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
            }
        }
        val week = StudyLog.totals(log, today.minusDays(6))
        section(header = "Last 7 days", footer = if (week.isNotEmpty()) "Total ${hm(week.sumOf { it.minutes })}" else null) {
            if (week.isEmpty()) ListRow("Nothing logged yet", "Finished sessions show up here.")
            Rows(week) { t -> ListRow(t.subject, hm(t.minutes)) }
        }
    }
}
