package com.shreenipane.orbit.core.calc

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Attendance arithmetic. VIT's debar line is 75%. Everything is in "classes"
 * the way VTOP counts them for that course (a two-hour lab is whatever VTOP
 * records it as), so the numbers stay consistent with the portal.
 */
object AttendanceCalc {
    const val DEFAULT_THRESHOLD = 0.75

    fun percent(attended: Int, total: Int): Double =
        if (total == 0) 100.0 else attended * 100.0 / total

    /** Largest k such that attended / (total + k) still meets the threshold. */
    fun skippable(attended: Int, total: Int, threshold: Double = DEFAULT_THRESHOLD): Int {
        if (total == 0) return 0
        val k = floor(attended / threshold - total + 1e-9).toInt()
        return k.coerceAtLeast(0)
    }

    /** Smallest n such that (attended + n) / (total + n) meets the threshold. */
    fun neededInARow(attended: Int, total: Int, threshold: Double = DEFAULT_THRESHOLD): Int {
        if (total == 0 || attended.toDouble() / total >= threshold) return 0
        val n = ceil((threshold * total - attended) / (1 - threshold) - 1e-9).toInt()
        return n.coerceAtLeast(0)
    }

    /** What the percentage becomes after attending [present] and missing [absent] more classes. */
    fun projected(attended: Int, total: Int, present: Int, absent: Int): Double =
        percent(attended + present, total + present + absent)
}
