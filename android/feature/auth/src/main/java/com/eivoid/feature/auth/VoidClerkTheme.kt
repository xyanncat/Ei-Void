package com.eivoid.feature.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clerk.api.ui.ClerkColors
import com.clerk.api.ui.ClerkDesign
import com.clerk.api.ui.ClerkTheme
import com.clerk.api.ui.ClerkTypography
import com.eivoid.designsystem.theme.VoidColors

/**
 * Maps Void's Material theme onto the Clerk UI tokens. There is no second
 * palette: the native AuthView uses the same colors, type scale and corner
 * radius as the rest of the app. A transparent background lets the cosmic
 * canvas show through.
 */
@Composable
fun voidClerkTheme(): ClerkTheme {
    val colors = MaterialTheme.colorScheme
    return ClerkTheme(
        colors = ClerkColors(
            primary = colors.primary,
            background = Color.Transparent,
            input = colors.surfaceContainerHigh,
            danger = colors.error,
            success = VoidColors.success,
            warning = colors.secondary,
            foreground = colors.onBackground,
            mutedForeground = colors.onSurfaceVariant,
            primaryForeground = colors.onPrimary,
            inputForeground = colors.onSurface,
            neutral = colors.outlineVariant,
            border = colors.outlineVariant,
            ring = colors.primary,
            muted = colors.surfaceContainerLow,
            // Brand marks (notably GitHub's black cat) need a light button
            // surface even in the dark theme: the same chalk-on-ink pair.
            secondaryButtonBackground = VoidColors.chalk,
            secondaryButtonForeground = VoidColors.ink,
            shadow = Color.Black.copy(alpha = 0.28f),
        ),
        typography = ClerkTypography(
            displaySmall = MaterialTheme.typography.displaySmall,
            headlineLarge = MaterialTheme.typography.headlineLarge,
            headlineMedium = MaterialTheme.typography.headlineMedium,
            headlineSmall = MaterialTheme.typography.headlineSmall,
            titleMedium = MaterialTheme.typography.titleMedium,
            titleSmall = MaterialTheme.typography.titleSmall,
            bodyLarge = MaterialTheme.typography.bodyLarge,
            bodyMedium = MaterialTheme.typography.bodyMedium,
            bodySmall = MaterialTheme.typography.bodySmall,
            labelMedium = MaterialTheme.typography.labelMedium,
            labelSmall = MaterialTheme.typography.labelSmall,
        ),
        design = ClerkDesign(
            borderRadius = 16.dp,
            logoMaxHeight = 64.dp,
        ),
    )
}
