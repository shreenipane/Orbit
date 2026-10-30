package com.shreenipane.orbit.data

import android.content.Context
import com.shreenipane.orbit.core.model.Friend
import com.shreenipane.orbit.core.model.Reminder
import com.shreenipane.orbit.core.model.Semester
import com.shreenipane.orbit.core.model.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

data class AppSettings(
    val alias: String = "",
    val biometricLock: Boolean = false,
    val classAlerts: Boolean = true,
    val classAlertLeadMinutes: Int = 10,
    val examAlerts: Boolean = true,
    val backgroundSync: Boolean = true,
)

/**
 * Local source of truth. VTOP data is fetched into one cached snapshot;
 * reminders, friends and settings live only on the phone. No password is ever
 * stored: the session lives in WebView cookies, and when it expires the user
 * signs in again.
 */
class Repository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs = context.getSharedPreferences("orbit", Context.MODE_PRIVATE)
    private val ioLock = Mutex()
    val cookieJar = WebViewCookieJar()

    // ---------- small prefs ----------

    var campus: Campus
        get() = Campus.byId(prefs.getString("campus", null))
        set(value) = prefs.edit().putString("campus", value.id).apply()

    var pinnedSemesterId: String?
        get() = prefs.getString("semester", null)
        set(value) = prefs.edit().putString("semester", value).apply()

    /** Set once we've told the user their session expired, so background sync doesn't nag. */
    var expiryNotified: Boolean
        get() = prefs.getBoolean("expiry_notified", false)
        set(value) = prefs.edit().putBoolean("expiry_notified", value).apply()

    var settings: AppSettings
        get() = AppSettings(
            alias = prefs.getString("alias", "") ?: "",
            biometricLock = prefs.getBoolean("lock", false),
            classAlerts = prefs.getBoolean("class_alerts", true),
            classAlertLeadMinutes = prefs.getInt("class_lead", 10),
            examAlerts = prefs.getBoolean("exam_alerts", true),
            backgroundSync = prefs.getBoolean("sync", true),
        )
        set(s) = prefs.edit()
            .putString("alias", s.alias.trim())
            .putBoolean("lock", s.biometricLock)
            .putBoolean("class_alerts", s.classAlerts)
            .putInt("class_lead", s.classAlertLeadMinutes)
            .putBoolean("exam_alerts", s.examAlerts)
            .putBoolean("sync", s.backgroundSync)
            .apply()

    // ---------- json files ----------

    private fun file(name: String) = File(context.filesDir, name)

    private suspend fun <T> read(name: String, ser: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(ser, file(name).readText()) }.getOrNull()
    }

    private suspend fun <T> write(name: String, ser: KSerializer<T>, value: T) = withContext(Dispatchers.IO) {
        ioLock.withLock {
            val tmp = file("$name.tmp")
            tmp.writeText(json.encodeToString(ser, value))
            tmp.renameTo(file(name))
        }
    }

    suspend fun cached(): Snapshot? = read("snapshot.json", Snapshot.serializer())

    suspend fun reminders(): List<Reminder> = read("reminders.json", ListSerializer(Reminder.serializer())) ?: emptyList()
    suspend fun saveReminders(list: List<Reminder>) = write("reminders.json", ListSerializer(Reminder.serializer()), list)

    suspend fun friends(): List<Friend> = read("friends.json", ListSerializer(Friend.serializer())) ?: emptyList()
    suspend fun saveFriends(list: List<Friend>) = write("friends.json", ListSerializer(Friend.serializer()), list)

    // ---------- VTOP ----------

    /**
     * Pulls a fresh snapshot. Throws [SessionExpiredException] when VTOP wants a
     * new sign-in; the cached snapshot stays on screen in that case.
     */
    suspend fun refresh(semesterId: String? = pinnedSemesterId): Snapshot = coroutineScope {
        val client = VtopClient(campus, cookieJar)
        val session = client.attach()
        val semesters = client.semesters()
        val sem: Semester? = semesters.firstOrNull { it.id == semesterId } ?: pickCurrent(semesters)
        val grades = async { runCatching { client.gradeHistory() }.getOrNull() }
        val snapshot = if (sem == null) {
            Snapshot(session.regNo, campus.id, null, semesters, grades = grades.await(), fetchedAtMillis = System.currentTimeMillis())
        } else {
            val tt = async { client.timetable(sem.id) }
            val att = async { client.attendance(sem.id) }
            // Optional menus: VTOP switches these off at times, which shouldn't sink the whole sync.
            val marks = async { runCatching { client.marks(sem.id) }.getOrDefault(emptyList()) }
            val exams = async { runCatching { client.examSchedule(sem.id) }.getOrDefault(emptyList()) }
            Snapshot(
                regNo = session.regNo, campusId = campus.id, semester = sem, semesters = semesters,
                timetable = tt.await(), attendance = att.await(), marks = marks.await(),
                grades = grades.await(), exams = exams.await(), fetchedAtMillis = System.currentTimeMillis(),
            )
        }
        write("snapshot.json", Snapshot.serializer(), snapshot)
        expiryNotified = false
        snapshot
    }

    /** VTOP lists the newest semester first, but also lists side semesters (transport, CDC, freshers). */
    private fun pickCurrent(list: List<Semester>): Semester? {
        val skip = Regex("transport|cdc|pat|freshers|fast track|re-?fat|summer", RegexOption.IGNORE_CASE)
        return list.firstOrNull { !skip.containsMatchIn(it.name) } ?: list.firstOrNull()
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        cookieJar.clear()
        file("snapshot.json").delete()
        prefs.edit().remove("semester").remove("expiry_notified").apply()
    }
}
