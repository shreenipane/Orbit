package com.shreenipane.orbit.data

import android.content.Context
import com.shreenipane.orbit.core.model.AcademicCalendar
import com.shreenipane.orbit.core.model.AttendanceRecord
import com.shreenipane.orbit.core.model.ClassOverride
import com.shreenipane.orbit.core.model.PlannedCourse
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
    /** 0 = follow the phone, 1 = light, 2 = dark. */
    val themeMode: Int = 0,
    val weeklyReport: Boolean = true,
    /** Accent colour index, see Pastel.ACCENTS. */
    val accent: Int = 0,
    /** Monday-morning "week ahead" summary and attendance-drop warnings. */
    val smartAlerts: Boolean = true,
    /** Credits your curriculum needs per category (code → credits), entered on the CGPA screen. */
    val creditTargets: Map<String, Double> = emptyMap(),
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
            themeMode = prefs.getInt("theme", 0),
            weeklyReport = prefs.getBoolean("weekly", true),
            accent = prefs.getInt("accent", 0),
            smartAlerts = prefs.getBoolean("smart_alerts", true),
            creditTargets = (prefs.getString("credit_targets", "") ?: "").split(';').mapNotNull { e ->
                val k = e.substringBefore('=', ""); val v = e.substringAfter('=', "").toDoubleOrNull()
                if (k.isBlank() || v == null) null else k to v
            }.toMap(),
        )
        set(s) = prefs.edit()
            .putString("alias", s.alias.trim())
            .putBoolean("lock", s.biometricLock)
            .putBoolean("class_alerts", s.classAlerts)
            .putInt("class_lead", s.classAlertLeadMinutes)
            .putBoolean("exam_alerts", s.examAlerts)
            .putBoolean("sync", s.backgroundSync)
            .putBoolean("keep_alive", s.keepSignedIn)
            .putInt("theme", s.themeMode)
            .putBoolean("weekly", s.weeklyReport)
            .putInt("accent", s.accent)
            .putBoolean("smart_alerts", s.smartAlerts)
            .putString("credit_targets", s.creditTargets.entries.joinToString(";") { "${it.key}=${it.value}" })
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

    suspend fun cached(): Snapshot? = read("snapshot.json", Snapshot.serializer())?.let { snap ->
        // Older versions saved whatever calendar VTOP returned, even another semester's.
        snap.copy(calendar = com.shreenipane.orbit.core.calc.SemesterWindow.fit(snap.calendar, snap.semester?.name))
    }

    suspend fun reminders(): List<Reminder> = read("reminders.json", ListSerializer(Reminder.serializer())) ?: emptyList()
    suspend fun saveReminders(list: List<Reminder>) = write("reminders.json", ListSerializer(Reminder.serializer()), list)

    suspend fun friends(): List<Friend> = read("friends.json", ListSerializer(Friend.serializer())) ?: emptyList()
    suspend fun saveFriends(list: List<Friend>) = write("friends.json", ListSerializer(Friend.serializer()), list)

    suspend fun overrides(): List<ClassOverride> = read("overrides.json", ListSerializer(ClassOverride.serializer())) ?: emptyList()
    suspend fun saveOverrides(list: List<ClassOverride>) = write("overrides.json", ListSerializer(ClassOverride.serializer()), list)

    suspend fun planner(): List<PlannedCourse> = read("planner.json", ListSerializer(PlannedCourse.serializer())) ?: emptyList()
    suspend fun savePlanner(list: List<PlannedCourse>) = write("planner.json", ListSerializer(PlannedCourse.serializer()), list)

    suspend fun studyLog(): List<com.shreenipane.orbit.core.model.StudySession> =
        read("study_log.json", ListSerializer(com.shreenipane.orbit.core.model.StudySession.serializer())) ?: emptyList()
    suspend fun saveStudyLog(list: List<com.shreenipane.orbit.core.model.StudySession>) =
        write("study_log.json", ListSerializer(com.shreenipane.orbit.core.model.StudySession.serializer()), list)

    suspend fun notes(): List<com.shreenipane.orbit.core.model.CourseNote> =
        read("notes.json", ListSerializer(com.shreenipane.orbit.core.model.CourseNote.serializer())) ?: emptyList()
    suspend fun saveNotes(list: List<com.shreenipane.orbit.core.model.CourseNote>) =
        write("notes.json", ListSerializer(com.shreenipane.orbit.core.model.CourseNote.serializer()), list)

    /** Where note photos live: app-private, not shared with other apps unless you share one. */
    fun notePhotoDir(): File = File(context.filesDir, "notes").apply { mkdirs() }

    suspend fun studyPlan(): List<com.shreenipane.orbit.core.model.StudyItem> =
        read("study_plan.json", ListSerializer(com.shreenipane.orbit.core.model.StudyItem.serializer())) ?: emptyList()
    suspend fun saveStudyPlan(list: List<com.shreenipane.orbit.core.model.StudyItem>) =
        write("study_plan.json", ListSerializer(com.shreenipane.orbit.core.model.StudyItem.serializer()), list)


    /** Attendance as of the last weekly report, to compare against. */
    suspend fun weeklyBaseline(): List<AttendanceRecord>? = read("weekly.json", ListSerializer(AttendanceRecord.serializer()))
    suspend fun saveWeeklyBaseline(list: List<AttendanceRecord>) = write("weekly.json", ListSerializer(AttendanceRecord.serializer()), list)

    // ---------- backup ----------

    @kotlinx.serialization.Serializable
    data class Backup(
        val version: Int = 1,
        val reminders: List<Reminder> = emptyList(),
        val friends: List<Friend> = emptyList(),
        val overrides: List<ClassOverride> = emptyList(),
        val planner: List<PlannedCourse> = emptyList(),
        val studyPlan: List<com.shreenipane.orbit.core.model.StudyItem> = emptyList(),
        val studyLog: List<com.shreenipane.orbit.core.model.StudySession> = emptyList(),
        /** Note text only; photos stay on the phone. */
        val notes: List<com.shreenipane.orbit.core.model.CourseNote> = emptyList(),
        val alias: String = "",
    )

    /** Everything that lives only on this phone, as JSON. VTOP data isn't included; it re-syncs. */
    suspend fun backupJson(): String = json.encodeToString(
        Backup.serializer(),
        Backup(reminders = reminders(), friends = friends(), overrides = overrides(), planner = planner(), studyPlan = studyPlan(), studyLog = studyLog(),
            notes = notes().map { it.copy(photo = null) }, alias = settings.alias),
    )

    /** Restores a backup, merging with what's already here (backup wins on clashes). */
    suspend fun restore(text: String): Backup {
        val b = json.decodeFromString(Backup.serializer(), text)
        saveReminders((reminders().filterNot { r -> b.reminders.any { it.id == r.id } } + b.reminders).sortedBy { it.dueAtMillis })
        saveFriends(friends().filterNot { f -> b.friends.any { it.name == f.name } } + b.friends)
        saveOverrides(overrides().filterNot { o -> b.overrides.any { it.id == o.id } } + b.overrides)
        savePlanner(planner().filterNot { p -> b.planner.any { it.id == p.id } } + b.planner)
        saveStudyPlan(studyPlan().filterNot { p -> b.studyPlan.any { it.id == p.id } } + b.studyPlan)
        saveStudyLog(studyLog().filterNot { p -> b.studyLog.any { it.id == p.id } } + b.studyLog)
        saveNotes(notes().filterNot { p -> b.notes.any { it.id == p.id } } + b.notes)
        if (b.alias.isNotBlank() && settings.alias.isBlank()) settings = settings.copy(alias = b.alias)
        return b
    }

    /** Writes the calendar export to a shareable file. */
    suspend fun icsFile(): File? = withContext(Dispatchers.IO) {
        val snap = cached() ?: return@withContext null
        val text = com.shreenipane.orbit.core.calc.IcsExport.build(snap, overrides(), java.time.LocalDate.now(), java.time.LocalDateTime.now())
        File(context.cacheDir, "orbit-${snap.semester?.id ?: "semester"}.ics").apply { writeText(text) }
    }

    // ---------- results ----------

    suspend fun gradeView(semId: String) = liveClient().gradeView(semId)

    suspend fun gradeStats(semId: String, courseId: String) = liveClient().gradeStats(semId, courseId)

    /** Grade ranges for a course; finds its detail id on the semester's grade view first. */
    suspend fun courseGradeStats(semId: String, code: String, type: String? = null): com.shreenipane.orbit.core.model.GradeStats? {
        val c = liveClient()
        val list = c.gradeView(semId)
        val course = list.firstOrNull { it.code == code && (type == null || it.type == type) } ?: list.firstOrNull { it.code == code }
        val id = course?.courseId?.takeIf { it.isNotBlank() } ?: return null
        return c.gradeStats(semId, id)
    }

    // ---------- VTOP ----------

    /** Keeps the last raw page of each kind, with the registration number masked, for Settings → debug export. */
    private fun recordRaw(name: String, html: String) {
        runCatching {
            val dir = File(context.filesDir, "debug").apply { mkdirs() }
            // Old pages go after a week; the report is for recent problems only.
            val week = System.currentTimeMillis() - 7 * 86_400_000L
            dir.listFiles().orEmpty().filter { it.lastModified() < week }.forEach { it.delete() }
            val safeName = name.replace(Regex("[^A-Za-z0-9_]"), "_").take(40)
            File(dir, "$safeName.html").writeText(com.shreenipane.orbit.core.Redact.page(html).take(400_000))
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
        // After registration VTOP lists a new semester; move to it even if an older one was picked in Settings.
        val newest = pickCurrent(semesters)
        val known = cached()?.semesters.orEmpty()
        val chosen = if (newest != null && known.isNotEmpty() && known.none { it.id == newest.id }) {
            pinnedSemesterId = null
            null
        } else semesterId
        val sem: Semester? = semesters.firstOrNull { it.id == chosen } ?: newest
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
            val calendar = async {
                optional<AcademicCalendar?>("Academic calendar", null) {
                    val raw = client.academicCalendar(sem.id, classGroupId, sem.name)
                    val fitted = com.shreenipane.orbit.core.calc.SemesterWindow.fit(raw, sem.name)
                    if (raw != null && fitted == null) warnings += "Academic calendar: VTOP sent another semester's calendar, so classes-left counts are off"
                    fitted
                }
            }
            val das = async { optional("Digital assignments", emptyList()) { client.digitalAssignments(sem.id) } }
            val previous = cached()
            Snapshot(
                regNo = session.regNo, campusId = campus.id, semester = sem, semesters = semesters,
                timetable = tt.await(), attendance = att.await(), marks = marks.await(),
                grades = grades.await(), exams = exams.await(),
                // Keep the last good calendar if VTOP's calendar menu is down this time.
                calendar = calendar.await()
                    ?: com.shreenipane.orbit.core.calc.SemesterWindow.fit(previous?.calendar?.takeIf { previous?.semester?.id == sem.id }, sem.name),
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
    suspend fun debugReport(): File {
        val dir = File(context.filesDir, "debug").apply { mkdirs() }
        val out = File(context.cacheDir, "orbit-debug.txt")
        val snap = cached()
        withContext(Dispatchers.IO) { out.writeText(buildString {
            append("Orbit debug report · campus ${campus.id}\n")
            if (snap != null) append(classCountSummary(snap))
            dir.listFiles().orEmpty().sortedBy { it.name }.forEach { f ->
                append("\n===== ${f.name} (${f.length()} bytes) =====\n")
                append(f.readText())
            }
        }) }
        return out
    }

    /**
     * What the classes-left counts are built from, in plain text: the
     * timetable's sessions, the calendar's remaining class days, and each
     * attendance course's match. No personal data beyond course details.
     */
    private fun classCountSummary(snap: Snapshot): String = buildString {
        val today = java.time.LocalDate.now()
        val now = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        val cal = snap.calendar
        append("\n===== class counts =====\n")
        append("semester: ${snap.semester?.name}\n")
        append("calendar: ${cal?.classGroupName ?: "none"} · ${cal?.days?.size ?: 0} days")
        append(" · last class day ${cal?.let { com.shreenipane.orbit.core.calc.ExamPlanner.semesterEnd(it)?.minusDays(1) }}\n")
        append("class days from today:\n")
        cal?.days?.filter { it.date >= today.toString() }?.take(40)?.forEach { d ->
            append("  ${d.date} ${d.kind}${d.dayOrder?.let { " order=$it" } ?: ""}${d.exam?.let { " exam=$it" } ?: ""} | ${d.label.take(120)}\n")
        }
        append("sessions:\n")
        snap.timetable.sessions.sortedWith(compareBy({ it.day }, { it.start })).forEach { x ->
            append("  day=${x.day} ${x.start}-${x.end} ${x.slot} ${x.courseCode} ${x.typeCode} lab=${x.isLab}\n")
        }
        append("slot grid: ${snap.timetable.slotGrid.size} slots\n")
        val left = com.shreenipane.orbit.core.calc.ExamPlanner.remainingAll(cal, snap.timetable.sessions, today, now)
        append("attendance:\n")
        snap.attendance.forEach { a ->
            val lab = com.shreenipane.orbit.core.calc.ExamPlanner.isLab(a)
            val n = snap.timetable.sessions.count { it.courseCode == a.courseCode && it.isLab == lab }
            append("  ${a.courseCode} '${a.courseType}' slot=${a.slot} lab=$lab sessions=$n left=${com.shreenipane.orbit.core.calc.ExamPlanner.remainingFrom(left, a)}\n")
        }
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
        File(context.cacheDir, "orbit-debug.txt").delete()
        file("snapshot.json").delete()
        file("campus.json").delete()
        prefs.edit().remove("semester").remove("expiry_notified").remove("class_group").apply()
    }
}
