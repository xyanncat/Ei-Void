package com.eivoid.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eivoid.designsystem.theme.VoidColors

/**
 * The Ei Void UI kit.
 *
 * One set of primitives for every screen: OLED black canvas, charcoal
 * surfaces, hairline borders, white type and a single electric-blue action
 * accent. Screens compose these instead of reaching for raw Material
 * components, so the whole app keeps the same density and shape language.
 */
object EiDimens {
    val screenPadding = 20.dp
    val cardRadius = 22.dp
    val fieldRadius = 16.dp
    val pill = 50.dp
    val hairline = 1.dp
}

private val cardShape = RoundedCornerShape(EiDimens.cardRadius)
private val fieldShape = RoundedCornerShape(EiDimens.fieldRadius)
private val pillShape = RoundedCornerShape(EiDimens.pill)

/** A black, safe-area-aware screen column. */
@Composable
fun EiScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidColors.oledBlack)
            .statusBarsPadding()
            .navigationBarsPadding(),
        content = content,
    )
}

/** Title row with an optional menu/back button and trailing actions. */
@Composable
fun EiTopBar(
    title: String,
    subtitle: String? = null,
    onMenu: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            onBack != null -> EiIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )

            onMenu != null -> EiIconButton(
                icon = Icons.Outlined.Menu,
                contentDescription = "Open navigation",
                onClick = onMenu,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VoidColors.oledTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = VoidColors.oledTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** A 44dp circular icon button with a hairline border. */
@Composable
fun EiIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = VoidColors.oledTextPrimary,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(VoidColors.oledSurface)
            .border(EiDimens.hairline, VoidColors.oledBorder, CircleShape)
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A 44dp circular button with custom content, for Canvas glyphs. */
@Composable
fun EiGlyphButton(
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(VoidColors.oledSurface)
            .border(EiDimens.hairline, VoidColors.oledBorder, CircleShape)
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** A charcoal card with a hairline border. */
@Composable
fun EiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(VoidColors.oledSurface)
            .border(EiDimens.hairline, VoidColors.oledBorder, cardShape)
            .then(clickable)
            .padding(contentPadding),
        content = content,
    )
}

/** A small uppercase section label. */
@Composable
fun EiSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
        color = VoidColors.oledTextSecondary,
        modifier = modifier.padding(top = 4.dp),
    )
}

/** A tappable row used by settings and libraries. */
@Composable
fun EiListRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(VoidColors.oledSurfaceHigh),
                contentAlignment = Alignment.Center,
            ) { leading() }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = VoidColors.oledTextPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = VoidColors.oledTextSecondary,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** A pill chip. Selected chips use the blue accent; the rest stay charcoal. */
@Composable
fun EiPill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leading: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val fill = if (selected) VoidColors.oledAccentBlue.copy(alpha = 0.16f) else VoidColors.oledSurface
    val border = if (selected) VoidColors.oledAccentBlue else VoidColors.oledBorder
    val contentColor = if (selected) VoidColors.oledTextPrimary else VoidColors.oledTextSecondary
    Row(
        modifier = modifier
            .clip(pillShape)
            .background(fill)
            .border(EiDimens.hairline, border, pillShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(text = leading, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/** Full-width primary action in the blue accent. */
@Composable
fun EiPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) VoidColors.oledAccentBlue else VoidColors.oledSurfaceHigh)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else VoidColors.oledTextSecondary,
            )
        }
    }
}

/** A quiet outlined action. */
@Composable
fun EiSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(VoidColors.oledSurface)
            .border(EiDimens.hairline, VoidColors.oledBorder, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = VoidColors.oledTextPrimary,
        )
    }
}

/** A charcoal text field with a blue focus ring. */
@Composable
fun EiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 4,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = fieldShape,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = VoidColors.oledTextPrimary),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = VoidColors.oledSurface,
            unfocusedContainerColor = VoidColors.oledSurface,
            disabledContainerColor = VoidColors.oledSurface,
            focusedBorderColor = VoidColors.oledAccentBlue,
            unfocusedBorderColor = VoidColors.oledBorder,
            disabledBorderColor = VoidColors.oledBorder,
            cursorColor = VoidColors.oledAccentBlue,
            focusedTextColor = VoidColors.oledTextPrimary,
            unfocusedTextColor = VoidColors.oledTextPrimary,
            focusedLabelColor = VoidColors.oledTextSecondary,
            unfocusedLabelColor = VoidColors.oledTextSecondary,
            focusedPlaceholderColor = VoidColors.oledTextSecondary,
            unfocusedPlaceholderColor = VoidColors.oledTextSecondary,
        ),
    )
}

/** A segmented selector made of pills. */
@Composable
fun <T> EiSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            EiPill(
                text = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

/** A progress bar in the accent colour. */
@Composable
fun EiProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = VoidColors.oledAccentBlue,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(VoidColors.oledSurfaceHigh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** Centered empty state with an optional action. */
@Composable
fun EiEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(VoidColors.oledSurface)
                    .border(EiDimens.hairline, VoidColors.oledBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = VoidColors.oledTextSecondary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = VoidColors.oledTextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = VoidColors.oledTextSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Box(modifier = Modifier.width(220.dp)) {
                EiPrimaryButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

/** A compact inline error/notice banner. */
@Composable
fun EiNotice(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    danger: Boolean = false,
) {
    val accent = if (danger) VoidColors.oledDanger else VoidColors.oledAccentBlue
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VoidColors.oledSurface)
            .border(EiDimens.hairline, accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = VoidColors.oledTextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun EiDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = EiDimens.hairline,
        color = VoidColors.oledBorder,
    )
}

/** A large numeric/value tile. */
@Composable
fun EiStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = VoidColors.oledTextPrimary,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = VoidColors.oledTextSecondary,
        )
    }
}

/** A section header with an optional action on the right. */
@Composable
fun EiSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            color = VoidColors.oledTextSecondary,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = VoidColors.oledAccentBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/** A small status chip for state such as Active, Paid or Private. */
@Composable
fun EiStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = VoidColors.oledTextSecondary,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(pillShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** A compact text field without a floating label, for inline editors. */
@Composable
fun EiInlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Box(
        modifier = modifier
            .clip(fieldShape)
            .background(VoidColors.oledSurface)
            .border(EiDimens.hairline, VoidColors.oledBorder, fieldShape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        if (value.isBlank()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = VoidColors.oledTextSecondary,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = VoidColors.oledTextPrimary),
            cursorBrush = SolidColor(VoidColors.oledAccentBlue),
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
