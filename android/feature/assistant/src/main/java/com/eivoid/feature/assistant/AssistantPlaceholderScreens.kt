package com.eivoid.feature.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eivoid.designsystem.component.VoidLogo
import com.eivoid.designsystem.theme.VoidColors

/** A quiet, branded header shared by the drawer screens. */
@Composable
internal fun AssistantScreenHeader(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
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
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = VoidColors.oledTextTertiary,
                )
            }
        }
    }
}

/** The Images destination: a useful explanation, not a dead placeholder. */
@Composable
fun ImagesScreen(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidColors.oledBlack),
    ) {
        AssistantScreenHeader(onOpenDrawer = onOpenDrawer, title = "Images")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(VoidColors.oledSurfaceLow),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = VoidColors.oledTextSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Image tools are next",
                    style = MaterialTheme.typography.titleLarge,
                    color = VoidColors.oledTextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Generate and edit visuals with your own provider keys. " +
                        "The workspace, key storage and routing are already here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoidColors.oledTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A black screen with a centered title and one muted line. */
@Composable
fun AssistantPlaceholderScreen(
    title: String,
    message: String,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidColors.oledBlack),
    ) {
        AssistantScreenHeader(onOpenDrawer = onOpenDrawer, title = title)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = VoidColors.oledTextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoidColors.oledTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Explore is the long-form version of the home task cards. */
@Composable
fun ExploreScreen(
    onOpenDrawer: () -> Unit,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidColors.oledBlack),
    ) {
        AssistantScreenHeader(onOpenDrawer = onOpenDrawer, title = "Explore")

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "A place to start",
                    style = MaterialTheme.typography.headlineSmall,
                    color = VoidColors.oledTextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Pick a capability, then shape the prompt in the composer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoidColors.oledTextSecondary,
                )
                Spacer(Modifier.height(8.dp))
            }

            items(items = ALL_TASKS, key = { it.label }) { task ->
                Surface(
                    onClick = { onPick(task.prompt) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = VoidColors.oledSurfaceLow,
                    border = BorderStroke(1.dp, VoidColors.oledBorderSubtle),
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
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

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Back to chat",
                    style = MaterialTheme.typography.labelLarge,
                    color = VoidColors.oledTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onBack)
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}

/** Remote reports the state of the Ei Snore bridge and routes to Customize. */
@Composable
fun RemoteScreen(
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidColors.oledBlack),
    ) {
        AssistantScreenHeader(onOpenDrawer = onOpenDrawer, title = "Remote")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = VoidColors.oledSurfaceLow,
                border = BorderStroke(1.dp, VoidColors.oledBorderSubtle),
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VoidColors.oledAccentBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Sensors,
                                contentDescription = null,
                                tint = VoidColors.oledAccentBlue,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ei Snore",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = VoidColors.oledTextPrimary,
                            )
                            Text(
                                text = "Discord notifications",
                                style = MaterialTheme.typography.bodySmall,
                                color = VoidColors.oledTextSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Connect Discord once in Customize and every reminder, " +
                            "build and assistant alert can reach your server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VoidColors.oledTextSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = VoidColors.oledAccentBlue,
                    ) {
                        Text(
                            text = "Open Customize",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = androidx.compose.ui.graphics.Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 13.dp),
                        )
                    }
                }
            }
        }
    }
}
