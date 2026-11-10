package com.voidapp.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voidapp.app.navigation.VoidNavHost
import com.voidapp.designsystem.theme.VoidTheme
import com.voidapp.feature.settings.SettingsViewModel
import com.voidapp.feature.settings.ThemeMode

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

    VoidTheme(darkTheme = darkTheme) {
        VoidNavHost()
    }
}
