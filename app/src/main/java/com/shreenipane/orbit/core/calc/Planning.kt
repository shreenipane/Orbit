package com.shreenipane.orbit.core.calc

import com.shreenipane.orbit.core.model.AttendanceRecord
import com.shreenipane.orbit.core.model.CourseMarks
import com.shreenipane.orbit.core.model.GradeHistory
import com.shreenipane.orbit.core.model.PlannedCourse
import com.shreenipane.orbit.core.model.SlotTime
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** "What do I need in FAT?" from internal marks posted so far. */
object FatNeeded {
    data class Line(val target: Int, val neededOutOf100: Int?, val possible: Boolean)

    data class Result(val internal: Double, val internalWeight: Double, val fatWeight: Double, val lines: List<Line>)

    /**
     * Assumes FAT (out of 100) is scaled to whatever weight hasn't been posted
     * yet. VIT grades relatively, so the targets are total-mark lines, not grades.
     */
    fun of(m: CourseMarks, targets: List<Int> = listOf(50, 60, 70, 80, 90)): Result? {
        val posted = m.weightageSoFar
        val fatWeight = 100.0 - posted
        if (fatWeight <= 0.0 || posted <= 0.0) return null
        val lines = targets.map { t ->
            val need = (t - m.weightedTotal) / fatWeight * 100.0
            when {
                need <= 0 -> Line(t, 0, true)
                need > 100 -> Line(t, null, false)
                else -> Line(t, ceil(need - 1e-9).toInt(), true)
            }
        }
        return Result(m.weightedTotal, posted, fatWeight, lines)
    }
}

/** Credits earned per curriculum bucket (UC, PC, PE, UE…), from grade history. */
object Curriculum {
    private val NAMES = mapOf(
        // Vellore B.Tech (2021+ curricula).
        "FC" to "Foundation core", "DLES" to "Discipline-linked engineering sciences", "DC" to "Discipline core",
        "DE" to "Discipline elective", "PI" to "Projects & internship", "OE" to "Open elective",
        "BC" to "Bridge course", "NGCR" to "Non-graded core",
        // Older curricula and other campuses.
        "UC" to "University core", "PC" to "Programme core", "UE" to "University elective",
        "PE" to "Programme elective", "NC" to "Non-credit", "HSS" to "Humanities", "PMT" to "Projects & training",
    )

    fun nameOf(code: String) = NAMES[code.uppercase()] ?: code

    private val FAILED = setOf("F", "N", "W", "U", "")
    private val NOT_CLEARED = setOf("F", "N", "W", "U", "FAIL", "AB", "ABSENT", "RA")

    /**
     * Non-graded requirements (NGCR): no letter grade and often no credits,
     * so they're tracked by courses cleared, not credits.
     */
    fun isNonGraded(code: String) = code.uppercase() in setOf("NGCR", "NC")

    /** [credits] counts graded credits; for non-graded categories [courses] is what matters. */
    data class Bucket(val code: String, val name: String, val credits: Double, val courses: Int, val byCourses: Boolean = false)

    fun buckets(h: GradeHistory): List<Bucket> =
        h.courses.groupBy { it.distribution.ifBlank { "Other" }.uppercase() }
            .mapNotNull { (code, list) ->
                if (isNonGraded(code)) {
                    // Listed in the grade history and not failed = cleared, whatever the grade column says (P, -, blank).
                    val cleared = list.filter { it.grade.trim().uppercase() !in NOT_CLEARED || it.grade.isBlank() }
                    Bucket(code, nameOf(code), cleared.sumOf { it.credits }, cleared.size, byCourses = true)
                } else {
                    val passed = list.filter { it.grade.uppercase() !in FAILED && it.credits > 0 }
                    if (passed.isEmpty()) null else Bucket(code, nameOf(code), passed.sumOf { it.credits }, passed.size)
                }
            }
            .sortedByDescending { it.credits }

    /** One category's progress. [required] is null when neither VTOP nor you have said how many it needs. */
    /** With [byCourses], [earned] and [required] count courses instead of credits (NGCR). */
    data class Progress(val key: String, val name: String, val earned: Double, val required: Double?, val courses: Int, val byCourses: Boolean = false) {
        val left: Double? get() = required?.let { (it - earned).coerceAtLeast(0.0) }
        val done: Boolean get() = left == 0.0
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetter() }

