package com.shreenipane.orbit.core.calc

import com.shreenipane.orbit.core.model.GradeHistory

/** VIT's 10-point scale and the CGPA planner maths. */
object GradeCalc {
    val POINTS = linkedMapOf("S" to 10, "A" to 9, "B" to 8, "C" to 7, "D" to 6, "E" to 5, "F" to 0, "N" to 0)

    /** Grades that count toward CGPA. P (pass/fail), W, U and blanks don't. */
    fun counts(grade: String) = grade.uppercase() in POINTS

    data class Standing(val cgpa: Double, val gradedCredits: Double)

    /**
     * CGPA as VTOP reports it when available, with credits recomputed from the
     * course list so the planner has a denominator.
     */
    fun standing(history: GradeHistory): Standing {
        val graded = history.courses.filter { counts(it.grade) && it.credits > 0 }
        val credits = graded.sumOf { it.credits }
        val computed = if (credits == 0.0) 0.0 else graded.sumOf { it.credits * POINTS.getValue(it.grade.uppercase()) } / credits
        return Standing(history.cgpa ?: computed, credits)
    }

    /** SGPA from (credits, grade) pairs. */
    fun sgpa(entries: List<Pair<Double, String>>): Double {
        val graded = entries.filter { counts(it.second) && it.first > 0 }
        val c = graded.sumOf { it.first }
        return if (c == 0.0) 0.0 else graded.sumOf { it.first * POINTS.getValue(it.second.uppercase()) } / c
    }

    /** CGPA after adding a semester of [semCredits] at [sgpa]. */
    fun cgpaAfter(current: Standing, semCredits: Double, sgpa: Double): Double {
        val total = current.gradedCredits + semCredits
        return if (total == 0.0) 0.0 else (current.cgpa * current.gradedCredits + sgpa * semCredits) / total
    }

    /**
     * SGPA needed over the next [upcomingCredits] to reach [target].
     * May be above 10 (unreachable this semester) or below 0 (already safe).
     */
    fun requiredSgpa(current: Standing, upcomingCredits: Double, target: Double): Double {
        if (upcomingCredits <= 0) return Double.NaN
        return (target * (current.gradedCredits + upcomingCredits) - current.cgpa * current.gradedCredits) / upcomingCredits
    }
}
