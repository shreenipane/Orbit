package com.shreenipane.orbit

import com.shreenipane.orbit.core.calc.AttendanceCalc
import com.shreenipane.orbit.core.calc.GradeCalc
import com.shreenipane.orbit.core.calc.Schedule
import com.shreenipane.orbit.core.model.GradeHistory
import com.shreenipane.orbit.core.parse.VtopParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser tests run against real VTOP responses recorded (and scrubbed) by the
 * vitap-vtop-client project (MIT). VIT-AP runs the same VTOP build as Vellore
 * with different semester ids, so the markup matches. Add Vellore captures
 * here as you collect them.
 */
class CoreTest {
    private fun fixture(name: String) =
        javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    @Test fun semesters() {
        val s = VtopParsers.semesters(fixture("semesters.html"))
        assertTrue(s.isNotEmpty())
        assertEquals("AP2026272", s.first().id)
        assertEquals("Fall Semester 2026-27", s.first().name)
    }

    @Test fun timetableCourses() {
        val tt = VtopParsers.timetable(fixture("timetable.html"))
        assertTrue("courses parsed", tt.courses.size >= 3)
        val theory = tt.courses.first { it.code == "CSE3009" && !it.isLab }
        assertEquals("NoSQL Databases", theory.title)
        assertEquals("Embedded Theory", theory.type)
        assertEquals(3.0, theory.credits, 0.0)
        assertEquals("C1+TCC1", theory.slot)
        assertEquals("105-AB-2", theory.venue)
    }

    @Test fun timetableGridMergesLabs() {
        val tt = VtopParsers.timetable(fixture("timetable.html"))
        assertTrue("sessions parsed", tt.sessions.isNotEmpty())
        val lab = tt.sessions.first { it.courseCode == "CSE3009" && it.isLab }
        assertEquals("L1+L2", lab.slot)
        assertEquals("08:00", lab.start)
        assertEquals("09:40", lab.end)
        assertEquals("NoSQL Databases", lab.courseTitle)
        assertEquals("409-CB", lab.venue)
        val c1 = tt.sessions.first { it.slot == "C1" }
        assertEquals("105-AB-2", c1.venue)
    }

    @Test fun attendance() {
        val a = VtopParsers.attendance(fixture("attendance.html"))
        assertTrue(a.size >= 2)
        val first = a.first()
        assertEquals("CSE3009", first.courseCode)
        assertEquals("NoSQL Databases", first.courseTitle)
        assertEquals("Embedded Theory", first.courseType)
        assertEquals(7, first.attended)
        assertEquals(12, first.total)
        assertEquals(59, first.vtopPercent)
        assertEquals("C1+TCC1", first.slot)
    }

    @Test fun gradeHistory() {
        val g = VtopParsers.gradeHistory(fixture("grade_history.html"))
        assertEquals(8.32, g.cgpa!!, 1e-9)
        assertEquals(137.0, g.creditsEarned!!, 1e-9)
        assertTrue(g.courses.size > 20)
        val py = g.courses.first { it.code == "CSE1012" }
        assertEquals("A", py.grade)
        assertEquals(4.0, py.credits, 0.0)
        val standing = GradeCalc.standing(g)
        // Recomputed from the course list, so it should land near VTOP's figure.
        val recomputed = GradeCalc.sgpa(g.courses.map { it.credits to it.grade })
        assertTrue("recomputed $recomputed vs 8.32", kotlin.math.abs(recomputed - 8.32) < 0.05)
        assertTrue(standing.gradedCredits > 100)
    }

    @Test fun marks() {
        val m = VtopParsers.marks(fixture("marks_synthetic.html"))
        assertEquals(2, m.size)
        val db = m[0]
        assertEquals("BCSE302L", db.courseCode)
        assertEquals("Some Faculty", db.faculty)
        assertEquals(2, db.components.size)
        assertEquals(21.3, db.weightedTotal, 1e-9)
        assertEquals(25.0, db.weightageSoFar, 1e-9)
        assertEquals(0, m[1].components.size)
    }

    @Test fun velloreStyleGridCell() {
        val html = """
            <table id="timeTableStyle">
              <tr><td>THEORY</td><td>Start</td><td>08:00</td><td>08:55</td></tr>
              <tr><td>End</td><td>08:50</td><td>09:45</td></tr>
              <tr><td>LAB</td><td>Start</td><td>08:00</td><td>08:51</td></tr>
              <tr><td>End</td><td>08:50</td><td>09:40</td></tr>
              <tr><td>MON</td><td>THEORY</td><td>A1-BCSE302L-TH-SJT-301-ALL</td><td>F1</td></tr>
              <tr><td>LAB</td><td>L1-BCSE302P-LO-SJT-517-ALL</td><td>L2-BCSE302P-LO-SJT-517-ALL</td></tr>
            </table>""".trimIndent()
        val s = VtopParsers.timetable(html).sessions
        assertEquals(2, s.size)
        assertEquals("BCSE302L", s[0].courseCode)
        assertEquals("SJT-301", s[0].venue)
        assertEquals("L1+L2", s[1].slot)
        assertEquals("09:40", s[1].end)
    }

    @Test fun attendanceMath() {
        assertEquals(0, AttendanceCalc.skippable(7, 12))
        assertEquals(8, AttendanceCalc.neededInARow(7, 12)) // (7+8)/(12+8) = 75%
        assertEquals(0, AttendanceCalc.neededInARow(9, 12))  // exactly 75%
        assertEquals(1, AttendanceCalc.skippable(30, 39))    // 30/40 = 75%
        assertEquals(0, AttendanceCalc.skippable(29, 39))
        assertEquals(2, AttendanceCalc.skippable(18, 22))    // 18/24 = 75%
    }

    @Test fun cgpaPlanner() {
        val s = GradeCalc.Standing(cgpa = 8.0, gradedCredits = 100.0)
        assertEquals(9.0, GradeCalc.requiredSgpa(s, 25.0, 8.2), 1e-9)
        assertEquals(8.2, GradeCalc.cgpaAfter(s, 25.0, 9.0), 1e-9)
        assertEquals(9.0, GradeCalc.sgpa(listOf(4.0 to "S", 4.0 to "B", 2.0 to "P")), 1e-9)
        val empty = GradeCalc.standing(GradeHistory(null, null, null))
        assertEquals(0.0, empty.gradedCredits, 0.0)
    }

    @Test fun scheduleNow() {
        val tt = VtopParsers.timetable(fixture("timetable.html"))
        val tue = Schedule.forDay(tt.sessions, 2)
        assertTrue(tue.isNotEmpty())
        val first = tue.first()
        val startMin = first.start.substring(0, 2).toInt() * 60 + first.start.substring(3).toInt()
        val now = Schedule.now(tt.sessions, 2, startMin + 5)
        assertEquals(first, now.ongoing)
    }
}
