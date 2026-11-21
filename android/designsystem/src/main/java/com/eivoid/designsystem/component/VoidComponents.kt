package com.eivoid.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A soft bordered card on the cosmic background. */
@Composable
fun VoidCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickable = if (onClick != null) {
        Modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick)
    } else {
        Modifier
    }
    Surface(
        modifier = modifier.then(clickable),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** An uppercase section label with a graphite dot. */
@Composable
fun VoidSectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The drawer opener for top-level screens. The app shell provides it; screens
 * that render [VoidTopBar] without a back affordance automatically get a menu
 * button, so every top-level destination can reach the navigation drawer.
 */
val LocalOpenDrawer = compositionLocalOf<(() -> Unit)?> { null }

/** A screen header with an optional back affordance and trailing actions. */
@Composable
fun VoidTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = if (onBack != null || LocalOpenDrawer.current != null) 4.dp else 20.dp,
                end = 12.dp,
                top = 10.dp,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        } else {
            LocalOpenDrawer.current?.let { openDrawer ->
                IconButton(onClick = openDrawer) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Open navigation")
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * The primary action: a 54 dp chalk pill in dark, ink in light. Solid colour
 * only — the artwork's line work is the decoration.
 */
@Composable
fun VoidPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val background = MaterialTheme.colorScheme.primary.copy(
        alpha = if (enabled) 1f else 0.38f,
    )
    val labelColor = MaterialTheme.colorScheme.onPrimary.copy(
        alpha = if (enabled) 1f else 0.7f,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = labelColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = labelColor,
                maxLines = 1,
            )
        }
    }
}

/** A quieter full-width action with a visible border. */
@Composable
fun VoidTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

/** A centred empty or error state with an orbital illustration. */
@Composable
fun VoidEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(112.dp)) {
                drawCircle(accent.copy(alpha = 0.07f), radius = size.minDimension / 2f)
                drawCircle(
                    color = accent.copy(alpha = 0.20f),
                    radius = size.minDimension / 2f - 10.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawArc(
                    color = accent,
                    startAngle = -40f,
                    sweepAngle = 95f,
                    useCenter = false,
                    topLeft = Offset(10.dp.toPx(), 10.dp.toPx()),
                    size = Size(
                        size.width - 20.dp.toPx(),
                        size.height - 20.dp.toPx(),
                    ),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.widthIn(max = 240.dp)) {
                VoidPrimaryButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

/** A small tinted status label. */
@Composable
fun VoidStatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // The same hue would be only a tint; the label needs its own contrast.
    val labelColor = if (dark) {
        lerp(color, Color.White, 0.35f)
    } else {
        lerp(color, Color.Black, 0.35f)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = if (dark) 0.16f else 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = labelColor)
    }
}

@Composable
fun VoidDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/** A rounded initial, used for repositories and files. */
@Composable
fun VoidAvatar(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** A rounded, gradient usage bar. */
@Composable
fun VoidProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    track: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    brush: Brush = Brush.horizontalGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
    ),
) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(brush),
        )
    }
}

/** A tiny outlined label used for the "clone · edit · push" strip. */
@Composable
fun VoidFeaturePill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
