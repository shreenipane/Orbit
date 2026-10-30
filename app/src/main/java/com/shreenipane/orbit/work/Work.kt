package com.shreenipane.orbit.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.shreenipane.orbit.OrbitApp
import com.shreenipane.orbit.core.calc.ExamTime
import com.shreenipane.orbit.core.calc.SyncDiff
import com.shreenipane.orbit.core.model.Reminder
import com.shreenipane.orbit.core.model.ReminderKind
import com.shreenipane.orbit.core.model.Snapshot
import com.shreenipane.orbit.data.AppSettings
import com.shreenipane.orbit.data.Notifier
import com.shreenipane.orbit.data.SessionExpiredException
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * All scheduling goes through WorkManager, which survives reboots and needs no
 * exact-alarm permission. Alerts can arrive a few minutes late under Doze.
 */
object Work {
    private const val TAG_CLASS = "alert-class"
    private const val TAG_EXAM = "alert-exam"

    /** Periodic jobs: VTOP sync every 6 h (if enabled) and alert planning every 12 h. */
    fun ensurePeriodic(context: Context, settings: AppSettings) {
        val wm = WorkManager.getInstance(context)
        if (settings.backgroundSync) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork("sync")
        }
        wm.enqueueUniquePeriodicWork(
            "plan", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PlanWorker>(12, TimeUnit.HOURS).build(),
        )
        if (settings.weeklyReport) {
            // First run next Sunday 19:00, then every 7 days.
            val now = LocalDateTime.now()
            var next = now.toLocalDate().with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)).atTime(19, 0)
            if (!next.isAfter(now)) next = next.plusWeeks(1)
            val req = PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniquePeriodicWork("weekly", ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork("weekly")
        }
        if (settings.keepSignedIn) {
            // 15 minutes is the shortest interval Android allows for periodic work.
            val req = PeriodicWorkRequestBuilder<KeepAliveWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork("keepalive", ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork("keepalive")
        }
    }

    private fun alert(context: Context, name: String, tag: String, at: LocalDateTime, channel: String, title: String, text: String) {
        val delay = Duration.between(LocalDateTime.now(), at).toMillis()
        if (delay <= 0) return
        val req = OneTimeWorkRequestBuilder<AlertWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("channel" to channel, "title" to title, "text" to text, "id" to name.hashCode()))
            .addTag(tag)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, req)
    }

    /** Re-plans class alerts for today and tomorrow, and exam alerts for the next two weeks. */
    fun planAlerts(
        context: Context, snap: Snapshot?, settings: AppSettings,
        overrides: List<com.shreenipane.orbit.core.model.ClassOverride> = emptyList(),
        campus: com.shreenipane.orbit.core.model.CampusInfo? = null,
    ) {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(TAG_CLASS)
        wm.cancelAllWorkByTag(TAG_EXAM)
        if (snap == null) return

        if (settings.classAlerts) {
            val today = LocalDate.now()
            for (date in listOf(today, today.plusDays(1))) {
                // Respect the academic calendar: no alerts on holidays, and Saturday day orders.
                val calDay = snap.calendar?.days?.firstOrNull { it.date == date.toString() }
                if (calDay != null && calDay.kind in setOf(com.shreenipane.orbit.core.model.DayKind.HOLIDAY, com.shreenipane.orbit.core.model.DayKind.NO_INSTRUCTION, com.shreenipane.orbit.core.model.DayKind.EXAM)) continue
                val weekday = calDay?.dayOrder ?: date.dayOfWeek.value
                com.shreenipane.orbit.core.calc.Overrides.day(snap.timetable.sessions.filter { it.day == weekday }, date, overrides)
                    .filterNot { it.cancelled }.map { it.session }.forEach { s ->
                    val start = date.atTime(LocalTime.parse(s.start))
                    alert(
                        context, "class-$date-${s.start}-${s.slot}", TAG_CLASS,
                        start.minusMinutes(settings.classAlertLeadMinutes.toLong()), Notifier.CLASSES,
                        "${s.courseTitle} at ${s.start}", "${s.venue} · ${s.slot}" + if (s.isLab) " · Lab" else "",
                    )
                }
            }
        }

        if (settings.examAlerts) {
            val now = LocalDateTime.now()
            ExamTime.upcoming(snap.exams, now).filter { it.second.isBefore(now.plusDays(14)) }.forEach { (e, start) ->
                val where = listOf(e.venue, e.seatLocation.takeIf { it.isNotBlank() }, e.seatNumber.takeIf { it.isNotBlank() }?.let { "seat $it" })
                    .filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
                val time = start.toLocalTime().toString()
                alert(context, "exam-eve-${e.examType}-${e.courseCode}", TAG_EXAM, start.toLocalDate().minusDays(1).atTime(20, 0),
                    Notifier.EXAMS, "${e.examType} tomorrow: ${e.courseTitle}", "$time · $where")
                alert(context, "exam-1h-${e.examType}-${e.courseCode}", TAG_EXAM, start.minusHours(1),
                    Notifier.EXAMS, "${e.examType} in 1 hour: ${e.courseTitle}", "Report by ${e.reportingTime.ifBlank { time }} · $where")
            }
            // Fees due on VTOP: two days before at 10:00, and on the day.
            if (settings.feeReminders) campus?.pending?.forEach { f ->
                val due = ExamTime.parseVtopDate(f.dueDate) ?: return@forEach
                if (due.isBefore(now.toLocalDate())) return@forEach
                alert(context, "fee-2d-${f.reference}", TAG_EXAM, due.minusDays(2).atTime(10, 0), Notifier.EXAMS,
                    "Fee due in 2 days: ${f.head}", "₹${f.total} · pay on VTOP by ${f.dueDate}")
                alert(context, "fee-day-${f.reference}", TAG_EXAM, due.atTime(9, 0), Notifier.EXAMS,
                    "Fee due today: ${f.head}", "₹${f.total} · pay on VTOP today")
            }
            // Digital assignments still to upload, due in the next two weeks.
            val today = now.toLocalDate()
            snap.assignments.filter { !it.submitted }.forEach { da ->
                val due = ExamTime.parseVtopDate(da.dueDate) ?: return@forEach
                if (due.isBefore(today) || due.isAfter(today.plusDays(14))) return@forEach
                val key = "${da.classId}-${da.title}"
                alert(context, "da-eve-$key", TAG_EXAM, due.minusDays(1).atTime(20, 0), Notifier.EXAMS,
                    "DA due tomorrow: ${da.title}", "${da.courseTitle} · not uploaded yet")
                alert(context, "da-day-$key", TAG_EXAM, due.atTime(9, 0), Notifier.EXAMS,
                    "DA due today: ${da.title}", "${da.courseTitle} · upload on VTOP before midnight")
            }
        }
    }

    fun scheduleReminder(context: Context, r: Reminder) {
        val name = "reminder-${r.id}"
        if (r.done) { WorkManager.getInstance(context).cancelUniqueWork(name); return }
        val at = java.time.Instant.ofEpochMilli(r.dueAtMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        val label = if (r.kind == ReminderKind.ASSIGNMENT) "Assignment due" else "Reminder"
        alert(context, name, "alert-reminder", at, Notifier.REMINDERS, r.title,
            listOfNotNull(label, r.courseCode, r.notes.takeIf { it.isNotBlank() }).joinToString(" · "))
    }

    fun cancelReminder(context: Context, id: Long) =
        WorkManager.getInstance(context).cancelUniqueWork("reminder-$id")

    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "sync-now", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}

class AlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val d = inputData
        Notifier.post(
            applicationContext,
            d.getString("channel") ?: Notifier.REMINDERS,
            d.getInt("id", 0),
            d.getString("title") ?: return Result.success(),
            d.getString("text").orEmpty(),
        )
        return Result.success()
    }
}

class PlanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as OrbitApp).repository
        val snap = repo.cached()
        Work.planAlerts(applicationContext, snap, repo.settings, repo.overrides(), repo.campusInfo())
        com.shreenipane.orbit.widget.OrbitWidget.update(applicationContext, snap, repo.overrides())
        return Result.success()
    }
}

/**
 * Background sync. VTOP sessions are short-lived and Orbit never stores your
 * password, so this only succeeds while the last sign-in is still valid. Once
 * it expires, you get one "sign in" notification instead of repeated failures.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as OrbitApp).repository
        if (!repo.settings.backgroundSync) return Result.success()
        val old = repo.cached() ?: return Result.success() // never signed in
        return try {
            val fresh = repo.refresh()
            val notes = SyncDiff.notes(old, fresh)
            notes.take(5).forEachIndexed { i, n -> Notifier.post(applicationContext, Notifier.SYNC, 2000 + i, n.title, n.text) }
            if (notes.size > 5) {
                Notifier.post(applicationContext, Notifier.SYNC, 2005, "${notes.size - 5} more updates", "Open Orbit to see everything that changed.")
            }
            // Announcements and fees ride along; new announcements get a notification.
            val oldCampus = repo.campusInfo()
            val campus = runCatching { repo.refreshCampus() }.getOrNull()
            if (campus != null && oldCampus != null) {
                val fresh2 = campus.announcements.filterNot { it in oldCampus.announcements }
                fresh2.take(3).forEachIndexed { i, a ->
                    Notifier.post(applicationContext, Notifier.SYNC, 2100 + i, "New on VTOP · ${a.category}", a.text)
                }
            }
            Work.planAlerts(applicationContext, fresh, repo.settings, repo.overrides(), campus ?: oldCampus)
            com.shreenipane.orbit.widget.OrbitWidget.update(applicationContext, fresh, repo.overrides())
            Result.success()
        } catch (e: SessionExpiredException) {
            if (!repo.expiryNotified) {
                Notifier.post(applicationContext, Notifier.SYNC, 1999, "Sign in to keep Orbit in sync",
                    "Your VTOP session ended. Saved data is still there; open Orbit and sign in to refresh.")
                repo.expiryNotified = true
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.retry()
        } catch (e: Exception) {
            Result.success()
        }
    }
}

/**
 * Touches the VTOP session every ~15 minutes so its idle timeout doesn't sign
 * you out. When the session has ended anyway (VTOP also ends sessions after a
 * while, or when you sign in elsewhere), it stops itself until the next sign-in.
 */
class KeepAliveWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as OrbitApp).repository
        if (!repo.settings.keepSignedIn || repo.cached() == null) return Result.success()
        val alive = repo.keepAlive()
        if (!alive) WorkManager.getInstance(applicationContext).cancelUniqueWork("keepalive")
        return Result.success()
    }
}

/** Sunday evening: how attendance moved this week, compared with last Sunday's numbers. */
class WeeklyReportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as OrbitApp).repository
        if (!repo.settings.weeklyReport) return Result.success()
        val snap = repo.cached() ?: return Result.success()
        val before = repo.weeklyBaseline()
        if (before != null) {
            val lines = com.shreenipane.orbit.core.calc.WeeklyReport.lines(before, snap.attendance)
            Notifier.post(applicationContext, Notifier.SYNC, 2200, "Your week in attendance",
                com.shreenipane.orbit.core.calc.WeeklyReport.summary(lines))
        }
        repo.saveWeeklyBaseline(snap.attendance)
        return Result.success()
    }
}
