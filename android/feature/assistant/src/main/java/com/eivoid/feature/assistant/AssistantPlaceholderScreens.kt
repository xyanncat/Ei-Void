package com.eivoid.feature.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eivoid.designsystem.theme.VoidColors

/** A menu button, plus an optional title, on a black screen. */
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistantCircleButton(
            onClick = onOpenDrawer,
            contentDescription = "Open navigation",
        ) {
            MenuGlyph(color = VoidColors.oledTextPrimary)
        }
        if (title != null) {
            Spacer(Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VoidColors.oledTextPrimary,
            )
        }
    }
}

/** The Images destination: a quiet placeholder until the tools ship. */
@Composable
fun ImagesScreen(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistantPlaceholderScreen(
        title = "Images",
        message = "Image tools are coming in a later build.",
        onOpenDrawer = onOpenDrawer,
        modifier = modifier,
    )
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
        AssistantScreenHeader(onOpenDrawer = onOpenDrawer)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = VoidColors.oledTextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
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

/**
 * Explore is the long-form version of the suggestion chips: pick a prompt and
 * the assistant home opens with it in the ask bar.
 */
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = ALL_TASKS, key = { it.label }) { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(VoidColors.oledSurface)
                        .border(1.dp, VoidColors.oledBorder, RoundedCornerShape(18.dp))
                        .clickable { onPick(task.label) }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = task.emoji, fontSize = 18.sp)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = task.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = VoidColors.oledTextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Use",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = VoidColors.oledAccentBlue,
                    )
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, VoidColors.oledBorder, RoundedCornerShape(50))
                        .clickable(onClick = onBack)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Back to chat",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = VoidColors.oledTextPrimary,
                    )
                }
            }
        }
    }
}

/**
 * Remote shows the Ei Snore bridge. The Discord OAuth flow itself lives in the
 * Settings screen, so this card only reports status and forwards the user.
 */
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = VoidColors.oledSurface,
                border = BorderStroke(1.dp, VoidColors.oledBorder),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Ei | Snore",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VoidColors.oledTextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(VoidColors.oledAccentBlue.copy(alpha = 0.14f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "NOT CONNECTED",
                                style = MaterialTheme.typography.labelSmall,
                                color = VoidColors.oledAccentBlue,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Discord notifications through Ei Snore. Connect once " +
                            "in Customize and every reminder, build and assistant alert " +
                            "can reach your server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VoidColors.oledTextSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(VoidColors.oledAccentBlue)
                            .clickable(onClick = onOpenSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Open Customize",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "The Ei Snore bot delivers the alerts. Its OAuth link lives in " +
                    "Settings so the web flow can finish outside the app.",
                style = MaterialTheme.typography.bodySmall,
                color = VoidColors.oledTextSecondary,
            )
        }
    }
}
