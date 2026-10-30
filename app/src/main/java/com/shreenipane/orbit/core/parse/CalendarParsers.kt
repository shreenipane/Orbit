package com.shreenipane.orbit.core.parse

import com.shreenipane.orbit.core.model.CalendarDay
import com.shreenipane.orbit.core.model.DayKind
import com.shreenipane.orbit.core.model.DigitalAssignment
import org.jsoup.Jsoup

/**
 * Academic calendar and digital assignment pages.
 *
 * Calendar flow: getDateForSemesterPreview lists class groups, getListForSemester
 * lists months, and processViewCalendar returns one month as a Sun–Sat grid.
 * Each day cell holds "<n>", then one or more "<description>" spans, each
 * optionally followed by a "(label)" span, e.g. "Instructional Day - General
 * (Semester)" + "(WorkingDay)", or "CAT - I - General (Semester)" + "(Exam Days)".
 */
object CalendarParsers {

    data class ClassGroup(val id: String, val name: String)

    fun classGroups(html: String): List<ClassGroup> =
        Jsoup.parse(html).select("select#classGroupId option, select[name=classGroupId] option").mapNotNull { o ->
            val id = o.attr("value").trim()
            val name = o.text().trim()
            if (id.isEmpty() || name.startsWith("--")) null else ClassGroup(id, name)
        }.distinctBy { it.id }

    /**
     * Picks the class group to read. Prefers the one the user set, then
     * "General (Flexible)", then any other "General" group, then the combined view.
     */
    fun pickClassGroup(groups: List<ClassGroup>, preferred: String?): ClassGroup? =
        groups.firstOrNull { preferred != null && (it.id == preferred || it.name.equals(preferred, true)) }
            ?: groups.firstOrNull { it.name.contains("Flexible", true) }
            ?: groups.firstOrNull { it.name.contains("General", true) }
            ?: groups.firstOrNull { it.id.equals("COMB", true) }
            ?: groups.firstOrNull()

    private val CAL_DATE = Regex("processViewCalendar\\(\\s*'([^']+)'\\s*\\)")

    /** Month keys ("01-AUG-2026") for processViewCalendar, in VTOP's order. */
    fun months(html: String): List<String> =
        Jsoup.parse(html).select("a[onclick], a[href], button[onclick]").mapNotNull { a ->
            CAL_DATE.find(a.attr("onclick") + " " + a.attr("href"))?.groupValues?.get(1)?.trim()
        }.distinct()

    private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    private val WEEKDAYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

    /**
     * Calendar entries for other programmes. VTOP's calendar can mix
     * programmes in the combined view; Orbit is B.Tech only.
     */
    private val OTHER_PROGRAMME = Regex(
        "\\b(m\\.?\\s?tech|mba|mca|m\\.?\\s?sc|b\\.?\\s?sc|bca|bba|b\\.?\\s?com|m\\.?\\s?com|ph\\.?\\s?d|integrated|freshers?|first year|law|b\\.?\\s?des|b\\.?\\s?arch|m\\.?\\s?des|pharm)\\b",
        RegexOption.IGNORE_CASE,
    )

    internal fun examOf(text: String): String? {
        val t = text.uppercase()
        Regex("CAT\\s*-?\\s*(II|2)\\b").find(t)?.let { return "CAT2" }
        Regex("CAT\\s*-?\\s*(I|1)\\b").find(t)?.let { return "CAT1" }
        if (Regex("\\bFAT\\b|FINAL ASSESSMENT").containsMatchIn(t) && !t.contains("LAB FAT")) return "FAT"
        return null
    }

    private const val WD = "(monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thurs|thur|thu|friday|fri|saturday|sat|sunday|sun)\\b"

    /**
     * Which weekday's timetable a date follows, when VTOP says so: "Monday Day
     * Order", "Saturday - Wednesday Day Order", "Tuesday timetable", "follows
     * Friday schedule". Null means the date's own weekday.
     */
    internal fun dayOrderOf(text: String): Int? {
        val patterns = listOf(
            Regex("\\b$WD\\s*(?:'s)?\\s*(?:day\\s*-?\\s*order|time\\s*-?\\s*table|schedule)", RegexOption.IGNORE_CASE),
            Regex("follow(?:s|ing)?\\s+(?:the\\s+)?$WD", RegexOption.IGNORE_CASE),
            Regex("day\\s*order\\s*[:\\-]?\\s*$WD", RegexOption.IGNORE_CASE),
        )
        val prefixes = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        for (p in patterns) {
            val m = p.find(text) ?: continue
            return prefixes.indexOf(m.groupValues[1].lowercase().take(3)) + 1
        }
        return null
    }

