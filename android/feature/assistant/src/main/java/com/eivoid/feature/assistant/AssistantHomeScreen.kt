package com.eivoid.feature.assistant

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eivoid.designsystem.theme.VoidColors

/**
 * The OLED assistant home: a transparent top bar, a centered suggestion
 * canvas, and a floating ask bar pinned above the keyboard.
 */
@Composable
fun AssistantHomeScreen(
    onOpenDrawer: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddReminder: () -> Unit,
    modifier: Modifier = Modifier,
    onDictate: () -> Unit = {},
    onVoiceMode: () -> Unit = {},
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidColors.oledBlack)
            .imePadding(),
    ) {
        AssistantTopBar(
            incognito = state.incognito,
            onOpenDrawer = onOpenDrawer,
            onToggleIncognito = viewModel::toggleIncognito,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (state.messages.isEmpty()) {
                AssistantEmptyState(
                    onPickTask = viewModel::addTask,
                    onOpenTaskPicker = viewModel::openTaskPicker,
                )
            } else {
                AssistantMessageList(
                    messages = state.messages,
                    streaming = state.streaming,
                )
            }
        }

        state.error?.let { message ->
            AssistantErrorBanner(
                message = message,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }

        AssistantInputBar(
            value = state.input,
            onValueChange = viewModel::onInputChange,
            onSend = viewModel::send,
            onAddReminder = onAddReminder,
            onOpenProjects = onOpenProjects,
            onOpenSettings = onOpenSettings,
            onDictate = onDictate,
            onVoiceMode = onVoiceMode,
            modifier = Modifier.navigationBarsPadding(),
        )
    }

    if (state.showTaskPicker) {
        AssistantTaskPicker(
            onPick = viewModel::addTask,
            onDismiss = viewModel::closeTaskPicker,
        )
    }
}

/* ------------------------------------------------------------------ */
/* Top bar                                                             */
/* ------------------------------------------------------------------ */

@Composable
internal fun AssistantTopBar(
    incognito: Boolean,
    onOpenDrawer: () -> Unit,
    onToggleIncognito: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistantCircleButton(
            onClick = onOpenDrawer,
            contentDescription = "Open navigation",
        ) {
            MenuGlyph(color = VoidColors.oledTextPrimary)
        }
        Spacer(Modifier.weight(1f))
        AssistantCircleButton(
            onClick = onToggleIncognito,
            contentDescription = if (incognito) {
                "Turn off incognito chat"
            } else {
                "Start incognito chat"
            },
            active = incognito,
        ) {
            DashedChatGlyph(
                color = if (incognito) {
                    VoidColors.oledAccentBlue
                } else {
                    VoidColors.oledTextPrimary
                },
            )
        }
    }
}

