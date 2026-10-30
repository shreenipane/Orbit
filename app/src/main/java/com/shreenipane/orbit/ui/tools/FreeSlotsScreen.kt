package com.shreenipane.orbit.ui.tools

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.calc.FreeNow
import com.shreenipane.orbit.core.calc.FreeTime
import com.shreenipane.orbit.core.calc.Schedule
import com.shreenipane.orbit.core.model.Friend
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.RowDivider
import com.shreenipane.orbit.ui.components.Rows
import com.shreenipane.orbit.ui.components.Segmented
import com.shreenipane.orbit.ui.components.Tag
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.Good
import com.shreenipane.orbit.ui.theme.Pastel
import com.shreenipane.orbit.ui.theme.Warn
import java.time.LocalDateTime

private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val FULL = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

private fun span(minutes: Int) = if (minutes < 60) "${minutes}m" else "${minutes / 60}h" + if (minutes % 60 > 0) " ${minutes % 60}m" else ""

/** Your free gaps for each day, and who (you included) is free right now. */
@Composable
fun FreeSlotsScreen(snap: Snapshot, friends: List<Friend>) {
    val now = LocalDateTime.now()
    val minute = now.hour * 60 + now.minute
    // Today runs on the calendar's day order when VTOP sets one (e.g. a Saturday on Monday's timetable).
    val todayOrder = snap.calendar?.days?.firstOrNull { it.date == now.toLocalDate().toString() }?.dayOrder ?: now.dayOfWeek.value
    var day by rememberSaveable { mutableIntStateOf(now.dayOfWeek.value.coerceIn(1, 6)) }
    val mine = snap.timetable.sessions

    OrbitScreen(title = "Free slots") {
        section(header = "Right now") {
            val meFree = FreeNow.isFree(mine, todayOrder, minute)
            val current = Schedule.forDay(mine, todayOrder).firstOrNull { s ->
                val st = s.start.take(2).toInt() * 60 + s.start.drop(3).take(2).toInt()
                val en = s.end.take(2).toInt() * 60 + s.end.drop(3).take(2).toInt()
                minute in st until en
            }
            ListRow(
                "You",
                if (meFree) FreeNow.freeUntil(mine, todayOrder, minute)?.let { "Free until $it" } ?: "Free for the rest of the day"
                else "In ${current?.courseTitle ?: "class"} until ${current?.end ?: "—"}",
                trailing = { Tag(if (meFree) "Free" else "Busy", if (meFree) Good else Warn) },
            )
            friends.forEach { f ->
                RowDivider()
                val free = FreeNow.isFree(f.sessions, todayOrder, minute)
                ListRow(
                    f.name,
                    if (free) FreeNow.freeUntil(f.sessions, todayOrder, minute)?.let { "Free until $it" } ?: "Free for the rest of the day"
                    else "In class",
                    trailing = { Tag(if (free) "Free" else "Busy", if (free) Good else Warn) },
                )
            }
            if (friends.isEmpty()) {
                RowDivider()
                ListRow("Add friends to see who's free", "More → Friends → scan their QR", titleColor = Pastel.InkMuted)
            }
        }

        item { Segmented(DAYS, day - 1, { day = it + 1 }, Modifier.padding(top = 20.dp)) }

        val gaps = FreeTime.common(listOf(mine), day)
        section(header = "Your free slots on ${FULL[day - 1]}") {
            if (gaps.isEmpty()) ListRow("No gaps of 30 minutes or more", null)
            Rows(gaps) { g -> ListRow("${g.start} – ${g.end}", span(g.minutes)) }
        }
    }
}
