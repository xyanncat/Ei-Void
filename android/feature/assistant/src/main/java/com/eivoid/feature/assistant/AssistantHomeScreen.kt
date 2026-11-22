package com.eivoid.feature.assistant

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.outlined.AddAlert
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eivoid.designsystem.component.VoidLogo
import com.eivoid.designsystem.theme.VoidColors
import java.time.LocalTime

/**
 * The assistant home: a quiet branded header, a small set of real starting
 * points, the user's workspace shortcuts, and a floating composer. No emoji,
 * no debug chrome, no oversized marketing headline.
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
            .windowInsetsPadding(
                WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal),
            )
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
                    onOpenReminders = onOpenReminders,
                    onOpenProjects = onOpenProjects,
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
            ),
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
/* Header                                                              */
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistantCircleButton(
            onClick = onOpenDrawer,
            contentDescription = "Open navigation",
        ) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = null,
                tint = VoidColors.oledTextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        VoidLogo(size = 30.dp, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "EI VOID",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.4.sp),
                color = VoidColors.oledTextPrimary,
            )
            Text(
                text = "AI workspace",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = VoidColors.oledTextTertiary,
            )
        }
        Spacer(Modifier.weight(1f))
        AssistantCircleButton(
            onClick = onToggleIncognito,
            contentDescription = if (incognito) {
                "Turn off incognito mode"
            } else {
                "Start an incognito chat"
            },
            active = incognito,
        ) {
            Icon(
                imageVector = Icons.Outlined.VisibilityOff,
                contentDescription = null,
                tint = if (incognito) VoidColors.oledAccentBlue else VoidColors.oledTextSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

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
            .size(42.dp)
            .clip(CircleShape)
            .background(
                if (active) {
                    VoidColors.oledAccentBlue.copy(alpha = 0.14f)
                } else {
                    VoidColors.oledSurfaceLow
                },
            )
            .border(
                width = 1.dp,
                color = if (active) {
                    VoidColors.oledAccentBlue.copy(alpha = 0.55f)
                } else {
                    VoidColors.oledBorderSubtle
                },
                shape = CircleShape,
            )
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/* ------------------------------------------------------------------ */
/* Empty state                                                         */
/* ------------------------------------------------------------------ */

@Composable
private fun AssistantEmptyState(
    onPickTask: (String) -> Unit,
    onOpenTaskPicker: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenProjects: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < 480.dp
        val hour = remember { LocalTime.now().hour }
        val greeting = when (hour) {
            in 5..11 -> "Good morning."
            in 12..16 -> "Good afternoon."
            else -> "Good evening."
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(if (compact) 12.dp else 34.dp))

            Text(
                text = greeting,
                style = if (compact) {
                    MaterialTheme.typography.headlineLarge
                } else {
                    MaterialTheme.typography.displaySmall
                },
                fontWeight = FontWeight.Bold,
                color = VoidColors.oledTextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "What would you like to get done?",
                style = if (compact) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = VoidColors.oledTextSecondary,
            )

            Spacer(Modifier.height(if (compact) 16.dp else 30.dp))
            SectionHeader(
                title = "START A TASK",
                action = "See all",
                onAction = onOpenTaskPicker,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))

            TaskGrid(
                tasks = HOME_TASKS,
                compact = compact,
                onPick = { task -> onPickTask(task.prompt) },
            )

            if (!compact) {
                Spacer(Modifier.height(26.dp))
                SectionHeader(title = "YOUR WORKSPACE")
                Spacer(Modifier.height(12.dp))
                WorkspaceCard(
                    onOpenReminders = onOpenReminders,
                    onOpenProjects = onOpenProjects,
                )
                Spacer(Modifier.height(20.dp))
                PrivacyNote()
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
            color = VoidColors.oledTextTertiary,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelMedium,
                color = VoidColors.oledAccentBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun TaskGrid(
    tasks: List<AssistantTask>,
    compact: Boolean,
    onPick: (AssistantTask) -> Unit,
) {
    val columns = if (compact) 4 else 2
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tasks.chunked(columns).forEach { rowTasks ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowTasks.forEach { task ->
                    TaskCard(
                        task = task,
                        compact = compact,
                        onClick = { onPick(task) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowTasks.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: AssistantTask,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (compact) 62.dp else 110.dp),
        shape = RoundedCornerShape(18.dp),
        color = VoidColors.oledSurfaceLow,
        border = BorderStroke(1.dp, VoidColors.oledBorderSubtle),
        tonalElevation = 0.dp,
    ) {
        if (compact) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskIconTile(icon = task.icon, size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = task.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = VoidColors.oledTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                TaskIconTile(icon = task.icon, size = 34.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = task.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = VoidColors.oledTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VoidColors.oledTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TaskIconTile(icon: AssistantTaskIcon, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(11.dp))
            .background(VoidColors.oledAccentBlue.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = taskIcon(icon),
            contentDescription = null,
            tint = VoidColors.oledAccentBlue,
            modifier = Modifier.size(size * 0.53f),
        )
    }
}

internal fun taskIcon(icon: AssistantTaskIcon): ImageVector = when (icon) {
    AssistantTaskIcon.CALENDAR -> Icons.Outlined.CalendarMonth
    AssistantTaskIcon.RECEIPT -> Icons.Outlined.ReceiptLong
    AssistantTaskIcon.CODE -> Icons.Outlined.Code
    AssistantTaskIcon.EDIT -> Icons.Outlined.EditNote
    AssistantTaskIcon.NEWS -> Icons.Outlined.Newspaper
    AssistantTaskIcon.LANGUAGE -> Icons.Outlined.School
}

@Composable
private fun WorkspaceCard(
    onOpenReminders: () -> Unit,
    onOpenProjects: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = VoidColors.oledSurfaceLow,
        border = BorderStroke(1.dp, VoidColors.oledBorderSubtle),
        tonalElevation = 0.dp,
    ) {
        Column {
            WorkspaceRow(
                icon = Icons.Outlined.Notifications,
                title = "Reminder library",
                subtitle = "Bills, renewals and subscriptions",
                onClick = onOpenReminders,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 64.dp)
                    .height(1.dp)
                    .background(VoidColors.oledBorderSubtle),
            )
            WorkspaceRow(
                icon = Icons.Outlined.FolderOpen,
                title = "Projects",
                subtitle = "Repositories and agent runs",
                onClick = onOpenProjects,
            )
        }
    }
}

@Composable
private fun WorkspaceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VoidColors.oledSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VoidColors.oledTextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = VoidColors.oledTextPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = VoidColors.oledTextSecondary,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = VoidColors.oledTextTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PrivacyNote() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = VoidColors.oledTextTertiary,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Your prompts and keys stay on this device.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = VoidColors.oledTextTertiary,
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
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
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
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomEnd = 6.dp,
                bottomStart = 18.dp,
            ),
            color = VoidColors.oledSurfaceHigh,
            border = BorderStroke(1.dp, VoidColors.oledBorderSubtle),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = VoidColors.oledTextPrimary,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun AssistantTurn(text: String, streaming: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoidLogo(size = 20.dp, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "EI VOID",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                color = VoidColors.oledTextTertiary,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (text.isNotBlank()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = VoidColors.oledTextPrimary,
            )
        }
        if (streaming) {
            Spacer(Modifier.height(8.dp))
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
                    .size(5.dp)
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
        color = VoidColors.oledSurfaceLow,
        border = BorderStroke(1.dp, VoidColors.oledDanger.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = VoidColors.oledDanger,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = VoidColors.oledTextPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Customize",
                style = MaterialTheme.typography.labelMedium,
                color = VoidColors.oledAccentBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Composer                                                            */
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
    val compact = LocalConfiguration.current.screenHeightDp < 420
    val hasInput = value.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (compact) 5.dp else 9.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = VoidColors.oledSurface,
            border = BorderStroke(1.dp, VoidColors.oledBorder),
            shadowElevation = if (compact) 0.dp else 10.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = if (compact) 5.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(
                                onClickLabel = "Add",
                                onClick = { menuOpen = true },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Add",
                            tint = VoidColors.oledTextSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = VoidColors.oledSurfaceHigh,
                    ) {
                        DropdownMenuItem(
                            text = { Text("New reminder", color = VoidColors.oledTextPrimary) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.AddAlert,
                                    contentDescription = null,
                                    tint = VoidColors.oledTextSecondary,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onAddReminder()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Projects", color = VoidColors.oledTextPrimary) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    tint = VoidColors.oledTextSecondary,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onOpenProjects()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Customize", color = VoidColors.oledTextPrimary) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Tune,
                                    contentDescription = null,
                                    tint = VoidColors.oledTextSecondary,
                                )
                            },
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
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isBlank()) {
                                Text(
                                    text = "Message Ei Void",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = VoidColors.oledTextTertiary,
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
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = "Dictate",
                        tint = VoidColors.oledTextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(if (compact) 40.dp else 44.dp)
                        .clip(CircleShape)
                        .background(VoidColors.oledAccentBlue)
                        .clickable(
                            onClickLabel = if (hasInput) "Send" else "Live voice",
                            onClick = if (hasInput) onSend else onVoiceMode,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasInput) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowUpward,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = "Live voice",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
        }

        if (!compact) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = "Ei Void can make mistakes. Check important information.",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.2.sp,
                ),
                color = VoidColors.oledTextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
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
            shape = RoundedCornerShape(26.dp),
            color = VoidColors.oledCharcoal,
            border = BorderStroke(1.dp, VoidColors.oledBorder),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Start a task",
                    style = MaterialTheme.typography.titleLarge,
                    color = VoidColors.oledTextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Choose a starting point. You can edit it before sending.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoidColors.oledTextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    ALL_TASKS.forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPick(task.prompt) }
                                .padding(horizontal = 10.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VoidColors.oledAccentBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = taskIcon(task.icon),
                                    contentDescription = null,
                                    tint = VoidColors.oledAccentBlue,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = VoidColors.oledTextPrimary,
                                )
                                Text(
                                    text = task.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VoidColors.oledTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = VoidColors.oledTextTertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = VoidColors.oledTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}
