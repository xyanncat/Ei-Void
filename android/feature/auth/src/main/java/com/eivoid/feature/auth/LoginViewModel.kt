package com.eivoid.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.eivoid.core.common.auth.AuthConfiguration
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class LoginUiState(
    val busy: Boolean = false,
    val status: String = "",
    val isLoggedIn: Boolean = false,
    /** Signed in to Clerk, but GitHub is not linked to the account yet. */
    val needsGithub: Boolean = false,
)

/**
 * Clerk is the only sign-in: Discord, Google, email and GitHub all enter
 * through one Clerk account. A GitHub token is still required for clone and
 * push, so a user who signed up another way links GitHub once in the Clerk
 * Account Portal.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val clerk: ClerkAuthRepository,
    configuration: AuthConfiguration,
) : ViewModel() {

    val clerkConfigured: Boolean = configuration.clerkConfigured
    val backendConfigured: Boolean = configuration.backendConfigured
    val accountPortalUrl: String? = configuration.accountPortal

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        // Reading the encrypted store touches the disk: keep it off the main thread.
        viewModelScope.launch {
            val loggedIn = withContext(Dispatchers.IO) { repository.isLoggedIn() }
            _state.update { it.copy(isLoggedIn = loggedIn) }
        }

        if (clerkConfigured) {
            // Returning users already have a Clerk session; exchange it once
            // the local GitHub token is missing.
            viewModelScope.launch {
                val ready = withTimeoutOrNull(10_000) {
                    Clerk.isInitialized.first { it }
                } != null
                if (!ready) {
                    _state.update {
                        it.copy(status = "Clerk is not ready. Check the build setup.")
                    }
                    return@launch
                }
                val loggedIn = withContext(Dispatchers.IO) { repository.isLoggedIn() }
                if (loggedIn || Clerk.userFlow.value == null) return@launch
                connectGithub()
            }
        }
    }

    /** Called by the in-app [AuthView] when Clerk has finished signing the user in. */
    fun onClerkAuthComplete() {
        if (_state.value.busy) return
        viewModelScope.launch { connectGithub() }
    }

    /** Retry after the user links GitHub in the Clerk Account Portal. */
    fun retryGithub() {
        if (_state.value.busy) return
        viewModelScope.launch { connectGithub() }
    }

    private suspend fun connectGithub() {
        if (!backendConfigured) {
            _state.update {
                it.copy(
                    busy = false,
                    status = "Signed in to Clerk. The GitHub bridge is not configured yet.",
                )
            }
            return
        }
        _state.update { it.copy(busy = true, status = "Connecting GitHub…") }
        clerk.connectGithub()
            .onSuccess {
                _state.update {
                    it.copy(
                        busy = false,
                        isLoggedIn = true,
                        needsGithub = false,
                        status = "Signed in.",
                    )
                }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        busy = false,
                        needsGithub = error is GithubNotConnectedException,
                        status = error.message ?: "Could not connect GitHub.",
                    )
                }
            }
    }

    /** Debug only: seeds a placeholder session so the UI can be reviewed without Clerk. */
    fun debugSignIn() {
        repository.debugSignIn()
        _state.update {
            it.copy(isLoggedIn = true, busy = false, status = "Debug session.")
        }
    }

    fun logout() {
        if (clerkConfigured) viewModelScope.launch { clerk.signOut() }
        repository.logout()
        _state.value = LoginUiState()
    }
}
