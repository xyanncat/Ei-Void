package com.eivoid.app.navigation

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eivoid.app.ui.WorkspaceRoute
import com.eivoid.designsystem.component.LocalOpenDrawer
import com.eivoid.designsystem.theme.VoidColors
import com.eivoid.feature.assistant.AssistantDestination
import com.eivoid.feature.assistant.AssistantDrawer
import com.eivoid.feature.assistant.AssistantHomeScreen
import com.eivoid.feature.assistant.AssistantViewModel
import com.eivoid.feature.assistant.ExploreScreen
import com.eivoid.feature.assistant.ImagesScreen
import com.eivoid.feature.assistant.RemoteScreen
import com.eivoid.feature.reminders.LibraryScreen
import com.eivoid.feature.reminders.ReminderEditorScreen
import com.eivoid.feature.repos.ProjectsScreen
import com.eivoid.feature.settings.CustomizeScreen
import com.eivoid.feature.settings.UsageOverviewScreen
import kotlinx.coroutines.launch

/**
 * The authenticated graph.
 *
 * The assistant is the home surface. Every other top-level destination is
 * reached from the left drawer; there is no bottom bar. The drawer is hosted
 * here, once, so the assistant and the existing Reminders, Projects, Customize
 * and Remote screens share the same slide-out.
 */
private const val ROUTE_ASSISTANT = "assistant"
private const val ROUTE_REPOS = "projects"
private const val ROUTE_REMINDERS = "library"
private const val ROUTE_SETTINGS = "customize"
private const val ROUTE_IMAGES = "images"
private const val ROUTE_REMOTE = "remote"
private const val ROUTE_EXPLORE = "explore"
private const val ROUTE_USAGE = "usage"
private const val ROUTE_EDIT_REMINDER = "reminder/{id}"
private const val ROUTE_WORKSPACE = "workspace/{owner}/{name}/{branch}"

private val TOP_LEVEL_ROUTES = setOf(
    ROUTE_ASSISTANT,
    ROUTE_REPOS,
    ROUTE_REMINDERS,
    ROUTE_SETTINGS,
    ROUTE_IMAGES,
    ROUTE_REMOTE,
    ROUTE_EXPLORE,
)

private fun drawerDestinationFor(route: String?): AssistantDestination = when (route) {
    ROUTE_IMAGES -> AssistantDestination.IMAGES
    ROUTE_REMINDERS -> AssistantDestination.LIBRARY
    ROUTE_SETTINGS -> AssistantDestination.CUSTOMIZE
    ROUTE_REPOS -> AssistantDestination.PROJECTS
    ROUTE_REMOTE -> AssistantDestination.REMOTE
    ROUTE_EXPLORE -> AssistantDestination.EXPLORE
    else -> AssistantDestination.HOME
}

private fun routeFor(destination: AssistantDestination): String = when (destination) {
    AssistantDestination.HOME -> ROUTE_ASSISTANT
    AssistantDestination.IMAGES -> ROUTE_IMAGES
    AssistantDestination.LIBRARY -> ROUTE_REMINDERS
    AssistantDestination.CUSTOMIZE -> ROUTE_SETTINGS
    AssistantDestination.PROJECTS -> ROUTE_REPOS
    AssistantDestination.REMOTE -> ROUTE_REMOTE
    AssistantDestination.EXPLORE -> ROUTE_EXPLORE
}

@Composable
fun VoidNavHost(
    userName: String = "there",
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // One assistant transcript for the whole graph, so Explore can fill the
    // ask bar and navigating away does not drop the conversation.
    val assistantViewModel: AssistantViewModel = hiltViewModel()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }
    val voiceUnavailable: () -> Unit = {
        Toast.makeText(
            context,
            "Voice mode is coming in a later build.",
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun navigateTop(route: String) {
        navController.navigate(route) {
            popUpTo(ROUTE_ASSISTANT) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        closeDrawer()
    }

    fun navigateTo(destination: AssistantDestination) = navigateTop(routeFor(destination))

    fun openReminder(id: Long) = navController.navigate("reminder/$id")

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        // Edge swipe is available on every top-level screen; pushed screens
        // such as the editor and workspace keep their own back gesture.
        gesturesEnabled = currentRoute in TOP_LEVEL_ROUTES,
        scrimColor = Color.Black.copy(alpha = 0.62f),
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
                    current = drawerDestinationFor(currentRoute),
                    accountLabel = userName,
                    onNavigate = { destination -> navigateTo(destination) },
                    onClose = closeDrawer,
                )
            }
        },
    ) {
        CompositionLocalProvider(LocalOpenDrawer provides openDrawer) {
            NavHost(
                navController = navController,
                startDestination = ROUTE_ASSISTANT,
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
            ) {
                composable(ROUTE_ASSISTANT) {
                    AssistantHomeScreen(
                        onOpenDrawer = openDrawer,
                        onOpenReminders = { navigateTop(ROUTE_REMINDERS) },
                        onOpenProjects = { navigateTop(ROUTE_REPOS) },
                        onOpenSettings = { navigateTop(ROUTE_SETTINGS) },
                        onAddReminder = { openReminder(0L) },
                        onDictate = voiceUnavailable,
                        onVoiceMode = voiceUnavailable,
                        viewModel = assistantViewModel,
                    )
                }

                composable(ROUTE_REMINDERS) {
                    LibraryScreen(
                        onOpenDrawer = openDrawer,
                        onAddReminder = { openReminder(0L) },
                        onEditReminder = { id -> openReminder(id) },
                    )
                }

                composable(ROUTE_REPOS) {
                    ProjectsScreen(
                        onOpenDrawer = openDrawer,
                        onOpenRepo = { repo ->
                            navController.navigate(
                                "workspace/${Uri.encode(repo.owner)}/" +
                                    "${Uri.encode(repo.name)}/${Uri.encode(repo.defaultBranch)}",
                            )
                        },
                    )
                }

                composable(ROUTE_IMAGES) {
                    ImagesScreen(onOpenDrawer = openDrawer)
                }

                composable(ROUTE_EXPLORE) {
                    ExploreScreen(
                        onOpenDrawer = openDrawer,
                        onPick = { label ->
                            assistantViewModel.addTask(label)
                            navigateTop(ROUTE_ASSISTANT)
                        },
                        onBack = { navigateTop(ROUTE_ASSISTANT) },
                    )
                }

                composable(ROUTE_REMOTE) {
                    RemoteScreen(
                        onOpenDrawer = openDrawer,
                        onOpenSettings = { navigateTop(ROUTE_SETTINGS) },
                    )
                }

                composable(ROUTE_SETTINGS) {
                    CustomizeScreen(
                        onOpenDrawer = openDrawer,
                        onLoggedOut = {
                            // The Clerk gate replaces the shell after sign-out;
                            // collapse the authenticated stack behind it.
                            navController.navigate(ROUTE_ASSISTANT) {
                                popUpTo(ROUTE_ASSISTANT) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onOpenUsage = { navController.navigate(ROUTE_USAGE) },
                    )
                }

                composable(ROUTE_USAGE) {
                    UsageOverviewScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    route = ROUTE_EDIT_REMINDER,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: 0L
                    ReminderEditorScreen(
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
            }
        }
    }
}
