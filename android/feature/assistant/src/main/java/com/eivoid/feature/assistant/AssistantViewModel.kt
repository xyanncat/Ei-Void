package com.eivoid.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the assistant home draws. */
data class AssistantUiState(
    val input: String = "",
    val messages: List<AssistantMessage> = emptyList(),
    val streaming: Boolean = false,
    val incognito: Boolean = false,
    val error: String? = null,
    val showTaskPicker: Boolean = false,
)

/**
 * The assistant conversation. The screen stays dumb: this view model owns the
 * transcript, the streaming target and the picker/incognito toggles.
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val engine: AssistantEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    /** Monotonic ids keep list keys stable without pulling in a UUID source. */
    private var nextMessageId = 0

    private fun messageId(role: AssistantRole): String {
        nextMessageId += 1
        return "${role.name.lowercase()}-$nextMessageId"
    }

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value) }
    }

    /** A chip or Explore card: put the prompt in the bar, close the picker. */
    fun addTask(label: String) {
        _state.update { it.copy(input = label, showTaskPicker = false) }
    }

    fun openTaskPicker() {
        _state.update { it.copy(showTaskPicker = true) }
    }

    fun closeTaskPicker() {
        _state.update { it.copy(showTaskPicker = false) }
    }

    fun toggleIncognito() {
        _state.update { it.copy(incognito = !it.incognito, error = null) }
    }

    fun clearChat() {
        _state.update { it.copy(messages = emptyList(), error = null, streaming = false) }
    }

    /**
     * Sends the trimmed input. The user turn and an empty assistant turn are
     * appended together so the transcript never flickers; deltas fill the
     * assistant turn in place. A failure keeps the user turn and shows the
     * short error instead of a half-written reply.
     */
    fun send() {
        val snapshot = _state.value
        val prompt = snapshot.input.trim()
        if (prompt.isEmpty() || snapshot.streaming) return

        val history = snapshot.messages
        val assistantId = messageId(AssistantRole.ASSISTANT)
        val userMessage = AssistantMessage(
            id = messageId(AssistantRole.USER),
            role = AssistantRole.USER,
            text = prompt,
        )

        _state.update {
            it.copy(
                input = "",
                messages = it.messages + userMessage +
                    AssistantMessage(assistantId, AssistantRole.ASSISTANT, ""),
                streaming = true,
                error = null,
            )
        }

        viewModelScope.launch {
            val result = engine.send(prompt, history) { delta ->
                _state.update { current ->
                    current.copy(
                        messages = current.messages.map { message ->
                            if (message.id == assistantId) {
                                message.copy(text = message.text + delta)
                            } else {
                                message
                            }
                        },
                    )
                }
            }

            val failure = result.exceptionOrNull()
            _state.update { current ->
                if (failure == null) {
                    current.copy(streaming = false)
                } else {
                    val placeholder = current.messages.lastOrNull()
                    val messages = if (
                        placeholder != null &&
                        placeholder.id == assistantId &&
                        placeholder.text.isBlank()
                    ) {
                        current.messages.dropLast(1)
                    } else {
                        current.messages
                    }
                    current.copy(
                        streaming = false,
                        error = failure.message ?: AssistantEngine.GENERIC_FAILURE_MESSAGE,
                        messages = messages,
                    )
                }
            }
        }
    }
}
