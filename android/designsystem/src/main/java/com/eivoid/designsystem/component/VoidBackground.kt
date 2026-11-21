package com.eivoid.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eivoid.designsystem.theme.VoidColors
import kotlin.random.Random

/**
 * The app canvas: a night-sky wash, one soft halo glow near the top and, in
 * the dark theme, a deterministic starfield. Drawn once per screen
 * composition; the stars never move, so nothing here animates or invalidates.
 */
@Composable
fun VoidBackground(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val halo = MaterialTheme.colorScheme.primary
    val stars = remember(darkTheme) {
        val random = Random(73)
        List(if (darkTheme) 56 else 24) {
            Star(
                x = random.nextFloat(),
                y = random.nextFloat(),
                alpha = 0.12f + random.nextFloat() * 0.38f,
                radius = 0.6f + random.nextFloat() * 1.1f,
            )
        }
    }

    val base = if (darkTheme) {
        listOf(VoidColors.spaceTop, VoidColors.spaceBottom)
    } else {
        listOf(VoidColors.dawnTop, VoidColors.dawnBottom)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(base)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val glowCenter = Offset(size.width * 0.5f, size.height * 0.06f)
            val glowRadius = size.width * 1.1f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        halo.copy(alpha = if (darkTheme) 0.11f else 0.07f),
                        Color.Transparent,
                    ),
                    center = glowCenter,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = glowCenter,
            )

            if (darkTheme) {
                stars.forEach { star ->
                    drawCircle(
                        color = Color.White.copy(alpha = star.alpha),
                        radius = star.radius.dp.toPx(),
                        center = Offset(size.width * star.x, size.height * star.y),
                    )
                }
            }
        }
        content()
    }
}

private data class Star(
    val x: Float,
    val y: Float,
    val alpha: Float,
    val radius: Float,
)
