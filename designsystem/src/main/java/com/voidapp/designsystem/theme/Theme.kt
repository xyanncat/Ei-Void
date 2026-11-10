package com.voidapp.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val VoidDarkScheme = darkColorScheme(
    primary = VoidColors.primary,
    onPrimary = VoidColors.surfaceLight,
    secondary = VoidColors.secondary,
    onSecondary = VoidColors.surfaceDark,
    tertiary = VoidColors.accent,
    onTertiary = VoidColors.surfaceLight,
    background = VoidColors.surfaceDark,
    onBackground = VoidColors.textPrimaryDark,
    surface = VoidColors.surfaceDark,
    onSurface = VoidColors.textPrimaryDark,
    surfaceVariant = VoidColors.border,
    onSurfaceVariant = VoidColors.textSecondaryDark,
    error = VoidColors.danger,
)

private val VoidLightScheme = lightColorScheme(
    primary = VoidColors.primary,
    onPrimary = VoidColors.surfaceLight,
    secondary = VoidColors.secondary,
    onSecondary = VoidColors.surfaceDark,
    tertiary = VoidColors.accent,
    onTertiary = VoidColors.surfaceLight,
    background = VoidColors.surfaceLight,
    onBackground = VoidColors.textPrimaryLight,
    surface = VoidColors.surfaceLight,
    onSurface = VoidColors.textPrimaryLight,
    surfaceVariant = VoidColors.indigo50,
    onSurfaceVariant = VoidColors.textSecondaryLight,
    error = VoidColors.danger,
)

@Composable
fun VoidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) VoidDarkScheme else VoidLightScheme,
        typography = VoidTypography,
        content = content,
    )
}
