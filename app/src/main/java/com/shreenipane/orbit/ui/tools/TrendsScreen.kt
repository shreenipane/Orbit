package com.shreenipane.orbit.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.model.CourseMarks
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.EmptyNote
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.RowDivider
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Good
import com.shreenipane.orbit.ui.theme.Pastel
import com.shreenipane.orbit.ui.theme.Warn
import java.util.Locale

private fun pct(v: Double) = String.format(Locale.US, "%.0f", v)
private fun num(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
private fun scoreColor(p: Double): Color = when { p >= 75 -> Good; p >= 50 -> Warn; else -> Bad }

/** Each course's assessments in order (CAT 1 → CAT 2 → … → FAT) as bars, so you can see the trend. */
@Composable
fun TrendsScreen(snap: Snapshot) {
    val courses = snap.marks.filter { m -> m.components.any { it.scored != null && (it.maxMark ?: 0.0) > 0 } }
    OrbitScreen(title = "Marks trends") {
        if (courses.isEmpty()) {
            item { EmptyNote("No marks yet", "Trends appear once VTOP has marks for your courses.") }
            return@OrbitScreen
        }
        courses.forEach { c ->
            val sofar = if (c.weightageSoFar > 0) c.weightedTotal * 100 / c.weightageSoFar else null
            section(
                header = c.courseTitle,
                footer = sofar?.let { "So far ${num(c.weightedTotal)} of ${num(c.weightageSoFar)} weighted marks (${pct(it)}%)" },
            ) { CourseBars(c) }
        }
    }
}

@Composable
private fun CourseBars(c: CourseMarks) {
    val parts = c.components.filter { it.scored != null && (it.maxMark ?: 0.0) > 0 }
    Column(Modifier.padding(vertical = 4.dp)) {
        parts.forEachIndexed { i, m ->
            if (i > 0) RowDivider()
            val max = m.maxMark ?: 1.0
            val p = (m.scored ?: 0.0) * 100 / max
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.title, style = MaterialTheme.typography.bodyMedium, color = Pastel.Ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.38f))
                Box(
                    Modifier.weight(0.42f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Pastel.Hairline),
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((p / 100).toFloat().coerceIn(0f, 1f)).clip(RoundedCornerShape(6.dp))
                        .background(scoreColor(p)))
                }
                Text("${num(m.scored ?: 0.0)}/${num(max)}", style = MaterialTheme.typography.labelLarge, color = Pastel.Ink,
                    modifier = Modifier.weight(0.2f).padding(start = 10.dp), maxLines = 1)
            }
        }
    }
}
