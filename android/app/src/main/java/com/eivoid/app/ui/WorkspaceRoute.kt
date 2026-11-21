package com.eivoid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eivoid.designsystem.component.EiIconButton
import com.eivoid.designsystem.component.EiPill
import com.eivoid.designsystem.component.EiScreen
import com.eivoid.designsystem.component.EiTopBar
import com.eivoid.feature.chat.ChatScreen
import com.eivoid.feature.workspace.WorkspaceScreen
import com.eivoid.feature.workspace.WorkspaceViewModel

/**
 * One repository, two views: its files and the conversation about it.
 *
 * The OLED header and the Files/Chat pills live here; the screens below draw
 * only their own content.
 */
@Composable
fun WorkspaceRoute(
    onExit: () -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var filesShown by rememberSaveable { mutableStateOf(false) }

    // Coming back to Files after an agent run should show what it changed;
    // the first composition is covered by the clone's own load.
    LaunchedEffect(selected) {
        if (selected == 0) {
            if (filesShown) viewModel.refresh() else filesShown = true
        }
    }

    EiScreen {
        EiTopBar(
            title = state.title,
            subtitle = state.openFile?.path ?: state.currentPath.ifEmpty { "root" },
            onBack = onExit,
            actions = {
                EiIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Refresh files",
                    onClick = viewModel::refresh,
                )
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EiPill(
                text = "Files",
                selected = selected == 0,
                onClick = { selected = 0 },
            )
            EiPill(
                text = "Chat",
                selected = selected == 1,
                onClick = { selected = 1 },
            )
        }

        if (selected == 0) {
            WorkspaceScreen(
                onExit = onExit,
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
                showHeader = false,
            )
        } else {
            ChatScreen(modifier = Modifier.weight(1f))
        }
    }
}
