package com.shreenipane.orbit.core.parse

import com.shreenipane.orbit.core.model.GradeRange
import com.shreenipane.orbit.core.model.GradeResult
import com.shreenipane.orbit.core.model.GradeStats
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * VTOP pages shown on the Campus screen and in Results. Every parser finds
 * columns by header text where it can and skips rows it doesn't understand,
 * so a layout change shows fewer rows rather than crashing.
 */
/** VTOP results pages: per-semester grades and each course's grade ranges. */
object CampusParsers {

    private fun Element.clean() = text().replace(Regex("\\s+"), " ").trim()

    private fun headerMap(row: Element?): Map<String, Int> =
        row?.select("> th, > td")?.mapIndexed { i, e -> e.clean().uppercase() to i }?.toMap() ?: emptyMap()

    private fun idx(h: Map<String, Int>, vararg names: String, fallback: Int): Int =
        h.entries.firstOrNull { (k, _) -> names.any { k.contains(it) } }?.value ?: fallback

    /** Published results for one semester (doStudentGradeView). */
    fun gradeView(html: String): List<GradeResult> {
        val rows = Jsoup.parse(html).select("tr")
        val headerRow = rows.firstOrNull { it.text().contains("Course Code") } ?: return emptyList()
        val h = headerMap(headerRow)
        val iCode = idx(h, "COURSE CODE", fallback = 1)
        val iTitle = idx(h, "COURSE TITLE", fallback = 2)
        val iType = idx(h, "COURSE TYPE", fallback = 3)
        val iTotal = idx(h, "GRAND TOTAL", "TOTAL", fallback = 5)
        val iGrade = h.entries.firstOrNull { it.key == "GRADE" }?.value ?: 6
        return rows.mapNotNull { tr ->
            if (tr == headerRow) return@mapNotNull null
            val c = tr.select("> td").map { it.clean() }
            if (c.size < 6 || c[0].isEmpty() || !c[0].all(Char::isDigit)) return@mapNotNull null
            GradeResult(c.getOrElse(iCode) { "" }, c.getOrElse(iTitle) { "" }, c.getOrElse(iType) { "" },
                c.getOrElse(iTotal) { "" }, c.getOrElse(iGrade) { "" },
                courseId = DETAIL_ID.find(tr.html())?.groupValues?.get(1).orEmpty())
        }.distinctBy { it.code + it.type }
    }

    private val DETAIL_ID = Regex("getGradeViewDetails\\(\\s*(?:'|&#39;)([^'&]+)")
    private val GRADE_LETTERS = listOf("S", "A", "B", "C", "D", "E", "F")

    /**
     * The statistics table from getGradeViewDetails: a row of grade letters,
     * then one row of class strength, grading strength, mean, SD and one mark
     * range per grade. Null when the page has no such table.
     */
    fun gradeStats(html: String): GradeStats? {
        val doc = Jsoup.parse(html)
        // The detail is injected inside the outer grade table, so take the innermost match.
        val table = doc.select("table").firstOrNull { it.select("table table").isEmpty() && it.select("table").size == 1 &&
            it.text().contains("Range of Grades", true) } ?: return null
        val rows = table.select("tr").map { tr -> tr.select("> td, > th").map { it.clean() } }
        val letters = rows.firstOrNull { r -> r.size >= 3 && r.all { it.uppercase() in GRADE_LETTERS } }?.map { it.uppercase() } ?: GRADE_LETTERS
        val values = rows.firstOrNull { r -> r.size >= 4 && r[0].toDoubleOrNull() != null } ?: return null
        val ranges = values.drop(4).zip(letters).map { (raw, g) -> GradeRange(g, prettyRange(raw)) }.filter { it.range.isNotBlank() }
        return GradeStats(values[0], values.getOrElse(2) { "" }, values.getOrElse(3) { "" }, ranges)
    }

    /** ">=67 and <81" → "67 – 81", ">=81 #" → "81 and above", "<33" → "below 33". */
    internal fun prettyRange(raw: String): String {
        val n = Regex("\\d+(?:\\.\\d+)?").findAll(raw).map { it.value }.toList()
        return when {
            n.size >= 2 -> "${n[0]} – ${n[1]}"
            n.size == 1 && raw.contains(">") -> "${n[0]} and above"
            n.size == 1 && raw.contains("<") -> "below ${n[0]}"
            n.size == 1 -> n[0]
            else -> raw.replace("#", "").trim()
        }
    }
}
