package com.shreenipane.orbit.core.parse

import com.shreenipane.orbit.core.model.Announcement
import com.shreenipane.orbit.core.model.BiometricEntry
import com.shreenipane.orbit.core.model.Faculty
import com.shreenipane.orbit.core.model.GradeRange
import com.shreenipane.orbit.core.model.GradeResult
import com.shreenipane.orbit.core.model.GradeStats
import com.shreenipane.orbit.core.model.KeyValue
import com.shreenipane.orbit.core.model.PendingPayment
import com.shreenipane.orbit.core.model.Receipt
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * VTOP pages shown on the Campus screen and in Results. Every parser finds
 * columns by header text where it can and skips rows it doesn't understand,
 * so a layout change shows fewer rows rather than crashing.
 */
object CampusParsers {

    private fun Element.clean() = text().replace(Regex("\\s+"), " ").trim()

    /**
     * Spotlight on POST /vtop/home: one "offcanvas" sheet per category, its
     * header naming the category, its body a list of announcements.
     */
    fun announcements(html: String): List<Announcement> {
        val doc = Jsoup.parse(html)
        val out = mutableListOf<Announcement>()
        for (sheet in doc.select(".offcanvas")) {
            val category = sheet.selectFirst(".offcanvas-header span, .offcanvas-header h5, .offcanvas-title")?.clean() ?: continue
            for (li in sheet.select(".offcanvas-body li")) {
                val text = li.clean()
                if (text.isBlank()) continue
                val a = li.selectFirst("a")
                val link = a?.attr("onclick")?.let { Regex("'([^']+)'").find(it)?.groupValues?.get(1) }
                    ?: a?.attr("href")?.takeIf { it.isNotBlank() && !it.startsWith("javascript") && it != "#" }
                out += Announcement(category, text, link)
            }
        }
        return out.distinct()
    }

    /**
     * Proctor and HOD/Dean pages: a details table of label/value cell pairs
     * (photo cells skipped).
     */
    fun keyValues(html: String): List<KeyValue> {
        val doc = Jsoup.parse(html)
        val scope = doc.getElementById("showDetails") ?: doc
        val out = mutableListOf<KeyValue>()
        for (tr in scope.select("tr")) {
            val cells = tr.select("> td").filter { it.selectFirst("img") == null }
            var i = 0
            while (i + 1 < cells.size) {
                val k = cells[i].clean().trimEnd(':').trim()
                val v = cells[i + 1].clean()
                if (k.isNotBlank() && v.isNotBlank() && k.length < 60) out += KeyValue(k, v)
                i += 2
            }
        }
        return out.distinct()
    }

    /** Faculty search results: name, designation, school; the employee id sits on the row's button. */
    fun facultySearch(html: String): List<Faculty> =
        Jsoup.parse(html).select("tr").mapNotNull { tr ->
            val c = tr.select("> td").map { it.clean() }
            if (c.size < 3) return@mapNotNull null
            val btn = tr.selectFirst("button")
            val id = btn?.id()?.takeIf { it.isNotBlank() }
                ?: btn?.attr("onclick")?.let { Regex("['\"](\\w+)['\"]").find(it)?.groupValues?.get(1) }
                ?: return@mapNotNull null
            Faculty(c[0], c[1], c[2], id)
        }

    /** One faculty member: labelled profile rows, then (optionally) open hours. */
    fun facultyDetail(html: String): List<KeyValue> {
        val doc = Jsoup.parse(html)
        val out = mutableListOf<KeyValue>()
        val tables = doc.select("table")
        tables.firstOrNull()?.select("tr")?.forEach { tr ->
            val c = tr.select("> td").filter { it.selectFirst("img") == null }.map { it.clean() }
            if (c.size >= 2 && c[0].isNotBlank() && c[1].isNotBlank()) out += KeyValue(c[0], c[1])
        }
        tables.drop(1).forEach { t ->
            t.select("tr").forEach { tr ->
                val c = tr.select("> td").map { it.clean() }
                if (c.size >= 2 && c[0].isNotBlank() && c[1].isNotBlank() &&
                    !c[0].contains("week day", true) && !c[0].contains("open hours", true)
                ) out += KeyValue("Open hours · ${c[0]}", c[1])
            }
        }
        return out
    }

