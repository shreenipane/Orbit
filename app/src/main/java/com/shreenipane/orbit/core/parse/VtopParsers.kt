package com.shreenipane.orbit.core.parse

import com.shreenipane.orbit.core.model.AttendanceRecord
import com.shreenipane.orbit.core.model.ClassSession
import com.shreenipane.orbit.core.model.CourseMarks
import com.shreenipane.orbit.core.model.Exam
import com.shreenipane.orbit.core.model.GradeHistory
import com.shreenipane.orbit.core.model.GradedCourse
import com.shreenipane.orbit.core.model.MarkComponent
import com.shreenipane.orbit.core.model.RegisteredCourse
import com.shreenipane.orbit.core.model.Semester
import com.shreenipane.orbit.core.model.Timetable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Turns VTOP HTML fragments into models. Pure JVM (only jsoup), so every parser
 * is unit-tested against recorded pages in src/test/resources/fixtures.
 *
 * VTOP changes its markup now and then. When a screen goes blank after a
 * VTOP update, save the raw response, add it as a fixture, and fix the parser
 * against it.
 */
object VtopParsers {

    // ---------- session ----------

    data class SessionInfo(val csrf: String, val regNo: String)

    /** Reads the post-login CSRF token and registration number from /vtop/content. */
    fun session(html: String): SessionInfo? {
        val doc = Jsoup.parse(html)
        val csrf = doc.selectFirst("input[name=_csrf]")?.attr("value")?.takeIf { it.isNotBlank() }
        val reg = doc.selectFirst("input[name=authorizedIDX]")?.attr("value")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("input[name=authorizedID]")?.attr("value")?.takeIf { it.isNotBlank() }
        return if (csrf != null && reg != null) SessionInfo(csrf, reg) else null
    }

    /** True when VTOP handed back its login/landing page instead of data. */
    fun looksLoggedOut(html: String): Boolean =
        html.contains("vtopLoginForm") || html.contains("id=\"captchaStr\"") ||
            html.contains("name=\"captchaStr\"") || html.contains("/vtop/prelogin/setup")

    fun menuUnavailable(html: String): Boolean = html.contains("This menu is not available")

    // ---------- semesters ----------

    fun semesters(html: String): List<Semester> =
        Jsoup.parse(html).select("select[name=semesterSubId] option").mapNotNull { o ->
            val id = o.attr("value").trim()
            val name = o.text().replace(Regex("\\s+"), " ").trim()
            if (id.isEmpty() || name.isEmpty()) null else Semester(id, name)
        }.distinctBy { it.id }

    // ---------- timetable ----------

    private val DAYS = mapOf("MON" to 1, "TUE" to 2, "WED" to 3, "THU" to 4, "FRI" to 5, "SAT" to 6, "SUN" to 7)

    /** "C1-CSE3009-ETH-105-AB-2-ALL" → slot, code, type, venue. Vellore codes look like BCSE302L. */
    private val GRID_CELL = Regex("^([A-Z]+\\d+)-([A-Z]{2,6}\\d{3,4}[A-Z]?)-([A-Z]{2,4})-(.+?)(?:-ALL)?$")

    fun timetable(html: String): Timetable {
        val doc = Jsoup.parse(html)
        val courses = registeredCourses(doc)
        val raw = gridSessions(doc)
        val merged = mergeAdjacent(raw)
        val withTitles = merged.map { s ->
            val candidates = courses.filter { it.code == s.courseCode }
            val match = candidates.firstOrNull { it.isLab == s.isLab } ?: candidates.firstOrNull()
            s.copy(courseTitle = match?.title ?: s.courseCode)
        }
        return Timetable(courses = courses, sessions = withTitles)
    }

    private fun normTime(t: String): String? {
        val m = Regex("^(\\d{1,2}):(\\d{2})").find(t.trim()) ?: return null
        return m.groupValues[1].padStart(2, '0') + ":" + m.groupValues[2]
    }

