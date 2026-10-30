package com.shreenipane.orbit.core.calc

import com.shreenipane.orbit.core.model.AcademicCalendar
import com.shreenipane.orbit.core.model.AttendanceRecord
import com.shreenipane.orbit.core.model.ClassOverride
import com.shreenipane.orbit.core.model.ClassSession
import com.shreenipane.orbit.core.model.DayKind
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.core.model.StudyItem
import com.shreenipane.orbit.core.model.StudySession
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

private fun minutes(hhmm: String) = hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()

/** "What if I skip these days?" — attendance after missing every class on the chosen dates. */
object BunkPlanner {
    data class Projection(val record: AttendanceRecord, val missed: Int, val before: Double, val after: Double) {
        val dropsBelow get() = before >= 75.0 && after < 75.0
        val below get() = after < 75.0
    }

    /** Classes that run on [date]: no classes on holidays/exam days, day orders followed, your cancellations dropped. */
    fun classesOn(cal: AcademicCalendar?, sessions: List<ClassSession>, date: LocalDate, overrides: List<ClassOverride>): List<ClassSession> {
        val day = cal?.days?.firstOrNull { it.date == date.toString() }
        if (day != null && day.kind in setOf(DayKind.HOLIDAY, DayKind.NO_INSTRUCTION, DayKind.EXAM)) return emptyList()
        val weekday = day?.dayOrder ?: date.dayOfWeek.value
        return Overrides.day(Schedule.forDay(sessions, weekday), date, overrides).filterNot { it.cancelled }.map { it.session }
    }

    fun project(
        records: List<AttendanceRecord>, sessions: List<ClassSession>, cal: AcademicCalendar?,
        dates: Collection<LocalDate>, overrides: List<ClassOverride> = emptyList(),
    ): List<Projection> {
        val skipped = dates.flatMap { classesOn(cal, sessions, it, overrides) }
        return records.map { r ->
            val lab = ExamPlanner.isLab(r)
            val n = skipped.count { it.courseCode == r.courseCode && it.isLab == lab }
            Projection(r, n, AttendanceCalc.percent(r.attended, r.total), AttendanceCalc.percent(r.attended, r.total + n))
        }.filter { it.missed > 0 }.sortedBy { it.after }
    }
}

/** Notices worth a notification after a sync: a course falling under 75%, or reaching the point where one more miss would. */
object AttendanceWatch {
    data class Alert(val courseTitle: String, val text: String)

    fun alerts(old: List<AttendanceRecord>, new: List<AttendanceRecord>): List<Alert> = new.mapNotNull { n ->
        val o = old.firstOrNull { it.courseCode == n.courseCode && it.slot == n.slot } ?: return@mapNotNull null
        if (n.total == o.total) return@mapNotNull null // nothing new recorded
        val now = AttendanceCalc.percent(n.attended, n.total)
        val was = AttendanceCalc.percent(o.attended, o.total)
        val lineNow = now >= 75.0 && AttendanceCalc.skippable(n.attended, n.total) == 0
        val lineWas = was >= 75.0 && AttendanceCalc.skippable(o.attended, o.total) == 0
        when {
            was >= 75.0 && now < 75.0 -> Alert(n.courseTitle, "Dropped below 75% (now ${Math.round(now)}%). Attend the next ${AttendanceCalc.neededInARow(n.attended, n.total)} to get back.")
            lineNow && !lineWas -> Alert(n.courseTitle, "At ${Math.round(now)}%: missing the next class takes you under 75%.")
            else -> null
        }
    }
}

/** Free-time helpers for "who's free right now?". */
object FreeNow {
    /** True when nothing in [sessions] runs on [day] at [minute] (within college hours). */
    fun isFree(sessions: List<ClassSession>, day: Int, minute: Int): Boolean =
        sessions.none { it.day == day && minutes(it.start) <= minute && minute < minutes(it.end) }

    /** When the current free stretch ends: the next class start today, or null if nothing later. */
    fun freeUntil(sessions: List<ClassSession>, day: Int, minute: Int): String? =
        sessions.filter { it.day == day && minutes(it.start) > minute }.minByOrNull { minutes(it.start) }?.start
}

/** Courses you share with a friend; "same class" when the slot matches too. */
object Buddies {
    data class Shared(val code: String, val title: String, val sameClass: Boolean)

