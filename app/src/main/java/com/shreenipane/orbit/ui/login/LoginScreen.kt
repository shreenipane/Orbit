@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.shreenipane.orbit.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.shreenipane.orbit.data.Campus

/**
 * Sign-in happens on VTOP's own page inside a WebView, so the captcha, OTP and
 * any future changes to the login flow keep working, and the app never sees
 * or stores the password. Once VTOP lands on its logged-in page, the session
 * cookies are already in CookieManager for the data client to reuse.
 */
@Composable
fun LoginScreen(
    campus: Campus,
    onCampus: (Campus) -> Unit,
    onSignedIn: () -> Unit,
    onCancel: (() -> Unit)?,
    busy: Boolean,
) {
    var showWeb by remember { mutableStateOf(false) }

    if (busy) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Pulling your data from VTOP…")
            }
        }
        return
    }

    if (showWeb) {
        VtopWebLogin(campus, onDone = { showWeb = false; onSignedIn() }, onClose = { showWeb = false })
        return
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🪐", fontSize = 64.sp)
        Spacer(Modifier.height(12.dp))
        Text("Orbit", style = MaterialTheme.typography.displaySmall)
        Text(
            "Your timetable, attendance, marks and CGPA, pulled from VTOP and kept on your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Text("Campus", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Campus.entries.forEach { c ->
                FilterChip(selected = c == campus, onClick = { onCampus(c) }, label = { Text(c.label) })
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { showWeb = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Sign in with VTOP")
        }
        if (onCancel != null) {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Keep using saved data") }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "You'll sign in on VTOP's own page. Your password goes straight to VTOP; Orbit never sees it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VtopWebLogin(campus: Campus, onDone: () -> Unit, onClose: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var finished by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VTOP · ${campus.label}") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") } },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        // Some portals refuse the "; wv" WebView marker.
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                if (!finished && url != null && isLoggedInUrl(url)) {
                                    finished = true
                                    CookieManager.getInstance().flush()
                                    onDone()
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
                        }
                        loadUrl(campus.loginUrl)
                        webView = this
                    }
                },
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

/** VTOP lands on /vtop/content after a successful login (and after the OTP step). */
private fun isLoggedInUrl(url: String): Boolean {
    val path = android.net.Uri.parse(url).path ?: return false
    return path.endsWith("/vtop/content") || path.endsWith("/vtop/home")
}