    private fun gridSessions(doc: Document): List<ClassSession> {
        val table = doc.getElementById("timeTableStyle") ?: return emptyList()
        val rows = table.select("tr").map { tr -> tr.select("> td, > th").map { it.text().trim() } }

        var theoryStart = emptyList<String>(); var theoryEnd = emptyList<String>()
        var labStart = emptyList<String>(); var labEnd = emptyList<String>()
        var pendingHeader: String? = null
        var currentDay: Int? = null
        val out = mutableListOf<ClassSession>()

        fun emit(day: Int, cells: List<String>, starts: List<String>, ends: List<String>) {
            cells.forEachIndexed { i, cell ->
                val m = GRID_CELL.find(cell) ?: return@forEachIndexed
                val start = starts.getOrNull(i)?.let(::normTime) ?: return@forEachIndexed
                val end = ends.getOrNull(i)?.let(::normTime) ?: return@forEachIndexed
                val (slot, code, type, venue) = m.destructured
                out += ClassSession(day, start, end, slot, code, code, type, venue)
            }
        }

        for (r in rows) {
            if (r.isEmpty()) continue
            val c0 = r[0].uppercase()
            val c1 = r.getOrNull(1)?.uppercase()
            when {
                (c0 == "THEORY" || c0 == "LAB") && c1 == "START" -> {
                    if (c0 == "THEORY") theoryStart = r.drop(2) else labStart = r.drop(2)
                    pendingHeader = c0
                }
                c0 == "END" -> {
                    if (pendingHeader == "THEORY") theoryEnd = r.drop(1) else labEnd = r.drop(1)
                    pendingHeader = null
                }
                c0 in DAYS && c1 == "THEORY" -> {
                    currentDay = DAYS.getValue(c0)
                    emit(currentDay, r.drop(2), theoryStart, theoryEnd)
                }
                c0 in DAYS && c1 == "LAB" -> { // some layouts put the day on the lab row too
                    currentDay = DAYS.getValue(c0)
                    emit(currentDay, r.drop(2), labStart, labEnd)
                }
                c0 == "LAB" && currentDay != null -> emit(currentDay, r.drop(1), labStart, labEnd)
            }
        }
        return out
    }

    private fun minutes(hhmm: String): Int = hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()

    /** L1 + L2 (and theory double slots) become one block when they are back to back. */
    internal fun mergeAdjacent(sessions: List<ClassSession>): List<ClassSession> {
        val sorted = sessions.sortedWith(compareBy({ it.day }, { minutes(it.start) }))
        val out = mutableListOf<ClassSession>()
        for (s in sorted) {
            val last = out.lastOrNull()
            if (last != null && last.day == s.day && last.courseCode == s.courseCode &&
                last.typeCode == s.typeCode && last.venue == s.venue &&
                minutes(s.start) - minutes(last.end) in -1..10
            ) {
                out[out.lastIndex] = last.copy(end = s.end, slot = last.slot + "+" + s.slot)
            } else {
                out += s
            }
        }
        return out
    }

    private fun headerIndex(headers: List<String>, vararg names: String): Int =
        headers.indexOfFirst { h -> names.any { n -> h.replace(Regex("\\s+"), " ").contains(n, ignoreCase = true) } }

    private fun registeredCourses(doc: Document): List<RegisteredCourse> {
        val table = doc.select("table").firstOrNull { t ->
            val head = t.selectFirst("tr:has(th)")?.text() ?: ""
            head.contains("Course") && head.contains("Slot")
        } ?: return emptyList()
        val headers = table.selectFirst("tr:has(th)")!!.select("th").map { it.text().trim() }
        val iCourse = headerIndex(headers, "Course").let { idx ->
            // "Course Option" also contains "Course"; prefer the exact header.
            headers.indexOfFirst { it.equals("Course", true) }.takeIf { it >= 0 } ?: idx
        }
        val iLtpjc = headerIndex(headers, "L T P J C", "LTPJC", "Credits")
        val iClass = headerIndex(headers, "Class Nbr", "Class Id", "Class Number")
        val iSlot = headerIndex(headers, "Slot")
        val iFaculty = headerIndex(headers, "Faculty")

        return table.select("tr").mapNotNull { tr ->
            val tds = tr.select("> td")
            if (tds.size < headers.size - 1 || tds.isEmpty()) return@mapNotNull null
            val courseText = tds.getOrNull(iCourse)?.text()?.trim() ?: return@mapNotNull null
            val cm = Regex("^([A-Z]{2,6}\\d{3,4}[A-Z]?)\\s*-\\s*(.+)$").find(courseText) ?: return@mapNotNull null
            val code = cm.groupValues[1]
            var title = cm.groupValues[2].trim()
            var type = ""
            Regex("\\(\\s*([^()]*)\\s*\\)\\s*$").find(title)?.let {
                type = it.groupValues[1].trim()
                title = title.removeRange(it.range).trim()
            }
            val credits = tds.getOrNull(iLtpjc)?.text()?.trim()?.split(Regex("\\s+"))?.lastOrNull()?.toDoubleOrNull() ?: 0.0
            val slotVenue = tds.getOrNull(iSlot)?.text()?.split(" - ", limit = 2) ?: emptyList()
            RegisteredCourse(
                code = code,
                title = title,
                type = type,
                credits = credits,
                slot = slotVenue.getOrNull(0)?.trim().orEmpty(),
                venue = slotVenue.getOrNull(1)?.trim().orEmpty(),
                faculty = tds.getOrNull(iFaculty)?.text()?.substringBefore(" - ")?.trim().orEmpty(),
                classId = tds.getOrNull(iClass)?.text()?.trim().orEmpty(),
            )
        }
    }

