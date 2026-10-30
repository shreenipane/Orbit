package com.shreenipane.orbit.core.calc

import com.shreenipane.orbit.core.model.AcademicCalendar
import com.shreenipane.orbit.core.model.AttendanceRecord
import com.shreenipane.orbit.core.model.ClassSession
import com.shreenipane.orbit.core.model.DayKind
import com.shreenipane.orbit.core.model.Exam
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * "How many classes are left before CAT 1 / CAT 2 / FAT?"
 *
 * Walks VTOP's academic calendar day by day. Only instructional days count,
 * and a day runs the timetable of its weekday, or of its "day order" when VTOP
 * says e.g. "Saturday – Monday day order". Each timetable block counts as one
 * class (a two-slot lab is one block).
 */
object ExamPlanner {

    val EXAMS = listOf("CAT1" to "CAT 1", "CAT2" to "CAT 2", "FAT" to "FAT")

    data class CourseLeft(val courseCode: String, val courseTitle: String, val isLab: Boolean, val classes: Int)

    data class Countdown(
        val exam: String,
        val label: String,
        val start: LocalDate,
        val daysLeft: Long,
        val instructionalDaysLeft: Int,
        val courses: List<CourseLeft>,
    ) {
        val totalClasses get() = courses.sumOf { it.classes }
    }

    private fun minutes(hhmm: String) = hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()

    /** VTOP exam-schedule headings ("CAT - I", "CAT1", "FAT") → CAT1 / CAT2 / FAT. */
    fun normalizeExam(name: String): String? {
        val t = name.uppercase().replace(" ", "")
        return when {
            t.startsWith("CAT-II") || t.startsWith("CATII") || t.startsWith("CAT2") || t.startsWith("CAT-2") -> "CAT2"
            t.startsWith("CAT-I") || t.startsWith("CATI") || t.startsWith("CAT1") || t.startsWith("CAT-1") -> "CAT1"
            t.contains("FAT") || t.contains("FINALASSESSMENT") -> "FAT"
            else -> null
        }
    }

    /**
     * First day of each exam window. The calendar gives the window; the exam
     * schedule (when published) gives per-course dates, and the earliest wins.
     * FAT falls back to the day after the last instructional day.
     */
    fun examStarts(cal: AcademicCalendar?, schedule: List<Exam>): Map<String, LocalDate> {
        val out = mutableMapOf<String, LocalDate>()
        cal?.days?.filter { it.kind == DayKind.EXAM && it.exam != null }?.forEach { d ->
            val date = LocalDate.parse(d.date)
            out.merge(d.exam!!, date) { a, b -> minOf(a, b) }
        }
        schedule.forEach { e ->
            val key = normalizeExam(e.examType) ?: return@forEach
            val date = ExamTime.date(e) ?: return@forEach
            out.merge(key, date) { a, b -> minOf(a, b) }
        }
        if ("FAT" !in out) {
            cal?.days?.lastOrNull { it.kind == DayKind.INSTRUCTIONAL }?.let { out["FAT"] = LocalDate.parse(it.date).plusDays(1) }
        }
        return out
    }

    private fun courseKey(code: String, isLab: Boolean) = code + if (isLab) "#L" else "#T"

    /**
     * Classes per course from [today] (only those not yet started, using
     * [nowMinute]) up to but not including [until].
     */
    fun classesBetween(
        cal: AcademicCalendar, sessions: List<ClassSession>,
        today: LocalDate, nowMinute: Int, until: LocalDate,
    ): Pair<Int, List<CourseLeft>> {
        val counts = linkedMapOf<String, Int>()
        val titles = sessions.associate { courseKey(it.courseCode, it.isLab) to it.courseTitle }
        sessions.forEach { counts.putIfAbsent(courseKey(it.courseCode, it.isLab), 0) }
        var days = 0
        for (d in cal.days) {
            if (d.kind != DayKind.INSTRUCTIONAL) continue
            val date = LocalDate.parse(d.date)
            if (date.isBefore(today) || !date.isBefore(until)) continue
            days++
            val weekday = d.dayOrder ?: date.dayOfWeek.value
            sessions.filter { it.day == weekday }
                .filter { date != today || minutes(it.start) > nowMinute }
                .forEach { s -> counts.merge(courseKey(s.courseCode, s.isLab), 1, Int::plus) }
        }
        val courses = counts.map { (k, n) ->
            CourseLeft(k.substringBefore('#'), titles[k] ?: k.substringBefore('#'), k.endsWith("#L"), n)
        }.sortedWith(compareBy({ it.courseTitle }, { it.isLab }))
        return days to courses
    }

    /** One countdown per upcoming exam, soonest first. Needs the academic calendar. */
    fun countdowns(
        cal: AcademicCalendar?, schedule: List<Exam>, sessions: List<ClassSession>,
        today: LocalDate, nowMinute: Int,
    ): List<Countdown> {
        if (cal == null) return emptyList()
        val starts = examStarts(cal, schedule)
        return EXAMS.mapNotNull { (key, label) ->
            val start = starts[key] ?: return@mapNotNull null
            if (start.isBefore(today)) return@mapNotNull null
            val (days, courses) = classesBetween(cal, sessions, today, nowMinute, start)
            Countdown(key, label, start, ChronoUnit.DAYS.between(today, start), days, courses)
        }
    }

    /** Classes left in the semester for the course behind an attendance record. */
    fun remainingFor(record: AttendanceRecord, cal: AcademicCalendar?, sessions: List<ClassSession>, today: LocalDate, nowMinute: Int): Int? {
        if (cal == null) return null
        val end = cal.days.lastOrNull { it.kind == DayKind.INSTRUCTIONAL }?.let { LocalDate.parse(it.date).plusDays(1) } ?: return null
        val isLab = record.slot.startsWith("L") || record.courseType.contains("Lab", true)
        val (_, courses) = classesBetween(cal, sessions, today, nowMinute, end)
        return courses.firstOrNull { it.courseCode == record.courseCode && it.isLab == isLab }?.classes
    }
}