    /**
     * Credits per category against what the curriculum needs. VTOP's own
     * requirement table wins; otherwise [targets] (category code → credits,
     * entered by you) fills in. Categories still to do come first.
     */
    fun progress(h: GradeHistory, targets: Map<String, Double> = emptyMap()): List<Progress> {
        val earned = buckets(h)
        val list = if (h.requirements.isNotEmpty()) {
            h.requirements.map { r ->
                // Match VTOP's row to course codes by name ("Discipline Elective" ↔ DE) for the course count.
                val b = earned.firstOrNull { norm(it.name) == norm(r.name) || it.code.equals(r.name, true) }
                Progress(b?.code ?: r.name, r.name, r.earned, r.required, b?.courses ?: 0)
            }
        } else {
            val codes = (earned.map { it.code } + targets.keys.map { it.uppercase() }).distinct()
            codes.map { code ->
                val b = earned.firstOrNull { it.code == code }
                val byCourses = isNonGraded(code)
                Progress(code, nameOf(code), if (byCourses) (b?.courses ?: 0).toDouble() else b?.credits ?: 0.0,
                    targets[code] ?: targets[code.lowercase()], b?.courses ?: 0, byCourses)
            }
        }
        return list.sortedWith(compareBy<Progress> { it.done }.thenBy { it.required == null }.thenByDescending { it.left ?: 0.0 })
    }
}

/** A simple revision plan: share the days before an exam across its subjects, last day to the first paper. */
object StudyPlan {
    data class Day(val date: LocalDate, val subjects: List<String>)

    /**
     * [papers] is subject → its exam date. Each day before the exams is given
     * to the subjects whose exam is still ahead, the nearest first; the day
     * right before a paper is reserved for that paper.
     */
    fun plan(papers: Map<String, LocalDate>, today: LocalDate, perDay: Int = 2): List<Day> {
        val upcoming = papers.filterValues { it.isAfter(today) }.toList().sortedBy { it.second }
        if (upcoming.isEmpty()) return emptyList()
        val last = upcoming.last().second
        val out = mutableListOf<Day>()
        var rotate = 0
        var d = today
        while (d.isBefore(last)) {
            val eve = upcoming.filter { it.second == d.plusDays(1) }.map { it.first }
            val pending = upcoming.filter { it.second.isAfter(d) }.map { it.first }
            val subjects = if (eve.isNotEmpty()) eve else {
                val pick = (0 until minOf(perDay, pending.size)).map { pending[(rotate + it) % pending.size] }
                rotate += perDay
                pick
            }
            if (subjects.isNotEmpty()) out += Day(d, subjects.distinct())
            d = d.plusDays(1)
        }
        return out
    }

    fun daysLeft(today: LocalDate, date: LocalDate) = ChronoUnit.DAYS.between(today, date)
}

/** FFCS planner: place slot combinations on the grid and find clashes. */
object FfcsPlanner {
    private fun m(hhmm: String) = hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()

    fun slotsOf(combo: String): List<String> =
        combo.uppercase().split('+', ',', ' ', '/').map { it.trim() }.filter { it.isNotEmpty() }

    /** Grid positions for a course; unknown slot names are returned separately. */
    fun place(c: PlannedCourse, grid: List<SlotTime>): Pair<List<SlotTime>, List<String>> {
        val names = slotsOf(c.slots)
        val found = names.flatMap { n -> grid.filter { it.slot == n } }
        val unknown = names.filter { n -> grid.none { it.slot == n } }
        return found to unknown
    }

    data class Clash(val a: PlannedCourse, val b: PlannedCourse, val where: SlotTime)

    fun clashes(courses: List<PlannedCourse>, grid: List<SlotTime>): List<Clash> {
        val placed = courses.map { it to place(it, grid).first }
        val out = mutableListOf<Clash>()
        for (i in placed.indices) for (j in i + 1 until placed.size) {
            val (a, sa) = placed[i]; val (b, sb) = placed[j]
            val hit = sa.firstOrNull { x -> sb.any { y -> x.day == y.day && m(x.start) < m(y.end) && m(y.start) < m(x.end) } }
            if (hit != null) out += Clash(a, b, hit)
        }
        return out
    }
}

/** Sunday report: how attendance moved since the last report. */
object WeeklyReport {
    data class Line(val title: String, val attendedDelta: Int, val heldDelta: Int, val before: Double, val after: Double)

    fun lines(before: List<AttendanceRecord>, after: List<AttendanceRecord>): List<Line> =
        after.mapNotNull { a ->
            val b = before.firstOrNull { it.courseCode == a.courseCode && it.slot == a.slot } ?: return@mapNotNull null
            val held = a.total - b.total
            if (held <= 0) null
            else Line(a.courseTitle, a.attended - b.attended, held,
                AttendanceCalc.percent(b.attended, b.total), AttendanceCalc.percent(a.attended, a.total))
        }

    fun summary(lines: List<Line>): String {
        if (lines.isEmpty()) return "No new classes recorded this week."
        val missed = lines.sumOf { it.heldDelta - it.attendedDelta }
        val worst = lines.minByOrNull { it.after - it.before }
        return buildString {
            append("Attended ${lines.sumOf { it.attendedDelta }} of ${lines.sumOf { it.heldDelta }} classes")
            if (missed > 0) append(", missed $missed")
            if (worst != null && worst.after < worst.before) {
                append(". Biggest drop: ${worst.title} ${String.format(java.util.Locale.US, "%.0f", worst.before)}% → ${String.format(java.util.Locale.US, "%.0f", worst.after)}%")
            }
            append(".")
        }
    }
}