    private fun headerMap(row: Element?): Map<String, Int> =
        row?.select("> th, > td")?.mapIndexed { i, e -> e.clean().uppercase() to i }?.toMap() ?: emptyMap()

    private fun idx(h: Map<String, Int>, vararg names: String, fallback: Int): Int =
        h.entries.firstOrNull { (k, _) -> names.any { k.contains(it) } }?.value ?: fallback

    fun pendingPayments(html: String): List<PendingPayment> {
        val table = Jsoup.parse(html).selectFirst("table") ?: return emptyList()
        val rows = table.select("tr")
        val h = headerMap(rows.firstOrNull())
        val iRef = idx(h, "FPREF", "REF", fallback = 1)
        val iHead = idx(h, "FEES HEAD", "HEAD", "DESCRIPTION", fallback = 2)
        val iDate = idx(h, "END DATE", "DUE", "DATE", fallback = 3)
        val iAmt = idx(h, "AMOUNT", fallback = 4)
        val iFine = idx(h, "FINE", fallback = 5)
        val iTotal = idx(h, "TOTAL", fallback = 6)
        return rows.drop(1).mapNotNull { tr ->
            val c = tr.select("> td").map { it.clean() }
            if (c.size < 5) null
            else PendingPayment(c.getOrElse(iRef) { "" }, c.getOrElse(iHead) { "" }, c.getOrElse(iDate) { "" },
                c.getOrElse(iAmt) { "" }, c.getOrElse(iFine) { "" }, c.getOrElse(iTotal) { "" })
        }
    }

    fun receipts(html: String): List<Receipt> {
        val rows = Jsoup.parse(html).select("tr")
        val h = headerMap(rows.firstOrNull())
        val iNo = idx(h, "RECEIPT", fallback = 0)
        val iDate = idx(h, "DATE", fallback = 1)
        val iAmt = idx(h, "AMOUNT", fallback = 2)
        return rows.drop(1).mapNotNull { tr ->
            val c = tr.select("> td").map { it.clean() }
            if (c.size < 3 || c.getOrElse(iNo) { "" }.isBlank()) null
            else Receipt(c[iNo], c.getOrElse(iDate) { "" }, c.getOrElse(iAmt) { "" })
        }
    }

    /** Biometric log rows: serial, date, time, location. */
    /**
     * The biometric log table. Columns are found by their headings (Date, Time,
     * Location/Device/Venue); without headings, Sl.No, date, time, location.
     */
    fun biometric(html: String): List<BiometricEntry> {
        val rows = Jsoup.parse(html).select("tr")
        val head = rows.firstOrNull { r -> r.select("> th, > td").any { it.clean().contains("time", true) } }
            ?.select("> th, > td")?.map { it.clean().lowercase() }
        val iDate = head?.indexOfFirst { it.contains("date") }?.takeIf { it >= 0 } ?: 1
        val iTime = head?.indexOfFirst { it.contains("time") }?.takeIf { it >= 0 } ?: 2
        val iLoc = head?.indexOfFirst { Regex("location|device|venue|block|gate|reader|place").containsMatchIn(it) }?.takeIf { it >= 0 } ?: 3
        return rows.mapNotNull { tr ->
            val c = tr.select("> td").map { it.clean() }
            if (c.size < 3 || c[0].isEmpty() || !c[0].all(Char::isDigit)) null
            else BiometricEntry(c.getOrElse(iDate) { "" }, c.getOrElse(iTime) { "" }, c.getOrElse(iLoc) { "" })
        }
    }

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
