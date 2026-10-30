package com.shreenipane.orbit.core.calc

import com.shreenipane.orbit.core.model.AcademicCalendar
import com.shreenipane.orbit.core.model.CalendarDay
import com.shreenipane.orbit.core.model.DayKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Holidays from the academic calendar, grouped into breaks: runs of
 * consecutive days without classes (holidays, no-instruction days, and the
 * weekends next to them). An ordinary weekend on its own isn't a break; a run
 * counts when it includes a weekday off or a named holiday.
 */
object Breaks {

    data class Break(
        val start: LocalDate,
        val end: LocalDate,
        /** Named holidays inside, e.g. "Diwali", "Independence Day". */
        val names: List<String>,
        /** Mon–Fri days off inside the break. */
        val weekdaysOff: Int,
    ) {
        val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
        val title: String
            get() = when {
                names.isNotEmpty() -> names.joinToString(" & ")
                days >= 5 -> "Break"
                else -> "No classes"
            }
    }

    private val GENERIC = Regex(
        "^(holiday|holidays|workingday|working day|no instructional day|exam days|instructional day|flexible|general.*|weekend.*|.*semester.*|.*day order)$",
        RegexOption.IGNORE_CASE,
    )

    /** "Holiday - General (Semester) (Milad-un-Nabi)" → "Milad-un-Nabi"; generic labels → null. */
    internal fun holidayName(day: CalendarDay): String? {
        if (day.kind != DayKind.HOLIDAY && day.kind != DayKind.NO_INSTRUCTION) return null
        val names = Regex("\\(([^()]+)\\)").findAll(day.label).map { it.groupValues[1].trim() }
            .filterNot { GENERIC.matches(it) }
            .toList()
        return names.lastOrNull()
    }

    private fun isOff(d: CalendarDay) = d.kind == DayKind.HOLIDAY || d.kind == DayKind.NO_INSTRUCTION ||
        (d.kind == DayKind.UNKNOWN && LocalDate.parse(d.date).dayOfWeek.value >= 6)

    fun all(cal: AcademicCalendar): List<Break> {
        val days = cal.days.sortedBy { it.date }
        val out = mutableListOf<Break>()
        var run = mutableListOf<CalendarDay>()

        fun flush() {
            if (run.isEmpty()) return
            val dates = run.map { LocalDate.parse(it.date) }
            val weekdays = dates.count { it.dayOfWeek.value <= 5 }
            val names = run.mapNotNull(::holidayName).distinct()
            if (weekdays > 0 || names.isNotEmpty()) out += Break(dates.first(), dates.last(), names, weekdays)
            run = mutableListOf()
        }

        var prev: LocalDate? = null
        for (d in days) {
            val date = LocalDate.parse(d.date)
            val contiguous = prev != null && ChronoUnit.DAYS.between(prev, date) == 1L
            if (isOff(d)) {
                if (!contiguous) flush()
                run.add(d)
            } else {
                flush()
            }
            prev = date
        }
        flush()
        return out
    }

    /** Breaks that haven't ended yet, soonest first. */
    fun upcoming(cal: AcademicCalendar?, today: LocalDate): List<Break> =
        cal?.let { all(it).filter { b -> !b.end.isBefore(today) } } ?: emptyList()

    /** "Mon 9 Nov" style day-of-week check for UI copy. */
    fun isWeekend(date: LocalDate) = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
}