    // ---------- attendance ----------

    private val DETAIL_CALL = Regex(
        "callStudentAttendanceDetailDisplay\\s*\\(\\s*'[^']*'\\s*,\\s*'[^']*'\\s*,\\s*'([^']*)'\\s*,\\s*'([^']*)'"
    )

    fun attendance(html: String): List<AttendanceRecord> {
        val doc = Jsoup.parse(html)
        val table = doc.select("table").firstOrNull { it.text().contains("Attended") } ?: return emptyList()
        val headerRow = table.select("tr").firstOrNull { it.text().contains("Attended") } ?: return emptyList()
        val headers = headerRow.select("> th, > td").map { it.text().trim() }
        val iCourse = headerIndex(headers, "Course Detail", "Course")
        val iClass = headerIndex(headers, "Class Detail")
        val iFaculty = headerIndex(headers, "Faculty")
        val iAttended = headerIndex(headers, "Attended")
        val iTotal = headerIndex(headers, "Total Classes", "Total")
        val iPct = headerIndex(headers, "Attendance Percentage", "Percentage")
        val iDebar = headerIndex(headers, "Debar")

        return table.select("tr").mapNotNull { tr ->
            if (tr == headerRow) return@mapNotNull null
            val tds = tr.select("> td")
            if (tds.size < 8) return@mapNotNull null
            val attended = tds.getOrNull(iAttended)?.text()?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val total = tds.getOrNull(iTotal)?.text()?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val courseParts = tds[iCourse].text().split(" - ").map { it.trim() }
            val classParts = tds.getOrNull(iClass)?.text()?.split(" - ")?.map { it.trim() } ?: emptyList()
            val detail = DETAIL_CALL.find(tr.outerHtml())
            AttendanceRecord(
                courseCode = courseParts.getOrNull(0).orEmpty(),
                courseTitle = courseParts.getOrNull(1).orEmpty(),
                courseType = if (courseParts.size > 2) courseParts.last() else "",
                slot = classParts.getOrNull(1).orEmpty(),
                faculty = tds.getOrNull(iFaculty)?.text()?.substringBefore(" - ")?.trim().orEmpty(),
                attended = attended,
                total = total,
                vtopPercent = tds.getOrNull(iPct)?.text()?.filter { it.isDigit() }?.toIntOrNull()
                    ?: if (total == 0) 0 else Math.ceil(attended * 100.0 / total).toInt(),
                debarStatus = tds.getOrNull(iDebar)?.text()?.trim().orEmpty(),
                courseId = detail?.groupValues?.get(1).orEmpty(),
                typeCode = detail?.groupValues?.get(2).orEmpty(),
            )
        }
    }

    // ---------- marks ----------

    private fun num(s: String?): Double? = s?.trim()?.replace(",", "")?.toDoubleOrNull()

