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
import com.shreenipane.orbit.core.calc.AttendanceAdvice
import com.shreenipane.orbit.core.calc.AttendanceCalc
import com.shreenipane.orbit.core.calc.ExamPlanner
import com.shreenipane.orbit.core.calc.ExamTime
import com.shreenipane.orbit.core.model.ClassOverride
import com.shreenipane.orbit.core.model.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Shared plumbing: both widgets use the same card layout as the next-class widget. */
private fun render(context: Context, cls: Class<*>, c: OrbitWidget.Companion.Content) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, cls))
    if (ids.isEmpty()) return
    val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val views = RemoteViews(context.packageName, R.layout.widget_orbit).apply {
        setTextViewText(R.id.widget_label, c.label)
        setTextViewText(R.id.widget_title, c.title)
        setTextViewText(R.id.widget_subtitle, c.subtitle)
        setTextViewText(R.id.widget_meta, c.meta)
        setOnClickPendingIntent(R.id.widget_root, open)
    }
    manager.updateAppWidget(ids, views)
}

private fun refreshFromCache(context: Context, block: (Snapshot?, List<ClassOverride>) -> Unit, done: () -> Unit) {
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            val repo = (context.applicationContext as OrbitApp).repository
            block(repo.cached(), repo.overrides())
        } finally { done() }
    }
}

/** Your lowest-attendance course, what to do about it, and the next two lowest. */
class AttendanceWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        refreshFromCache(context, { s, o -> update(context, s, o) }) { pending.finish() }
    }

    companion object {
        fun content(snap: Snapshot?, overrides: List<ClassOverride>, now: LocalDateTime): OrbitWidget.Companion.Content {
            if (snap == null || snap.attendance.isEmpty()) return OrbitWidget.Companion.Content("ATTENDANCE", "Sign in to see attendance", "Open Orbit", "")
            val a = snap.attendance
            val overall = AttendanceCalc.percent(a.sumOf { it.attended }, a.sumOf { it.total })
            val left = ExamPlanner.remainingAll(snap.calendar, snap.timetable.sessions, now.toLocalDate(), now.hour * 60 + now.minute, overrides)
            val sorted = a.sortedBy { AttendanceCalc.percent(it.attended, it.total) }
            val low = sorted.first()
            val advice = AttendanceAdvice.of(low.attended, low.total, ExamPlanner.remainingFrom(left, low))
            return OrbitWidget.Companion.Content(
                "ATTENDANCE · ${Math.round(overall)}%",
                "${low.courseTitle} ${low.vtopPercent}%",
                advice.line,
                sorted.drop(1).take(2).joinToString(" · ") { "${it.courseTitle.take(18)} ${it.vtopPercent}%" },
            )
        }

        fun update(context: Context, snap: Snapshot?, overrides: List<ClassOverride>) =
            render(context, AttendanceWidget::class.java, content(snap, overrides, LocalDateTime.now()))
    }
}

/** The next exam: paper, time, venue and seat when scheduled, else the CAT/FAT window from the calendar. */
class ExamWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        refreshFromCache(context, { s, o -> update(context, s, o) }) { pending.finish() }
    }

    companion object {
        private val WHEN = DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.ENGLISH)

        fun content(snap: Snapshot?, overrides: List<ClassOverride>, now: LocalDateTime): OrbitWidget.Companion.Content {
            if (snap == null) return OrbitWidget.Companion.Content("NEXT EXAM", "Sign in to see exams", "Open Orbit", "")
            val paper = ExamTime.upcoming(snap.exams, now).firstOrNull()
            val window = ExamPlanner.countdowns(snap.calendar, snap.exams, snap.timetable.sessions, now.toLocalDate(),
                now.hour * 60 + now.minute, overrides).firstOrNull()
            if (paper != null) {
                val (e, start) = paper
                val days = ChronoUnit.DAYS.between(now.toLocalDate(), start.toLocalDate())
                return OrbitWidget.Companion.Content(
                    "${e.examType.uppercase()} · " + if (days == 0L) "TODAY" else "IN ${days}D",
                    e.courseTitle,
                    start.format(WHEN),
                    listOf(e.venue, e.seatLocation.takeIf { it.isNotBlank() }?.let { "seat $it" }).filterNotNull()
                        .filter { it.isNotBlank() }.joinToString(" · "),
                )
            }
            if (window != null) {
                return OrbitWidget.Companion.Content(
                    "NEXT EXAM · IN ${window.daysLeft}D", window.label,
                    "Starts ${window.start.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))}",
                    "${window.totalClasses} classes before it",
                )
            }
            return OrbitWidget.Companion.Content("NEXT EXAM", "No exams coming up", "Enjoy it", "")
        }

        fun update(context: Context, snap: Snapshot?, overrides: List<ClassOverride>) =
            render(context, ExamWidget::class.java, content(snap, overrides, LocalDateTime.now()))
    }
}
