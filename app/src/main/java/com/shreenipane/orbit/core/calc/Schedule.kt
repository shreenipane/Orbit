package com.shreenipane.orbit.core.calc

import com.shreenipane.orbit.core.model.ClassSession

/** What's happening now and next, for the dashboard. */
object Schedule {
    data class Now(val ongoing: ClassSession?, val next: ClassSession?, val today: List<ClassSession>, val minutesToNext: Int?)

    private fun m(hhmm: String) = hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()

    fun forDay(sessions: List<ClassSession>, isoDay: Int) =
        sessions.filter { it.day == isoDay }.sortedBy { m(it.start) }

    /** [isoDay] 1..7, [minuteOfDay] 0..1439. */
    fun now(sessions: List<ClassSession>, isoDay: Int, minuteOfDay: Int): Now {
        val today = forDay(sessions, isoDay)
        val ongoing = today.firstOrNull { minuteOfDay >= m(it.start) && minuteOfDay < m(it.end) }
        val next = today.firstOrNull { m(it.start) > minuteOfDay }
        return Now(ongoing, next, today, next?.let { m(it.start) - minuteOfDay })
    }
}
