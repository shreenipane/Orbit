package com.shreenipane.orbit.core.calc

import com.shreenipane.orbit.core.model.ClassOverride
import com.shreenipane.orbit.core.model.Snapshot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * An .ics file Google Calendar (or any calendar app) can import: every
 * remaining class from the academic calendar, exams, DA deadlines and holiday
 * breaks. Times are floating local times, which calendars read as the phone's zone.
 */
object IcsExport {
    private val DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val D = DateTimeFormatter.ofPattern("yyyyMMdd")

    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    /** RFC 5545 lines are folded at 75 octets. */
    private fun fold(line: String): String {
        if (line.length <= 74) return line
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            val end = minOf(line.length, i + if (i == 0) 74 else 73)
            if (i > 0) sb.append("\r\n ")
            sb.append(line, i, end)
            i = end
        }
        return sb.toString()
    }

    fun build(snap: Snapshot, overrides: List<ClassOverride>, today: LocalDate, stamp: LocalDateTime): String {
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Orbit//VTOP companion//EN", "CALSCALE:GREGORIAN")
        var n = 0
        fun event(summary: String, start: String, end: String, allDay: Boolean, location: String = "", desc: String = "") {
            n++
            lines += "BEGIN:VEVENT"
            lines += "UID:orbit-${snap.regNo}-${snap.semester?.id}-$n@orbit"
            lines += "DTSTAMP:${stamp.format(DT)}"
            lines += if (allDay) "DTSTART;VALUE=DATE:$start" else "DTSTART:$start"
            lines += if (allDay) "DTEND;VALUE=DATE:$end" else "DTEND:$end"
            lines += "SUMMARY:${esc(summary)}"
            if (location.isNotBlank()) lines += "LOCATION:${esc(location)}"
            if (desc.isNotBlank()) lines += "DESCRIPTION:${esc(desc)}"
            lines += "END:VEVENT"
        }

        val cal = snap.calendar
        if (cal != null) {
            val keys = snap.timetable.sessions.map { it.courseCode to it.isLab }.distinct()
            keys.forEach { (code, lab) ->
                ExamPlanner.classDates(cal, snap.timetable.sessions, code, lab, today, -1, overrides = overrides).forEach { cd ->
                    val s = cd.session
                    event(
                        s.courseTitle + if (lab) " (Lab)" else "",
                        cd.date.atTime(java.time.LocalTime.parse(s.start)).format(DT),
                        cd.date.atTime(java.time.LocalTime.parse(s.end)).format(DT),
                        false, s.venue, "${s.courseCode} · ${s.slot}",
                    )
                }
            }
            Breaks.upcoming(cal, today).forEach { b ->
                event("${b.title} (no classes)", b.start.format(D), b.end.plusDays(1).format(D), true)
            }
        }
        ExamTime.upcoming(snap.exams, today.atStartOfDay()).forEach { (e, start) ->
            event("${e.examType}: ${e.courseTitle}", start.format(DT), start.plusHours(1).plusMinutes(30).format(DT), false,
                listOf(e.venue, e.seatLocation, e.seatNumber.takeIf { it.isNotBlank() }?.let { "Seat $it" }).filterNotNull()
                    .filter { it.isNotBlank() }.joinToString(" · "),
                "Report by ${e.reportingTime}")
        }
        snap.assignments.filter { !it.submitted }.forEach { da ->
            val due = ExamTime.parseVtopDate(da.dueDate) ?: return@forEach
            if (due.isBefore(today)) return@forEach
            event("DA due: ${da.title} · ${da.courseTitle}", due.format(D), due.plusDays(1).format(D), true)
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") { fold(it) } + "\r\n"
    }
}
