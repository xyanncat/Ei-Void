package com.eivoid.app.ui

import android.app.Activity
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eivoid.app.navigation.VoidNavHost
import com.eivoid.designsystem.component.VoidBackground
import com.eivoid.designsystem.theme.VoidColors
import com.eivoid.designsystem.theme.VoidTheme
import com.eivoid.feature.settings.SettingsViewModel
import com.eivoid.feature.settings.ThemeMode

/** Applies the stored theme mode, then hands off to navigation. */
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
            VoidNavHost()
        }
    }
}

@Suppress("DEPRECATION")
private fun setNavigationBarColor(window: Window, color: Int) {
    window.navigationBarColor = color
}
