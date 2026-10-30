package com.shreenipane.orbit.data

import com.shreenipane.orbit.core.model.AcademicCalendar
import com.shreenipane.orbit.core.model.GradeResult
import com.shreenipane.orbit.core.model.GradeStats
import com.shreenipane.orbit.core.model.AttendanceRecord
import com.shreenipane.orbit.core.model.CourseMarks
import com.shreenipane.orbit.core.model.DigitalAssignment
import com.shreenipane.orbit.core.model.Exam
import com.shreenipane.orbit.core.model.GradeHistory
import com.shreenipane.orbit.core.model.Semester
import com.shreenipane.orbit.core.model.Timetable
import com.shreenipane.orbit.core.parse.CalendarParsers
import com.shreenipane.orbit.core.parse.CampusParsers
import com.shreenipane.orbit.core.parse.VtopParsers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class SessionExpiredException : Exception("VTOP session expired. Sign in again.")
class VtopUnavailableException(msg: String) : Exception(msg)

/**
 * Thin client over VTOP's form endpoints. Data endpoints want `_csrf`,
 * `authorizedID` and `x` (a GMT timestamp); page shells want `verifyMenu`
 * and `nocache` instead.
 */
class VtopClient(
    private val campus: Campus,
    cookieJar: WebViewCookieJar,
    private val userAgent: String = DEFAULT_UA,
    /** Receives raw pages (name, html) for the debug export in Settings. */
    private val onRaw: (String, String) -> Unit = { _, _ -> },
) {
    // Built from one shared client so every VtopClient (sync, keep-alive, lookups) reuses the same
    // connection pool and threads instead of opening its own.
    private val http = SharedHttp.base.newBuilder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var session: VtopParsers.SessionInfo? = null

    val regNo: String? get() = session?.regNo

    /** Picks up CSRF + registration number from the logged-in content page. */
    suspend fun attach(): VtopParsers.SessionInfo = withContext(Dispatchers.IO) {
        val html = get("/vtop/content")
        // Logged out, VTOP serves the landing/login page here, which has no authorizedIDX.
        VtopParsers.session(html)?.also { session = it } ?: throw SessionExpiredException()
    }

    suspend fun semesters(): List<Semester> =
        VtopParsers.semesters(postShell("/vtop/academics/common/StudentTimeTable"))

    suspend fun timetable(semId: String): Timetable =
        VtopParsers.timetable(postData("/vtop/processViewTimeTable", "semesterSubId" to semId))

    suspend fun attendance(semId: String): List<AttendanceRecord> =
        VtopParsers.attendance(postData("/vtop/processViewStudentAttendance", "semesterSubId" to semId))

    suspend fun marks(semId: String): List<CourseMarks> =
        VtopParsers.marks(postData("/vtop/examinations/doStudentMarkView", "semesterSubId" to semId))

    suspend fun examSchedule(semId: String): List<Exam> =
        VtopParsers.examSchedule(postData("/vtop/examinations/doSearchExamScheduleForStudent", "semesterSubId" to semId))

    /**
     * The semester's academic calendar for one class group (default: General
     * (Flexible)), every month fetched and flattened.
     */
    suspend fun academicCalendar(semId: String, preferredGroup: String?, semesterName: String? = null): AcademicCalendar? {
        val groupsHtml = postData("/vtop/getDateForSemesterPreview", "paramReturnId" to "getDateForSemesterPreview", "semSubId" to semId)
        val group = CalendarParsers.pickClassGroup(CalendarParsers.classGroups(groupsHtml), preferredGroup) ?: return null
        val monthsHtml = postData(
            "/vtop/getListForSemester",
            "paramReturnId" to "getListForSemester", "semSubId" to semId, "classGroupId" to group.id,
        )
        val listed = CalendarParsers.months(monthsHtml).ifEmpty { CalendarParsers.months(groupsHtml) }
        // VTOP's list can stop at the current month; ask for every month the semester spans as well.
        val months = (listed + semesterName?.let { com.shreenipane.orbit.core.calc.SemesterWindow.monthKeys(it) }.orEmpty()).distinct()
        val days = coroutineScope {
            months.map { m ->
                async {
                    try {
                        CalendarParsers.month(
                            postData("/vtop/processViewCalendar", "calDate" to m, "semSubId" to semId, "classGroupId" to group.id)
                                .also { if (m.contains("-NOV-")) onRaw("calendar_nov", it) },
                            m,
                            group.name,
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: SessionExpiredException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList() // one bad month (outside the semester, say) shouldn't sink the rest
                    }
                }
            }.awaitAll().flatten()
        }
        return AcademicCalendar(group.id, group.name, days.distinctBy { it.date }.sortedBy { it.date })
    }

    /**
     * Every DA of the semester, across all courses. VTOP's course list is
     * fetched form-encoded first and multipart as a fallback, because the
     * campuses differ in which they accept.
     */
    suspend fun digitalAssignments(semId: String): List<DigitalAssignment> {
        val s = requireSession()
        var html = try {
            postData("/vtop/examinations/doDigitalAssignment", "semesterSubId" to semId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            "" // fall through to the multipart form below
        }
        onRaw("da_courses", html)
        var courses = CalendarParsers.daCourses(html)
        if (courses.isEmpty()) {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("authorizedID", s.regNo)
                .addFormDataPart("semesterSubId", semId)
                .addFormDataPart("_csrf", s.csrf)
                .build()
            html = send("/vtop/examinations/doDigitalAssignment", body)
            onRaw("da_courses_multipart", html)
            courses = CalendarParsers.daCourses(html)
        }
        if (courses.isEmpty()) {
            if (VtopParsers.menuUnavailable(html)) throw VtopUnavailableException("VTOP's DA menu is switched off right now.")
            return emptyList()
        }
        return coroutineScope {
            courses.mapIndexed { i, c ->
                async {
                    val page = postData("/vtop/examinations/processDigitalAssignment", "classId" to c.classId)
                    if (i == 0) onRaw("da_list_first_course", page)
                    CalendarParsers.daList(page, c)
                }
            }.awaitAll().flatten()
        }
    }

    /**
     * Uploads a DA file. VTOP needs the course's DA page opened first, then a
     * "prepare upload" call that returns code/opt tokens, then the multipart
     * upload. It may mail an OTP, confirmed with [verifyDaOtp].
     */
    suspend fun uploadDa(da: DigitalAssignment, fileName: String, bytes: ByteArray, mime: String): CalendarParsers.UploadResult {
        val s = requireSession()
        postData("/vtop/examinations/processDigitalAssignment", "classId" to da.classId)
        val prep = postData("/vtop/examinations/processDigitalAssignmentUpload", "classId" to da.classId, "mode" to da.mcode)
        onRaw("da_upload_prepare", prep)
        val tokens = CalendarParsers.uploadTokens(prep)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            addFormDataPart("authorizedID", s.regNo)
            tokens.forEach { (code, opt) -> addFormDataPart("code", code); addFormDataPart("opt", opt) }
            addFormDataPart("studDaUpload", fileName, bytes.toRequestBody(mime.toMediaType()))
            addFormDataPart("_csrf", s.csrf)
            addFormDataPart("classId", da.classId)
            addFormDataPart("mCode", da.mcode)
        }.build()
        val html = send("/vtop/examinations/doDAssignmentUploadMethod", body)
        onRaw("da_upload_result", html)
        return CalendarParsers.uploadResult(html)
    }

    suspend fun verifyDaOtp(otp: String): CalendarParsers.UploadResult {
        val s = requireSession()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("authorizedID", s.regNo)
            .addFormDataPart("otpEmail", otp.trim())
            .addFormDataPart("_csrf", s.csrf)
            .build()
        val html = send("/vtop/examinations/doDAssignmentOtpUpload", body)
        onRaw("da_upload_otp_result", html)
        return CalendarParsers.uploadResult(html)
    }

    /** Touches the session so VTOP's idle timeout doesn't end it. True if still signed in. */
    suspend fun keepAlive(): Boolean = withContext(Dispatchers.IO) {
        runCatching { VtopParsers.session(get("/vtop/content")) != null }.getOrDefault(false)
    }

    // ---------- campus pages ----------

    /**
     * Posts form-encoded first and multipart second: VTOP campuses differ in
     * which one a page accepts. Returns the first response that isn't the
     * "menu not available" modal.
     */
    private suspend fun postEither(name: String, path: String, vararg fields: Pair<String, String>): String {
        val s = requireSession()
        val first = try {
            postData(path, *fields)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (first != null && first.contains("<t")) { onRaw(name, first); return first }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            addFormDataPart("authorizedID", s.regNo)
            fields.forEach { (k, v) -> addFormDataPart(k, v) }
            addFormDataPart("_csrf", s.csrf)
        }.build()
        return send(path, body).also { onRaw("${name}_multipart", it) }
    }

    suspend fun gradeView(semId: String): List<GradeResult> =
        CampusParsers.gradeView(postEither("grade_view", "/vtop/examinations/examGradeView/doStudentGradeView", "semesterSubId" to semId))

    /** Grade ranges, mean and SD for one course of a semester's results. */
    suspend fun gradeStats(semId: String, courseId: String): GradeStats? =
        CampusParsers.gradeStats(
            postEither("grade_detail", "/vtop/examinations/examGradeView/getGradeViewDetails", "semesterSubId" to semId, "courseId" to courseId),
        )

    suspend fun gradeHistory(): GradeHistory =
        VtopParsers.gradeHistory(postShell("/vtop/examinations/examGradeView/StudentGradeHistory"))

    // ---------- plumbing ----------

    private fun requireSession() = session ?: throw SessionExpiredException()

    private suspend fun postData(path: String, vararg extra: Pair<String, String>): String {
        val s = requireSession()
        return post(path, listOf("_csrf" to s.csrf, "authorizedID" to s.regNo, "x" to gmtNow()) + extra)
    }

    private suspend fun postShell(path: String): String {
        val s = requireSession()
        return post(path, listOf(
            "verifyMenu" to "true", "authorizedID" to s.regNo, "_csrf" to s.csrf,
            "nocache" to System.currentTimeMillis().toString(),
        ))
    }

    private suspend fun post(path: String, fields: List<Pair<String, String>>): String =
        send(path, FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build())

    private suspend fun send(path: String, body: RequestBody): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(campus.baseUrl + path).post(body)
            .header("User-Agent", userAgent)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", campus.baseUrl + "/vtop/content")
            .build()
        http.newCall(req).execute().use { res ->
            // A dead session/CSRF token makes Spring's filter answer 404 before routing, but so does a
            // page this campus doesn't have. Check the session itself before calling it a logout.
            if (res.code == 404 || res.code == 401 || res.code == 403) {
                if (stillSignedIn()) throw VtopUnavailableException("VTOP doesn't have this page (HTTP ${res.code}).")
                throw SessionExpiredException()
            }
            val html = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw VtopUnavailableException("VTOP returned HTTP ${res.code}")
            if (VtopParsers.looksLoggedOut(html)) throw SessionExpiredException()
            if (VtopParsers.menuUnavailable(html)) throw VtopUnavailableException("VTOP says this menu isn't available right now.")
            html
        }
    }

    /** True when VTOP's content page still shows a logged-in session. */
    private fun stillSignedIn(): Boolean = runCatching { VtopParsers.session(get("/vtop/content")) != null }.getOrDefault(false)

    private fun get(path: String): String {
        val req = Request.Builder().url(campus.baseUrl + path).header("User-Agent", userAgent).build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw VtopUnavailableException("VTOP returned HTTP ${res.code}")
            return res.body?.string().orEmpty()
        }
    }

    private fun gmtNow(): String =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("GMT") }
            .format(Date())

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
