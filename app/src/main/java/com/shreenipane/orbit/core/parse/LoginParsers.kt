package com.shreenipane.orbit.core.parse

import org.jsoup.Jsoup

/**
 * Pieces of VTOP's login flow, so Orbit can sign in natively:
 * /vtop/open/page (CSRF) → POST /vtop/prelogin/setup → GET /vtop/login
 * (captcha image) → POST /vtop/login → optional OTP → /vtop/content.
 */
object LoginParsers {

    fun csrf(html: String): String? =
        Jsoup.parse(html).selectFirst("input[name=_csrf]")?.attr("value")?.takeIf { it.isNotBlank() }

    /** The captcha is inlined as a base64 image on the login page. */
    fun captchaBase64(html: String): String? =
        Regex("data:image/(?:jpeg|jpg|png);base64,([A-Za-z0-9+/=\\s]+)").find(html)?.groupValues?.get(1)?.replace(Regex("\\s"), "")

    /** VTOP sometimes switches to Google reCAPTCHA, which can only be solved in a browser. */
    fun usesRecaptcha(html: String): Boolean =
        html.contains("g-recaptcha") || html.contains("recaptcha/api.js") || html.contains("grecaptcha")

    fun hasOtpForm(html: String): Boolean = Jsoup.parse(html).selectFirst("form#securityOtpForm") != null

    sealed class Outcome {
        object Success : Outcome()
        object WrongCaptcha : Outcome()
        data class OtpRequired(val csrf: String?) : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    private fun errorText(html: String): String? {
        val doc = Jsoup.parse(html)
        return listOfNotNull(
            doc.selectFirst("strong")?.text(),
            doc.selectFirst(".alert, .error, #errorMsg, .text-danger")?.text(),
        ).map { it.trim() }.firstOrNull { it.isNotBlank() }
    }

    /** Reads what POST /vtop/login led to, given the final URL after redirects and its HTML. */
    fun outcome(finalUrl: String, html: String): Outcome {
        val err = errorText(html)
        return when {
            html.contains("Invalid Captcha", ignoreCase = true) -> Outcome.WrongCaptcha
            hasOtpForm(html) -> Outcome.OtpRequired(csrf(html))
            VtopParsers.session(html) != null -> Outcome.Success
            finalUrl.contains("error") -> Outcome.Failed(err ?: "VTOP rejected the login.")
            captchaBase64(html) != null || html.contains("captchaStr") ->
                Outcome.Failed(err ?: "VTOP sent the login page back. Check your password.")
            else -> Outcome.Success // e.g. a redirect page; the next step verifies the session
        }
    }

    data class OtpReply(val status: String, val message: String, val redirectUrl: String?)

    /** validateSecurityOtp answers with a small JSON object. */
    fun otpReply(json: String): OtpReply {
        fun field(name: String) = Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)
        return OtpReply(field("status") ?: "UNKNOWN", field("message").orEmpty(), field("redirectUrl"))
    }
}
