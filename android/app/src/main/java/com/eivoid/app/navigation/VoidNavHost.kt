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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eivoid.app.BuildConfig
import com.eivoid.app.ui.WorkspaceRoute
import com.eivoid.feature.auth.LoginScreen
import com.eivoid.feature.repos.ReposScreen
import com.eivoid.feature.settings.SettingsScreen
import com.eivoid.feature.settings.UsageScreen

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_REPOS = "repos"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_USAGE = "usage"
private const val ROUTE_WORKSPACE = "workspace/{owner}/{name}/{branch}"

private data class Destination(val route: String, val label: String, val icon: ImageVector)

@Composable
fun VoidNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val destinations = listOf(
        Destination(ROUTE_REPOS, "Repos", Icons.AutoMirrored.Outlined.List),
        Destination(ROUTE_SETTINGS, "Settings", Icons.Outlined.Settings),
    )

    Scaffold(
        // The cosmic wash is drawn behind everything.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            // Only the two top-level destinations carry the tab bar. The
            // workspace and usage screens are full-height pushed routes.
            val topLevel = currentRoute == ROUTE_REPOS || currentRoute == ROUTE_SETTINGS
            if (topLevel) {
                VoidBottomBar(
                    currentRoute = currentRoute,
                    destinations = destinations,
                    onSelect = { destination ->
                        // Repos is the root of the authenticated back stack;
                        // popping to it keeps tab switching flat and reversible.
                        navController.navigate(destination.route) {
                            popUpTo(ROUTE_REPOS) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_ONBOARDING,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            composable(ROUTE_ONBOARDING) {
                LoginScreen(
                    onLoggedIn = {
                        navController.navigate(ROUTE_REPOS) {
                            popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    // Debug builds can enter the signed-in UI without OAuth so
                    // the screens can be reviewed on a device.
                    showDebugSignIn = BuildConfig.DEBUG,
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
                        // Popping to Repos (the authenticated root) drops Settings
                        // and every other authenticated entry, so Back cannot
                        // return to signed-in screens.
                        navController.navigate(ROUTE_ONBOARDING) {
                            popUpTo(ROUTE_REPOS) { inclusive = true }
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
 * A floating pill instead of the Material bar: two destinations, a soft
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
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
                            .padding(horizontal = 22.dp, vertical = 10.dp),
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
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}
