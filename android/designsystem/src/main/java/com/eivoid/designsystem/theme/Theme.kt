package com.eivoid.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val VoidDarkScheme = darkColorScheme(
    // Chalk on ink: the primary action is the brightest thing on screen.
    primary = VoidColors.chalk,
    onPrimary = VoidColors.ink,
    primaryContainer = VoidColors.darkSurfaceContainerHighest,
    onPrimaryContainer = VoidColors.chalk,
    secondary = VoidColors.graphite,
    onSecondary = VoidColors.ink,
    secondaryContainer = VoidColors.darkSurfaceContainerHigh,
    onSecondaryContainer = VoidColors.chalk,
    tertiary = VoidColors.graphite,
    onTertiary = VoidColors.ink,
    background = VoidColors.spaceTop,
    onBackground = VoidColors.chalk,
    surface = VoidColors.surfaceDark,
    onSurface = VoidColors.chalk,
    surfaceVariant = VoidColors.darkSurfaceContainerHigh,
    onSurfaceVariant = VoidColors.textSecondaryDark,
    error = VoidColors.danger,
    onError = VoidColors.onErrorDark,
    errorContainer = VoidColors.errorContainerDark,
    onErrorContainer = VoidColors.onErrorContainerDark,
    outline = VoidColors.outlineDark,
    outlineVariant = VoidColors.outlineVariantDark,
    surfaceContainerLowest = VoidColors.darkSurfaceContainerLowest,
    surfaceContainerLow = VoidColors.darkSurfaceContainerLow,
    surfaceContainer = VoidColors.darkSurfaceContainer,
    surfaceContainerHigh = VoidColors.darkSurfaceContainerHigh,
    surfaceContainerHighest = VoidColors.darkSurfaceContainerHighest,
)

private val VoidLightScheme = lightColorScheme(
    // Ink on paper inverts the same pair; there is no second accent.
    primary = VoidColors.ink,
    onPrimary = VoidColors.paper,
    primaryContainer = VoidColors.paperDim,
    onPrimaryContainer = VoidColors.ink,
    secondary = VoidColors.stone,
    onSecondary = VoidColors.paper,
    secondaryContainer = VoidColors.paperDim,
    onSecondaryContainer = VoidColors.ink,
    tertiary = VoidColors.stone,
    onTertiary = VoidColors.paper,
    background = VoidColors.paper,
    onBackground = VoidColors.ink,
    surface = VoidColors.lightSurfaceContainerLow,
    onSurface = VoidColors.ink,
    surfaceVariant = VoidColors.lightSurfaceContainerHigh,
    onSurfaceVariant = VoidColors.textSecondaryLight,
    error = VoidColors.dangerOnLight,
    onError = VoidColors.white,
    errorContainer = VoidColors.errorContainerLight,
    onErrorContainer = VoidColors.onErrorContainerLight,
    outline = VoidColors.outlineLight,
    outlineVariant = VoidColors.outlineVariantLight,
    surfaceContainerLowest = VoidColors.lightSurfaceContainerLowest,
    surfaceContainerLow = VoidColors.lightSurfaceContainerLow,
    surfaceContainer = VoidColors.lightSurfaceContainer,
    surfaceContainerHigh = VoidColors.lightSurfaceContainerHigh,
    surfaceContainerHighest = VoidColors.lightSurfaceContainerHighest,
)

@Composable
fun VoidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) VoidDarkScheme else VoidLightScheme,
        typography = VoidTypography,
        shapes = VoidShapes,
        content = content,
    )
}
