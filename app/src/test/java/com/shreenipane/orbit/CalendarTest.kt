package com.shreenipane.orbit

import com.shreenipane.orbit.core.calc.AttendanceCalc
import com.shreenipane.orbit.core.calc.ExamPlanner
import com.shreenipane.orbit.core.calc.ExamTime
import com.shreenipane.orbit.core.model.AcademicCalendar
import com.shreenipane.orbit.core.model.CalendarDay
import com.shreenipane.orbit.core.model.ClassSession
import com.shreenipane.orbit.core.model.DayKind
import com.shreenipane.orbit.core.parse.CalendarParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    @Test fun classGroupsAndPick() {
        val g = CalendarParsers.classGroups(fixture("calendar_options.html"))
        assertEquals(listOf("COMB", "ALL"), g.map { it.id })
        // No Flexible group here, so "General (Semester)" wins over the combined view.
        assertEquals("ALL", CalendarParsers.pickClassGroup(g, null)!!.id)
        val vellore = listOf(
            CalendarParsers.ClassGroup("COMB", "All Class Group (Combined)"),
            CalendarParsers.ClassGroup("ALL", "General (Semester)"),
            CalendarParsers.ClassGroup("GFLX", "General (Flexible)"),
        )
        assertEquals("GFLX", CalendarParsers.pickClassGroup(vellore, null)!!.id)
        assertEquals("ALL", CalendarParsers.pickClassGroup(vellore, "ALL")!!.id)
        assertEquals(6, CalendarParsers.months(fixture("calendar_options.html")).size)
    }

    @Test fun monthGrid() {
        val days = CalendarParsers.month(fixture("calendar_month.html"), "01-AUG-2026")
        assertEquals(31, days.size)
        assertEquals(DayKind.INSTRUCTIONAL, days[0].kind)          // 1 Aug
        assertEquals(DayKind.HOLIDAY, days[1].kind)                // 2 Aug
        assertEquals(DayKind.NO_INSTRUCTION, days[2].kind)         // 3 Aug
        assertEquals(DayKind.HOLIDAY, days[14].kind)               // 15 Aug, Independence Day
        assertEquals(DayKind.EXAM, days[16].kind)                  // 17 Aug
        assertEquals("CAT1", days[16].exam)
        assertEquals(14, days.count { it.kind == DayKind.INSTRUCTIONAL })
    }

    @Test fun classifyEdgeCases() {
        assertEquals(DayKind.INSTRUCTIONAL, CalendarParsers.classify(listOf("Instructional Day - General (Flexible) (Monday Day Order)")).first)
        assertEquals(1, CalendarParsers.classify(listOf("Instructional Day - General (Flexible) (Monday Day Order)")).third)
        assertEquals("CAT2", CalendarParsers.classify(listOf("CAT - II - General (Flexible) (Exam Days)")).second)
        assertEquals("FAT", CalendarParsers.classify(listOf("FAT - General (Flexible) (Exam Days)")).second)
        // Other programmes are ignored: only B.Tech counts.
        assertEquals(DayKind.UNKNOWN, CalendarParsers.classify(listOf("Instructional Day - MBA (Semester)")).first)
        assertEquals(DayKind.HOLIDAY, CalendarParsers.classify(listOf("Instructional Day - M.Tech", "Holiday - General (Flexible)")).first)
    }

    private fun day(date: String, kind: DayKind, exam: String? = null, order: Int? = null) = CalendarDay(date, kind, exam, order)

    @Test fun classesUntilExams() {
        // Mon 2 Nov 2026 … : Mon/Tue/Wed instructional, Thu holiday, Fri instructional,
        // Sat 7 Nov runs Monday's timetable, CAT2 from Mon 9 Nov, last class Fri 20 Nov.
        val cal = AcademicCalendar("GFLX", "General (Flexible)", listOf(
            day("2026-11-02", DayKind.INSTRUCTIONAL), day("2026-11-03", DayKind.INSTRUCTIONAL),
            day("2026-11-04", DayKind.INSTRUCTIONAL), day("2026-11-05", DayKind.HOLIDAY),
            day("2026-11-06", DayKind.INSTRUCTIONAL), day("2026-11-07", DayKind.INSTRUCTIONAL, order = 1),
            day("2026-11-08", DayKind.HOLIDAY),
            day("2026-11-09", DayKind.EXAM, "CAT2"), day("2026-11-10", DayKind.EXAM, "CAT2"),
            day("2026-11-16", DayKind.INSTRUCTIONAL), day("2026-11-20", DayKind.INSTRUCTIONAL),
        ))
        val sessions = listOf(
            ClassSession(1, "08:00", "08:50", "A1", "BCSE302L", "DBMS", "TH", "SJT-301"),
            ClassSession(3, "08:00", "08:50", "A2", "BCSE302L", "DBMS", "TH", "SJT-301"),
            ClassSession(4, "14:00", "15:40", "L31+L32", "BCSE302P", "DBMS", "LO", "SJT-517"),
            ClassSession(5, "10:00", "10:50", "B1", "BMAT201L", "Maths", "TH", "AB1-101"),
        )
        // It's Monday 2 Nov, 09:00: the 08:00 DBMS class has already started.
        val cds = ExamPlanner.countdowns(cal, emptyList(), sessions, LocalDate.of(2026, 11, 2), 9 * 60)
        assertEquals(listOf("CAT2", "FAT"), cds.map { it.exam })
        val cat2 = cds[0]
        assertEquals(7L, cat2.daysLeft)
        assertEquals(5, cat2.instructionalDaysLeft)             // 2,3,4,6,7 Nov
        val dbms = cat2.courses.first { it.courseCode == "BCSE302L" }
        assertEquals(2, dbms.classes)                           // Wed 4 + Sat 7 (Monday order); Mon 2 already started
        assertEquals(0, cat2.courses.first { it.courseCode == "BCSE302P" }.classes) // Thursday was a holiday
        assertEquals(1, cat2.courses.first { it.courseCode == "BMAT201L" }.classes)
        val fat = cds[1]
        assertEquals(LocalDate.of(2026, 11, 21), fat.start)     // day after last instructional day
        assertEquals(3, fat.courses.first { it.courseCode == "BCSE302L" }.classes) // Wed 4, Sat 7 (Monday order), Mon 16
    }

    @Test fun dayOrderWordings() {
        assertEquals(1, CalendarParsers.dayOrderOf("Instructional Day - General (Flexible) (Monday Day Order)"))
        assertEquals(3, CalendarParsers.dayOrderOf("Instructional Day (Saturday - Wednesday Day Order)"))
        assertEquals(2, CalendarParsers.dayOrderOf("Instructional Day - Tuesday Timetable"))
        assertEquals(5, CalendarParsers.dayOrderOf("Working day, follows Friday schedule"))
        assertEquals(4, CalendarParsers.dayOrderOf("Day Order: Thu"))
        assertEquals(null, CalendarParsers.dayOrderOf("Instructional Day - General (Semester) (WorkingDay)"))
        assertEquals(null, CalendarParsers.dayOrderOf("Monthly schedule review"))
    }

    @Test fun weekendSemesterAndSaturdayOrders() {
        // Weekend semester: classes on Sat/Sun rows of the timetable, both days instructional.
        // A normal-week Saturday that VTOP marks "Monday Day Order" runs Monday's classes.
        val cal = AcademicCalendar("GFLX", "General (Flexible)", listOf(
            day("2026-11-07", DayKind.INSTRUCTIONAL),            // Sat
            day("2026-11-08", DayKind.INSTRUCTIONAL),            // Sun
            day("2026-11-09", DayKind.INSTRUCTIONAL),            // Mon
            day("2026-11-14", DayKind.INSTRUCTIONAL, order = 1), // Sat, Monday day order
            day("2026-11-15", DayKind.HOLIDAY),                  // Sun, holiday
        ))
        val weekend = listOf(
            ClassSession(6, "09:00", "10:40", "L1+L2", "BCSE301P", "SE Lab", "LO", "SJT-1"),
            ClassSession(7, "09:00", "09:50", "A1", "BCSE301L", "SE", "TH", "SJT-2"),
            ClassSession(1, "08:00", "08:50", "B1", "BCSE301L", "SE", "TH", "SJT-2"),
        )
        val theory = ExamPlanner.classDates(cal, weekend, "BCSE301L", false, LocalDate.of(2026, 11, 7), 0)
        assertEquals(listOf("2026-11-08", "2026-11-09", "2026-11-14"), theory.map { it.date.toString() })
        assertEquals(1, theory.last().followsDay)                 // Saturday running Monday
        val lab = ExamPlanner.classDates(cal, weekend, "BCSE301P", true, LocalDate.of(2026, 11, 7), 0)
        assertEquals(listOf("2026-11-07"), lab.map { it.date.toString() }) // 14 Nov runs Monday, not Saturday
    }

    @Test fun bestPossible() {
        // 24/40 with 2 classes left: best is 26/42 = 61.9%, so 75% is out of reach.
        assertEquals(-1, AttendanceCalc.missableOfRemaining(24, 40, 2))
        assertEquals(26.0 / 42 * 100, AttendanceCalc.bestPossible(24, 40, 2), 1e-9)
    }

    @Test fun missableOfRemaining() {
        assertEquals(8, AttendanceCalc.missableOfRemaining(30, 36, 20))   // 42/56 = 75% after missing 8
        assertEquals(-1, AttendanceCalc.missableOfRemaining(10, 30, 5))   // 15/35 < 75% even attending all
        assertEquals(0, AttendanceCalc.missableOfRemaining(30, 40, 0))
    }

    @Test fun daParsing() {
        val courses = """<table><tr><td>Sl.No.</td><td>Class Nbr</td><td>Course Code</td><td>Course Title</td><td>Course Type</td><td>Faculty</td><td>Dashboard</td></tr>
            <tr><td>1</td><td>VL2025260100123</td><td>BCSE302L</td><td>Database Systems</td><td>Theory Only</td><td>Faculty</td><td></td></tr></table>"""
        val c = CalendarParsers.daCourses(courses)
        assertEquals(1, c.size)
        val list = """<table><tr><td>h</td></tr><tr><td>h</td></tr><tr><td>h</td></tr><tr><td>h</td></tr>
            <tr><td>1</td><td>DA 1</td><td>10</td><td>10</td><td>21-Nov-2026</td><td></td><td>File Not Uploaded</td><td></td><td></td></tr>
            <tr><td>2</td><td>DA 2</td><td>10</td><td>10</td><td>5-Dec-2026</td><td></td><td>02-Dec-2026 10:22</td><td></td><td></td></tr></table>"""
        val das = CalendarParsers.daList(list, c[0])
        assertEquals(2, das.size)
        assertEquals("DA 1", das[0].title)
        assertTrue(!das[0].submitted)
        assertTrue(das[1].submitted)
        assertEquals(LocalDate.of(2026, 11, 21), ExamTime.parseVtopDate(das[0].dueDate))
        assertEquals(LocalDate.of(2026, 12, 5), ExamTime.parseVtopDate(das[1].dueDate))
    }
}

class BreaksAndMatrixTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    @Test fun breaksFromRealMonth() {
        val days = com.shreenipane.orbit.core.parse.CalendarParsers.month(fixture("calendar_month.html"), "01-AUG-2026")
        val cal = AcademicCalendar("ALL", "General (Semester)", days)
        val breaks = com.shreenipane.orbit.core.calc.Breaks.all(cal)
        val byStart = breaks.associateBy { it.start.toString() }
        // Sat 15 + Sun 16 Aug: Independence Day weekend.
        val ind = byStart.getValue("2026-08-15")
        assertEquals(2, ind.days)
        assertEquals(listOf("Independence Day"), ind.names)
        // Wed 26 Aug: Milad-un-Nabi, one weekday off.
        val milad = byStart.getValue("2026-08-26")
        assertEquals(1, milad.days)
        assertEquals("Milad-un-Nabi", milad.title)
        assertEquals(1, milad.weekdaysOff)
        // A lone Sunday (23 Aug) isn't a break.
        assertTrue("2026-08-23" !in byStart)
        // Upcoming from 20 Aug: Milad-un-Nabi first (exam days 17–24 aren't holidays).
        val next = com.shreenipane.orbit.core.calc.Breaks.upcoming(cal, LocalDate.of(2026, 8, 20)).first()
        assertEquals("2026-08-26", next.start.toString())
    }

    private fun day(date: String, kind: DayKind, exam: String? = null) = CalendarDay(date, kind, exam)

    @Test fun matrixKeepsPastExams() {
        val cal = AcademicCalendar("G", "General (Flexible)", listOf(
            day("2026-09-01", DayKind.EXAM, "CAT1"),
            day("2026-10-01", DayKind.EXAM, "CAT2"),
            day("2026-11-02", DayKind.INSTRUCTIONAL),   // Mon
            day("2026-11-03", DayKind.INSTRUCTIONAL),   // Tue
            day("2026-11-04", DayKind.INSTRUCTIONAL),   // Wed
        ))
        val s = listOf(
            ClassSession(1, "08:00", "08:50", "A1", "BCSE302L", "DBMS", "TH", "V"),
            ClassSession(3, "08:00", "08:50", "A2", "BCSE302L", "DBMS", "TH", "V"),
            ClassSession(2, "14:00", "15:40", "L1+L2", "BCSE302P", "DBMS", "LO", "V"),
        )
        val m = ExamPlanner.matrix(cal, emptyList(), s, LocalDate.of(2026, 11, 2), 0)!!
        assertEquals(listOf("CAT1", "CAT2", "FAT"), m.exams.map { it.first })
        val th = m.rows.first { !it.isLab }
        assertEquals(null, th.left["CAT1"])
        assertEquals(null, th.left["CAT2"])
        assertEquals(2, th.left["FAT"])
        assertEquals(1, m.rows.first { it.isLab }.left["FAT"])
    }
}
