package com.shreenipane.orbit.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shreenipane.orbit.MainActivity
import com.shreenipane.orbit.R

object Notifier {
    const val CLASSES = "classes"
    const val EXAMS = "exams"
    const val SYNC = "sync"
    const val REMINDERS = "reminders"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CLASSES, "Class alerts", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "A heads-up before each class" },
            NotificationChannel(EXAMS, "Exam alerts", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "The evening before and an hour before each exam" },
            NotificationChannel(SYNC, "New marks and attendance", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "What changed on VTOP since the last sync" },
            NotificationChannel(REMINDERS, "Reminders and assignments", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Your own reminders and assignment deadlines" },
        ).forEach(nm::createNotificationChannel)
    }

    fun canPost(context: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, channel: String, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_orbit)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }
}