    /**
     * VTOP can put every class group's entry for a day into one block:
     * "Instructional Day General (Semester) Instructional Day General (Flexible)
     * Instructional Day General Freshers". Split before each entry so one
     * group's text (e.g. Freshers) doesn't knock out the whole day.
     */
    private val ENTRY_START = Regex(
        "(?<!no\\s)(?=\\b(?:no\\s+)?instructional\\s+day|\\bholiday\\b|\\bworking\\s*day)",
        RegexOption.IGNORE_CASE,
    )

    internal fun entries(texts: List<String>): List<String> =
        texts.flatMap { t -> t.split(ENTRY_START).map { it.trim() }.filter { it.isNotEmpty() } }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    internal fun classify(texts: List<String>, group: String? = null): Triple<DayKind, String?, Int?> {
        val all = entries(texts)
        // Your class group's own entry wins when VTOP lists several groups on one day.
        val mine = group?.let(::norm)?.takeIf { it.isNotEmpty() }?.let { g -> all.filter { norm(it).contains(g) } }.orEmpty()
        val relevant = mine.ifEmpty { all.filterNot { OTHER_PROGRAMME.containsMatchIn(it) } }
        if (relevant.isEmpty()) return Triple(DayKind.UNKNOWN, null, null)
        val joined = relevant.joinToString(" ")
        // A "Saturday - Monday Day Order" note can sit in its own entry next to the group's.
        val dayOrder = dayOrderOf(joined) ?: dayOrderOf(all.filterNot { OTHER_PROGRAMME.containsMatchIn(it) }.joinToString(" "))
        examOf(joined)?.let { return Triple(DayKind.EXAM, it, null) }
        val lower = joined.lowercase()
        return when {
            Regex("(?<!no )instructional day").containsMatchIn(lower) || lower.contains("workingday") || lower.contains("working day") ->
                Triple(DayKind.INSTRUCTIONAL, null, dayOrder)
            lower.contains("no instructional") -> Triple(DayKind.NO_INSTRUCTION, null, null)
            lower.contains("holiday") || lower.contains("vacation") -> Triple(DayKind.HOLIDAY, null, null)
            else -> Triple(DayKind.UNKNOWN, null, null)
        }
    }

    /** One month grid → dated days. [calDate] is the key it was requested with, e.g. "01-AUG-2026". */
    private val FULL_MONTHS = listOf("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST",
        "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER")

    /** The "AUGUST 2026" heading of a month page, as (year, month). */
    internal fun monthHeading(text: String): Pair<Int, Int>? {
        val m = Regex("\\b(${FULL_MONTHS.joinToString("|")})\\s+(\\d{4})\\b", RegexOption.IGNORE_CASE).find(text) ?: return null
        return m.groupValues[2].toInt() to FULL_MONTHS.indexOf(m.groupValues[1].uppercase()) + 1
    }

    fun month(html: String, calDate: String, group: String? = null): List<CalendarDay> {
        val parts = calDate.split("-")
        if (parts.size != 3) return emptyList()
        val month = MONTHS.indexOf(parts[1].uppercase()) + 1
        val year = parts[2].toIntOrNull() ?: return emptyList()
        if (month == 0) return emptyList()

        val doc = Jsoup.parse(html)
        // A page for a different month than asked (VTOP falls back to its default month) would mislabel every day.
        monthHeading(doc.text())?.let { (y, mo) -> if (y != year || mo != month) return emptyList() }
        val table = doc.selectFirst("table.calendar-table") ?: doc.selectFirst("table") ?: return emptyList()
        val out = mutableListOf<CalendarDay>()
        for (cell in table.select("td")) {
            val spans = cell.select("span").map { it.text().replace(Regex("\\s+"), " ").trim() }
            val n = spans.firstOrNull()?.toIntOrNull() ?: continue
            val texts = spans.drop(1).filter { it.isNotBlank() }
            // Fold "(label)" spans into the description before them.
            val events = mutableListOf<String>()
            texts.forEach { if (it.startsWith("(") && events.isNotEmpty()) events[events.lastIndex] += " $it" else events += it }
            val (kind, exam, order) = classify(events, group)
            out += CalendarDay(
                date = "%04d-%02d-%02d".format(year, month, n),
                kind = kind, exam = exam, dayOrder = order,
                label = events.joinToString(" · "),
            )
        }
        return out.distinctBy { it.date }.sortedBy { it.date }
    }

    // ---------- digital assignments ----------

    data class DaCourse(val classId: String, val code: String, val title: String, val type: String)

