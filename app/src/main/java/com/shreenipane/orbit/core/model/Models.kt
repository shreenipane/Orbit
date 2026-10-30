package com.shreenipane.orbit.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Semester(val id: String, val name: String)

/** One row of the "registered courses" table on the timetable page. */
@Serializable
data class RegisteredCourse(
    val code: String,
    val title: String,
    /** e.g. "Embedded Theory", "Embedded Lab", "Theory Only", "Lab Only". */
    val type: String,
    val credits: Double,
    /** e.g. "C1+TCC1" or "L1+L2". */
    val slot: String,
    val venue: String,
    val faculty: String,
    val classId: String,
) {
    val isLab: Boolean get() = slot.startsWith("L") || type.contains("Lab", ignoreCase = true)
}

/** One class session on the weekly grid, after consecutive lab slots are merged. */
@Serializable
data class ClassSession(
    /** ISO day of week: 1 = Monday … 7 = Sunday. */
    val day: Int,
    /** "HH:mm" 24-hour. */
    val start: String,
    val end: String,
    val slot: String,
    val courseCode: String,
    val courseTitle: String,
    /** Grid type code, e.g. ETH, ELA, TH, LO, SS. */
    val typeCode: String,
    val venue: String,
) {
    val isLab: Boolean get() = slot.startsWith("L")
}

/** Where a slot sits on the weekly grid, e.g. A1 → Mon 08:00–08:50. Read from the timetable page. */
@Serializable
data class SlotTime(val slot: String, val day: Int, val start: String, val end: String)

@Serializable
data class Timetable(
    val courses: List<RegisteredCourse> = emptyList(),
    val sessions: List<ClassSession> = emptyList(),
    /** Every slot on the grid, filled or not; the FFCS planner uses it. */
    val slotGrid: List<SlotTime> = emptyList(),
)

/** A change to one class on one date: cancelled, or an extra class added. Stored locally. */
@Serializable
data class ClassOverride(
    val id: Long,
    /** ISO date. */
    val date: String,
    val kind: OverrideKind,
    val courseCode: String,
    val courseTitle: String,
    val isLab: Boolean,
    val start: String,
    val end: String,
    val venue: String = "",
)

@Serializable
enum class OverrideKind { CANCELLED, EXTRA }

@Serializable
data class AttendanceRecord(
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val slot: String,
    val faculty: String,
    val attended: Int,
    val total: Int,
    /** The percentage exactly as VTOP shows it (VTOP applies its own rounding). */
    val vtopPercent: Int,
    val debarStatus: String,
    /** Needed for the per-class detail call. */
    val courseId: String,
    val typeCode: String,
)

@Serializable
data class MarkComponent(
    val title: String,
    val maxMark: Double?,
    val weightage: Double?,
    val scored: Double?,
    val weighted: Double?,
    val status: String,
    val remark: String,
)

@Serializable
data class CourseMarks(
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val faculty: String,
    val slot: String,
    val components: List<MarkComponent>,
) {
    val weightedTotal: Double get() = components.sumOf { it.weighted ?: 0.0 }
    val weightageSoFar: Double get() = components.filter { it.scored != null }.sumOf { it.weightage ?: 0.0 }
}

@Serializable
data class GradedCourse(
    val code: String,
    val title: String,
    val type: String,
    val credits: Double,
    val grade: String,
    val examMonth: String,
    /** Curriculum bucket, e.g. UC, PC, PE, UE / OE. */
    val distribution: String = "",
)

@Serializable
data class GradeHistory(
    val creditsRegistered: Double?,
    val creditsEarned: Double?,
    val cgpa: Double?,
    val courses: List<GradedCourse> = emptyList(),
    /** Curriculum requirements per category, when VTOP's grade history lists them. */
    val requirements: List<CreditNeed> = emptyList(),
)

/** One curriculum category (e.g. "Discipline Elective"): credits required vs earned. */
@Serializable
data class CreditNeed(val name: String, val required: Double, val earned: Double)

@Serializable
data class Exam(
    /** Group heading on VTOP, e.g. "CAT1", "CAT2", "FAT". */
    val examType: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val slot: String,
    /** As VTOP shows it, e.g. "21-Nov-2026". Blank until scheduled. */
    val date: String,
    val session: String,
    val reportingTime: String,
    /** e.g. "09:30 AM - 11:00 AM". */
    val examTime: String,
    val venue: String,
    val seatLocation: String,
    val seatNumber: String,
)

@Serializable
enum class DayKind { INSTRUCTIONAL, EXAM, HOLIDAY, NO_INSTRUCTION, UNKNOWN }

