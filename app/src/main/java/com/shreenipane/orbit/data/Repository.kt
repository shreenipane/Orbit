package com.shreenipane.orbit.data

import android.content.Context
import com.shreenipane.orbit.core.model.AcademicCalendar
import com.shreenipane.orbit.core.model.DigitalAssignment
import com.shreenipane.orbit.core.model.Friend
import com.shreenipane.orbit.core.model.GradeHistory
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
    /** Touch the VTOP session every 15 minutes so it doesn't time out. */
    val keepSignedIn: Boolean = true,
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
    val vault = CredentialVault(context)

    // ---------- small prefs ----------

    var campus: Campus
        get() = Campus.byId(prefs.getString("campus", null))
        set(value) = prefs.edit().putString("campus", value.id).apply()

    var pinnedSemesterId: String?
        get() = prefs.getString("semester", null)
        set(value) = prefs.edit().putString("semester", value).apply()

    /** Academic-calendar class group; null = automatic (General (Flexible) first). */
    var classGroupId: String?
        get() = prefs.getString("class_group", null)
        set(value) = prefs.edit().putString("class_group", value).apply()

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
            keepSignedIn = prefs.getBoolean("keep_alive", true),
        )
        set(s) = prefs.edit()
            .putString("alias", s.alias.trim())
            .putBoolean("lock", s.biometricLock)
            .putBoolean("class_alerts", s.classAlerts)
            .putInt("class_lead", s.classAlertLeadMinutes)
            .putBoolean("exam_alerts", s.examAlerts)
            .putBoolean("sync", s.backgroundSync)
            .putBoolean("keep_alive", s.keepSignedIn)
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

    /** Keeps the last raw page of each kind, with the registration number masked, for Settings → debug export. */
    private fun recordRaw(name: String, html: String) {
        runCatching {
            val dir = File(context.filesDir, "debug").apply { mkdirs() }
            val masked = html.replace(Regex("\\b\\d{2}[A-Z]{3}\\d{4,5}\\b"), "<REGNO>")
            File(dir, "$name.html").writeText(masked.take(400_000))
        }
    }

    private fun newClient() = VtopClient(campus, cookieJar, onRaw = ::recordRaw)

    /** The client from the last successful attach, reused for uploads and OTPs. */
    @Volatile private var live: VtopClient? = null

    private suspend fun liveClient(): VtopClient =
        live ?: newClient().also { it.attach(); live = it }

    fun newLogin(): VtopLogin {
        live = null
        return VtopLogin(campus, cookieJar)
    }

    /**
     * Pulls a fresh snapshot. Throws [SessionExpiredException] when VTOP wants a
     * new sign-in; the cached snapshot stays on screen in that case. Optional
     * parts that fail are listed in [Snapshot.warnings] instead of vanishing.
     */
    suspend fun refresh(semesterId: String? = pinnedSemesterId): Snapshot = coroutineScope {
        val client = newClient()
        val session = client.attach()
        live = client
        val semesters = client.semesters()
        val sem: Semester? = semesters.firstOrNull { it.id == semesterId } ?: pickCurrent(semesters)
        val warnings = java.util.Collections.synchronizedList(mutableListOf<String>())
        suspend fun <T> optional(label: String, fallback: T, block: suspend () -> T): T = try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            warnings += "$label: ${e.message ?: e.javaClass.simpleName}"
            fallback
        }
        val grades = async { optional<GradeHistory?>("Grade history", null) { client.gradeHistory() } }
        val snapshot = if (sem == null) {
            Snapshot(session.regNo, campus.id, null, semesters, grades = grades.await(), warnings = warnings.toList(),
                fetchedAtMillis = System.currentTimeMillis())
        } else {
            val tt = async { client.timetable(sem.id) }
            val att = async { client.attendance(sem.id) }
            // Optional menus: VTOP switches these off at times, which shouldn't sink the whole sync.
            val marks = async { optional("Marks", emptyList()) { client.marks(sem.id) } }
            val exams = async { optional("Exam schedule", emptyList()) { client.examSchedule(sem.id) } }
            val calendar = async { optional<AcademicCalendar?>("Academic calendar", null) { client.academicCalendar(sem.id, classGroupId) } }
            val das = async { optional("Digital assignments", emptyList()) { client.digitalAssignments(sem.id) } }
            val previous = cached()
            Snapshot(
                regNo = session.regNo, campusId = campus.id, semester = sem, semesters = semesters,
                timetable = tt.await(), attendance = att.await(), marks = marks.await(),
                grades = grades.await(), exams = exams.await(),
                // Keep the last good calendar if VTOP's calendar menu is down this time.
                calendar = calendar.await() ?: previous?.calendar?.takeIf { previous?.semester?.id == sem.id },
                assignments = das.await(),
                warnings = warnings.toList(),
                fetchedAtMillis = System.currentTimeMillis(),
            )
        }
        write("snapshot.json", Snapshot.serializer(), snapshot)
        expiryNotified = false
        snapshot
    }

    suspend fun uploadDa(da: DigitalAssignment, fileName: String, bytes: ByteArray, mime: String) =
        liveClient().uploadDa(da, fileName, bytes, mime)

    suspend fun verifyDaOtp(otp: String) = liveClient().verifyDaOtp(otp)

    /** Called by the keep-alive job. False means the session has ended. */
    suspend fun keepAlive(): Boolean = newClient().keepAlive()

    /** One text file with the last raw VTOP pages, for sharing when something parses wrong. */
    fun debugReport(): File {
        val dir = File(context.filesDir, "debug").apply { mkdirs() }
        val out = File(context.cacheDir, "orbit-debug.txt")
        out.writeText(buildString {
            append("Orbit debug report · campus ${campus.id}\n")
            dir.listFiles().orEmpty().sortedBy { it.name }.forEach { f ->
                append("\n===== ${f.name} (${f.length()} bytes) =====\n")
                append(f.readText())
            }
        })
        return out
    }

    /** VTOP lists the newest semester first, but also lists side semesters (transport, CDC, freshers). */
    private fun pickCurrent(list: List<Semester>): Semester? {
        val skip = Regex("transport|cdc|pat|freshers|fast track|re-?fat|summer", RegexOption.IGNORE_CASE)
        return list.firstOrNull { !skip.containsMatchIn(it.name) } ?: list.firstOrNull()
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        cookieJar.clear()
        vault.clear()
        live = null
        File(context.filesDir, "debug").deleteRecursively()
        file("snapshot.json").delete()
        prefs.edit().remove("semester").remove("expiry_notified").remove("class_group").apply()
    }
}
