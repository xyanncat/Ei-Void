package com.voidapp.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Void's colour system.
 *
 * Three brand colours on two surfaces. The accent is deliberately mismatched
 * against the primary/secondary pair — that tension is the identity.
 */
object VoidColors {
    // Brand
    val primary = Color(0xFF4F46E5)   // Deep Indigo
    val secondary = Color(0xFFF59E0B) // Warm Amber
    val accent = Color(0xFFFF5A5F)    // Electric Coral

    // Surfaces
    val surfaceDark = Color(0xFF0B0B12)
    val surfaceLight = Color(0xFFF7F7FB)

    // Primitive indigo scale (used by components that need shades)
    val indigo50 = Color(0xFFEEF2FF)
    val indigo100 = Color(0xFFE0E7FF)
    val indigo300 = Color(0xFFA5B4FC)
    val indigo500 = Color(0xFF6366F1)
    val indigo700 = Color(0xFF4338CA)
    val indigo900 = Color(0xFF312E81)

    // Primitive amber scale
    val amber100 = Color(0xFFFEF3C7)
    val amber400 = Color(0xFFFBBF24)
    val amber600 = Color(0xFFD97706)

    // Text
    val textPrimaryDark = Color(0xFFF4F4F8)
    val textSecondaryDark = Color(0xFFA0A0B0)
    val textPrimaryLight = Color(0xFF12121A)
    val textSecondaryLight = Color(0xFF4A4A57)

    // Semantic
    val success = Color(0xFF22C55E)
    val danger = accent
    val border = Color(0xFF26263A)
}
