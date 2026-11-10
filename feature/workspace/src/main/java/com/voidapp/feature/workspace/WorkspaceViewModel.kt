package com.voidapp.feature.workspace

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voidapp.core.common.github.Repo
import com.voidapp.core.git.CloneManager
import com.voidapp.core.security.SecretStore
import com.voidapp.core.workspace.FileContent
import com.voidapp.core.workspace.FileInfo
import com.voidapp.core.workspace.GitResult
import com.voidapp.core.workspace.Workspace
import com.voidapp.core.workspace.WorkspaceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkspaceUiState(
    val title: String = "",
    val currentPath: String = "",
    val files: List<FileInfo> = emptyList(),
    val openFile: FileContent? = null,
    val isLoading: Boolean = true,
    val progress: Int = 0,
    val error: String? = null,
)

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val cloneManager: CloneManager,
    private val workspaceManager: WorkspaceManager,
    private val secretStore: SecretStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val owner: String = savedStateHandle["owner"] ?: ""
    private val name: String = savedStateHandle["name"] ?: ""
    private val branch: String = savedStateHandle["branch"] ?: "main"

    private val repo = Repo(
        id = "$owner/$name",
        owner = owner,
        name = name,
        fullName = "$owner/$name",
        isPrivate = false,
        defaultBranch = branch,
        description = null,
        updatedAt = 0L,
    )

    private val workspace: Workspace = workspaceManager.workspaceFor(repo)

    private val _state = MutableStateFlow(WorkspaceUiState(title = repo.fullName))
    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    init {
        openRepository()
    }

    fun retry() = openRepository()

    fun openDirectory(directoryName: String) {
        val path = joinPath(_state.value.currentPath, directoryName)
        viewModelScope.launch { loadFiles(path) }
    }

    fun openFile(filePath: String) {
        viewModelScope.launch {
            runCatching { workspaceManager.readFile(workspace, filePath) }
                .onSuccess { content -> _state.update { it.copy(openFile = content) } }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "Could not open $filePath") }
                }
        }
    }

    fun closeFile() {
        _state.update { it.copy(openFile = null) }
    }

    /** Returns true when the caller should leave the screen instead. */
    fun goUp(): Boolean {
        val current = _state.value.currentPath
        if (current.isEmpty()) return true

        val parent = current.substringBeforeLast('/', missingDelimiterValue = "")
        viewModelScope.launch { loadFiles(parent) }
        return false
    }

    private fun openRepository() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, progress = 0) }

            val token = secretStore.getToken()
            if (token.isNullOrBlank()) {
                fail("Sign in to GitHub before opening a repository.")
                return@launch
            }

            when (val result = cloneManager.clone(repo, token) { progress ->
                    _state.update { it.copy(progress = progress.percent) }
                }) {
                is GitResult.Success -> loadFiles("")
                is GitResult.Failure -> fail(result.message)
                is GitResult.Conflict -> fail(result.message)
            }
        }
    }

    private suspend fun loadFiles(path: String) {
        runCatching { workspaceManager.listFiles(workspace, path) }
            .onSuccess { files ->
                _state.update {
                    it.copy(
                        currentPath = path,
                        files = files,
                        openFile = null,
                        isLoading = false,
                        error = null,
                    )
                }
            }
            .onFailure { error -> fail(error.message ?: "Could not list files.") }
    }

    private fun fail(message: String) {
        _state.update { it.copy(isLoading = false, error = message) }
    }

    private fun joinPath(parent: String, child: String) =
        if (parent.isEmpty()) child else "$parent/$child"
}
