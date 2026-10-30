package com.shreenipane.orbit.core

/**
 * Strips personal details and session secrets from VTOP pages before they're
 * kept for the debug report: registration numbers, e-mail addresses, phone
 * numbers, and CSRF tokens (which are tied to the live session).
 */
object Redact {
    private val REG_NO = Regex("\\b\\d{2}[A-Z]{3}\\d{4,5}\\b")
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val PHONE = Regex("(?<![\\d.])(?:\\+?91[\\s-]?)?[6-9]\\d{9}(?![\\d.])")
    private val CSRF_INPUT = Regex("(name=[\"']_csrf[\"'][^>]*?value=[\"'])[^\"']*", RegexOption.IGNORE_CASE)
    private val CSRF_INPUT_REV = Regex("(value=[\"'])[^\"']*([\"'][^>]*?name=[\"']_csrf[\"'])", RegexOption.IGNORE_CASE)
    private val CSRF_JS = Regex("(csrf\\w*\\s*[=:]\\s*[\"'])[^\"']*", RegexOption.IGNORE_CASE)

    fun page(html: String): String = html
        .replace(CSRF_INPUT) { it.groupValues[1] + "<CSRF>" }
        .replace(CSRF_INPUT_REV) { it.groupValues[1] + "<CSRF>" + it.groupValues[2] }
        .replace(CSRF_JS) { it.groupValues[1] + "<CSRF>" }
        .replace(EMAIL, "<EMAIL>")
        .replace(PHONE, "<PHONE>")
        .replace(REG_NO, "<REGNO>")
}
