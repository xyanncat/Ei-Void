package com.voidapp.feature.repos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voidapp.core.common.github.Repo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
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

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            runCatching { repository.refreshRepos() }
                .onFailure { _error.value = it.message ?: "Could not load repositories." }
            _isRefreshing.value = false
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
