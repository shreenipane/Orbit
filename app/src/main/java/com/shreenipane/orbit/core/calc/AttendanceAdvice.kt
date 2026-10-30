package com.shreenipane.orbit.core.calc

import java.util.Locale

/**
 * What to tell the user about one course, given how many classes are really
 * left. Pure and unit-tested; the UI only maps [level] to a colour.
 *
 * Strings are built with templates, never String.format on text that
 * contains a literal "%", which is what crashed v0.4/v0.5.
 */
object AttendanceAdvice {
    enum class Level { GOOD, WARN, BAD }

    data class Advice(val tag: String, val line: String, val level: Level)

    private fun pct(d: Double) = String.format(Locale.US, "%.1f", d)
    private fun pct0(d: Double) = String.format(Locale.US, "%.0f", d)

    fun of(attended: Int, total: Int, remaining: Int?): Advice {
        val now = AttendanceCalc.percent(attended, total)
        if (remaining == null) {
            val need = AttendanceCalc.neededInARow(attended, total)
            val skip = AttendanceCalc.skippable(attended, total)
            return when {
                need > 0 -> Advice("Attend $need", "Attend the next $need in a row to reach 75%", Level.BAD)
                skip > 0 -> Advice("Skip $skip", "You can skip $skip and stay at 75%", Level.GOOD)
                else -> Advice("On the line", "Don't skip the next class", Level.WARN)
            }
        }
        if (remaining <= 0) {
            val level = if (now >= 80) Level.GOOD else if (now >= 75) Level.WARN else Level.BAD
            return Advice("Final", "No classes left this semester · final ${pct(now)}%", level)
        }
        val miss = AttendanceCalc.missableOfRemaining(attended, total, remaining)
        val best = AttendanceCalc.bestPossible(attended, total, remaining)
        return when {
            miss < 0 -> Advice(
                "Max ${pct0(best)}%",
                "75% is out of reach: best possible is ${pct(best)}% even attending all $remaining left",
                Level.BAD,
            )
            miss == 0 -> Advice("Attend all $remaining", "Attend all $remaining classes left to finish at 75%", Level.WARN)
            now < 75.0 -> Advice(
                "Attend ${remaining - miss}/$remaining",
                "Attend at least ${remaining - miss} of the $remaining classes left to reach 75%",
                Level.WARN,
            )
            else -> Advice("Can miss $miss", "You can miss $miss of the $remaining classes left", Level.GOOD)
        }
    }
}
