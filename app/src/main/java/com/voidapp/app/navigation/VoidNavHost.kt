package com.voidapp.app.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.voidapp.feature.auth.LoginScreen
import com.voidapp.feature.repos.ReposScreen
import com.voidapp.feature.settings.SettingsScreen
import com.voidapp.feature.workspace.WorkspaceScreen

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_REPOS = "repos"
private const val ROUTE_SETTINGS = "settings"
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
        bottomBar = {
            if (currentRoute != ROUTE_ONBOARDING) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                // Repos is the root of the authenticated back stack;
                                // popping to it keeps tab switching flat and reversible.
                                navController.navigate(destination.route) {
                                    popUpTo(ROUTE_REPOS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_ONBOARDING,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ROUTE_ONBOARDING) {
                LoginScreen(
                    onLoggedIn = {
                        navController.navigate(ROUTE_REPOS) {
                            popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
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
                WorkspaceScreen(onExit = { navController.popBackStack() })
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
                )
            }
        }
    }
}