    fun marks(html: String): List<CourseMarks> {
        val doc = Jsoup.parse(html)
        val main = doc.selectFirst("table.customTable") ?: return emptyList()
        val rows = main.select("tr.tableContent").filter { it.parents().none { p -> p.hasClass("customTable-level1") } }
        val out = mutableListOf<CourseMarks>()
        for ((i, row) in rows.withIndex()) {
            // Course rows alternate with a row holding the nested component table.
            if (row.selectFirst("table.customTable-level1") != null) continue
            val info = row.select("> td")
            if (info.size < 8) continue
            val nested: Element? = rows.getOrNull(i + 1)?.selectFirst("table.customTable-level1")
            run {
                val components = nested?.select("tr")?.drop(1)?.mapNotNull { r ->
                    val c = r.select("> td").map { it.text().trim() }
                    if (c.size < 7) null else MarkComponent(
                        title = c[1], maxMark = num(c[2]), weightage = num(c[3]), status = c[4],
                        scored = num(c[5]), weighted = num(c[6]), remark = c.getOrNull(7).orEmpty(),
                    )
                } ?: emptyList()
                out += CourseMarks(
                    courseCode = info[2].text().trim(),
                    courseTitle = info[3].text().trim(),
                    courseType = info[4].text().trim(),
                    faculty = info[6].text().substringBefore(" - ").trim(),
                    slot = info[7].text().trim(),
                    components = components,
                )
            }
        }
        return out
    }

    // ---------- exam schedule ----------

    /**
     * doSearchExamScheduleForStudent returns one table: a single-cell row names
     * the exam (CAT1, CAT2, FAT…), followed by one 13-column row per course.
     */
    fun examSchedule(html: String): List<Exam> {
        val table = Jsoup.parse(html).selectFirst("table") ?: return emptyList()
        var type = ""
        val out = mutableListOf<Exam>()
        for (tr in table.select("tr")) {
            val v = tr.select("> td, > th").map { it.text().trim() }
            when {
                v.size == 1 -> type = v[0]
                v.size < 13 || v.any { it.equals("S.No.", true) || it.equals("Course Code", true) } -> Unit
                else -> out += Exam(
                    examType = type, courseCode = v[1], courseTitle = v[2], courseType = v[3],
                    slot = v[5], date = v[6], session = v[7], reportingTime = v[8], examTime = v[9],
                    venue = v[10], seatLocation = v[11], seatNumber = v[12],
                )
            }
        }
        return out
    }

    // ---------- grade history ----------

    fun gradeHistory(html: String): GradeHistory {
        val doc = Jsoup.parse(html)

        var registered: Double? = null; var earned: Double? = null; var cgpa: Double? = null
        for (t in doc.select("table")) {
            val headerCells = t.select("tr").firstOrNull()?.select("> th, > td")?.map { it.text().trim() } ?: continue
            val iCgpa = headerCells.indexOfFirst { it.equals("CGPA", true) }
            if (iCgpa < 0) continue
            val values = t.select("tr").drop(1).firstOrNull()?.select("> td")?.map { it.text().trim() } ?: continue
            cgpa = num(values.getOrNull(iCgpa))
            registered = num(values.getOrNull(headerCells.indexOfFirst { it.contains("Registered", true) }))
            earned = num(values.getOrNull(headerCells.indexOfFirst { it.contains("Earned", true) }))
            break
        }

        val courses = mutableListOf<GradedCourse>()
        val table = doc.select("table.customTable").firstOrNull { it.text().contains("Course Code") }
        if (table != null) {
            val headerRow = table.select("tr").first { it.text().contains("Course Code") }
            val h = headerRow.select("> th, > td").map { it.text().trim() }
            val iCode = h.indexOfFirst { it.equals("Course Code", true) }
            val iTitle = h.indexOfFirst { it.equals("Course Title", true) }
            val iType = h.indexOfFirst { it.equals("Course Type", true) }
            val iCredits = h.indexOfFirst { it.equals("Credits", true) }
            val iGrade = h.indexOfFirst { it.equals("Grade", true) }
            val iMonth = h.indexOfFirst { it.equals("Exam Month", true) }
            for (tr in table.select("tr")) {
                if (tr.parents().any { it.hasClass("customTable-level1") }) continue
                val c = tr.select("> td").map { it.text().trim() }
                if (c.size < h.size - 1) continue
                val code = c.getOrNull(iCode) ?: continue
                if (code.isBlank() || code == "Course Code") continue
                courses += GradedCourse(
                    code = code,
                    title = c.getOrNull(iTitle).orEmpty(),
                    type = c.getOrNull(iType).orEmpty(),
                    credits = num(c.getOrNull(iCredits)) ?: 0.0,
                    grade = c.getOrNull(iGrade).orEmpty(),
                    examMonth = c.getOrNull(iMonth).orEmpty(),
                )
            }
        }
        return GradeHistory(registered, earned, cgpa, courses)
    }
}
