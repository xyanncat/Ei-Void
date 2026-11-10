package com.voidapp.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voidapp.core.agent.AgentEngine
import com.voidapp.core.agent.AgentEvent
import com.voidapp.core.agent.ConfirmationGate
import com.voidapp.core.agent.ConfirmationRequest
import com.voidapp.core.agent.ToolContext
import com.voidapp.core.common.github.Repo
import com.voidapp.core.security.SecretStore
import com.voidapp.core.workspace.WorkspaceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row in the conversation. */
sealed interface ChatEntry {
    data class User(val text: String) : ChatEntry
    data class Assistant(val text: String) : ChatEntry
    data class Tool(
        val name: String,
        val detail: String,
        val ok: Boolean,
        val running: Boolean,
    ) : ChatEntry
    data class Plan(val steps: List<String>) : ChatEntry
    data class Failure(val message: String) : ChatEntry
}

data class ChatUiState(
    val repoFullName: String = "",
    val entries: List<ChatEntry> = emptyList(),
    val input: String = "",
    val tier: String? = null,
    val isRunning: Boolean = false,
    val pendingConfirmation: ConfirmationRequest? = null,
    val error: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val engine: AgentEngine,
    private val workspaceManager: WorkspaceManager,
    private val secretStore: SecretStore,
    private val chatRepository: ChatRepository,
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

    private val _state = MutableStateFlow(ChatUiState(repoFullName = repo.fullName))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var sessionId: String? = null
    private var confirmation: CompletableDeferred<Boolean>? = null

    private val gate = object : ConfirmationGate {
        override suspend fun confirm(request: ConfirmationRequest): Boolean {
            val answer = CompletableDeferred<Boolean>()
            confirmation = answer
            _state.update { it.copy(pendingConfirmation = request) }
            return try {
                answer.await()
            } finally {
                confirmation = null
                _state.update { it.copy(pendingConfirmation = null) }
            }
        }
    }

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.isRunning) return

        _state.update { it.copy(input = "", isRunning = true, error = null) }
        appendEntry(ChatEntry.User(text))

        viewModelScope.launch {
            val session = ensureSession(text)
            chatRepository.appendMessage(session, "user", text)

            val workspace = workspaceManager.workspaceFor(repo)
            val context = ToolContext(
                workspace = workspace,
                gate = gate,
                token = secretStore.getToken(),
            )

            val assistant = StringBuilder()
            try {
                engine.run(text, context).collect { event ->
                    when (event) {
                        is AgentEvent.Thinking -> _state.update { it.copy(tier = event.tier) }
                        is AgentEvent.Token -> {
                            assistant.append(event.text)
                            updateAssistant(assistant.toString())
                        }
                        is AgentEvent.ToolCallStarted -> {
                            flushAssistant(session, assistant)
                            appendEntry(
                                ChatEntry.Tool(
                                    name = event.call.name,
                                    detail = event.call.arguments.toString(),
                                    ok = true,
                                    running = true,
                                ),
                            )
                        }
                        is AgentEvent.ToolCallFinished -> {
                            val detail = when (val result = event.result) {
                                is com.voidapp.core.agent.ToolResult.Success -> result.output
                                is com.voidapp.core.agent.ToolResult.Error -> result.message
                            }
                            replaceLastTool(
                                ChatEntry.Tool(
                                    name = event.call.name,
                                    detail = detail,
                                    ok = event.result is com.voidapp.core.agent.ToolResult.Success,
                                    running = false,
                                ),
                            )
                            chatRepository.appendMessage(
                                sessionId = session,
                                role = "tool",
                                content = detail,
                                toolName = event.call.name,
                                toolCallId = event.call.id,
                            )
                        }
                        is AgentEvent.PlanUpdated -> appendEntry(ChatEntry.Plan(event.steps))
                        is AgentEvent.Finished -> {
                            flushAssistant(session, assistant)
                        }
                        is AgentEvent.Failed -> {
                            flushAssistant(session, assistant)
                            appendEntry(ChatEntry.Failure(event.message))
                            _state.update { it.copy(error = event.message) }
                        }
                    }
                }
            } catch (error: Exception) {
                appendEntry(ChatEntry.Failure(error.message ?: "The run failed."))
                _state.update { it.copy(error = error.message ?: "The run failed.") }
            } finally {
                _state.update { it.copy(isRunning = false) }
            }
        }
    }

    fun approve() {
        confirmation?.complete(true)
    }

    fun reject() {
        confirmation?.complete(false)
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun ensureSession(firstMessage: String): String {
        sessionId?.let { return it }
        val created = chatRepository.createSession(repo.fullName, firstMessage)
        sessionId = created
        return created
    }

    private suspend fun flushAssistant(session: String, text: StringBuilder) {
        if (text.isEmpty()) return
        chatRepository.appendMessage(session, "assistant", text.toString())
        text.clear()
    }

    private fun appendEntry(entry: ChatEntry) {
        _state.update { it.copy(entries = it.entries + entry) }
    }

    private fun updateAssistant(text: String) {
        _state.update { current ->
            val entries = current.entries.toMutableList()
            val index = entries.indexOfLast { it is ChatEntry.Assistant }
            if (index >= 0) {
                entries[index] = ChatEntry.Assistant(text)
            } else {
                entries += ChatEntry.Assistant(text)
            }
            current.copy(entries = entries)
        }
    }

    private fun replaceLastTool(tool: ChatEntry.Tool) {
        _state.update { current ->
            val entries = current.entries.toMutableList()
            val index = entries.indexOfLast { it is ChatEntry.Tool }
            if (index >= 0) entries[index] = tool else entries += tool
            current.copy(entries = entries)
        }
    }
}
