package com.voidapp.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.voidapp.feature.chat.ChatScreen
import com.voidapp.feature.workspace.WorkspaceScreen

/**
 * One repository, two views: its files and the conversation about it.
 * Tabs instead of a split pane, because the app is phone-first.
 */
@Composable
fun WorkspaceRoute(onExit: () -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected) {
            Tab(
                selected = selected == 0,
                onClick = { selected = 0 },
                text = { Text("Files") },
            )
            Tab(
                selected = selected == 1,
                onClick = { selected = 1 },
                text = { Text("Chat") },
            )
        }

        when (selected) {
            0 -> WorkspaceScreen(onExit = onExit, modifier = Modifier.weight(1f))
            else -> ChatScreen(modifier = Modifier.weight(1f))
        }
    }
}
