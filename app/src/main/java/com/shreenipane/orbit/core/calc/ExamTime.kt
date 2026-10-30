package com.shreenipane.orbit.core.calc

import com.shreenipane.orbit.core.model.Exam
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** Turns VTOP's "21-Nov-2026" + "09:30 AM - 11:00 AM" into real times. */
object ExamTime {
    private val DATE: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive().appendPattern("dd-MMM-yyyy").toFormatter(Locale.ENGLISH)
    private val TIME: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive().appendPattern("hh:mm a").toFormatter(Locale.ENGLISH)

    /** "21-Nov-2026" (VTOP's usual date format) → LocalDate. */
    fun parseVtopDate(s: String): LocalDate? =
        Regex("\\d{1,2}-[A-Za-z]{3}-\\d{4}").find(s)?.value?.let { v ->
            runCatching { LocalDate.parse(v.padStart(11, '0'), DATE) }.getOrNull()
        }

    fun date(e: Exam): LocalDate? = parseVtopDate(e.date)

    private fun time(s: String): LocalTime? {
        val t = Regex("(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])").find(s) ?: return null
        val norm = t.groupValues[1].padStart(2, '0') + ":" + t.groupValues[2] + " " + t.groupValues[3].uppercase()
        return runCatching { LocalTime.parse(norm, TIME) }.getOrNull()
    }

    /** Exam start; falls back to the reporting time, then 09:00, when VTOP leaves the time blank. */
    fun start(e: Exam): LocalDateTime? {
        val d = date(e) ?: return null
        val t = time(e.examTime) ?: time(e.reportingTime) ?: LocalTime.of(9, 0)
        return d.atTime(t)
    }

    fun startMillis(e: Exam, zone: ZoneId = ZoneId.systemDefault()): Long? =
        start(e)?.atZone(zone)?.toInstant()?.toEpochMilli()

    /** Exams that haven't started yet, soonest first. */
    fun upcoming(exams: List<Exam>, now: LocalDateTime): List<Pair<Exam, LocalDateTime>> =
        exams.mapNotNull { e -> start(e)?.let { e to it } }
            .filter { it.second.isAfter(now) }
            .sortedBy { it.second }

    /** "in 3 days", "tomorrow", "today 2:00 PM", "in 4 h". */
    fun countdown(start: LocalDateTime, now: LocalDateTime): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), start.toLocalDate())
        val hours = java.time.Duration.between(now, start).toHours()
        return when {
            days == 0L && hours < 1 -> "starting soon"
            days == 0L -> "in $hours h"
            days == 1L -> "tomorrow"
            else -> "in $days days"
        }
    }
}
