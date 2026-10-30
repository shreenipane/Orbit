package com.shreenipane.orbit.data

import com.shreenipane.orbit.core.parse.LoginParsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Native VTOP sign-in, so the portal's website never has to be shown.
 * The user still reads and types the captcha (and the OTP when VTOP asks);
 * Orbit fills in the rest from the fingerprint locker.
 *
 * Cookies go into the same jar the data client uses, so a successful login
 * here is immediately usable for syncing.
 */
class VtopLogin(private val campus: Campus, cookieJar: WebViewCookieJar) {

    private val http = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var csrf: String? = null

    sealed class Challenge {
        /** A captcha to show; [image] is JPEG/PNG bytes. */
        class Captcha(val image: ByteArray) : Challenge()
        /** VTOP is using Google reCAPTCHA today; only the website can do that. */
        object NeedsWebsite : Challenge()
    }

    sealed class Result {
        object Success : Result()
        object WrongCaptcha : Result()
        object OtpRequired : Result()
        data class Failed(val message: String) : Result()
    }

    private data class Page(val url: String, val html: String)

    private fun get(path: String): Page {
        val req = Request.Builder().url(abs(path)).header("User-Agent", VtopClient.DEFAULT_UA).build()
        http.newCall(req).execute().use { return Page(it.request.url.toString(), it.body?.string().orEmpty()) }
    }

    private fun post(path: String, body: RequestBody): Page {
        val req = Request.Builder().url(abs(path)).post(body)
            .header("User-Agent", VtopClient.DEFAULT_UA)
            .header("Referer", campus.baseUrl + "/vtop/login")
            .build()
        http.newCall(req).execute().use { return Page(it.request.url.toString(), it.body?.string().orEmpty()) }
    }

    private fun abs(path: String) = if (path.startsWith("http")) path else campus.baseUrl + path

    /** Opens a fresh login session and returns the captcha to show. */
    suspend fun start(): Challenge = withContext(Dispatchers.IO) {
        val open = get("/vtop/open/page")
        csrf = LoginParsers.csrf(open.html)
        csrf?.let { post("/vtop/prelogin/setup", FormBody.Builder().add("_csrf", it).add("flag", "VTOP").build()) }
        get("/vtop/init/page")
        val login = get("/vtop/login")
        LoginParsers.csrf(login.html)?.let { csrf = it }
        val b64 = LoginParsers.captchaBase64(login.html)
        when {
            b64 != null -> Challenge.Captcha(Base64.getDecoder().decode(b64))
            LoginParsers.usesRecaptcha(login.html) -> Challenge.NeedsWebsite
            else -> throw VtopUnavailableException("VTOP's login page didn't load properly. Try again.")
        }
    }

    suspend fun submit(regNo: String, password: String, captcha: String): Result = withContext(Dispatchers.IO) {
        val token = csrf ?: throw VtopUnavailableException("Login expired. Reload the captcha.")
        val page = post(
            "/vtop/login",
            FormBody.Builder()
                .add("_csrf", token)
                .add("username", regNo.trim().uppercase())
                .add("password", password)
                .add("captchaStr", captcha.trim())
                .build(),
        )
        when (val o = LoginParsers.outcome(page.url, page.html)) {
            LoginParsers.Outcome.Success -> Result.Success
            LoginParsers.Outcome.WrongCaptcha -> Result.WrongCaptcha
            is LoginParsers.Outcome.OtpRequired -> { o.csrf?.let { csrf = it }; Result.OtpRequired }
            is LoginParsers.Outcome.Failed -> Result.Failed(o.message)
        }
    }

    suspend fun verifyOtp(otp: String): Result = withContext(Dispatchers.IO) {
        val token = csrf ?: throw VtopUnavailableException("Login expired. Start again.")
        val page = post(
            "/vtop/validateSecurityOtp",
            MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("otpCode", otp.trim())
                .addFormDataPart("_csrf", token)
                .build(),
        )
        val reply = LoginParsers.otpReply(page.html)
        when (reply.status) {
            "SUCCESS" -> {
                reply.redirectUrl?.let { get(it) }
                Result.Success
            }
            "INVALID" -> Result.Failed("That OTP isn't right.")
            "EXPIRED" -> Result.Failed("The OTP expired. Start again for a new one.")
            else -> Result.Failed(reply.message.ifBlank { "VTOP didn't accept the OTP." })
        }
    }
}
