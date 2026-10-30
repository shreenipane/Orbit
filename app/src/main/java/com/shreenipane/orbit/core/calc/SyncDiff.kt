package com.shreenipane.orbit.core.calc

import com.shreenipane.orbit.core.model.Snapshot

/** Compares two syncs so background sync can notify only about what's new. */
object SyncDiff {
    data class Note(val title: String, val text: String)

    private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else "%.2f".format(d)

    fun notes(previous: Snapshot?, current: Snapshot): List<Note> {
        if (previous == null || previous.semester?.id != current.semester?.id) return emptyList()
        val notes = mutableListOf<Note>()

        current.marks.forEach { course ->
            val old = previous.marks.find { it.courseCode == course.courseCode && it.courseType == course.courseType }
            val seen = old?.components.orEmpty().filter { it.scored != null }.map { it.title }.toSet()
            course.components.filter { it.scored != null && it.title !in seen }.forEach { c ->
                val outOf = c.maxMark?.let { "/" + fmt(it) } ?: ""
                notes += Note("New marks: ${c.title}", "${course.courseTitle} · ${fmt(c.scored!!)}$outOf")
            }
        }

        current.attendance.forEach { a ->
            val old = previous.attendance.find { it.courseCode == a.courseCode && it.courseType == a.courseType && it.slot == a.slot }
                ?: return@forEach
            val newClasses = a.total - old.total
            if (newClasses <= 0) return@forEach
            val missed = newClasses - (a.attended - old.attended)
            val pct = AttendanceCalc.percent(a.attended, a.total)
            val text = "%s · now %.1f%% (%d/%d)".format(a.courseTitle, pct, a.attended, a.total)
            // Only absences are worth a ping; "you were present" every day is noise.
            if (missed > 0) notes += Note("Marked absent ×$missed", text)
        }

        val oldExamKeys = previous.exams.map { it.examType + it.courseCode + it.date }.toSet()
        current.exams.filter { it.date.isNotBlank() && (it.examType + it.courseCode + it.date) !in oldExamKeys }
            .groupBy { it.examType }
            .forEach { (type, list) -> notes += Note("$type schedule posted", list.joinToString { it.courseCode + " " + it.date }) }

        val oldDas = previous.assignments.map { it.classId + it.title }.toSet()
        current.assignments.filter { (it.classId + it.title) !in oldDas }.forEach { da ->
            notes += Note("New DA: ${da.title}", "${da.courseTitle} · due ${da.dueDate}")
        }

        return notes
    }
}
