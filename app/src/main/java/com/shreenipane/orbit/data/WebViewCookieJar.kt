package com.shreenipane.orbit.data

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Shares one cookie store between the login WebView and OkHttp. The user signs
 * in on VTOP's own page (captcha, OTP and all); OkHttp then reuses that
 * session to fetch data. CookieManager persists to disk on its own.
 */
class WebViewCookieJar(private val manager: CookieManager = CookieManager.getInstance()) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val key = url.toString()
        cookies.forEach { manager.setCookie(key, it.toString()) }
        manager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = manager.getCookie(url.toString()) ?: return emptyList()
        return header.split(";").mapNotNull { Cookie.parse(url, it.trim()) }
    }

    fun clear() {
        manager.removeAllCookies(null)
        manager.flush()
    }
}