    private val COURSE_CODE = Regex("^[A-Z]{2,6}\\d{3,4}[A-Z]?$")
    /** Class numbers look like VL2025260100123 / AP2025264000131: campus letters + long digits. */
    private val CLASS_ID = Regex("\\b([A-Z]{2,4}\\d{9,})\\b")
    private val DUE = Regex("\\d{1,2}-[A-Za-z]{3}-\\d{4}")

    /**
     * doDigitalAssignment: one row per course that has DAs. Located by
     * content, not column position: the course-code cell, then title and
     * type after it, and the class number from a cell or the row's onclick.
     */
    fun daCourses(html: String): List<DaCourse> =
        Jsoup.parse(html).select("tr").mapNotNull { tr ->
            val cells = tr.select("> td").map { it.text().trim() }
            if (cells.size < 5) return@mapNotNull null
            val iCode = cells.indexOfFirst { COURSE_CODE.matches(it) }
            if (iCode < 0) return@mapNotNull null
            val classId = cells.firstOrNull { CLASS_ID.matches(it) }
                ?: CLASS_ID.find(tr.outerHtml())?.groupValues?.get(1)
                ?: return@mapNotNull null
            DaCourse(
                classId = classId,
                code = cells[iCode],
                title = cells.getOrNull(iCode + 1).orEmpty(),
                type = cells.getOrNull(iCode + 2).orEmpty(),
            )
        }.distinctBy { it.classId }

    private val DOWNLOAD = Regex("vtopDownload\\('([^']+)'\\)")

    /**
     * processDigitalAssignment: one row per assignment. Usual layout is
     * Sl.No · Title · Max · Weightage · Due · QP · Status · Update · DA, but
     * only the serial and title positions are relied on; the due date is the
     * first date-looking cell, and the upload code is the hidden "code" input.
     */
    fun daList(html: String, course: DaCourse): List<DigitalAssignment> =
        Jsoup.parse(html).select("tr").mapNotNull { tr ->
            val tds = tr.select("> td")
            val c = tds.map { it.text().trim() }
            if (c.size < 6 || c[0].isEmpty() || !c[0].all(Char::isDigit)) return@mapNotNull null
            val iDue = c.indexOfFirst { DUE.containsMatchIn(it) }.takeIf { it >= 0 } ?: 4
            val status = when {
                c.size >= 9 -> c[6]
                else -> c.firstOrNull { it.contains("Uploaded", true) || it.contains("Not Uploaded", true) }.orEmpty()
            }
            val mcode = tr.selectFirst("input[name=code]")?.attr("value")?.trim().orEmpty()
            val qp = tds.getOrNull(iDue + 1)?.outerHtml()?.let { DOWNLOAD.find(it)?.groupValues?.get(1) }.orEmpty()
            DigitalAssignment(
                courseCode = course.code, courseTitle = course.title, courseType = course.type, classId = course.classId,
                title = c[1], maxMark = c.getOrNull(2).orEmpty(), weightage = c.getOrNull(3).orEmpty(),
                dueDate = c.getOrNull(iDue).orEmpty(), status = status, mcode = mcode, qpUrl = qp,
            )
        }

    /** processDigitalAssignmentUpload returns hidden code/opt pairs the upload must echo back. */
    fun uploadTokens(html: String): List<Pair<String, String>> {
        val doc = Jsoup.parse(html)
        val codes = doc.select("input[name=code]").map { it.attr("value") }
        val opts = doc.select("input[name=opt]").map { it.attr("value") }
        return codes.zip(opts)
    }

    sealed class UploadResult {
        object Success : UploadResult()
        object OtpRequired : UploadResult()
        data class Failed(val message: String) : UploadResult()
    }

    /**
     * VTOP answers an upload with a few spans: "Uploaded successfully", or the
     * student's e-mail address when it has mailed an OTP (then an error, if any,
     * in the third span), or an error message in the second span.
     */
    fun uploadResult(html: String): UploadResult {
        val spans = Jsoup.parse(html).select("span").map { it.text().trim() }
        if (spans.isEmpty()) return UploadResult.Failed("VTOP gave an empty response.")
        if (spans.any { it.equals("Uploaded successfully", true) }) return UploadResult.Success
        if (spans[0].contains("@")) {
            val err = spans.getOrNull(2).orEmpty()
            return if (err.isBlank() && spans.getOrNull(1).isNullOrBlank()) UploadResult.OtpRequired
            else UploadResult.Failed(err.ifBlank { "VTOP rejected the OTP." })
        }
        return UploadResult.Failed(spans.getOrNull(1)?.ifBlank { null } ?: spans[0])
    }
}