/** One date from VTOP's academic calendar, already filtered to the user's class group. */
@Serializable
data class CalendarDay(
    /** ISO date, "2026-08-17". */
    val date: String,
    val kind: DayKind,
    /** "CAT1", "CAT2" or "FAT" on exam days. */
    val exam: String? = null,
    /** ISO weekday whose timetable runs on this date (Saturday "Monday day order" → 1). */
    val dayOrder: Int? = null,
    val label: String = "",
)

@Serializable
data class AcademicCalendar(
    val classGroupId: String,
    val classGroupName: String,
    val days: List<CalendarDay>,
)

/** One digital assignment (DA) from VTOP. */
@Serializable
data class DigitalAssignment(
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val classId: String,
    val title: String,
    val maxMark: String,
    val weightage: String,
    /** As VTOP shows it, e.g. "21-Nov-2026". */
    val dueDate: String,
    /** e.g. "File Not Uploaded" or an upload timestamp. */
    val status: String,
    /** VTOP's per-assignment code, needed to upload. Blank when uploads are closed. */
    val mcode: String = "",
    /** Download path of the question paper, if any. */
    val qpUrl: String = "",
) {
    val canUpload: Boolean get() = mcode.isNotBlank()

    val submitted: Boolean
        get() = status.isNotBlank() && !status.contains("Not Uploaded", ignoreCase = true) && status != "-"
}

/** Everything the app shows, cached to disk so it opens offline. */
@Serializable
data class Snapshot(
    val regNo: String,
    val campusId: String,
    val semester: Semester?,
    val semesters: List<Semester> = emptyList(),
    val timetable: Timetable = Timetable(),
    val attendance: List<AttendanceRecord> = emptyList(),
    val marks: List<CourseMarks> = emptyList(),
    val grades: GradeHistory? = null,
    val exams: List<Exam> = emptyList(),
    val calendar: AcademicCalendar? = null,
    val assignments: List<DigitalAssignment> = emptyList(),
    /** Parts of the last sync that failed, e.g. "Digital assignments: menu not available". */
    val warnings: List<String> = emptyList(),
    val fetchedAtMillis: Long = 0,
)

/** A manual reminder or an assignment (kind = ASSIGNMENT), stored locally. */
@Serializable
data class Reminder(
    val id: Long,
    val title: String,
    val kind: ReminderKind = ReminderKind.REMINDER,
    val courseCode: String? = null,
    val dueAtMillis: Long,
    val notes: String = "",
    val done: Boolean = false,
)

@Serializable
enum class ReminderKind { REMINDER, ASSIGNMENT }

/** A friend's timetable imported from a share code. */
@Serializable
data class Friend(
    val name: String,
    val semesterName: String,
    val sessions: List<ClassSession>,
    val importedAtMillis: Long,
)

// ---------- campus pages (fetched on demand) ----------

@Serializable
data class Announcement(val category: String, val text: String, val link: String? = null)

@Serializable
data class KeyValue(val key: String, val value: String)

@Serializable
data class Faculty(val name: String, val designation: String, val school: String, val empId: String)

@Serializable
data class PendingPayment(val reference: String, val head: String, val dueDate: String, val amount: String, val fine: String, val total: String)

@Serializable
data class Receipt(val number: String, val date: String, val amount: String)

@Serializable
data class BiometricEntry(val date: String, val time: String, val location: String)

/** One course of a semester's published results. */
@Serializable
data class GradeResult(
    val code: String, val title: String, val type: String, val total: String, val grade: String,
    /** VTOP's id for the course's detail view (grade ranges, class mean). */
    val courseId: String = "",
)

/** One grade's mark range in a course, e.g. S → "81 and above". */
@Serializable
data class GradeRange(val grade: String, val range: String)

/** How the class did in a course: size, mean, SD and where each grade started. */
@Serializable
data class GradeStats(val classStrength: String, val mean: String, val sd: String, val ranges: List<GradeRange>)

@Serializable
data class CampusInfo(
    val announcements: List<Announcement> = emptyList(),
    val proctor: List<KeyValue> = emptyList(),
    val hodDean: List<KeyValue> = emptyList(),
    val pending: List<PendingPayment> = emptyList(),
    val receipts: List<Receipt> = emptyList(),
    val fetchedAtMillis: Long = 0,
)

/** A course added to the FFCS planner: a slot combination like "A1+TA1" or "L31+L32". */
/** One entry of your own study plan: what to revise on which day. */
@Serializable
data class StudyItem(val id: Long, val date: String, val subject: String, val topic: String = "", val done: Boolean = false)

@Serializable
data class PlannedCourse(val id: Long, val title: String, val slots: String, val credits: Double = 0.0)
