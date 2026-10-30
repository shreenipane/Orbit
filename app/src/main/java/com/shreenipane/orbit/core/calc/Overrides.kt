package com.shreenipane.orbit.core.calc

import com.shreenipane.orbit.core.model.ClassOverride
import com.shreenipane.orbit.core.model.ClassSession
import com.shreenipane.orbit.core.model.OverrideKind
import java.time.LocalDate

/** Applies the user's own "cancelled" and "extra class" marks on top of VTOP's timetable. */
object Overrides {
    private fun m(hhmm: String) = hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()

    fun isCancelled(list: List<ClassOverride>, date: LocalDate, s: ClassSession): Boolean =
        list.any {
            it.kind == OverrideKind.CANCELLED && it.date == date.toString() &&
                it.courseCode == s.courseCode && it.isLab == s.isLab && it.start == s.start
        }

    fun asSession(o: ClassOverride): ClassSession = ClassSession(
        day = LocalDate.parse(o.date).dayOfWeek.value, start = o.start, end = o.end,
        slot = "Extra", courseCode = o.courseCode, courseTitle = o.courseTitle,
        typeCode = if (o.isLab) "LO" else "TH", venue = o.venue,
    )

    /** Extra classes from [today] (not yet started) up to, not including, [until]. */
    fun extrasBetween(list: List<ClassOverride>, today: LocalDate, nowMinute: Int, until: LocalDate?): List<ClassOverride> =
        list.filter { it.kind == OverrideKind.EXTRA }.filter { o ->
            val d = LocalDate.parse(o.date)
            !d.isBefore(today) && (until == null || d.isBefore(until)) && (d != today || m(o.start) > nowMinute)
        }

    /** The day's classes after overrides: cancelled ones flagged, extras added. */
    data class DayClass(val session: ClassSession, val cancelled: Boolean, val extra: Boolean)

    fun day(base: List<ClassSession>, date: LocalDate, list: List<ClassOverride>): List<DayClass> {
        val regular = base.map { DayClass(it, isCancelled(list, date, it), false) }
        val extras = list.filter { it.kind == OverrideKind.EXTRA && it.date == date.toString() }.map { DayClass(asSession(it), false, true) }
        return (regular + extras).sortedBy { m(it.session.start) }
    }
}
