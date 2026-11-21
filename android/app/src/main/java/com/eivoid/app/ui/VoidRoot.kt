package com.eivoid.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.Window
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clerk.api.Clerk
import com.eivoid.app.BuildConfig
import com.eivoid.app.navigation.VoidNavHost
import com.eivoid.designsystem.component.VoidBackground
import com.eivoid.designsystem.theme.VoidColors
import com.eivoid.designsystem.theme.VoidTheme
import com.eivoid.feature.auth.EiLoginScreen
import com.eivoid.feature.settings.SettingsViewModel
import com.eivoid.feature.settings.ThemeMode

/**
 * Root theme wrapper and the Clerk gate.
 *
 * Signed in -> the app graph (Repos remains the authenticated root).
 * Signed out -> Clerk's native AuthView.
 * Clerk still starting -> a loading screen.
 *
 * A build without a publishable key cannot initialize Clerk; rather than
 * spinning forever it falls back to the existing login screen, which explains
 * what is missing and (in debug) offers a local session so the UI can be
 * reviewed on a device.
 */
@Composable
fun VoidRoot() {
    val settings: SettingsViewModel = hiltViewModel()
    val mode by settings.theme.collectAsStateWithLifecycle()

    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // A forced in-app theme must also drive the system bars: MIUI keeps a
    // light navigation bar while the system itself is in light mode.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            setNavigationBarColor(
                window,
                (if (darkTheme) VoidColors.surfaceDark else VoidColors.surfaceLight).toArgb(),
            )
        }
    }

    VoidTheme(darkTheme = darkTheme) {
        VoidBackground(darkTheme = darkTheme) {
            ClerkGate()
        }
    }
}

@Composable
private fun ClerkGate() {
    val clerkConfigured = BuildConfig.CLERK_PUBLISHABLE_KEY.startsWith("pk_")

    if (!clerkConfigured) {
        // No key means Clerk never initializes. A release build documents the
        // missing configuration; a debug build opens the real shell so the
        // reminders UI can be exercised on a device without credentials.
        if (BuildConfig.DEBUG) {
            SignedInShell(userName = "debug user", debugBypass = true)
        } else {
            EiLoginScreen(onLoggedIn = {}, showDebugSignIn = false)
        }
        return
    }

    val initialized by Clerk.isInitialized.collectAsStateWithLifecycle()
    val user by Clerk.userFlow.collectAsStateWithLifecycle()

    when {
        !initialized -> LoadingGate()
        user == null -> EiLoginScreen(onLoggedIn = {})
        else -> SignedInShell(
            userName = displayNameOf(user?.firstName, user?.lastName, user?.username),
            debugBypass = false,
        )
    }
}

/**
 * The signed-in app, plus the two startup duties that belong to the shell:
 * asking for the notification permission once and showing a loud banner when
 * Clerk is bypassed in a debug build.
 */
@Composable
private fun SignedInShell(userName: String, debugBypass: Boolean) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (debugBypass) DebugBypassBanner()
        NotificationPermissionRequest()
        VoidNavHost(
            userName = userName,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DebugBypassBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoidColors.reminderAmber.copy(alpha = 0.22f))
            .statusBarsPadding()
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "DEBUG BUILD · Clerk bypass active · reminders are local only",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * POST_NOTIFICATIONS is a runtime grant. Ask once when the shell appears; the
 * Settings permissions card is the place to recover after a denial.
 */
@Composable
private fun NotificationPermissionRequest() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun LoadingGate() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Starting Ei Void…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun displayNameOf(first: String?, last: String?, username: String?): String {
    val full = listOfNotNull(first?.trim(), last?.trim())
        .filter { it.isNotBlank() }
        .joinToString(" ")
    return full.ifBlank { username?.trim().orEmpty() }.ifBlank { "there" }
}

@Suppress("DEPRECATION")
private fun setNavigationBarColor(window: Window, color: Int) {
    window.navigationBarColor = color
}
