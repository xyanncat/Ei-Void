package com.eivoid.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.eivoid.designsystem.component.VoidDivider
import com.eivoid.feature.chat.ChatScreen
import com.eivoid.feature.workspace.WorkspaceScreen
import com.eivoid.feature.workspace.WorkspaceViewModel

/**
 * One repository, two views: its files and the conversation about it.
 * Tabs instead of a split pane, because the app is phone-first.
 */
@Composable
fun WorkspaceRoute(
    onExit: () -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var filesShown by rememberSaveable { mutableStateOf(false) }

    // Coming back to Files after an agent run should show what it changed;
    // the first composition is covered by the clone's own load.
    LaunchedEffect(selected) {
        if (selected == 0) {
            if (filesShown) viewModel.refresh() else filesShown = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selected,
            containerColor = Color.Transparent,
            divider = { VoidDivider() },
        ) {
            Tab(
                selected = selected == 0,
                onClick = { selected = 0 },
                text = { Text("Files", style = androidx.compose.material3.MaterialTheme.typography.titleSmall) },
            )
            Tab(
                selected = selected == 1,
                onClick = { selected = 1 },
                text = { Text("Chat", style = androidx.compose.material3.MaterialTheme.typography.titleSmall) },
            )
        }

        when (selected) {
            0 -> WorkspaceScreen(
                onExit = onExit,
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
            )
            else -> ChatScreen(modifier = Modifier.weight(1f))
        }
    }
}
