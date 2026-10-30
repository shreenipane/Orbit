package com.shreenipane.orbit.ui.tools

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.shreenipane.orbit.R
import com.shreenipane.orbit.core.calc.Recap
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.ui.components.BrutalButton
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.Metric
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.RowDivider
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.Pastel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private fun f0(v: Double) = String.format(Locale.US, "%.0f", v)
private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)

/** The semester on one screen, shareable as an image. */
@Composable
fun RecapScreen(snap: Snapshot, name: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val r = remember(snap) { Recap.of(snap) }

    OrbitScreen(title = "Semester recap", subtitle = r.semester.ifBlank { null }) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric(r.overallAttendance?.let { "${f0(it)}%" } ?: "–", "Attendance", Modifier.weight(1f), fill = Pastel.Green)
                Metric("${r.classesAttended}", "Classes attended", Modifier.weight(1f), fill = Pastel.Sky)
                Metric(r.cgpa?.let(::f2) ?: "–", "CGPA", Modifier.weight(1f), fill = Pastel.Lavender)
            }
        }
        section(header = "Highlights") {
            ListRow("Best attendance", r.bestAttendance?.let { "${it.title} · ${f0(it.value)}%" } ?: "—")
            RowDivider()
            ListRow("Needs attention", r.worstAttendance?.let { "${it.title} · ${f0(it.value)}%" } ?: "—")
            RowDivider()
            ListRow("Strongest subject", r.bestMarks?.let { "${it.title} · ${f0(it.value)}% of marks so far" } ?: "No marks yet")
            RowDivider()
            ListRow("Toughest subject", r.worstMarks?.let { "${it.title} · ${f0(it.value)}% of marks so far" } ?: "No marks yet")
        }
        item {
            BrutalButton("Share as image", {
                scope.launch {
                    val file = withContext(Dispatchers.IO) { runCatching { RecapImage.render(ctx, r, name) }.getOrNull() }
                    if (file != null) {
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
                        runCatching {
                            ctx.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share recap"))
                        }
                    }
                }
            }, Modifier.fillMaxWidth().padding(top = 20.dp))
        }
    }
}

/** Draws the recap card as a 1080×1350 PNG in the app's cache (light pastel style, regardless of theme). */
object RecapImage {
    fun render(context: Context, r: Recap.Data, name: String): File {
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val page = 0xFFCEE4E5.toInt(); val card = 0xFFE6F3F3.toInt(); val ink = 0xFF111111.toInt()
        val fills = intArrayOf(0xFFC1FF83.toInt(), 0xFF75E5EA.toInt(), 0xFFC4AFF3.toInt(), 0xFFFFEA79.toInt())
        c.drawColor(page)
        val head = ResourcesCompat.getFont(context, R.font.pangolin_regular) ?: Typeface.DEFAULT_BOLD
        val body = ResourcesCompat.getFont(context, R.font.poppins_semibold) ?: Typeface.DEFAULT
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; style = Paint.Style.STROKE; strokeWidth = 6f }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        fun box(x: Float, y: Float, bw: Float, bh: Float, color: Int) {
            fill.color = ink; c.drawRoundRect(RectF(x + 12, y + 12, x + bw + 12, y + bh + 12), 36f, 36f, fill)
            fill.color = color; c.drawRoundRect(RectF(x, y, x + bw, y + bh), 36f, 36f, fill)
            c.drawRoundRect(RectF(x, y, x + bw, y + bh), 36f, 36f, stroke)
        }

        text.typeface = head; text.textSize = 96f
        c.drawText("My semester", 80f, 190f, text)
        text.typeface = body; text.textSize = 38f
        c.drawText(listOf(name, r.semester).filter { it.isNotBlank() }.joinToString(" · ").take(48), 84f, 260f, text)

        val stats = listOf(
            (r.overallAttendance?.let { "${f0(it)}%" } ?: "–") to "attendance",
            "${r.classesAttended}" to "classes attended",
            (r.cgpa?.let(::f2) ?: "–") to "CGPA",
            "${r.courses}" to "courses",
        )
        stats.forEachIndexed { i, (v, label) ->
            val x = 80f + (i % 2) * 470f; val y = 330f + (i / 2) * 270f
            box(x, y, 430f, 220f, fills[i])
            text.typeface = body; text.textSize = 88f; c.drawText(v, x + 40f, y + 120f, text)
            text.textSize = 36f; c.drawText(label, x + 40f, y + 180f, text)
        }

        box(80f, 900f, 920f, 330f, card)
        val lines = listOfNotNull(
            r.bestAttendance?.let { "Best attendance: ${it.title.take(26)} ${f0(it.value)}%" },
            r.worstAttendance?.let { "Needs attention: ${it.title.take(26)} ${f0(it.value)}%" },
            r.bestMarks?.let { "Strongest: ${it.title.take(30)}" },
            r.worstMarks?.let { "Toughest: ${it.title.take(30)}" },
        )
        text.typeface = body; text.textSize = 38f
        lines.forEachIndexed { i, l -> c.drawText(l, 120f, 975f + i * 68f, text) }
        text.textSize = 30f; text.color = 0xB3000000.toInt()
        c.drawText("Made with Orbit", 80f, 1300f, text)

        val out = File(context.cacheDir, "orbit-recap.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return out
    }
}
