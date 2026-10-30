package com.shreenipane.orbit

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shreenipane.orbit.ui.AppViewModel
import com.shreenipane.orbit.ui.LockScreen
import com.shreenipane.orbit.ui.OrbitRoot
import com.shreenipane.orbit.ui.theme.OrbitTheme

/** FragmentActivity (not plain ComponentActivity) because BiometricPrompt needs it. */
class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(false)
    private var stoppedAt = 0L
    private val repo get() = (application as OrbitApp).repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locked = repo.settings.biometricLock && canUseLock(this)
        setContent {
            OrbitTheme {
                if (locked) {
                    LockScreen(onUnlock = ::promptUnlock)
                } else {
                    val vm: AppViewModel = viewModel(factory = AppViewModel.factory(application, repo))
                    OrbitRoot(vm, canUseLock = canUseLock(this))
                }
            }
        }
        if (locked) promptUnlock()
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Re-lock after a minute in the background.
        if (stoppedAt != 0L && repo.settings.biometricLock && canUseLock(this) &&
            SystemClock.elapsedRealtime() - stoppedAt > 60_000
        ) {
            locked = true
            promptUnlock()
        }
    }

    private fun promptUnlock() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                locked = false
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Orbit")
                .setSubtitle("Use your fingerprint, face or screen lock")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }

    companion object {
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

        fun canUseLock(activity: FragmentActivity) =
            BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
