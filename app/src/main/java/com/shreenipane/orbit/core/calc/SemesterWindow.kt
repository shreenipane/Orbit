package com.shreenipane.orbit.core.calc

import com.shreenipane.orbit.core.model.AcademicCalendar
import com.shreenipane.orbit.core.model.DayKind
import java.time.LocalDate

/**
 * Guards against VTOP handing back another semester's academic calendar
 * (seen in the wild: a Fall semester came back with the previous Winter's
 * dates, so every course showed "0 classes left").
 *
 * The semester's name gives the months it can run in; calendar days
 * outside them are dropped, and a calendar with no instructional days
 * left after that is rejected.
 */
object SemesterWindow {
    private val NAME = Regex("(fall|winter|summer)\\D*?(\\d{4})", RegexOption.IGNORE_CASE)

    /** Dates a semester can plausibly span, from names like "Fall Semester 2026-27". */
    fun of(name: String): ClosedRange<LocalDate>? {
        val m = NAME.find(name) ?: return null
        val y = m.groupValues[2].toInt()
        return when (m.groupValues[1].lowercase()) {
            // Fall of AY y–y+1: July to December, with slack for early starts and FATs in January.
            "fall" -> LocalDate.of(y, 6, 1)..LocalDate.of(y + 1, 1, 31)
            // Winter of AY y–y+1: the first half of y+1.
            "winter" -> LocalDate.of(y, 12, 1)..LocalDate.of(y + 1, 6, 30)
            // Summer of AY y–y+1: the middle of y+1.
            else -> LocalDate.of(y + 1, 4, 15)..LocalDate.of(y + 1, 8, 31)
        }
    }

    /**
     * [cal] trimmed to [semesterName]'s window. Null when nothing
     * instructional is left, i.e. the calendar belongs to another semester.
     * Unrecognised names pass through untouched.
     */
    fun fit(cal: AcademicCalendar?, semesterName: String?): AcademicCalendar? {
        if (cal == null) return null
        val window = semesterName?.let(::of) ?: return cal
        val days = cal.days.filter { runCatching { LocalDate.parse(it.date) in window }.getOrDefault(false) }
        if (days.none { it.kind == DayKind.INSTRUCTIONAL }) return null
        return if (days.size == cal.days.size) cal else cal.copy(days = days)
    }

    private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    private val SIDE = Regex("transport|cdc|pat|fast track|re-?fat|intra|weekend", RegexOption.IGNORE_CASE)

    /**
     * The semester a grade-history row belongs to, from its exam month
     * ("Nov-2025" → Fall Semester 2025-26). Regular semesters win over
     * side ones (transport, CDC) that share the same months.
     */
    fun semesterFor(examMonth: String, semesters: List<com.shreenipane.orbit.core.model.Semester>): com.shreenipane.orbit.core.model.Semester? {
        val m = Regex("([A-Za-z]{3})[A-Za-z]*\\W*(\\d{4})").find(examMonth) ?: return null
        val month = MONTHS.indexOf(m.groupValues[1].uppercase()) + 1
        if (month == 0) return null
        val date = LocalDate.of(m.groupValues[2].toInt(), month, 15)
        val hits = semesters.filter { s -> of(s.name)?.let { date in it } == true }
        return hits.firstOrNull { !SIDE.containsMatchIn(it.name) } ?: hits.firstOrNull()
    }
}
