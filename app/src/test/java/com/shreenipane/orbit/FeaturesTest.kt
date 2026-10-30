package com.shreenipane.orbit

import com.shreenipane.orbit.core.ShareCode
import com.shreenipane.orbit.core.calc.ExamTime
import com.shreenipane.orbit.core.calc.FreeTime
import com.shreenipane.orbit.core.calc.SyncDiff
import com.shreenipane.orbit.core.model.AttendanceRecord
import com.shreenipane.orbit.core.model.ClassSession
import com.shreenipane.orbit.core.model.CourseMarks
import com.shreenipane.orbit.core.model.MarkComponent
import com.shreenipane.orbit.core.model.Semester
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.core.parse.VtopParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class FeaturesTest {

    private val examHtml = """
        <table>
          <tr><td>S.No.</td><td>Course Code</td><td>Course Title</td><td>Course Type</td><td>Class ID</td><td>Slot</td><td>Exam Date</td><td>Exam Session</td><td>Reporting Time</td><td>Exam Time</td><td>Venue</td><td>Seat Location</td><td>Seat No.</td></tr>
          <tr><td>CAT1</td></tr>
          <tr><td>1</td><td>BCSE302L</td><td>Database Systems</td><td>Theory Only</td><td>VL01</td><td>A1+TA1</td><td>21-Nov-2026</td><td>FN</td><td>09:15 AM</td><td>09:30 AM - 11:00 AM</td><td>SJT-301</td><td>Row 3</td><td>17</td></tr>
          <tr><td>FAT</td></tr>
          <tr><td>1</td><td>BCSE302L</td><td>Database Systems</td><td>Theory Only</td><td>VL01</td><td>A1+TA1</td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
        </table>""".trimIndent()

    @Test fun examSchedule() {
        val e = VtopParsers.examSchedule(examHtml)
        assertEquals(2, e.size)
        assertEquals("CAT1", e[0].examType)
        assertEquals("SJT-301", e[0].venue)
        assertEquals("17", e[0].seatNumber)
        assertEquals(LocalDateTime.of(2026, 11, 21, 9, 30), ExamTime.start(e[0]))
        assertEquals(null, ExamTime.start(e[1])) // FAT not scheduled yet
        val up = ExamTime.upcoming(e, LocalDateTime.of(2026, 11, 18, 12, 0))
        assertEquals(1, up.size)
        assertEquals("in 3 days", ExamTime.countdown(up[0].second, LocalDateTime.of(2026, 11, 18, 12, 0)))
        assertEquals("tomorrow", ExamTime.countdown(up[0].second, LocalDateTime.of(2026, 11, 20, 22, 0)))
    }

    private val sessions = listOf(
        ClassSession(1, "08:00", "08:50", "A1", "BCSE302L", "Database | Systems", "TH", "SJT-301"),
        ClassSession(1, "14:00", "15:40", "L31+L32", "BCSE302P", "DB Lab\nweird", "LO", "SJT-517"),
    )

    @Test fun shareCodeRoundTrip() {
        val code = ShareCode.encode(ShareCode.Payload("Shree", "Fall 2026-27", sessions))
        assertTrue(code.startsWith("ORBIT1:"))
        val back = ShareCode.decode("hey here's mine: $code  see you")!!
        assertEquals("Shree", back.name)
        assertEquals(sessions, back.sessions)
        assertEquals(null, ShareCode.decode("not a code"))
    }

    @Test fun freeTime() {
        val friend = listOf(ClassSession(1, "09:00", "10:40", "L1+L2", "X", "X", "LO", "V"))
        val gaps = FreeTime.common(listOf(sessions, friend), day = 1)
        assertEquals(listOf("10:40" to "14:00", "15:40" to "19:30"), gaps.map { it.start to it.end })
    }

    @Test fun syncDiff() {
        val sem = Semester("VL1", "Fall")
        val att = AttendanceRecord("BCSE302L", "Database Systems", "Theory Only", "A1+TA1", "F", 10, 12, 84, "-", "", "")
        val mk = { comps: List<MarkComponent> -> CourseMarks("BCSE302L", "Database Systems", "Theory Only", "F", "A1", comps) }
        val cat1 = MarkComponent("CAT-1", 50.0, 15.0, 41.0, 12.3, "Present", "")
        val old = Snapshot("21XX", "vellore", sem, attendance = listOf(att), marks = listOf(mk(emptyList())))
        val new = old.copy(attendance = listOf(att.copy(attended = 11, total = 14)), marks = listOf(mk(listOf(cat1))))
        val notes = SyncDiff.notes(old, new)
        assertEquals(2, notes.size)
        assertEquals("New marks: CAT-1", notes[0].title)
        assertEquals("Database Systems · 41/50", notes[0].text)
        assertEquals("Marked absent ×1", notes[1].title)
        assertTrue(SyncDiff.notes(null, new).isEmpty())
        assertTrue(SyncDiff.notes(new, new).isEmpty())
    }
}
