@file:OptIn(ExperimentalMaterial3Api::class)

package com.shreenipane.orbit.ui.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shreenipane.orbit.core.model.GradeStats
import com.shreenipane.orbit.ui.components.Metric
import com.shreenipane.orbit.ui.components.RowDivider
import com.shreenipane.orbit.ui.components.Tag
import com.shreenipane.orbit.ui.components.brutal
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Good
import com.shreenipane.orbit.ui.theme.Pastel
import com.shreenipane.orbit.ui.theme.Warn

/** What a course's grades looked like that semester. */
sealed class GradeDetail {
    data object Loading : GradeDetail()
    data class Loaded(val stats: GradeStats) : GradeDetail()
    data class Failed(val message: String) : GradeDetail()
}

fun gradeColor(g: String) = when (g.trim().uppercase()) {
    "S", "A" -> Good
    "B", "C", "P" -> Warn
    "" , "-" -> Pastel.InkMuted
    "D", "E" -> Warn
    else -> Bad
}

/** Your grade, the class mean, and the mark range for every grade, yours highlighted. */
@Composable
fun GradeSheet(title: String, subtitle: String, grade: String, total: String?, detail: GradeDetail, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Pastel.Page,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 24.dp, bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = Pastel.Ink)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted)
                }
                Spacer(Modifier.width(12.dp))
                Tag(grade.ifBlank { "–" }, gradeColor(grade))
            }
            Spacer(Modifier.height(16.dp))
            when (detail) {
                GradeDetail.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(22.dp), strokeWidth = 2.dp, color = Pastel.Ink)
                    Spacer(Modifier.width(10.dp)); Text("Loading grade ranges…", color = Pastel.Ink)
                }
                is GradeDetail.Failed -> Text(detail.message, color = Pastel.InkMuted, style = MaterialTheme.typography.bodyMedium)
                is GradeDetail.Loaded -> {
                    val s = detail.stats
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Metric(total?.takeIf { it.isNotBlank() } ?: "–", "Your total", Modifier.weight(1f), fill = Pastel.Yellow)
                        Metric(s.mean.ifBlank { "–" }, "Class mean", Modifier.weight(1f), fill = Pastel.Sky)
                        Metric(s.classStrength.ifBlank { "–" }, "Students", Modifier.weight(1f), fill = Pastel.Lavender)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Grade ranges", style = MaterialTheme.typography.titleMedium, color = Pastel.Ink)
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.fillMaxWidth().brutal(Pastel.Card)) {
                        s.ranges.forEachIndexed { i, r ->
                            if (i > 0) RowDivider()
                            val mine = r.grade.equals(grade.trim(), true)
                            Row(
                                Modifier.fillMaxWidth()
                                    .then(if (mine) Modifier.background(Pastel.Yellow) else Modifier)
                                    .padding(horizontal = 16.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(r.grade, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                    color = Pastel.Ink, modifier = Modifier.width(36.dp))
                                Text(r.range, style = MaterialTheme.typography.bodyLarge, color = Pastel.Ink, modifier = Modifier.weight(1f))
                                if (mine) Text("You", style = MaterialTheme.typography.labelLarge, color = Pastel.Ink)
                            }
                        }
                    }
                    if (s.sd.isNotBlank()) {
                        Text("Standard deviation ${s.sd}", style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}
