package com.eivoid.app.navigation

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eivoid.app.ui.WorkspaceRoute
import com.eivoid.feature.reminders.EditReminderScreen
import com.eivoid.feature.reminders.HomeScreen
import com.eivoid.feature.reminders.RemindersScreen
import com.eivoid.feature.repos.ReposScreen
import com.eivoid.feature.settings.SettingsScreen
import com.eivoid.feature.settings.UsageScreen

/**
 * `home` is the authenticated root of the graph: it is the start destination
 * and the anchor every tab switch pops to. The Code tab uses the repository
 * list directly, while Reminders and Settings are sibling top-level routes.
 */
private const val ROUTE_REPOS = "repos"
private const val ROUTE_HOME = "home"
private const val ROUTE_REMINDERS = "reminders"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_USAGE = "usage"
private const val ROUTE_EDIT_REMINDER = "reminder/{id}"
private const val ROUTE_WORKSPACE = "workspace/{owner}/{name}/{branch}"

private val TOP_LEVEL_ROUTES = setOf(
    ROUTE_REPOS,
    ROUTE_HOME,
    ROUTE_REMINDERS,
    ROUTE_SETTINGS,
)

private data class Destination(val route: String, val label: String, val icon: ImageVector)

@Composable
fun VoidNavHost(
    userName: String = "there",
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val destinations = listOf(
        Destination(ROUTE_HOME, "Home", Icons.Outlined.Home),
        Destination(ROUTE_REMINDERS, "Reminders", Icons.Outlined.Notifications),
        Destination(ROUTE_REPOS, "Code", Icons.AutoMirrored.Outlined.List),
        Destination(ROUTE_SETTINGS, "Settings", Icons.Outlined.Settings),
    )

    fun openReminder(id: Long) = navController.navigate("reminder/$id")

    fun switchTab(route: String) {
        navController.navigate(route) {
            // Home is the authenticated root. Popping to it keeps tab switches
            // flat and reversible; the Code tab remains a normal destination.
            popUpTo(ROUTE_HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            // Only the four top-level destinations carry the tab bar; edit,
            // workspace and usage are full-height pushed routes.
            if (currentRoute in TOP_LEVEL_ROUTES) {
                VoidBottomBar(
                    currentRoute = currentRoute,
                    destinations = destinations,
                    onSelect = { destination -> switchTab(destination.route) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    userName = userName,
                    onAddReminder = { openReminder(0L) },
                    onEditReminder = { id -> openReminder(id) },
                    onOpenReminders = { switchTab(ROUTE_REMINDERS) },
                    onOpenCode = { switchTab(ROUTE_REPOS) },
                )
            }

            composable(ROUTE_REMINDERS) {
                RemindersScreen(
                    onAddReminder = { openReminder(0L) },
                    onEditReminder = { id -> openReminder(id) },
                )
            }

            composable(ROUTE_REPOS) {
                ReposScreen(
                    onOpenRepo = { repo ->
                        navController.navigate(
                            "workspace/${Uri.encode(repo.owner)}/" +
                                "${Uri.encode(repo.name)}/${Uri.encode(repo.defaultBranch)}",
                        )
                    },
                )
            }

            composable(
                route = ROUTE_EDIT_REMINDER,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                EditReminderScreen(
                    reminderId = id,
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                )
            }

            composable(
                route = ROUTE_WORKSPACE,
                arguments = listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                    navArgument("branch") {
                        type = NavType.StringType
                        defaultValue = "main"
                    },
                ),
            ) {
                WorkspaceRoute(onExit = { navController.popBackStack() })
            }

            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    onLoggedOut = {
                        // Popping to Repos (the authenticated root) drops every
                        // authenticated entry, so Back cannot return to them.
                        navController.navigate(ROUTE_HOME) {
                            popUpTo(ROUTE_HOME) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenUsage = { navController.navigate(ROUTE_USAGE) },
                )
            }

            composable(ROUTE_USAGE) {
                UsageScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * A floating pill instead of the Material bar: four destinations, a soft
 * border, and the cosmic background visible around it.
 */
@Composable
private fun VoidBottomBar(
    currentRoute: String?,
    destinations: List<Destination>,
    onSelect: (Destination) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(62.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
            shadowElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { destination ->
                    val selected = currentRoute == destination.route
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                            )
                            .clickable { onSelect(destination) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}
