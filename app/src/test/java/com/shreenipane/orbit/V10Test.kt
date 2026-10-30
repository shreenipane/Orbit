package com.shreenipane.orbit

import com.shreenipane.orbit.core.calc.AttendanceWatch
import com.shreenipane.orbit.core.calc.Buddies
import com.shreenipane.orbit.core.calc.BunkPlanner
import com.shreenipane.orbit.core.calc.FreeNow
import com.shreenipane.orbit.core.calc.PlanCode
import com.shreenipane.orbit.core.calc.Recap
import com.shreenipane.orbit.core.calc.StudyLog
import com.shreenipane.orbit.core.model.AcademicCalendar
import com.shreenipane.orbit.core.model.AttendanceRecord
import com.shreenipane.orbit.core.model.CalendarDay
import com.shreenipane.orbit.core.model.ClassSession
import com.shreenipane.orbit.core.model.DayKind
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.core.model.StudyItem
import com.shreenipane.orbit.core.model.StudySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class V10Test {
    private fun rec(code: String, att: Int, tot: Int, slot: String = "A1") =
        AttendanceRecord(code, code, "Theory Only", slot, "F", att, tot, 0, "", "", "TH")
    private val mon = ClassSession(1, "08:00", "08:50", "A1", "CSE1", "One", "TH", "SJT")
    private val mon2 = ClassSession(1, "10:00", "10:50", "B1", "CSE2", "Two", "TH", "SJT")
    private val sat = AcademicCalendar("G", "G", listOf(
        CalendarDay("2026-11-02", DayKind.INSTRUCTIONAL), // Monday
        CalendarDay("2026-11-07", DayKind.INSTRUCTIONAL, dayOrder = 1), // Saturday on Monday's timetable
        CalendarDay("2026-11-09", DayKind.HOLIDAY),
    ))

    @Test fun bunkPlanner() {
        val r = listOf(rec("CSE1", 30, 40), rec("CSE2", 40, 40))
        val p = BunkPlanner.project(r, listOf(mon, mon2), sat,
            listOf(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-07"), LocalDate.parse("2026-11-09")))
        val one = p.first { it.record.courseCode == "CSE1" }
        assertEquals(2, one.missed)                         // Monday + the Saturday that follows Monday; holiday adds none
        assertTrue(one.dropsBelow)                          // 30/42 < 75%
        assertEquals(2, p.first { it.record.courseCode == "CSE2" }.missed)
    }

    @Test fun attendanceWatch() {
        val old = listOf(rec("CSE1", 30, 40), rec("CSE2", 34, 44))
        val new = listOf(rec("CSE1", 30, 41), rec("CSE2", 34, 45))
        val a = AttendanceWatch.alerts(old, new)
        assertTrue(a.any { it.courseTitle == "CSE1" && it.text.startsWith("Dropped below 75%") })
        // CSE2 34/44 = 77.3% could miss one; after a miss 34/45 = 75.6%, and one more → 34/46 = 73.9%: now on the line.
        assertTrue(a.any { it.courseTitle == "CSE2" && it.text.contains("missing the next class") })
        assertTrue(AttendanceWatch.alerts(old, old).isEmpty())
    }

    @Test fun freeNowAndBuddies() {
        assertTrue(!FreeNow.isFree(listOf(mon), 1, 8 * 60 + 10))
        assertTrue(FreeNow.isFree(listOf(mon), 1, 9 * 60))
        assertEquals("10:00", FreeNow.freeUntil(listOf(mon, mon2), 1, 9 * 60))
        val b = Buddies.shared(listOf(mon, mon2), listOf(mon, mon2.copy(slot = "B2")))
        assertEquals(listOf("CSE1" to true, "CSE2" to false), b.map { it.code to it.sameClass })
    }

    @Test fun planCodeRoundTripAndLimits() {
        val items = listOf(StudyItem(1, "2026-11-10", "DBMS", "Normalisation | 3NF"), StudyItem(2, "2026-11-11", "OS", ""))
        val back = PlanCode.decode(PlanCode.encode("Shree", items), idBase = 100)!!
        assertEquals("Shree", back.from)
        assertEquals(listOf("DBMS", "OS"), back.items.map { it.subject })
        assertEquals("Normalisation | 3NF", back.items[0].topic)
        assertEquals(listOf(100L, 101L), back.items.map { it.id })
        assertEquals(null, PlanCode.decode("ORBITPLAN1:@@@"))
        assertEquals(null, PlanCode.decode(PlanCode.encode("x", listOf(StudyItem(1, "not-a-date", "A", "")))))
    }

    @Test fun studyLogAndRecap() {
        val log = listOf(StudySession(1, "2026-11-01", "DBMS", 25), StudySession(2, "2026-11-02", "DBMS", 50),
            StudySession(3, "2026-10-01", "OS", 25))
        val t = StudyLog.totals(log, LocalDate.parse("2026-10-26"))
        assertEquals(listOf("DBMS" to 75), t.map { it.subject to it.minutes })
        val snap = Snapshot("R", "vellore", null, attendance = listOf(rec("CSE1", 30, 40), rec("CSE2", 40, 40)))
        val r = Recap.of(snap)
        assertEquals(70, r.classesAttended); assertEquals(80, r.classesHeld)
        assertEquals("CSE2", r.bestAttendance?.title); assertEquals("CSE1", r.worstAttendance?.title)
    }

    @Test fun unmarkedDaysHaveNoClasses() {
        val D = com.shreenipane.orbit.core.calc.ClassDays
        assertTrue(D.runs(sat, LocalDate.parse("2026-11-02")))
        assertTrue(!D.runs(sat, LocalDate.parse("2026-11-09")))      // holiday
        assertTrue(!D.runs(sat, LocalDate.parse("2026-11-19")))      // not on the calendar at all
        assertTrue(D.runs(null, LocalDate.parse("2026-11-19")))      // no calendar: follow the timetable
        assertTrue(BunkPlanner.classesOn(sat, listOf(mon), LocalDate.parse("2026-11-16"), emptyList()).isEmpty())
    }
}
