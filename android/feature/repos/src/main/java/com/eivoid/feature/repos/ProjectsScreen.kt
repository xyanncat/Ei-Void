package com.eivoid.feature.repos

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eivoid.core.common.github.Repo
import com.eivoid.core.database.RepoEntity
import com.eivoid.designsystem.component.EiCard
import com.eivoid.designsystem.component.EiDimens
import com.eivoid.designsystem.component.EiEmptyState
import com.eivoid.designsystem.component.EiIconButton
import com.eivoid.designsystem.component.EiNotice
import com.eivoid.designsystem.component.EiScreen
import com.eivoid.designsystem.component.EiSectionLabel
import com.eivoid.designsystem.component.EiStat
import com.eivoid.designsystem.component.EiStatusPill
import com.eivoid.designsystem.component.EiTextField
import com.eivoid.designsystem.component.EiTopBar
import com.eivoid.designsystem.theme.VoidColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Projects workspace.
 *
 * A quiet OLED ledger: one summary card for the workspace, then a single
 * column of repository cards. Each card leads with a monogram, names the repo
 * in white, keeps the owner secondary and carries branch / visibility /
 * linked state as pills. The blue accent is reserved for an open project and
 * the one refresh action, so the screen stays calm at any list length.
 */
@Composable
fun ProjectsScreen(
    onOpenDrawer: () -> Unit,
    onOpenRepo: (RepoEntity) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReposViewModel = hiltViewModel(),
) {
    val repos by viewModel.repos.collectAsStateWithLifecycle()
    val linked by viewModel.linked.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshIfEmpty() }

    EiScreen(modifier = modifier) {
        EiTopBar(
            title = "Projects",
            subtitle = "Your GitHub workspace",
            onMenu = onOpenDrawer,
            actions = {
                EiIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Refresh projects",
                    onClick = viewModel::refresh,
                    modifier = Modifier.size(48.dp),
                    tint = if (isRefreshing) {
                        VoidColors.oledTextSecondary
                    } else {
                        VoidColors.oledTextPrimary
                    },
                )
            },
        )

        ProjectsSearch(
            query = query,
            onQueryChange = viewModel::onQueryChange,
        )

        if (error != null) {
            EiNotice(
                message = error.orEmpty(),
                actionLabel = "Try again",
                onAction = viewModel::refresh,
                danger = true,
                modifier = Modifier.padding(
                    start = EiDimens.screenPadding,
                    end = EiDimens.screenPadding,
                    bottom = 12.dp,
                ),
            )
        }

        when {
            isRefreshing && repos.isEmpty() -> LoadingProjects(
                modifier = Modifier.weight(1f),
            )

            repos.isEmpty() && query.isNotBlank() -> EmptyProjects(
                modifier = Modifier.weight(1f),
                title = "No matching projects",
                message = "Nothing matches \"$query\". Try another name or owner.",
                actionLabel = "Clear search",
                onAction = { viewModel.onQueryChange("") },
                search = true,
            )

            repos.isEmpty() -> EmptyProjects(
                modifier = Modifier.weight(1f),
                title = "No projects yet",
                message = "Connect GitHub and clone a repository to start.",
                actionLabel = "Refresh",
                onAction = viewModel::refresh,
                search = false,
            )

            else -> ProjectsList(
                repos = repos,
                linkedCount = linked.size,
                query = query,
                onOpenRepo = onOpenRepo,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProjectsSearch(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    EiTextField(
        value = query,
        onValueChange = onQueryChange,
        label = "Search projects",
        placeholder = "Name, owner, or branch",
        trailingIcon = {
            if (query.isEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = VoidColors.oledTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = VoidColors.oledTextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EiDimens.screenPadding)
            .padding(bottom = 12.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectsList(
    repos: List<Repo>,
    linkedCount: Int,
    query: String,
    onOpenRepo: (RepoEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = EiDimens.screenPadding,
            end = EiDimens.screenPadding,
            top = 2.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (query.isBlank()) {
            item(key = "workspace-summary") {
                WorkspaceSummary(
                    repoCount = repos.size,
                    linkedCount = linkedCount,
                )
            }
        }

        item(key = "list-heading") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EiSectionLabel(
                    text = if (query.isBlank()) "Recently updated" else "Search results",
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = repos.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = VoidColors.oledTextSecondary,
                )
            }
        }

        items(repos, key = { repo -> repo.id }) { repo ->
            ProjectCard(
                repo = repo,
                onClick = { onOpenRepo(repo.toEntity()) },
            )
        }
    }
}

/** A two-value overview of the workspace, shown above the repository list. */
@Composable
private fun WorkspaceSummary(
    repoCount: Int,
    linkedCount: Int,
) {
    EiCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EiStat(
                value = repoCount.toString(),
                label = "Repositories",
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(EiDimens.hairline)
                    .height(30.dp)
                    .background(VoidColors.oledBorder),
            )
            Spacer(Modifier.width(18.dp))
            EiStat(
                value = linkedCount.toString(),
                label = "Linked",
                modifier = Modifier.weight(1f),
                accent = VoidColors.oledAccentBlue,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectCard(
    repo: Repo,
    onClick: () -> Unit,
) {
    EiCard(
        onClick = onClick,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RepoMonogram(repo)
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VoidColors.oledTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = repo.owner,
                    style = MaterialTheme.typography.bodySmall,
                    color = VoidColors.oledTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EiStatusPill(
                        text = repo.defaultBranch.take(18),
                        color = VoidColors.oledAccentBlue,
                    )
                    EiStatusPill(
                        text = if (repo.isPrivate) "Private" else "Public",
                        color = if (repo.isPrivate) {
                            VoidColors.oledAccentAmber
                        } else {
                            VoidColors.oledSuccess
                        },
                    )
                    if (repo.isLinked) {
                        EiStatusPill(
                            text = "Linked",
                            color = VoidColors.oledAccentBlue,
                        )
                    }
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    text = updatedLabel(repo.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = VoidColors.oledTextSecondary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = VoidColors.oledTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A square monogram tile; linked projects pick up the blue accent. */
@Composable
private fun RepoMonogram(repo: Repo) {
    val linked = repo.isLinked
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (linked) {
                    VoidColors.oledAccentBlue.copy(alpha = 0.18f)
                } else {
                    VoidColors.oledSurfaceHigh
                },
            )
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = repo.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (linked) VoidColors.oledAccentBlue else VoidColors.oledTextPrimary,
        )
    }
}

@Composable
private fun EmptyProjects(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    search: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        EiEmptyState(
            title = title,
            message = message,
            icon = if (search) Icons.Filled.Search else Icons.AutoMirrored.Filled.List,
            actionLabel = actionLabel,
            onAction = onAction,
        )
    }
}

/** Calm, pulsing skeleton cards while the first page loads. */
@Composable
private fun LoadingProjects(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "projects-loading")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "projects-loading-pulse",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = EiDimens.screenPadding)
            .clearAndSetSemantics { contentDescription = "Loading projects" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(2.dp))
        repeat(4) { index ->
            EiCard(contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(VoidColors.oledSurfaceHigh.copy(alpha = pulse)),
                    )
                    Spacer(Modifier.width(13.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (index % 2 == 0) 0.55f else 0.72f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(VoidColors.oledSurfaceHigh.copy(alpha = pulse)),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.3f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(VoidColors.oledSurfaceHigh.copy(alpha = pulse)),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(2) {
                                Box(
                                    modifier = Modifier
                                        .width(62.dp)
                                        .height(20.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(VoidColors.oledSurfaceHigh.copy(alpha = pulse)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val updatedDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** "Updated 4m ago" for recent work, an absolute date for older history. */
private fun updatedLabel(updatedAt: Long): String {
    if (updatedAt <= 0L) return "Update time unavailable"

    val delta = System.currentTimeMillis() - updatedAt
    if (delta < 0L) return "Updated just now"

    val minutes = delta / 60_000L
    val hours = delta / 3_600_000L
    val days = delta / 86_400_000L

    return when {
        minutes < 1L -> "Updated just now"
        minutes < 60L -> "Updated ${minutes}m ago"
        hours < 24L -> "Updated ${hours}h ago"
        days < 7L -> "Updated ${days}d ago"
        days < 30L -> "Updated ${days / 7L}w ago"
        else -> {
            val date = Instant.ofEpochMilli(updatedAt)
                .atZone(ZoneId.systemDefault())
                .format(updatedDateFormatter)
            "Updated $date"
        }
    }
}
