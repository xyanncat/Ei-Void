package com.eivoid.feature.repos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eivoid.core.common.github.GitHubApiException
import com.eivoid.core.common.github.Repo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReposViewModel @Inject constructor(
    private val repository: ReposRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** The repositories the user added as projects. */
    val linked: StateFlow<List<Repo>> = repository.observeLinked()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val repos: StateFlow<List<Repo>> = _query
        // No debounce for the cached list: the first frame shows what Room
        // already has instead of an empty screen.
        .debounce { query -> if (query.isBlank()) 0L else 150L }
        .flatMapLatest { query ->
            if (query.isBlank()) repository.observeRepos() else repository.search(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** Links or unlinks a repository, respecting the plan's project limit. */
    fun toggleLink(repo: Repo) {
        viewModelScope.launch {
            _error.value = null
            if (repo.isLinked) {
                repository.unlink(repo.id)
                return@launch
            }
            when (val result = repository.link(repo.id)) {
                LinkResult.Linked -> Unit
                is LinkResult.LimitReached -> {
                    _error.value =
                        "Your ${result.plan.displayName} plan has ${result.plan.maxProjects} " +
                            "projects. Remove one to add another."
                }
            }
        }
    }

    /**
     * Refreshes only when the Room cache is empty, so returning to the tab
     * with cached data does not spend a network call.
     */
    fun refreshIfEmpty() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            if (repository.observeRepos().first().isEmpty()) refresh()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            runCatching { repository.refreshRepos() }
                .onFailure { error ->
                    _error.value = when (error) {
                        is GitHubApiException -> error.message ?: "Could not load repositories."
                        // Anything else is transport-level: say it in words the
                        // user can act on, never a deserializer stack message.
                        else -> "Could not reach GitHub. Check your connection and try again."
                    }
                }
            _isRefreshing.value = false
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
