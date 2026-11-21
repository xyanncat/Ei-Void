package com.eivoid.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eivoid.core.agent.AgentEngine
import com.eivoid.core.agent.AgentEvent
import com.eivoid.core.agent.ConfirmationGate
import com.eivoid.core.agent.ConfirmationRequest
import com.eivoid.core.agent.ToolContext
import com.eivoid.core.ai.ChatMessage as CoreChatMessage
import com.eivoid.core.ai.Role
import com.eivoid.core.common.github.Repo
import com.eivoid.core.security.SecretStore
import com.eivoid.core.workspace.WorkspaceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
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
    private var runJob: Job? = null

    /** Invalidates the finally block of a stopped run. */
    private var runGeneration = 0

    /**
     * Restores the last conversation about this repository. Sends wait for
     * this job, so a fast first message cannot race the restore.
     */
    private val restoreJob: Job = viewModelScope.launch {
        val existing = chatRepository.observeSessions(repo.fullName).first().firstOrNull()
            ?: return@launch
        if (sessionId != null) return@launch
        sessionId = existing.id
        val restored = chatRepository.observeMessages(existing.id).first()
            .mapNotNull { it.toEntry() }
        if (restored.isEmpty()) return@launch
        _state.update { current -> current.copy(entries = restored + current.entries) }
    }

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
        val generation = ++runGeneration

        runJob = viewModelScope.launch {
            val assistant = StringBuilder()
            try {
                // The restore must finish before a session is chosen, or a
                // new session could overwrite the restored one.
                restoreJob.join()
                appendEntry(ChatEntry.User(text))
                val session = ensureSession(text)
                // Read the history before this turn is persisted, so the
                // current task is not sent twice.
                val history = chatRepository.observeMessages(session).first()
                    .mapNotNull { it.toCoreMessage() }
                chatRepository.appendMessage(session, "user", text)

                val workspace = workspaceManager.workspaceFor(repo)
                if (!File(workspace.path, ".git").exists()) {
                    fail(
                        "This repository is not cloned yet. Open the Files tab " +
                            "once, then send again.",
                    )
                    return@launch
                }
                val token = secretStore.getToken()
                if (token.isNullOrBlank()) {
                    fail("Sign in to GitHub before asking the agent to work.")
                    return@launch
                }

                engine.run(
                    task = text,
                    ctx = ToolContext(workspace = workspace, gate = gate, token = token),
                    history = history,
                ).collect { event ->
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
                                is com.eivoid.core.agent.ToolResult.Success -> result.output
                                is com.eivoid.core.agent.ToolResult.Error -> result.message
                            }
                            replaceLastTool(
                                ChatEntry.Tool(
                                    name = event.call.name,
                                    detail = detail,
                                    ok = event.result is com.eivoid.core.agent.ToolResult.Success,
                                    running = false,
                                ),
                            )
                            chatRepository.appendMessage(
                                sessionId = session,
                                role = "tool",
                                content = detail,
                                toolName = event.call.name,
                                toolCallId = event.call.id,
                                toolOk = event.result is
                                    com.eivoid.core.agent.ToolResult.Success,
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
            } catch (cancelled: CancellationException) {
                // Stop was pressed; not an error to report.
                throw cancelled
            } catch (error: Exception) {
                fail(error.message ?: "The run failed.")
            } finally {
                // A stopped run must not clear the flag of the next one.
                if (generation == runGeneration) {
                    _state.update { it.copy(isRunning = false) }
                }
            }
        }
    }

    private fun fail(message: String) {
        appendEntry(ChatEntry.Failure(message))
        _state.update { it.copy(error = message) }
    }

    /** Only plain user/assistant text is replayed; tool traffic lacks its call metadata. */
    private fun ChatMessage.toCoreMessage(): CoreChatMessage? = when (role) {
        "user" -> CoreChatMessage(Role.USER, content)
        "assistant" -> content.takeIf { it.isNotBlank() }
            ?.let { CoreChatMessage(Role.ASSISTANT, it) }
        else -> null
    }

    /** Rebuilds the visible conversation from what was persisted. */
    private fun ChatMessage.toEntry(): ChatEntry? = when (role) {
        "user" -> ChatEntry.User(content)
        "assistant" -> content.takeIf { it.isNotBlank() }?.let { ChatEntry.Assistant(it) }
        "tool" -> ChatEntry.Tool(
            name = toolName ?: "tool",
            detail = content,
            ok = toolOk ?: true,
            running = false,
        )
        else -> null
    }

    /**
     * Cancels the running agent. The flow is cancelled at its next suspension
     * point; a tool already executing finishes its own call.
     */
    fun stop() {
        runGeneration++
        runJob?.cancel()
        runJob = null
        confirmation?.complete(false)
        confirmation = null
        _state.update { it.copy(isRunning = false, pendingConfirmation = null) }
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
            if (entries.lastOrNull() is ChatEntry.Assistant) {
                entries[entries.lastIndex] = ChatEntry.Assistant(text)
            } else {
                // After a tool card the next tokens belong to a new bubble;
                // the assistant text from before the tool call stays visible.
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
