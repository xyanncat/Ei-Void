package com.eivoid.feature.repos

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eivoid.core.common.github.Repo
import com.eivoid.designsystem.component.VoidAvatar
import com.eivoid.designsystem.component.VoidCard
import com.eivoid.designsystem.component.VoidEmptyState
import com.eivoid.designsystem.component.VoidSectionTitle
import com.eivoid.designsystem.component.VoidStatusChip
import com.eivoid.designsystem.component.VoidTopBar

@Composable
fun ReposScreen(
    onOpenRepo: (Repo) -> Unit,
    viewModel: ReposViewModel = hiltViewModel(),
) {
    val repos by viewModel.repos.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val linked by viewModel.linked.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshIfEmpty() }

    Column(modifier = Modifier.fillMaxSize()) {
        VoidTopBar(
            title = "Repositories",
            subtitle = "Your GitHub orbit",
            actions = {
                IconButton(onClick = viewModel::refresh, enabled = !isRefreshing) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            },
        )

        SearchRow(
            query = query,
            onQueryChange = viewModel::onQueryChange,
        )

        if (isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }

        if (error != null && repos.isNotEmpty()) {
            InlineError(
                message = error.orEmpty(),
                onDismiss = viewModel::dismissError,
            )
        }

        when {
            isRefreshing && repos.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
            }

            error != null && repos.isEmpty() -> VoidEmptyState(
                icon = Icons.Filled.Warning,
                title = "Lost in space",
                message = error.orEmpty(),
                actionLabel = "Try again",
                onAction = viewModel::refresh,
                accent = MaterialTheme.colorScheme.error,
            )

            repos.isEmpty() && query.isNotBlank() -> VoidEmptyState(
                icon = Icons.Filled.Search,
                title = "Nothing in this orbit",
                message = "No repository matches \"$query\".",
                actionLabel = "Clear search",
                onAction = { viewModel.onQueryChange("") },
            )

            repos.isEmpty() -> VoidEmptyState(
                icon = Icons.AutoMirrored.Filled.List,
                title = "Your orbit is empty",
                message = "Sync your GitHub repositories to begin.",
                actionLabel = "Sync now",
                onAction = viewModel::refresh,
            )

            else -> RepoList(
                repos = repos,
                linked = linked,
                query = query,
                onOpenRepo = onOpenRepo,
                onToggleLink = viewModel::toggleLink,
            )
        }
    }
}

@Composable
private fun SearchRow(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        placeholder = { Text("Search repositories") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.secondary,
        ),
    )
}

@Composable
private fun InlineError(message: String, onDismiss: () -> Unit) {
    VoidCard(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun RepoList(
    repos: List<Repo>,
    linked: List<Repo>,
    query: String,
    onOpenRepo: (Repo) -> Unit,
    onToggleLink: (Repo) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (linked.isNotEmpty() && query.isBlank()) {
            item(key = "projects-title") {
                VoidSectionTitle("Your projects", modifier = Modifier.padding(top = 4.dp))
            }
            item(key = "projects-strip") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(linked, key = { it.id }) { repo ->
                        ProjectCard(repo = repo, onClick = { onOpenRepo(repo) })
                    }
                }
            }
            item(key = "all-title") {
                VoidSectionTitle("All repositories", modifier = Modifier.padding(top = 8.dp))
            }
        }

        items(repos, key = { it.id }) { repo ->
            RepoCard(
                repo = repo,
                onClick = { onOpenRepo(repo) },
                onToggleLink = { onToggleLink(repo) },
            )
        }
    }
}

@Composable
private fun ProjectCard(repo: Repo, onClick: () -> Unit) {
    VoidCard(
        modifier = Modifier.width(190.dp),
        onClick = onClick,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoidAvatar(
                text = repo.name,
                size = 34.dp,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = repo.defaultBranch,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RepoCard(
    repo: Repo,
    onClick: () -> Unit,
    onToggleLink: () -> Unit,
) {
    VoidCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoidAvatar(
                text = repo.name,
                size = 46.dp,
                color = if (repo.isPrivate) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                repo.description?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (repo.isPrivate) {
                        VoidStatusChip("Private", MaterialTheme.colorScheme.secondary)
                    }
                    VoidStatusChip(
                        text = repo.defaultBranch.take(16),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (repo.isLinked) {
                        VoidStatusChip("Project", MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            IconButton(onClick = onToggleLink) {
                Icon(
                    imageVector = if (repo.isLinked) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = if (repo.isLinked) {
                        "Remove ${repo.fullName} from your projects"
                    } else {
                        "Add ${repo.fullName} to your projects"
                    },
                    tint = if (repo.isLinked) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