    fun shared(mine: List<ClassSession>, theirs: List<ClassSession>): List<Shared> {
        val theirCodes = theirs.groupBy { it.courseCode }
        return mine.groupBy { it.courseCode }.mapNotNull { (code, list) ->
            val other = theirCodes[code] ?: return@mapNotNull null
            val same = list.map { it.slot }.toSet().intersect(other.map { it.slot }.toSet()).isNotEmpty()
            Shared(code, list.first().courseTitle, same)
        }.sortedByDescending { it.sameClass }
    }
}

/**
 * A study plan packed into one QR-friendly string ("ORBITPLAN1:…"), the same
 * way timetables are shared. Decoding is size-limited and validated.
 */
object PlanCode {
    private const val PREFIX = "ORBITPLAN1:"
    private const val MAX_CODE = 12_000
    private const val MAX_TEXT = 64 * 1024
    private const val MAX_ITEMS = 150
    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n")
    private fun unesc(s: String): String {
        val sb = StringBuilder(); var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) { 'p' -> sb.append('|'); 'n' -> sb.append('\n'); else -> sb.append(s[i + 1]) }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    fun encode(from: String, items: List<StudyItem>): String {
        val text = buildString {
            append(esc(from)).append('\n')
            items.take(MAX_ITEMS).forEach { append(listOf(it.date, it.subject, it.topic).joinToString("|") { f -> esc(f) }).append('\n') }
        }
        val d = Deflater(Deflater.BEST_COMPRESSION).apply { setInput(text.toByteArray(Charsets.UTF_8)); finish() }
        val out = ByteArrayOutputStream(); val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    data class Plan(val from: String, val items: List<StudyItem>)

    /** New ids are assigned from [idBase] so imported items never clash with yours. */
    fun decode(raw: String, idBase: Long = System.currentTimeMillis()): Plan? = runCatching {
        val code = Regex("ORBITPLAN1:([A-Za-z0-9_-]+)").find(raw)?.groupValues?.get(1) ?: return null
        if (code.length > MAX_CODE) return null
        val inf = Inflater().apply { setInput(Base64.getUrlDecoder().decode(code)) }
        val out = ByteArrayOutputStream(); val buf = ByteArray(1024)
        try {
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > MAX_TEXT) return null
            }
        } finally { inf.end() }
        val lines = out.toString(Charsets.UTF_8.name()).split('\n')
        val rows = lines.drop(1).filter { it.isNotBlank() }
        if (rows.size > MAX_ITEMS) return null
        val items = rows.mapIndexed { i, l ->
            val f = l.split('|').map(::unesc)
            require(f.size >= 3 && DATE.matches(f[0]) && f[1].isNotBlank() && f[1].length <= 120 && f[2].length <= 300)
            LocalDate.parse(f[0])
            StudyItem(idBase + i, f[0], f[1], f[2])
        }
        Plan(unesc(lines[0]).take(40), items)
    }.getOrNull()
}

/** Time logged with the study timer. */
object StudyLog {
    data class Total(val subject: String, val minutes: Int)

    fun totals(log: List<StudySession>, since: LocalDate): List<Total> =
        log.filter { !LocalDate.parse(it.date).isBefore(since) }
            .groupBy { it.subject }
            .map { (s, list) -> Total(s, list.sumOf { it.minutes }) }
            .sortedByDescending { it.minutes }
}

/** End-of-semester summary numbers. */
object Recap {
    data class Line(val title: String, val value: Double)
    data class Data(
        val semester: String, val overallAttendance: Double?, val classesAttended: Int, val classesHeld: Int,
        val bestAttendance: Line?, val worstAttendance: Line?,
        val bestMarks: Line?, val worstMarks: Line?, val cgpa: Double?, val courses: Int,
    )

    fun of(snap: Snapshot): Data {
        val a = snap.attendance
        val att = a.sumOf { it.attended }; val held = a.sumOf { it.total }
        val byAtt = a.filter { it.total > 0 }.map { Line(it.courseTitle, AttendanceCalc.percent(it.attended, it.total)) }
        // Marks as a share of what's been assessed so far, per course.
        val byMarks = snap.marks.filter { it.weightageSoFar > 0 }.map { Line(it.courseTitle, it.weightedTotal * 100 / it.weightageSoFar) }
        return Data(
            semester = snap.semester?.name.orEmpty(),
            overallAttendance = if (held == 0) null else att * 100.0 / held,
            classesAttended = att, classesHeld = held,
            bestAttendance = byAtt.maxByOrNull { it.value }, worstAttendance = byAtt.minByOrNull { it.value },
            bestMarks = byMarks.maxByOrNull { it.value }, worstMarks = byMarks.minByOrNull { it.value },
            cgpa = snap.grades?.cgpa, courses = snap.timetable.courses.size,
        )
    }
}
