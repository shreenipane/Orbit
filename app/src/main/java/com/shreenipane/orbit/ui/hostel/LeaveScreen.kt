package com.shreenipane.orbit.ui.hostel

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shreenipane.orbit.data.Campus
import com.shreenipane.orbit.ui.components.BrutalButton
import com.shreenipane.orbit.ui.components.ListRow
import com.shreenipane.orbit.ui.components.OrbitScreen
import com.shreenipane.orbit.ui.components.section
import com.shreenipane.orbit.ui.theme.Pastel

/**
 * Hostel leave. Orbit doesn't fill VTOP's leave form itself: a guessed field
 * could file the wrong leave. It opens VTOP's own form inside the app, signed
 * in with your current session, and you fill and submit it there.
 */
@Composable
fun LeaveScreen() {
    var open by remember { mutableStateOf(false) }
    OrbitScreen(title = "Hostel leave") {
        section(footer = "Opens VTOP's own leave form in the app, already signed in. You fill and submit it there.") {
            Column(Modifier.padding(16.dp)) {
                Text("Apply for leave", style = MaterialTheme.typography.titleMedium, color = Pastel.Ink)
                Text("Outing, home town or emergency leave, and the status of your requests.",
                    style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
                BrutalButton("Open leave form", { open = true }, Modifier.fillMaxWidth(), fill = Pastel.Yellow)
            }
        }
        section {
            ListRow("If it asks you to sign in", "Your VTOP session ended. Sign in there, or in Orbit, and open it again.")
        }
    }
    if (open) VtopPage(title = "Hostel leave", findMenu = Regex("leave", RegexOption.IGNORE_CASE)) { open = false }
}

/**
 * A VTOP page Orbit doesn't read itself (faculty info, fees): opened inside
 * the app on your session, jumping to the menu matching [menu]. Orbit calls
 * no VTOP address of its own here, so it can't end your session.
 */
@Composable
fun VtopLinkScreen(title: String, description: String, menu: Regex) {
    var open by remember { mutableStateOf(false) }
    OrbitScreen(title = title) {
        section(footer = "Opens VTOP's own page in the app, already signed in.") {
            Column(Modifier.padding(16.dp)) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = Pastel.Ink,
                    modifier = Modifier.padding(bottom = 14.dp))
                BrutalButton("Open on VTOP", { open = true }, Modifier.fillMaxWidth())
            }
        }
    }
    if (open) VtopPage(title = title, findMenu = menu) { open = false }
}

/**
 * VTOP inside the app, on the shared session cookies. After the dashboard
 * loads it clicks the first menu link matching [findMenu], so you land on the
 * right page; it only ever clicks menu links, never submits anything.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VtopPage(title: String, findMenu: Regex?, onClose: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var jumped by remember { mutableStateOf(false) }
    val host = remember { Uri.parse(Campus.VELLORE.baseUrl).host.orEmpty() }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler {
            val w = webView
            if (w != null && w.canGoBack()) w.goBack() else onClose()
        }
        Surface(Modifier.fillMaxSize(), color = Pastel.Page, contentColor = Pastel.Ink) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close", tint = Pastel.Ink) }
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                CookieManager.getInstance().setAcceptCookie(true)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.userAgentString = settings.userAgentString.replace("; wv", "")
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { loading = true }
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        loading = false
                                        if (!jumped && findMenu != null && url?.contains("/vtop/content") == true) {
                                            jumped = true
                                            view?.evaluateJavascript(menuScript(findMenu.pattern), null)
                                        }
                                    }
                                    // Stay on VTOP.
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                                        request?.url?.scheme != "https" || request.url?.host != host
                                }
                                loadUrl("${Campus.VELLORE.baseUrl}/vtop/content")
                                webView = this
                            }
                        },
                    )
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
    }
}

/** Clicks the first VTOP menu link whose text matches [pattern] (never logout). The pattern is JSON-quoted. */
private fun menuScript(pattern: String): String {
    val p = org.json.JSONObject.quote(pattern)
    return """
        (function() {
          var re = new RegExp($p, 'i');
          var links = Array.prototype.slice.call(document.querySelectorAll('a, button'));
          var hit = links.filter(function(a) {
            var t = (a.innerText || a.textContent || '').trim();
            return t && re.test(t) && !/logout|sign ?out/i.test(t);
          })[0];
          if (hit) hit.click();
        })();
    """.trimIndent()
}
