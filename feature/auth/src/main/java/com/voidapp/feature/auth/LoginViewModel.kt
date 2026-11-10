package com.voidapp.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voidapp.core.common.github.DeviceCode
import com.voidapp.core.common.github.TokenResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LoginUiState(
    val deviceCode: DeviceCode? = null,
    val status: String = "",
    val busy: Boolean = false,
    val isLoggedIn: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        // Reading the encrypted store touches the disk: keep it off the main thread.
        viewModelScope.launch {
            val loggedIn = withContext(Dispatchers.IO) { repository.isLoggedIn() }
            _state.update { it.copy(isLoggedIn = loggedIn) }
        }
    }

    fun startLogin() {
        if (_state.value.busy) return
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            _state.update { it.copy(busy = true, status = "Requesting a sign-in code…") }
            runCatching { repository.requestDeviceCode() }
                .onSuccess { code ->
                    _state.update {
                        it.copy(
                            deviceCode = code,
                            status = "Enter the code on GitHub to continue.",
                        )
                    }
                    pollUntilResolved(code)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            busy = false,
                            status = error.message ?: "Could not reach GitHub.",
                        )
                    }
                }
        }
    }

    private suspend fun pollUntilResolved(code: DeviceCode) {
        var intervalSeconds = code.interval.coerceAtLeast(1).toLong()
        val expiresAt = System.currentTimeMillis() + code.expiresIn * 1_000L

        while (System.currentTimeMillis() < expiresAt) {
            delay(intervalSeconds * 1_000L)
            when (val result = repository.pollForToken(code)) {
                is TokenResult.Success -> {
                    _state.update {
                        it.copy(busy = false, isLoggedIn = true, status = "Signed in.")
                    }
                    return
                }
                TokenResult.AuthorizationPending -> Unit
                TokenResult.SlowDown -> intervalSeconds += 5
                TokenResult.Expired -> {
                    _state.update { it.copy(busy = false, status = "The code expired. Try again.") }
                    return
                }
                is TokenResult.Failed -> {
                    _state.update { it.copy(busy = false, status = result.message) }
                    return
                }
            }
        }
        _state.update { it.copy(busy = false, status = "The code expired. Try again.") }
    }

    fun logout() {
        pollJob?.cancel()
        repository.logout()
        _state.value = LoginUiState()
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
