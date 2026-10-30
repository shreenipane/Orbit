@file:OptIn(ExperimentalMaterial3Api::class)

package com.shreenipane.orbit.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import com.shreenipane.orbit.data.Campus
import com.shreenipane.orbit.data.CredentialVault
import com.shreenipane.orbit.data.VtopLogin
import com.shreenipane.orbit.ui.components.BrutalButton
import com.shreenipane.orbit.ui.components.Segmented
import com.shreenipane.orbit.ui.components.brutal
import com.shreenipane.orbit.ui.theme.Bad
import com.shreenipane.orbit.ui.theme.Pastel
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class Mode { Home, Native, Web }

/**
 * Sign-in without the VTOP website: Orbit runs VTOP's own login steps in the
 * background and shows only the captcha (and the OTP, when VTOP asks). With a
 * saved login, the fingerprint prompt opens straight away and fills in the
 * registration number and password. The website is a fallback for days when
 * VTOP switches to Google reCAPTCHA.
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
    newLogin: () -> VtopLogin,
) {
    val activity = LocalContext.current as FragmentActivity
    var mode by remember { mutableStateOf(Mode.Home) }
    var creds by remember { mutableStateOf<CredentialVault.Credentials?>(null) }
    var offerSave by remember { mutableStateOf<CredentialVault.Credentials?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val canFingerprint = remember { Biometrics.available(activity) }
    val saved = vault.savedRegNo

    fun unlockWithFingerprint() {
        val cipher = vault.decryptCipher()
        if (cipher == null) {
            error = "Fingerprints changed, so sign in once more to save your login."
            onLockerChanged()
            return
        }
        Biometrics.unlock(
            activity, cipher, "Sign in to VTOP", saved ?: "",
            onSuccess = { c ->
                val opened = vault.open(c)
                if (opened == null) error = "Couldn't open the locker. Sign in with your password."
                else { creds = opened; mode = Mode.Native }
            },
            onError = { error = it },
        )
    }

    // With a saved login, go straight to the fingerprint prompt.
    LaunchedEffect(Unit) {
        if (saved != null && canFingerprint && !busy) unlockWithFingerprint()
    }

    fun finished(typed: CredentialVault.Credentials?) {
        val fromLocker = creds != null && typed?.password == creds?.password
        if (canFingerprint && typed != null && typed.password.isNotBlank() && !fromLocker) offerSave = typed else onSignedIn()
        mode = Mode.Home
    }

    if (busy && offerSave == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(strokeWidth = 3.dp, color = Pastel.Ink)
                Spacer(Modifier.height(16.dp))
                Text("Syncing with VTOP", style = MaterialTheme.typography.headlineSmall)
            }
        }
        return
    }

    offerSave?.let { typed ->
        AlertDialog(
            onDismissRequest = { offerSave = null; onSignedIn() },
            icon = { Icon(Icons.Rounded.Fingerprint, null) },
            title = { Text("Save login to the locker?") },
            text = {
                Text("Next time it's just your fingerprint and the captcha.")
            },
            confirmButton = {
                TextButton(onClick = {
                    Biometrics.unlock(
                        activity, vault.encryptCipher(), "Save VTOP login", "Confirm with your fingerprint",
                        onSuccess = { c -> vault.save(c, typed); onLockerChanged(); offerSave = null; onSignedIn() },
                        onError = { msg -> error = msg; offerSave = null; onSignedIn() },
                    )
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { offerSave = null; onSignedIn() }) { Text("Not now") } },
        )
    }

    when (mode) {
        Mode.Native -> {
            NativeLogin(
                campus = campus,
                prefill = creds,
                newLogin = newLogin,
                onSuccess = { typed -> finished(typed) },
                onUseWebsite = { mode = Mode.Web },
                onClose = { mode = Mode.Home; creds = null },
            )
            return
        }
        Mode.Web -> {
            VtopWebLogin(
                campus = campus,
                fill = creds,
                onDone = { typed -> finished(typed) },
                onClose = { mode = Mode.Home },
            )
            return
        }
        Mode.Home -> Unit
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(start = 24.dp, end = 28.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Orbit", style = MaterialTheme.typography.displayMedium.copy(fontSize = 56.sp))
        Spacer(Modifier.height(6.dp))
        Text(
            "Your VTOP, made simple. For VIT Vellore.",
            style = MaterialTheme.typography.bodyLarge,
            color = Pastel.InkMuted,
        )
        Spacer(Modifier.height(36.dp))

        if (saved != null && canFingerprint) {
            BrutalButton(
                text = "Sign in with fingerprint",
                onClick = { unlockWithFingerprint() },
                modifier = Modifier.fillMaxWidth(),
                fill = Pastel.Green,
                leading = { Icon(Icons.Rounded.Fingerprint, null, tint = Pastel.Ink) },
            )
            Text("Saved login: $saved", style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted,
                modifier = Modifier.padding(top = 10.dp, start = 2.dp))
            Spacer(Modifier.height(14.dp))
            BrutalButton("Use password instead", { creds = null; mode = Mode.Native }, Modifier.fillMaxWidth(), fill = Pastel.Card)
        } else {
            BrutalButton("Sign in", { creds = null; mode = Mode.Native }, Modifier.fillMaxWidth(), fill = Pastel.Yellow)
        }
        if (onCancel != null) {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Keep using saved data", color = Pastel.Ink)
            }
        }
        error?.let {
            Text(it, color = Bad, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Your password only goes to VTOP.",
            style = MaterialTheme.typography.bodySmall,
            color = Pastel.InkMuted,
        )
    }
}

@Composable
private fun NativeLogin(
    campus: Campus,
    prefill: CredentialVault.Credentials?,
    newLogin: () -> VtopLogin,
    onSuccess: (CredentialVault.Credentials) -> Unit,
    onUseWebsite: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val login = remember { newLogin() }
    var reg by remember { mutableStateOf(prefill?.regNo.orEmpty()) }
    var pass by remember { mutableStateOf(prefill?.password.orEmpty()) }
    var captcha by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var needOtp by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val captchaFocus = remember { FocusRequester() }

    BackHandler(onBack = onClose)

    LaunchedEffect(reloadKey) {
        working = true
        image = null
        captcha = ""
        try {
            when (val ch = login.start()) {
                is VtopLogin.Challenge.Captcha -> image = BitmapFactory.decodeByteArray(ch.image, 0, ch.image.size)
                VtopLogin.Challenge.NeedsWebsite -> { onUseWebsite(); return@LaunchedEffect }
            }
        } catch (e: Exception) {
            error = e.message ?: "Couldn't reach VTOP."
        }
        working = false
        if (prefill != null) runCatching { captchaFocus.requestFocus() }
    }

    fun submit() {
        if (working) return
        error = null
        working = true
        scope.launch {
            try {
                val result = if (needOtp) login.verifyOtp(otp) else login.submit(reg, pass, captcha)
                when (result) {
                    VtopLogin.Result.Success -> onSuccess(CredentialVault.Credentials(reg.trim().uppercase(), pass))
                    VtopLogin.Result.WrongCaptcha -> { error = "Captcha didn't match. Here's a new one."; reloadKey++ }
                    VtopLogin.Result.OtpRequired -> { needOtp = true; error = null }
                    is VtopLogin.Result.Failed -> { error = result.message; if (!needOtp) reloadKey++ }
                }
            } catch (e: Exception) {
                error = e.message ?: "Couldn't reach VTOP."
            }
            working = false
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Pastel.Ink, unfocusedBorderColor = Pastel.Ink,
        focusedContainerColor = Pastel.Card, unfocusedContainerColor = Pastel.Card,
        focusedLabelColor = Pastel.Ink, cursorColor = Pastel.Ink,
    )

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 28.dp, top = 8.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sign in", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close", tint = Pastel.Ink) }
        }
        Text("VTOP · ${campus.label}", style = MaterialTheme.typography.labelLarge, color = Pastel.InkMuted)
        Spacer(Modifier.height(20.dp))

        if (!needOtp) {
            OutlinedTextField(
                reg, { reg = it }, label = { Text("Registration number") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                colors = fieldColors, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                pass, { pass = it }, label = { Text("VTOP password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                colors = fieldColors, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Text("Captcha", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).height(64.dp).brutal(androidx.compose.ui.graphics.Color.White, radius = 12.dp, shadow = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = image
                    if (bmp != null) {
                        Image(
                            bmp.asImageBitmap(), contentDescription = "Captcha",
                            contentScale = ContentScale.Fit, filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                        )
                    } else {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Pastel.Ink)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(52.dp).brutal(Pastel.Sky, radius = 12.dp, shadow = 3.dp), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { reloadKey++ }) { Icon(Icons.Rounded.Refresh, "New captcha", tint = Pastel.Ink) }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                captcha, { captcha = it.trim() }, label = { Text("Type the captcha") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                colors = fieldColors, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().focusRequester(captchaFocus),
            )
        } else {
            Text("VTOP sent an OTP to your registered e-mail.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                otp, { otp = it.filter(Char::isLetterOrDigit).take(8) }, label = { Text("OTP") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                colors = fieldColors, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            )
        }

        error?.let { Text(it, color = Bad, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = Pastel.Ink, trackColor = Pastel.CardTeal)
        Spacer(Modifier.height(20.dp))
        BrutalButton(
            text = if (needOtp) "Verify OTP" else "Sign in",
            onClick = { submit() },
            modifier = Modifier.fillMaxWidth(),
            fill = Pastel.Yellow,
            enabled = !working && (if (needOtp) otp.isNotBlank() else reg.isNotBlank() && pass.isNotBlank() && captcha.isNotBlank()),
        )
        TextButton(onClick = onUseWebsite, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Having trouble? Use the VTOP website", color = Pastel.InkMuted)
        }
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
