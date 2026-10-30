package com.shreenipane.orbit.data

import com.shreenipane.orbit.core.model.AttendanceRecord
import com.shreenipane.orbit.core.model.CourseMarks
import com.shreenipane.orbit.core.model.Exam
import com.shreenipane.orbit.core.model.GradeHistory
import com.shreenipane.orbit.core.model.Semester
import com.shreenipane.orbit.core.model.Timetable
import com.shreenipane.orbit.core.parse.VtopParsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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
) {
    private val http = OkHttpClient.Builder()
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

    private suspend fun post(path: String, fields: List<Pair<String, String>>): String = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        val req = Request.Builder().url(campus.baseUrl + path).post(body)
            .header("User-Agent", userAgent)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", campus.baseUrl + "/vtop/content")
            .build()
        http.newCall(req).execute().use { res ->
            // A dead session/CSRF token makes Spring's filter answer 404 before routing.
            if (res.code == 404 || res.code == 401 || res.code == 403) throw SessionExpiredException()
            val html = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw VtopUnavailableException("VTOP returned HTTP ${res.code}")
            if (VtopParsers.looksLoggedOut(html)) throw SessionExpiredException()
            if (VtopParsers.menuUnavailable(html)) throw VtopUnavailableException("VTOP says this menu isn't available right now.")
            html
        }
    }

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
