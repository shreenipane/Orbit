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
    fun planAlerts(context: Context, snap: Snapshot?, settings: AppSettings) {
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
                snap.timetable.sessions.filter { it.day == weekday }.forEach { s ->
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
        Work.planAlerts(applicationContext, repo.cached(), repo.settings)
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
            Work.planAlerts(applicationContext, fresh, repo.settings)
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
