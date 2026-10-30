@file:OptIn(ExperimentalMaterial3Api::class)

package com.shreenipane.orbit.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import com.shreenipane.orbit.data.Campus
import com.shreenipane.orbit.data.CredentialVault
import com.shreenipane.orbit.ui.components.Segmented
import org.json.JSONObject

/**
 * Sign-in happens on VTOP's own page in a WebView, so the captcha and OTP
 * keep working. With a saved login, a fingerprint unlocks the locker and
 * Orbit fills in your registration number and password; you only type the
 * captcha. After a manual sign-in, Orbit offers to save the login.
 */
@Composable
fun LoginScreen(
    campus: Campus,
    onCampus: (Campus) -> Unit,
    onSignedIn: () -> Unit,
    onCancel: (() -> Unit)?,
    busy: Boolean,
    vault: CredentialVault,
    onLockerChanged: () -> Unit,
) {
    val activity = LocalContext.current as FragmentActivity
    var web by remember { mutableStateOf<CredentialVault.Credentials?>(null) }
    var showWeb by remember { mutableStateOf(false) }
    var offerSave by remember { mutableStateOf<CredentialVault.Credentials?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val canFingerprint = remember { Biometrics.available(activity) }

    if (busy && offerSave == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(strokeWidth = 3.dp)
                Spacer(Modifier.height(16.dp))
                Text("Syncing with VTOP", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    offerSave?.let { creds ->
        AlertDialog(
            onDismissRequest = { offerSave = null; onSignedIn() },
            icon = { Icon(Icons.Rounded.Fingerprint, null) },
            title = { Text("Save login to the locker?") },
            text = {
                Text("Next time, sign in with your fingerprint. Your password is encrypted on this phone with a key only your fingerprint can unlock, and stays until you sign out.")
            },
            confirmButton = {
                TextButton(onClick = {
                    Biometrics.unlock(
                        activity, vault.encryptCipher(), "Save VTOP login", "Confirm with your fingerprint",
                        onSuccess = { c -> vault.save(c, creds); onLockerChanged(); offerSave = null; onSignedIn() },
                        onError = { msg -> error = msg; offerSave = null; onSignedIn() },
                    )
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { offerSave = null; onSignedIn() }) { Text("Not now") } },
        )
    }

    if (showWeb) {
        VtopWebLogin(
            campus = campus,
            fill = web,
            onDone = { typed ->
                showWeb = false
                val shouldOffer = canFingerprint && typed != null && typed.password.isNotBlank() &&
                    (vault.savedRegNo == null || vault.savedRegNo != typed.regNo.uppercase() || web == null)
                if (shouldOffer && web == null) offerSave = typed else onSignedIn()
                web = null
            },
            onClose = { showWeb = false; web = null },
        )
        return
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Orbit", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Timetable, attendance, marks, exams and deadlines from VTOP, on your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(36.dp))
        Text("CAMPUS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        val campuses = Campus.entries
        Segmented(
            options = campuses.map { if (it == Campus.AP) "AP" else it.label },
            selected = campuses.indexOf(campus),
            onSelect = { onCampus(campuses[it]) },
        )
        Spacer(Modifier.height(28.dp))

        val saved = vault.savedRegNo
        if (saved != null && canFingerprint) {
            Button(
                onClick = {
                    val cipher = vault.decryptCipher()
                    if (cipher == null) {
                        error = "Your saved login was reset because the phone's fingerprints changed. Sign in once more to save it again."
                        onLockerChanged()
                    } else {
                        Biometrics.unlock(
                            activity, cipher, "Sign in to VTOP", saved,
                            onSuccess = { c ->
                                val creds = vault.open(c)
                                if (creds == null) error = "Couldn't open the locker. Sign in with your password." else { web = creds; showWeb = true }
                            },
                            onError = { error = it },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Rounded.Fingerprint, null)
                Spacer(Modifier.padding(4.dp))
                Text("Sign in with fingerprint")
            }
            Text(
                "Saved login: $saved",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { web = null; showWeb = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.medium) {
                Text("Use password instead")
            }
        } else {
            Button(onClick = { web = null; showWeb = true }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium) {
                Text("Sign in with VTOP")
            }
        }
        if (onCancel != null) {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Keep using saved data") }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "You sign in on VTOP's own page and type the captcha yourself. Orbit never sends your password anywhere except VTOP.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Receives what the user typed on VTOP's login form, for the save-to-locker offer. */
private class LoginBridge {
    @Volatile var typed: CredentialVault.Credentials? = null

    @JavascriptInterface
    fun capture(user: String?, pass: String?) {
        if (!user.isNullOrBlank() && !pass.isNullOrBlank()) typed = CredentialVault.Credentials(user.trim(), pass)
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun VtopWebLogin(
    campus: Campus,
    fill: CredentialVault.Credentials?,
    onDone: (CredentialVault.Credentials?) -> Unit,
    onClose: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var finished by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val bridge = remember { LoginBridge() }
    val vtopHost = remember { Uri.parse(campus.baseUrl).host.orEmpty() }

    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VTOP · ${campus.label}", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
                        settings.saveFormData = false
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")
                        addJavascriptInterface(bridge, "OrbitBridge")
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                if (url == null || Uri.parse(url).host != vtopHost) return
                                view?.evaluateJavascript(loginScript(fill), null)
                                if (!finished && isLoggedInUrl(url)) {
                                    finished = true
                                    CookieManager.getInstance().flush()
                                    onDone(bridge.typed)
                                }
                            }

                            // Stay on VTOP: the bridge must never be exposed to other sites.
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val host = request?.url?.host ?: return true
                                return host != vtopHost && !host.endsWith("google.com") && !host.endsWith("gstatic.com")
                            }
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

/**
 * Runs on every VTOP page. On the login form it fills saved credentials (if
 * unlocked) and focuses the captcha, and it reports what was typed when the
 * form is submitted. Values are JSON-quoted so they can't break out of the script.
 */
private fun loginScript(fill: CredentialVault.Credentials?): String {
    val user = JSONObject.quote(fill?.regNo ?: "")
    val pass = JSONObject.quote(fill?.password ?: "")
    return """
        (function() {
          var u = document.querySelector('input[name="username"], #username');
          var p = document.querySelector('input[name="password"], #password');
          if (!u || !p) return;
          var U = $user, P = $pass;
          function set(el, v) {
            var d = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
            d.set.call(el, v);
            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
          }
          if (U && !u.value) { set(u, U); set(p, P); }
          var c = document.querySelector('#captchaStr, input[name="captchaStr"]');
          if (U && c) c.focus();
          function report() { try { OrbitBridge.capture(u.value, p.value); } catch (e) {} }
          if (!window.__orbitHooked) {
            window.__orbitHooked = true;
            document.addEventListener('submit', report, true);
            document.addEventListener('click', function(e) {
              var t = e.target && e.target.closest ? e.target.closest('button, input[type=submit]') : null;
              if (t) report();
            }, true);
            document.addEventListener('keydown', function(e) { if (e.key === 'Enter') report(); }, true);
          }
        })();
    """.trimIndent()
}

/** VTOP lands on /vtop/content after a successful login (and after the OTP step). */
private fun isLoggedInUrl(url: String): Boolean {
    val path = Uri.parse(url).path ?: return false
    return path.endsWith("/vtop/content") || path.endsWith("/vtop/home")
}
