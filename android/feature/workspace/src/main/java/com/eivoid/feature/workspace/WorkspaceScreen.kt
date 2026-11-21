package com.eivoid.feature.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eivoid.core.workspace.FileContent
import com.eivoid.core.workspace.FileInfo
import com.eivoid.designsystem.component.VoidAvatar
import com.eivoid.designsystem.component.VoidCard
import com.eivoid.designsystem.component.VoidEmptyState
import com.eivoid.designsystem.component.VoidStatusChip
import com.eivoid.designsystem.component.VoidTopBar
import com.eivoid.designsystem.theme.VoidCodeStyle
import java.text.DateFormat
import java.util.Date

@Composable
fun WorkspaceScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // System Back mirrors the header arrow: close the file, then go up a
    // directory, then leave the workspace.
    BackHandler(enabled = state.openFile != null || state.currentPath.isNotEmpty()) {
        if (state.openFile != null) viewModel.closeFile() else viewModel.goUp()
    }

    Column(modifier = modifier.fillMaxSize()) {
        VoidTopBar(
            title = state.title,
            subtitle = state.openFile?.path ?: state.currentPath.ifEmpty { "root" },
            onBack = {
                when {
                    state.openFile != null -> viewModel.closeFile()
                    viewModel.goUp() -> onExit()
                }
            },
            actions = {
                IconButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh files")
                }
            },
        )

        if (state.isLoading) {
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }

        state.error?.let { message ->
            VoidEmptyState(
                icon = Icons.Filled.Warning,
                title = "Can't open this repository",
                message = message,
                actionLabel = "Try again",
                onAction = viewModel::retry,
                accent = MaterialTheme.colorScheme.error,
            )
        } ?: run {
            val openFile = state.openFile
            if (openFile != null) {
                FileViewer(openFile)
            } else if (state.files.isEmpty() && !state.isLoading) {
                VoidEmptyState(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "Nothing here",
                    message = "This directory has no files.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.files, key = { it.path }) { file ->
                        FileRow(
                            file = file,
                            onClick = {
                                if (file.isDirectory) {
                                    viewModel.openDirectory(file.name)
                                } else {
                                    viewModel.openFile(file.path)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: FileInfo, onClick: () -> Unit) {
    VoidCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoidAvatar(
                text = file.name,
                size = 38.dp,
                color = if (file.isDirectory) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (file.isDirectory) "${file.name}/" else file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (file.isDirectory) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        if (file.isDirectory) {
                            append("Directory")
                        } else {
                            append(formatSize(file.sizeBytes))
                        }
                        append(" · ")
                        append(DateFormat.getDateTimeInstance().format(Date(file.lastModified)))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (file.isDirectory) {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FileViewer(content: FileContent) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        if (content.truncated) {
            VoidStatusChip(
                text = "First 500 lines",
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        VoidCard(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                ) {
                    content.lines.forEachIndexed { index, line ->
                        Text(
                            text = "${index + 1}\t$line",
                            style = VoidCodeStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}
