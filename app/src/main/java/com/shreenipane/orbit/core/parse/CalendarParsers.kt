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

    internal fun classify(texts: List<String>): Triple<DayKind, String?, Int?> {
        val relevant = texts.filterNot { OTHER_PROGRAMME.containsMatchIn(it) }
        if (relevant.isEmpty()) return Triple(DayKind.UNKNOWN, null, null)
        val joined = relevant.joinToString(" ")
        val dayOrder = Regex("(${WEEKDAYS.joinToString("|")})\\s+day\\s*order", RegexOption.IGNORE_CASE)
            .find(joined)?.groupValues?.get(1)?.lowercase()?.let { WEEKDAYS.indexOf(it) + 1 }
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
    fun month(html: String, calDate: String): List<CalendarDay> {
        val parts = calDate.split("-")
        if (parts.size != 3) return emptyList()
        val month = MONTHS.indexOf(parts[1].uppercase()) + 1
        val year = parts[2].toIntOrNull() ?: return emptyList()
        if (month == 0) return emptyList()

        val doc = Jsoup.parse(html)
        val table = doc.selectFirst("table.calendar-table") ?: doc.selectFirst("table") ?: return emptyList()
        val out = mutableListOf<CalendarDay>()
        for (cell in table.select("td")) {
            val spans = cell.select("span").map { it.text().replace(Regex("\\s+"), " ").trim() }
            val n = spans.firstOrNull()?.toIntOrNull() ?: continue
            val texts = spans.drop(1).filter { it.isNotBlank() }
            // Fold "(label)" spans into the description before them.
            val events = mutableListOf<String>()
            texts.forEach { if (it.startsWith("(") && events.isNotEmpty()) events[events.lastIndex] += " $it" else events += it }
            val (kind, exam, order) = classify(events)
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

    /** doDigitalAssignment: one 7-column row per course with DAs. */
    fun daCourses(html: String): List<DaCourse> =
        Jsoup.parse(html).select("tr").mapNotNull { tr ->
            val c = tr.select("> td").map { it.text().trim() }
            if (c.size != 7 || !c[0].all(Char::isDigit) || c[0].isEmpty()) null
            else DaCourse(classId = c[1], code = c[2], title = c[3], type = c[4])
        }

    /** processDigitalAssignment: one 9-column row per assignment of a course. */
    fun daList(html: String, course: DaCourse): List<DigitalAssignment> =
        Jsoup.parse(html).select("tr").mapNotNull { tr ->
            val c = tr.select("> td").map { it.text().trim() }
            if (c.size != 9 || !c[0].all(Char::isDigit) || c[0].isEmpty()) null
            else DigitalAssignment(
                courseCode = course.code, courseTitle = course.title, courseType = course.type, classId = course.classId,
                title = c[1], maxMark = c[2], weightage = c[3], dueDate = c[4], status = c[6],
            )
        }
}
