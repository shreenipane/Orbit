package com.shreenipane.orbit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.shreenipane.orbit.MainActivity
import com.shreenipane.orbit.OrbitApp
import com.shreenipane.orbit.R
import com.shreenipane.orbit.core.calc.AttendanceCalc
import com.shreenipane.orbit.core.calc.ExamPlanner
import com.shreenipane.orbit.core.calc.ExamTime
import com.shreenipane.orbit.core.calc.Overrides
import com.shreenipane.orbit.core.model.ClassOverride
import com.shreenipane.orbit.core.model.DayKind
import com.shreenipane.orbit.core.model.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Home-screen widget. Content is computed from the cached snapshot; it never hits the network. */
class OrbitWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = (context.applicationContext as OrbitApp).repository
                update(context, repo.cached(), repo.overrides())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        data class Content(val label: String, val title: String, val subtitle: String, val meta: String)

        private fun m(hhmm: String) = hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()

        fun content(snap: Snapshot?, overrides: List<ClassOverride>, now: LocalDateTime): Content {
            if (snap == null) return Content("ORBIT", "Sign in to see your day", "Open Orbit", "")
            val today = now.toLocalDate()
            val minute = now.hour * 60 + now.minute
            val cal = snap.calendar?.days?.firstOrNull { it.date == today.toString() }
            val off = cal != null && cal.kind in setOf(DayKind.HOLIDAY, DayKind.NO_INSTRUCTION, DayKind.EXAM)
            val weekday = cal?.dayOrder ?: today.dayOfWeek.value
            val classes = if (off) emptyList() else
                Overrides.day(snap.timetable.sessions.filter { it.day == weekday }, today, overrides).filterNot { it.cancelled }.map { it.session }
            val ongoing = classes.firstOrNull { minute >= m(it.start) && minute < m(it.end) }
            val next = classes.firstOrNull { m(it.start) > minute }

            val att = snap.attendance.let { a ->
                val t = a.sumOf { it.total }
                if (t == 0) null else AttendanceCalc.percent(a.sumOf { it.attended }, t)
            }
            val exam = ExamPlanner.countdowns(snap.calendar, snap.exams, snap.timetable.sessions, today, minute, overrides).firstOrNull()
            val da = snap.assignments.filter { !it.submitted }
                .mapNotNull { d -> ExamTime.parseVtopDate(d.dueDate)?.let { d to it } }
                .filter { !it.second.isBefore(today) }.minByOrNull { it.second }
            val meta = listOfNotNull(
                att?.let { "Attendance ${String.format(java.util.Locale.US, "%.0f", it)}%" },
                exam?.let { "${it.label} in ${it.daysLeft}d" },
                da?.let { (d, due) -> "${d.title} due ${ChronoUnit.DAYS.between(today, due).let { n -> if (n == 0L) "today" else "in ${n}d" }}" },
            ).joinToString(" · ")

            return when {
                ongoing != null -> Content("NOW · TILL ${ongoing.end}", ongoing.courseTitle,
                    "${ongoing.venue}" + (next?.let { " · next ${it.start}" } ?: ""), meta)
                next != null -> Content("NEXT · ${next.start}", next.courseTitle, "${next.venue} · ${next.slot}", meta)
                classes.isNotEmpty() -> Content("DONE FOR TODAY", "All ${classes.size} classes done", "See you tomorrow", meta)
                else -> Content("FREE DAY", if (off && cal != null) cal.label.substringBefore(" -").ifBlank { "No classes" } else "No classes today", "Enjoy it", meta)
            }
        }

        /** Re-renders every placed widget. Safe to call from anywhere; no-ops if none are placed. */
        fun update(context: Context, snap: Snapshot?, overrides: List<ClassOverride>) {
            // The attendance and exam widgets refresh whenever this one does.
            runCatching { AttendanceWidget.update(context, snap, overrides) }
            runCatching { ExamWidget.update(context, snap, overrides) }
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, OrbitWidget::class.java))
            if (ids.isEmpty()) return
            val c = content(snap, overrides, LocalDateTime.now())
            val open = PendingIntent.getActivity(
                context, 1, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_orbit).apply {
                setTextViewText(R.id.widget_label, c.label)
                setTextViewText(R.id.widget_title, c.title)
                setTextViewText(R.id.widget_subtitle, c.subtitle)
                setTextViewText(R.id.widget_meta, c.meta)
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
