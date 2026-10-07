package com.pdfmaster

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import com.pdfmaster.ui.AppNav
import com.pdfmaster.ui.common.LocalContainer
import com.pdfmaster.ui.theme.PdfMasterTheme

class MainActivity : FragmentActivity() {

    private val container get() = (application as PdfMasterApp).container
    private val unlocked = mutableStateOf(false)
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        unlocked.value = savedInstanceState?.getBoolean(KEY_UNLOCKED) ?: false

        setContent {
            val themeMode by container.prefs.themeMode.collectAsState()
            val dynamic by container.prefs.dynamicColor.collectAsState()
            val lockEnabled by container.prefs.appLock.collectAsState()
            CompositionLocalProvider(LocalContainer provides container) {
                PdfMasterTheme(themeMode, dynamic) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (lockEnabled && !unlocked.value) {
                            LockScreen(onUnlock = ::authenticate)
                            LaunchedEffect(Unit) { authenticate() }
                        } else {
                            AppNav()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Re-lock after 30 s in the background.
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > 30_000) unlocked.value = false
    }

    override fun onResume() {
        super.onResume()
        container.billing.connect()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, unlocked.value)
    }

    private fun authenticate() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                unlocked.value = true
            }
        })
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
        else Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.unlock_title))
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val type = intent.type.orEmpty()
        val request = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { IncomingRequest.OpenPdf(it) }
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let {
                if (type.startsWith("image/")) IncomingRequest.Images(listOf(it)) else IncomingRequest.OpenPdf(it)
            }
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.takeIf { it.isNotEmpty() }
                ?.let { if (type.startsWith("image/")) IncomingRequest.Images(it) else IncomingRequest.Pdfs(it) }
            else -> null
        }
        if (request != null) container.incoming.value = request
    }

    private companion object {
        const val KEY_UNLOCKED = "unlocked"
    }
}

@androidx.compose.runtime.Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_locked), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlock) { Text(stringResource(R.string.unlock)) }
    }
}