/** A 44 dp circular, hairline-bordered icon button on charcoal. */
@Composable
internal fun AssistantCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (active) {
                    VoidColors.oledAccentBlue.copy(alpha = 0.16f)
                } else {
                    VoidColors.oledSurface
                },
            )
            .border(
                width = 1.dp,
                color = if (active) {
                    VoidColors.oledAccentBlue.copy(alpha = 0.65f)
                } else {
                    VoidColors.oledBorder
                },
                shape = CircleShape,
            )
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** The two staggered lines of the drawer affordance. */
@Composable
internal fun MenuGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.14f, size.height * 0.36f),
            end = Offset(size.width * 0.86f, size.height * 0.36f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.14f, size.height * 0.64f),
            end = Offset(size.width * 0.60f, size.height * 0.64f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** A dashed circle with a small chat bubble inside: incognito chat. */
@Composable
private fun DashedChatGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val stroke = 1.4.dp.toPx()
        drawCircle(
            color = color,
            radius = size.minDimension / 2f - stroke,
            style = Stroke(
                width = stroke,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(3.dp.toPx(), 2.6.dp.toPx()),
                ),
            ),
        )
        val bubbleWidth = size.width * 0.46f
        val bubbleHeight = size.height * 0.34f
        val left = (size.width - bubbleWidth) / 2f
        val top = (size.height - bubbleHeight) / 2f - 1.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(bubbleWidth, bubbleHeight),
            cornerRadius = CornerRadius(bubbleHeight * 0.38f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(left + bubbleWidth * 0.30f, top + bubbleHeight),
            end = Offset(left + bubbleWidth * 0.24f, top + bubbleHeight + 2.2.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/* ------------------------------------------------------------------ */
/* Empty state                                                         */
/* ------------------------------------------------------------------ */

@Composable
private fun AssistantEmptyState(
    onPickTask: (String) -> Unit,
    onOpenTaskPicker: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Put Ei Void to work",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = VoidColors.oledTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(22.dp))

            TASK_ROWS.forEach { row ->
                SuggestionRow(tasks = row, onPick = onPickTask)
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(VoidColors.oledBlack)
                    .border(1.dp, VoidColors.oledBorder, RoundedCornerShape(50))
                    .clickable(onClick = onOpenTaskPicker)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Pick a task",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = VoidColors.oledTextPrimary,
                )
            }
        }
    }
}

/**
 * One horizontal chip row. The row scrolls under soft black fades at both
 * edges, so long lists feel like they continue past the screen.
 */
@Composable
private fun SuggestionRow(
    tasks: List<AssistantTask>,
    onPick: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tasks.forEach { task ->
                SuggestionChip(task = task, onClick = { onPick(task.label) })
            }
        }

        Canvas(Modifier.matchParentSize()) {
            val fade = 34.dp.toPx()
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to VoidColors.oledBlack,
                    1f to Color.Transparent,
                    startX = 0f,
                    endX = fade,
                ),
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    1f to VoidColors.oledBlack,
                    startX = size.width - fade,
                    endX = size.width,
                ),
            )
        }
    }
}

@Composable
private fun SuggestionChip(task: AssistantTask, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(VoidColors.oledSurface)
            .border(1.dp, VoidColors.oledBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = task.emoji, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = task.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = VoidColors.oledTextPrimary.copy(alpha = 0.84f),
        )
    }
}

/* ------------------------------------------------------------------ */
/* Transcript                                                          */
/* ------------------------------------------------------------------ */

@Composable
private fun AssistantMessageList(
    messages: List<AssistantMessage>,
    streaming: Boolean,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items = messages, key = { it.id }) { message ->
            when (message.role) {
                AssistantRole.USER -> UserBubble(text = message.text)
                AssistantRole.ASSISTANT -> AssistantTurn(
                    text = message.text,
                    streaming = streaming && message.id == messages.lastOrNull()?.id,
                )
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomEnd = 6.dp,
                bottomStart = 20.dp,
            ),
            color = VoidColors.oledSurface,
            border = BorderStroke(1.dp, VoidColors.oledBorder),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = VoidColors.oledTextPrimary,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun AssistantTurn(text: String, streaming: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "EI VOID",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
            color = VoidColors.oledTextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        if (text.isNotBlank()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = VoidColors.oledTextPrimary,
            )
        }
        if (streaming) {
            Spacer(Modifier.height(6.dp))
            StreamingDots()
        }
    }
}

@Composable
private fun StreamingDots() {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        VoidColors.oledAccentBlue.copy(
                            alpha = (alpha - index * 0.15f).coerceIn(0.15f, 1f),
                        ),
                    ),
            )
            if (index != 2) Spacer(Modifier.width(4.dp))
        }
    }
}

/* ------------------------------------------------------------------ */
/* Error                                                               */
/* ------------------------------------------------------------------ */

