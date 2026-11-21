package com.eivoid.feature.assistant

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eivoid.designsystem.theme.VoidColors
import com.eivoid.designsystem.theme.VoidTheme
import kotlinx.coroutines.launch

/** The drawer's destinations. HOME is the assistant itself. */
enum class AssistantDestination(val label: String) {
    HOME("Chat"),
    IMAGES("Images"),
    LIBRARY("Library"),
    CUSTOMIZE("Customize"),
    PROJECTS("Projects"),
    REMOTE("Remote"),
    EXPLORE("Explore"),
}

/**
 * The slide-out navigation. Charcoal, generous rows, one highlighted
 * destination and a quiet account footer.
 */
@Composable
fun AssistantDrawer(
    current: AssistantDestination,
    accountLabel: String,
    onNavigate: (AssistantDestination) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidColors.oledCharcoal)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ei Void",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = VoidColors.oledTextPrimary,
                modifier = Modifier.weight(1f),
            )
            AssistantCircleButton(
                onClick = onClose,
                contentDescription = "Search",
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = VoidColors.oledTextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        DrawerRow(
            destination = AssistantDestination.HOME,
            selected = current == AssistantDestination.HOME,
            onClick = { onNavigate(AssistantDestination.HOME) },
        )

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(VoidColors.oledBorder),
        )
        Spacer(Modifier.height(8.dp))

        DRAWER_DESTINATIONS.forEach { destination ->
            DrawerRow(
                destination = destination,
                selected = current == destination,
                onClick = { onNavigate(destination) },
            )
        }

        Spacer(Modifier.weight(1f))
        Text(
            text = "Ei Void · $accountLabel",
            style = MaterialTheme.typography.labelSmall,
            color = VoidColors.oledTextSecondary,
            modifier = Modifier.padding(vertical = 14.dp),
        )
    }
}

private val DRAWER_DESTINATIONS = listOf(
    AssistantDestination.IMAGES,
    AssistantDestination.LIBRARY,
    AssistantDestination.CUSTOMIZE,
    AssistantDestination.PROJECTS,
    AssistantDestination.REMOTE,
    AssistantDestination.EXPLORE,
)

@Composable
private fun DrawerRow(
    destination: AssistantDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) VoidColors.oledSurface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DrawerGlyph(destination)
        Spacer(Modifier.width(14.dp))
        Text(
            text = destination.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = VoidColors.oledTextPrimary,
        )
    }
}

/**
 * White line art for the drawer. Icons outside the Compose core set are drawn
 * here, so the module needs no extra icon dependency.
 */
@Composable
private fun DrawerGlyph(destination: AssistantDestination) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        val line = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val white = Color.White

        when (destination) {
            AssistantDestination.HOME -> {
                drawRoundRect(
                    color = white,
                    topLeft = Offset(w * 0.06f, h * 0.10f),
                    size = Size(w * 0.88f, h * 0.66f),
                    cornerRadius = CornerRadius(h * 0.18f),
                    style = line,
                )
                drawLine(
                    color = white,
                    start = Offset(w * 0.30f, h * 0.76f),
                    end = Offset(w * 0.24f, h * 0.94f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }

            AssistantDestination.IMAGES -> {
                drawRoundRect(
                    color = white,
                    topLeft = Offset(w * 0.06f, h * 0.10f),
                    size = Size(w * 0.88f, h * 0.80f),
                    cornerRadius = CornerRadius(h * 0.12f),
                    style = line,
                )
                drawCircle(
                    color = white,
                    radius = h * 0.09f,
                    center = Offset(w * 0.35f, h * 0.35f),
                    style = line,
                )
                drawPath(
                    path = Path().apply {
                        moveTo(w * 0.13f, h * 0.78f)
                        lineTo(w * 0.40f, h * 0.50f)
                        lineTo(w * 0.60f, h * 0.70f)
                        lineTo(w * 0.72f, h * 0.58f)
                        lineTo(w * 0.88f, h * 0.78f)
                    },
                    color = white,
                    style = line,
                )
            }

            AssistantDestination.LIBRARY -> {
                drawRoundRect(
                    color = white,
                    topLeft = Offset(w * 0.10f, h * 0.14f),
                    size = Size(w * 0.20f, h * 0.72f),
                    cornerRadius = CornerRadius(h * 0.04f),
                    style = line,
                )
                drawRoundRect(
                    color = white,
                    topLeft = Offset(w * 0.38f, h * 0.14f),
                    size = Size(w * 0.20f, h * 0.72f),
                    cornerRadius = CornerRadius(h * 0.04f),
                    style = line,
                )
                drawLine(
                    color = white,
                    start = Offset(w * 0.70f, h * 0.20f),
                    end = Offset(w * 0.88f, h * 0.82f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }

            AssistantDestination.CUSTOMIZE -> {
                listOf(0.24f, 0.50f, 0.76f).forEach { y ->
                    drawLine(
                        color = white,
                        start = Offset(w * 0.08f, h * y),
                        end = Offset(w * 0.92f, h * y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
                drawCircle(white, radius = h * 0.08f, center = Offset(w * 0.34f, h * 0.24f))
                drawCircle(white, radius = h * 0.08f, center = Offset(w * 0.66f, h * 0.50f))
                drawCircle(white, radius = h * 0.08f, center = Offset(w * 0.46f, h * 0.76f))
            }

            AssistantDestination.PROJECTS -> {
                drawPath(
                    path = Path().apply {
                        moveTo(w * 0.06f, h * 0.76f)
                        lineTo(w * 0.06f, h * 0.24f)
                        lineTo(w * 0.40f, h * 0.24f)
                        lineTo(w * 0.48f, h * 0.38f)
                        lineTo(w * 0.94f, h * 0.38f)
                        lineTo(w * 0.94f, h * 0.76f)
                        close()
                    },
                    color = white,
                    style = line,
                )
            }

            AssistantDestination.REMOTE -> {
                drawCircle(
                    color = white,
                    radius = h * 0.075f,
                    center = Offset(w * 0.5f, h * 0.82f),
                )
                drawArc(
                    color = white,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.22f, h * 0.44f),
                    size = Size(w * 0.56f, h * 0.56f),
                    style = line,
                )
                drawArc(
                    color = white,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.04f, h * 0.24f),
                    size = Size(w * 0.92f, h * 0.92f),
                    style = line,
                )
            }

            AssistantDestination.EXPLORE -> {
                drawCircle(
                    color = white,
                    radius = size.minDimension / 2f - stroke,
                    style = line,
                )
                drawPath(
                    path = Path().apply {
                        moveTo(w * 0.62f, h * 0.28f)
                        lineTo(w * 0.46f, h * 0.46f)
                        lineTo(w * 0.38f, h * 0.72f)
                        lineTo(w * 0.54f, h * 0.54f)
                        close()
                    },
                    color = white,
                    style = line,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Shell                                                               */
/* ------------------------------------------------------------------ */

/**
 * Entry point for the feature: hosts the drawer, the assistant home and the
 * placeholder destinations. Drop this into the app graph; it forces the
 * OLED dark theme for its own subtree only.
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
                // Existing screens own these two destinations.
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
            scrimColor = Color.Black.copy(alpha = 0.6f),
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier
                        .width(320.dp)
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
                    )
                }
            },
        ) {
            when (destination) {
                AssistantDestination.IMAGES -> ImagesScreen(onOpenDrawer = { openDrawer() })

                AssistantDestination.LIBRARY -> AssistantPlaceholderScreen(
                    title = "Library",
                    message = "Saved prompts and files will live here in a later build.",
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
