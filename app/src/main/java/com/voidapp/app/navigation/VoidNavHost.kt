package com.voidapp.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_REPOS = "repos"
private const val ROUTE_SETTINGS = "settings"

private data class Destination(val route: String, val label: String, val icon: ImageVector)

@Composable
fun VoidNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val destinations = listOf(
        Destination(ROUTE_REPOS, "Repos", Icons.Outlined.List),
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
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
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
                Placeholder(
                    title = "Ei | Void",
                    body = "An AI coding agent that works on your repositories, on your phone.",
                )
            }
            composable(ROUTE_REPOS) {
                Placeholder("Repositories", "Sign in with GitHub to list your repos.")
            }
            composable(ROUTE_SETTINGS) {
                Placeholder("Settings", "Provider keys, default model and theme.")
            }
        }
    }
}

@Composable
private fun Placeholder(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