@Composable
private fun AssistantErrorBanner(
    message: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = VoidColors.oledSurface,
        border = BorderStroke(1.dp, VoidColors.oledAccentBlue.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = VoidColors.oledTextPrimary.copy(alpha = 0.9f),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Customize",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = VoidColors.oledAccentBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Input bar                                                           */
/* ------------------------------------------------------------------ */

@Composable
private fun AssistantInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAddReminder: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenSettings: () -> Unit,
    onDictate: () -> Unit,
    onVoiceMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = VoidColors.oledSurface,
            border = BorderStroke(1.dp, VoidColors.oledBorder),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = "Add", onClick = { menuOpen = true }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Add",
                            tint = VoidColors.oledTextPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = VoidColors.oledSurface,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add reminder", color = VoidColors.oledTextPrimary) },
                            onClick = {
                                menuOpen = false
                                onAddReminder()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Projects", color = VoidColors.oledTextPrimary) },
                            onClick = {
                                menuOpen = false
                                onOpenProjects()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Customize", color = VoidColors.oledTextPrimary) },
                            onClick = {
                                menuOpen = false
                                onOpenSettings()
                            },
                        )
                    }
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = VoidColors.oledTextPrimary,
                    ),
                    cursorBrush = SolidColor(VoidColors.oledAccentBlue),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isBlank()) {
                                Text(
                                    text = "Ask Ei Void",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = VoidColors.oledTextSecondary,
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Dictate", onClick = onDictate),
                    contentAlignment = Alignment.Center,
                ) {
                    MicGlyph(color = VoidColors.oledTextPrimary)
                }
                Spacer(Modifier.width(2.dp))
                val hasInput = value.isNotBlank()
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(VoidColors.oledAccentBlue)
                        .clickable(
                            onClickLabel = if (hasInput) "Send" else "Live voice",
                            onClick = if (hasInput) onSend else onVoiceMode,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasInput) {
                        SendArrowGlyph(color = Color.White)
                    } else {
                        WaveformGlyph(color = Color.White)
                    }
                }
            }
        }
    }
}

/** A white outline microphone: capsule, cradle and stand. */
@Composable
private fun MicGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val stroke = 1.6.dp.toPx()
        val bodyWidth = size.width * 0.32f
        drawRoundRect(
            color = color,
            topLeft = Offset((size.width - bodyWidth) / 2f, size.height * 0.06f),
            size = Size(bodyWidth, size.height * 0.46f),
            cornerRadius = CornerRadius(bodyWidth / 2f),
            style = Stroke(width = stroke),
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.22f, size.height * 0.30f),
            size = Size(size.width * 0.56f, size.height * 0.34f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.64f),
            end = Offset(size.width * 0.5f, size.height * 0.86f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** A white up arrow: the blue button becomes Send once the user has typed. */
@Composable
private fun SendArrowGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val stroke = 2.2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.82f),
            end = Offset(size.width * 0.5f, size.height * 0.18f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.18f),
            end = Offset(size.width * 0.22f, size.height * 0.46f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.18f),
            end = Offset(size.width * 0.78f, size.height * 0.46f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** Five rounded bars: the live voice action. */
@Composable
private fun WaveformGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val stroke = 2.4.dp.toPx()
        val heights = listOf(0.28f, 0.55f, 0.90f, 0.62f, 0.34f)
        val step = size.width / (heights.size + 1)
        heights.forEachIndexed { index, fraction ->
            val x = step * (index + 1)
            val half = size.height * fraction / 2f
            drawLine(
                color = color,
                start = Offset(x, size.height / 2f - half),
                end = Offset(x, size.height / 2f + half),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Task picker                                                         */
/* ------------------------------------------------------------------ */

@Composable
private fun AssistantTaskPicker(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = VoidColors.oledCharcoal,
            border = BorderStroke(1.dp, VoidColors.oledBorder),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Pick a task",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VoidColors.oledTextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Ei Void will start from this prompt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoidColors.oledTextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ALL_TASKS.forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPick(task.label) }
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = task.emoji, fontSize = 17.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = task.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = VoidColors.oledTextPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}
