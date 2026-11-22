package com.eivoid.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Void's colour system, drawn from the app artwork: a chalk sketch on an ink
 * night sky. One brand pair (chalk on ink), one paper surface for light, and
 * an otherwise monochrome tier scale. No decorative accent competes with the
 * line art.
 */
object VoidColors {
    // Brand
    val ink = Color(0xFF0E0E0F)
    val chalk = Color(0xFFF2F2EF)
    val graphite = Color(0xFFB8B8B4)
    val stone = Color(0xFF6E6E6A)
    val paper = Color(0xFFF3F1EA)
    val paperDim = Color(0xFFE4E1D9)
    val white = Color(0xFFFFFFFF)

    // Surfaces — OLED black for the assistant-first app.
    val surfaceDark = Color(0xFF000000)
    val surfaceLight = Color(0xFFF3F1EA)

    // Text
    val textPrimaryDark = Color(0xFFF2F2EF)
    val textSecondaryDark = Color(0xFFA8A8A4)
    val textPrimaryLight = Color(0xFF0E0E0F)
    val textSecondaryLight = Color(0xFF5A5852)

    // Semantic
    val success = Color(0xFF9DBFA3)
    val danger = Color(0xFFFF8A8A)
    val dangerOnLight = Color(0xFFB3261E)

    // Reminder accent. A warm lime/amber pair marks due dates without
    // introducing a second brand colour: it reads as "attention" on both the
    // ink and the paper surface.
    val reminderAccent = Color(0xFFD7E36B)
    val reminderAmber = Color(0xFFE9B949)
    val reminderAccentOnLight = Color(0xFF5C6410)
    val reminderAmberOnLight = Color(0xFF7A4F00)

    // Surface containers (Material 3.2+ roles). Without them the nav bar,
    // dialogs and cards fall back to the baseline purple/pink palette.
    val darkSurfaceContainerLowest = Color(0xFF070708)
    val darkSurfaceContainerLow = Color(0xFF0E0E10)
    val darkSurfaceContainer = Color(0xFF131315)
    val darkSurfaceContainerHigh = Color(0xFF1A1A1C)
    val darkSurfaceContainerHighest = Color(0xFF222224)
    val lightSurfaceContainerLowest = Color(0xFFFFFFFF)
    val lightSurfaceContainerLow = Color(0xFFFBFAF6)
    val lightSurfaceContainer = Color(0xFFF3F1EA)
    val lightSurfaceContainerHigh = Color(0xFFEDEAE2)
    val lightSurfaceContainerHighest = Color(0xFFE6E2D9)

    // Error roles
    val errorContainerDark = Color(0xFF3A0F12)
    val onErrorContainerDark = Color(0xFFFFDAD6)
    val onErrorDark = Color(0xFF2B0A0C)
    val errorContainerLight = Color(0xFFFFDAD6)
    val onErrorContainerLight = Color(0xFF410002)

    // Outlines
    val outlineDark = Color(0xFF3A3A3C)
    val outlineVariantDark = Color(0xFF262628)
    val outlineLight = Color(0xFF8A877F)
    val outlineVariantLight = Color(0xFFD5D1C7)

    // The void: OLED black (dark) and paper wash (light).
    val spaceTop = Color(0xFF000000)
    val spaceBottom = Color(0xFF000000)
    val dawnTop = Color(0xFFF3F1EA)
    val dawnBottom = Color(0xFFE9E5DC)

    // OLED assistant home. The AI screen is pure black with charcoal chrome,
    // matching the phone-first assistant layout. These are separate tokens so
    // the reminders and code screens keep their existing palette untouched.
    val oledBlack = Color(0xFF000000)
    val oledCharcoal = Color(0xFF0B0B0E)
    val oledSurfaceLow = Color(0xFF0F0F12)
    val oledSurface = Color(0xFF151518)
    val oledSurfaceHigh = Color(0xFF1C1C21)
    val oledBorder = Color(0xFF26262C)
    val oledBorderSubtle = Color(0xFF1B1B20)
    val oledTextPrimary = Color(0xFFF5F5F7)
    val oledTextSecondary = Color(0xFFA7A7B0)
    val oledTextTertiary = Color(0xFF8A8A94)
    val oledAccentBlue = Color(0xFF3B82F6)
    val oledAccentAmber = Color(0xFFF5B851)
    val oledSuccess = Color(0xFF4ADE80)
    val oledWarning = Color(0xFFFBBF24)
    val oledDanger = Color(0xFFF87171)

    // Tier markers, brightest to dimmest: chalk, graphite, stone.
    val tierRed = chalk
    val tierWhite = graphite
    val tierAmber = stone

    // Brand primitives used by the color test and brand-facing components.
    val primary = chalk
    val secondary = graphite
    val accent = stone
}
