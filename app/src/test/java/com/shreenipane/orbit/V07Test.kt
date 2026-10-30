package com.shreenipane.orbit

import com.shreenipane.orbit.core.calc.Curriculum
import com.shreenipane.orbit.core.calc.ExamPlanner
import com.shreenipane.orbit.core.calc.FatNeeded
import com.shreenipane.orbit.core.calc.FfcsPlanner
import com.shreenipane.orbit.core.calc.IcsExport
import com.shreenipane.orbit.core.calc.Overrides
import com.shreenipane.orbit.core.calc.StudyPlan
import com.shreenipane.orbit.core.calc.WeeklyReport
import com.shreenipane.orbit.core.model.AcademicCalendar
import com.shreenipane.orbit.core.model.AttendanceRecord
import com.shreenipane.orbit.core.model.CalendarDay
import com.shreenipane.orbit.core.model.ClassOverride
import com.shreenipane.orbit.core.model.ClassSession
import com.shreenipane.orbit.core.model.CourseMarks
import com.shreenipane.orbit.core.model.DayKind
import com.shreenipane.orbit.core.model.MarkComponent
import com.shreenipane.orbit.core.model.OverrideKind
import com.shreenipane.orbit.core.model.PlannedCourse
import com.shreenipane.orbit.core.model.Semester
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.core.model.Timetable
import com.shreenipane.orbit.core.parse.CampusParsers
import com.shreenipane.orbit.core.parse.VtopParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class V07Test {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    @Test fun slotGridAndDistribution() {
        val grid = VtopParsers.timetable(fixture("timetable.html")).slotGrid
        val a1 = grid.filter { it.slot == "A1" }
        assertTrue("A1 placed", a1.isNotEmpty())
        assertTrue(a1.any { it.day == 2 && it.start == "09:00" })   // Tuesday 09:00 in the fixture
        assertTrue(grid.any { it.slot == "L1" })
        val g = VtopParsers.gradeHistory(fixture("grade_history.html"))
        assertEquals("UC", g.courses.first { it.code == "CSE1012" }.distribution)
        assertTrue(Curriculum.buckets(g).isNotEmpty())
    }

    @Test fun campusPages() {
        val results = CampusParsers.gradeView(fixture("grade_view.html"))
        assertTrue(results.size >= 2)
        assertEquals("CSE1008", results[0].code)
        assertEquals("B", results[0].grade)
        assertEquals("63", results[0].total)

        val spot = CampusParsers.announcements("""
            <div class="offcanvas"><div class="offcanvas-header"><span>Academics</span></div>
            <div class="offcanvas-body"><ul><li>FAT timetable published <a onclick="openLink('downloads/fat.pdf')">here</a></li><li>  </li></ul></div></div>""")
        assertEquals(1, spot.size)
        assertEquals("Academics", spot[0].category)
        assertEquals("downloads/fat.pdf", spot[0].link)

        val kv = CampusParsers.keyValues("""<table id="showDetails"><tr><td><img src="x"/></td></tr>
            <tr><td>Faculty Name</td><td>Dr. X</td></tr><tr><td>Cabin</td><td>SJT 305</td><td>Mobile</td><td>98xxxx</td></tr></table>""")
        assertEquals(listOf("Faculty Name", "Cabin", "Mobile"), kv.map { it.key })

        val fac = CampusParsers.facultySearch("""<table><tr><td>Name</td><td>Designation</td><td>School</td><td></td></tr>
            <tr><td>SRIVANI A</td><td>Assistant Professor</td><td>SCOPE</td><td><button id="70345">View</button></td></tr></table>""")
        assertEquals("70345", fac.single().empId)

        val due = CampusParsers.pendingPayments("""<table><tr><td>S.No</td><td>FPREFNO</td><td>Fees Heads</td><td>End Date</td><td>Amount</td><td>Fine</td><td>Total Amount</td></tr>
            <tr><td>1</td><td>FP123</td><td>Hostel Fee</td><td>15-Nov-2026</td><td>90000</td><td>0</td><td>90000</td></tr></table>""")
        assertEquals("Hostel Fee", due.single().head)
        assertEquals("15-Nov-2026", due.single().dueDate)
    }

    private val cal = AcademicCalendar("G", "General (Flexible)", listOf(
        CalendarDay("2026-11-02", DayKind.INSTRUCTIONAL), CalendarDay("2026-11-03", DayKind.INSTRUCTIONAL),
        CalendarDay("2026-11-04", DayKind.INSTRUCTIONAL),
    ))
    private val dbms = ClassSession(1, "08:00", "08:50", "A1", "BCSE302L", "DBMS", "TH", "SJT-301")
    private val dbms2 = ClassSession(3, "08:00", "08:50", "A2", "BCSE302L", "DBMS", "TH", "SJT-301")

    @Test fun overridesChangeCounts() {
        val today = LocalDate.of(2026, 11, 2)
        val base = ExamPlanner.remainingAll(cal, listOf(dbms, dbms2), today, 0)!!
        assertEquals(2, base.values.single())
        val ov = listOf(
            ClassOverride(1, "2026-11-04", OverrideKind.CANCELLED, "BCSE302L", "DBMS", false, "08:00", "08:50"),
            ClassOverride(2, "2026-11-03", OverrideKind.EXTRA, "BCSE302L", "DBMS", false, "16:00", "16:50"),
            ClassOverride(3, "2026-11-03", OverrideKind.EXTRA, "BCSE302L", "DBMS", false, "17:00", "17:50"),
        )
        assertEquals(3, ExamPlanner.remainingAll(cal, listOf(dbms, dbms2), today, 0, ov)!!.values.single())
        val dates = ExamPlanner.classDates(cal, listOf(dbms, dbms2), "BCSE302L", false, today, 0, overrides = ov)
        assertEquals(listOf("2026-11-02", "2026-11-03", "2026-11-03"), dates.map { it.date.toString() })
        val day = Overrides.day(listOf(dbms2), LocalDate.of(2026, 11, 4), ov)
        assertTrue(day.single().cancelled)
    }

    @Test fun fatNeeded() {
        val m = CourseMarks("X", "X", "TH", "F", "A1", listOf(
            MarkComponent("CAT-1", 50.0, 15.0, 40.0, 12.0, "P", ""),
            MarkComponent("CAT-2", 50.0, 15.0, 40.0, 12.0, "P", ""),
            MarkComponent("DA", 10.0, 30.0, 10.0, 30.0, "P", ""),
        ))
        val r = FatNeeded.of(m)!!
        assertEquals(54.0, r.internal, 1e-9)
        assertEquals(40.0, r.fatWeight, 1e-9)
        assertEquals(0, r.lines.first { it.target == 50 }.neededOutOf100)   // already past 50
        assertEquals(40, r.lines.first { it.target == 70 }.neededOutOf100)  // 16/40
        assertEquals(90, r.lines.first { it.target == 90 }.neededOutOf100)  // 36/40
        assertTrue(!FatNeeded.of(m, listOf(95))!!.lines.single().possible)  // would need 102.5
    }

    @Test fun studyPlanAndClashes() {
        val today = LocalDate.of(2026, 11, 1)
        val plan = StudyPlan.plan(mapOf("DBMS" to LocalDate.of(2026, 11, 4), "OS" to LocalDate.of(2026, 11, 6)), today)
        assertEquals(LocalDate.of(2026, 11, 3), plan.first { it.subjects == listOf("DBMS") && it.date.dayOfMonth == 3 }.date)
        assertEquals(listOf("OS"), plan.last().subjects)                 // 5 Nov, eve of OS
        val grid = VtopParsers.timetable(fixture("timetable.html")).slotGrid
        val clashes = FfcsPlanner.clashes(listOf(
            PlannedCourse(1, "A", "A1+TA1"), PlannedCourse(2, "B", "A1"), PlannedCourse(3, "C", "ZZ9"),
        ), grid)
        assertEquals(1, clashes.size)
        assertEquals(listOf("ZZ9"), FfcsPlanner.place(PlannedCourse(3, "C", "ZZ9"), grid).second)
    }

    @Test fun weeklyAndIcs() {
        val b = AttendanceRecord("X", "DBMS", "TH", "A1", "F", 10, 12, 84, "-", "", "")
        val lines = WeeklyReport.lines(listOf(b), listOf(b.copy(attended = 12, total = 15)))
        assertEquals(1, lines.size)
        assertTrue(WeeklyReport.summary(lines).startsWith("Attended 2 of 3 classes, missed 1"))
        val snap = Snapshot("24BCE2903", "vellore", Semester("VL1", "Fall"), timetable = Timetable(sessions = listOf(dbms, dbms2)), calendar = cal)
        val ics = IcsExport.build(snap, emptyList(), LocalDate.of(2026, 11, 2), LocalDateTime.of(2026, 11, 1, 9, 0))
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue(ics.contains("DTSTART:20261102T080000"))
        assertTrue(ics.lines().all { it.length <= 75 })
    }

    @Test fun shareCodeFitsInOneQr() {
        // A heavy week: 6 days x 8 classes with long titles and venues.
        val sessions = (1..6).flatMap { d ->
            (0 until 8).map { i ->
                ClassSession(d, "%02d:00".format(8 + i), "%02d:50".format(8 + i), "A${i + 1}", "BCSE${300 + i}L",
                    "Course number $i with a fairly long descriptive title", "ETH", "SJT${400 + i * 3}")
            }
        }
        val code = com.shreenipane.orbit.core.ShareCode.encode(com.shreenipane.orbit.core.ShareCode.Payload("Shree", "Fall Semester 2026-27", sessions))
        // A version-40 QR at error-correction L holds 2953 bytes.
        assertTrue("code is ${code.length} chars", code.length < 2953)
        assertEquals(48, com.shreenipane.orbit.core.ShareCode.decode(code)?.sessions?.size)
    }

    @Test fun calendarFromAnotherSemesterIsDropped() {
        fun day(d: String) = CalendarDay(d, DayKind.INSTRUCTIONAL)
        val winter = AcademicCalendar("G", "General (Flexible)", listOf(day("2026-01-05"), day("2026-04-17")))
        // The bug: Fall 2026-27 came back with Winter's dates.
        assertEquals(null, com.shreenipane.orbit.core.calc.SemesterWindow.fit(winter, "Fall Semester 2026-27"))
        assertEquals(winter, com.shreenipane.orbit.core.calc.SemesterWindow.fit(winter, "Winter Semester 2025-26"))
        // Stray days outside the window are trimmed, the rest kept.
        val fall = AcademicCalendar("G", "G", listOf(day("2026-04-17"), day("2026-07-20"), day("2026-11-20")))
        assertEquals(listOf("2026-07-20", "2026-11-20"),
            com.shreenipane.orbit.core.calc.SemesterWindow.fit(fall, "FALL SEM 2026-27")?.days?.map { it.date })
        // Unknown names pass through.
        assertEquals(fall, com.shreenipane.orbit.core.calc.SemesterWindow.fit(fall, "Weekend Intra Semester"))
    }

    @Test fun dayListingEveryGroupStaysInstructional() {
        val P = com.shreenipane.orbit.core.parse.CalendarParsers
        // VTOP's Fall cells list every group; the Freshers entry used to knock the whole day out.
        val cell = listOf("Instructional Day General (Semester) Instructional Day General (Flexible) Instructional Day General Freshers")
        assertEquals(DayKind.INSTRUCTIONAL, P.classify(cell).first)
        assertEquals(DayKind.INSTRUCTIONAL, P.classify(cell, "General (Flexible)").first)
        // Your group's entry wins over another group's.
        val split = listOf("Instructional Day General (Semester) Holiday General (Flexible)")
        assertEquals(DayKind.HOLIDAY, P.classify(split, "General (Flexible)").first)
        assertEquals(DayKind.INSTRUCTIONAL, P.classify(split, "General (Semester)").first)
        // "No Instructional Day" isn't split into "No" + "Instructional Day".
        assertEquals(DayKind.NO_INSTRUCTION, P.classify(listOf("No Instructional Day General (Flexible)"), "General (Flexible)").first)
        // A day-order note beside the group's entry still applies.
        val sat = listOf("Saturday - Monday Day Order", "Instructional Day General (Flexible)")
        assertEquals(1, P.classify(sat, "General (Flexible)").third)
        // Freshers-only days still don't count.
        assertEquals(DayKind.UNKNOWN, P.classify(listOf("Instructional Day General Freshers")).first)
    }

    @Test fun curriculumProgress() {
        fun c(code: String, cr: Double, grade: String, dist: String) =
            com.shreenipane.orbit.core.model.GradedCourse(code, code, "TH", cr, grade, "Nov-2025", dist)
        val h = com.shreenipane.orbit.core.model.GradeHistory(20.0, 17.0, 8.5, listOf(
            c("BCSE101E", 4.0, "A", "FC"), c("BCSE202L", 3.0, "B", "DC"), c("BCSE305L", 3.0, "F", "DE"), c("BCSE306L", 3.0, "S", "DE"),
        ))
        // From your own targets: DE needs 12, OE (nothing taken yet) needs 9.
        val p = Curriculum.progress(h, mapOf("DE" to 12.0, "OE" to 9.0))
        assertEquals("DE", p[0].key); assertEquals(9.0, p[0].left)       // failed course doesn't count
        assertEquals("OE", p[1].key); assertEquals(9.0, p[1].left); assertEquals("Open elective", p[1].name)
        assertEquals(null, p.first { it.key == "FC" }.required)
        // VTOP's own requirement table wins.
        val withTable = h.copy(requirements = listOf(com.shreenipane.orbit.core.model.CreditNeed("Discipline Elective", 12.0, 3.0),
            com.shreenipane.orbit.core.model.CreditNeed("Foundation Core", 4.0, 4.0)))
        val q = Curriculum.progress(withTable)
        assertEquals("DE", q[0].key); assertEquals(1, q[0].courses); assertTrue(q[1].done)
    }

    @Test fun creditNeedsTable() {
        val html = """<table><tr><th>Sl.No.</th><th>Distribution Type</th><th>Credits Required</th><th>Credits Earned</th></tr>
            <tr><td>1</td><td>Discipline Elective</td><td>12</td><td>6</td></tr>
            <tr><td>2</td><td>Open Elective</td><td>9.0</td><td>0</td></tr>
            <tr><td></td><td>Total</td><td>160</td><td>90</td></tr></table>"""
        val needs = com.shreenipane.orbit.core.parse.VtopParsers.creditNeeds(org.jsoup.Jsoup.parse(html))
        assertEquals(listOf("Discipline Elective", "Open Elective"), needs.map { it.name })
        assertEquals(9.0, needs[1].required)
        // The recorded VIT-AP page has no such table.
        assertTrue(com.shreenipane.orbit.core.parse.VtopParsers.gradeHistory(
            fixture("grade_history.html")).requirements.isEmpty())
    }

    @Test fun ngcrCountsCoursesNotGrades() {
        fun c(code: String, cr: Double, grade: String, dist: String) =
            com.shreenipane.orbit.core.model.GradedCourse(code, code, "TH", cr, grade, "Nov-2025", dist)
        val h = com.shreenipane.orbit.core.model.GradeHistory(10.0, 10.0, 9.0, listOf(
            c("BCHY102N", 0.0, "P", "NGCR"), c("BENG101N", 0.0, "-", "NGCR"), c("BSTS101P", 1.5, "", "NGCR"),
            c("BHUM101N", 0.0, "F", "NGCR"), c("BCSE101E", 4.0, "A", "FC"),
        ))
        val p = Curriculum.progress(h, mapOf("NGCR" to 5.0))
        val ngcr = p.first { it.key == "NGCR" }
        assertTrue(ngcr.byCourses)
        assertEquals(3.0, ngcr.earned)       // P, "-" and blank count; F doesn't
        assertEquals(2.0, ngcr.left)
        // And none of them touch the CGPA maths.
        assertEquals(4.0, com.shreenipane.orbit.core.calc.GradeCalc.standing(h).gradedCredits)
    }

    @Test fun gradeRangesFromDetailPage() {
        val st = com.shreenipane.orbit.core.parse.CampusParsers.gradeStats(fixture("grade_view_detail.html"))!!
        assertEquals("71", st.classStrength); assertEquals("60.4", st.mean); assertEquals("13.71", st.sd)
        assertEquals(listOf("S", "A", "B", "C", "D", "E", "F"), st.ranges.map { it.grade })
        assertEquals("81 and above", st.ranges[0].range)
        assertEquals("67 – 81", st.ranges[1].range)
        assertEquals("below 33", st.ranges[6].range)
        // The grade view row carries the detail id.
        val list = com.shreenipane.orbit.core.parse.CampusParsers.gradeView(fixture("grade_view.html"))
        assertEquals("AM_CSE1008_00200", list.first { it.code == "CSE1008" }.courseId)
    }

    @Test fun semesterForExamMonth() {
        val sems = listOf(com.shreenipane.orbit.core.model.Semester("A", "Fall_Win Transport Semester 2025-26"),
            com.shreenipane.orbit.core.model.Semester("B", "Fall Semester 2025-26"),
            com.shreenipane.orbit.core.model.Semester("C", "Winter Semester 2025-26"))
        assertEquals("B", com.shreenipane.orbit.core.calc.SemesterWindow.semesterFor("Nov-2025", sems)?.id)
        assertEquals("C", com.shreenipane.orbit.core.calc.SemesterWindow.semesterFor("Apr-2026", sems)?.id)
        assertEquals(null, com.shreenipane.orbit.core.calc.SemesterWindow.semesterFor("Nov-2019", sems))
    }
}
