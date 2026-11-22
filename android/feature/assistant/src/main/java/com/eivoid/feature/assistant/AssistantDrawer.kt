package com.eivoid.feature.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eivoid.designsystem.component.VoidLogo
import com.eivoid.designsystem.theme.VoidColors
import com.eivoid.designsystem.theme.VoidTheme
import kotlinx.coroutines.launch

/** The drawer's destinations. HOME is the assistant itself. */
enum class AssistantDestination(val label: String) {
    HOME("Chat"),
    LIBRARY("Library"),
    PROJECTS("Projects"),
    REMOTE("Remote"),
    CUSTOMIZE("Customize"),
    USAGE("Usage"),
    IMAGES("Images"),
    EXPLORE("Explore"),
}

/**
 * The slide-out navigation. A product drawer, not a list of links: brand,
 * account, one primary action, grouped destinations and a quiet footer.
 */
@Composable
fun AssistantDrawer(
    current: AssistantDestination,
    accountLabel: String,
    onNavigate: (AssistantDestination) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onNewChat: () -> Unit = {},
) {
    val shortViewport = LocalConfiguration.current.screenHeightDp < 600
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidColors.oledCharcoal)
            .statusBarsPadding()
            .navigationBarsPadding()
            .then(
                if (shortViewport) {
                    Modifier.verticalScroll(rememberScrollState())
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoidLogo(size = 34.dp, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EI VOID",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.4.sp),
                    color = VoidColors.oledTextPrimary,
                )
                Text(
                    text = "Assistant",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = VoidColors.oledTextTertiary,
                )
            }
            AssistantCircleButton(
                onClick = onClose,
                contentDescription = "Close navigation",
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    tint = VoidColors.oledTextSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }

        Surface(
            onClick = { onNavigate(AssistantDestination.CUSTOMIZE) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = VoidColors.oledSurfaceLow,
            border = BorderStroke(1.dp, VoidColors.oledBorderSubtle),
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VoidLogo(size = 30.dp, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = accountLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = VoidColors.oledTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (accountLabel == "Local workspace") {
                            "On-device profile"
                        } else {
                            "Personal workspace"
                        },
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

        Spacer(Modifier.height(12.dp))
        Surface(
            onClick = {
                onNewChat()
                onClose()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = VoidColors.oledAccentBlue,
        ) {
            Row(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddComment,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "New chat",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("WORKSPACE")
        DrawerRow(
            destination = AssistantDestination.HOME,
            selected = current == AssistantDestination.HOME,
            onClick = { onNavigate(AssistantDestination.HOME) },
        )
        DrawerRow(
            destination = AssistantDestination.LIBRARY,
            selected = current == AssistantDestination.LIBRARY,
            onClick = { onNavigate(AssistantDestination.LIBRARY) },
        )
        DrawerRow(
            destination = AssistantDestination.PROJECTS,
            selected = current == AssistantDestination.PROJECTS,
            onClick = { onNavigate(AssistantDestination.PROJECTS) },
        )
        DrawerRow(
            destination = AssistantDestination.REMOTE,
            selected = current == AssistantDestination.REMOTE,
            onClick = { onNavigate(AssistantDestination.REMOTE) },
        )

        Spacer(Modifier.height(18.dp))
        SectionLabel("PREFERENCES")
        DrawerRow(
            destination = AssistantDestination.CUSTOMIZE,
            selected = current == AssistantDestination.CUSTOMIZE,
            onClick = { onNavigate(AssistantDestination.CUSTOMIZE) },
        )
        DrawerRow(
            destination = AssistantDestination.USAGE,
            selected = current == AssistantDestination.USAGE,
            onClick = { onNavigate(AssistantDestination.USAGE) },
        )

        if (shortViewport) {
            Spacer(Modifier.height(24.dp))
        } else {
            Spacer(Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
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
                text = "Private by design",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                color = VoidColors.oledTextTertiary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Ei Void",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                color = VoidColors.oledTextTertiary,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = VoidColors.oledTextTertiary,
        modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun DrawerRow(
    destination: AssistantDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val icon = drawerIcon(destination)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) VoidColors.oledSurface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) {
                        VoidColors.oledAccentBlue.copy(alpha = 0.13f)
                    } else {
                        Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) VoidColors.oledAccentBlue else VoidColors.oledTextSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = destination.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) VoidColors.oledTextPrimary else VoidColors.oledTextSecondary,
        )
        Spacer(Modifier.weight(1f))
        if (selected) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(VoidColors.oledAccentBlue),
            )
        }
    }
}

private fun drawerIcon(destination: AssistantDestination): ImageVector = when (destination) {
    AssistantDestination.HOME -> Icons.Outlined.ChatBubbleOutline
    AssistantDestination.LIBRARY -> Icons.Outlined.Notifications
    AssistantDestination.PROJECTS -> Icons.Outlined.FolderOpen
    AssistantDestination.REMOTE -> Icons.Outlined.Sensors
    AssistantDestination.CUSTOMIZE -> Icons.Outlined.Tune
    AssistantDestination.USAGE -> Icons.Outlined.BarChart
    AssistantDestination.IMAGES -> Icons.Outlined.Image
    AssistantDestination.EXPLORE -> Icons.Outlined.Explore
}

/* ------------------------------------------------------------------ */
/* Legacy shell                                                        */
/* ------------------------------------------------------------------ */

/**
 * Entry point for the feature used by the original standalone shell. The app
 * graph uses VoidNavHost directly, but the shell remains for previews.
 */
@Composable
fun AssistantShell(
    accountLabel: String = "signed in",
    onOpenReminders: () -> Unit = {},
    onOpenProjects: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onAddReminder: () -> Unit = {},
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    VoidTheme(darkTheme = true) {
        var destinationName by rememberSaveable {
            mutableStateOf(AssistantDestination.HOME.name)
        }
        val destination = AssistantDestination.valueOf(destinationName)
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        fun openDrawer() {
            scope.launch { drawerState.open() }
        }

        fun closeDrawer() {
            scope.launch { drawerState.close() }
        }

        fun navigate(target: AssistantDestination) {
            when (target) {
                AssistantDestination.CUSTOMIZE -> {
                    destinationName = AssistantDestination.HOME.name
                    onOpenSettings()
                }

                AssistantDestination.PROJECTS -> {
                    destinationName = AssistantDestination.HOME.name
                    onOpenProjects()
                }

                else -> destinationName = target.name
            }
            closeDrawer()
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            scrimColor = Color.Black.copy(alpha = 0.62f),
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier
                        .width(316.dp)
                        .fillMaxHeight(),
                    drawerShape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
                    drawerContainerColor = VoidColors.oledCharcoal,
                    drawerContentColor = VoidColors.oledTextPrimary,
                    drawerTonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    AssistantDrawer(
                        current = destination,
                        accountLabel = accountLabel,
                        onNavigate = { target -> navigate(target) },
                        onClose = { closeDrawer() },
                        onNewChat = viewModel::clearChat,
                    )
                }
            },
        ) {
            when (destination) {
                AssistantDestination.IMAGES -> ImagesScreen(onOpenDrawer = { openDrawer() })

                AssistantDestination.LIBRARY -> AssistantPlaceholderScreen(
                    title = "Library",
                    message = "Your reminder library lives in the main app graph.",
                    onOpenDrawer = { openDrawer() },
                )

                AssistantDestination.EXPLORE -> ExploreScreen(
                    onOpenDrawer = { openDrawer() },
                    onPick = { label ->
                        viewModel.addTask(label)
                        destinationName = AssistantDestination.HOME.name
                    },
                    onBack = { destinationName = AssistantDestination.HOME.name },
                )

                AssistantDestination.REMOTE -> RemoteScreen(
                    onOpenDrawer = { openDrawer() },
                    onOpenSettings = onOpenSettings,
                )

                else -> AssistantHomeScreen(
                    onOpenDrawer = { openDrawer() },
                    onOpenReminders = onOpenReminders,
                    onOpenProjects = onOpenProjects,
                    onOpenSettings = onOpenSettings,
                    onAddReminder = onAddReminder,
                    viewModel = viewModel,
                )
            }
        }
    }
}
