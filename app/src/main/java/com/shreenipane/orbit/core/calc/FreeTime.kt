package com.shreenipane.orbit.core.calc

import com.shreenipane.orbit.core.model.ClassSession

/** Gaps when everyone is free, for "when can we meet?". */
object FreeTime {
    data class Gap(val start: String, val end: String) {
        val minutes get() = m(end) - m(start)
    }

    private fun m(hhmm: String) = hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()
    private fun hhmm(min: Int) = "%02d:%02d".format(min / 60, min % 60)

    /**
     * Common free gaps on [day] between [dayStart] and [dayEnd] across all
     * [schedules], at least [minMinutes] long.
     */
    fun common(
        schedules: List<List<ClassSession>>, day: Int,
        dayStart: String = "08:00", dayEnd: String = "19:30", minMinutes: Int = 30,
    ): List<Gap> {
        val busy = schedules.flatten().filter { it.day == day }
            .map { m(it.start) to m(it.end) }.sortedBy { it.first }
        val out = mutableListOf<Gap>()
        var cursor = m(dayStart)
        val end = m(dayEnd)
        for ((s, e) in busy) {
            if (s > cursor) out += Gap(hhmm(cursor), hhmm(minOf(s, end)))
            cursor = maxOf(cursor, e)
            if (cursor >= end) break
        }
        if (cursor < end) out += Gap(hhmm(cursor), hhmm(end))
        return out.filter { it.minutes >= minMinutes }
    }
}
