# Plan B — Void agent: AI layer, engine, chat

**Goal:** turn the Plan A foundation into a working coding agent: talk to a model, let it call tools against the cloned workspace, review and push the result.

**Spec:** `docs/superpowers/specs/2026-11-10-void-core-design.md` (sections 6, 8, 10.4). Ledger: `.superpowers/sdd/void-agent/progress.md`.

**Preconditions (all met by Plan A):** GitHub device-flow login, repository list, clone, file read/write, commit/push, settings with encrypted provider keys, theme.

**Status of this plan:** draft. It follows Plan A's working agreement: implement task by task, verify, audit each task independently, commit each task.

---

## Ground rules for this plan

- The agent runs **on the phone** and talks to the user's provider **directly**. There is no backend, no proxy, no telemetry. The only network peers are GitHub and the AI provider.
- Provider keys are read from `SecretStore` per request and never logged, never written to a file, never put in a URL.
- Every tool that touches the workspace goes through `WorkspaceManager` (path-traversal safe) and every git operation goes through `GitClient` (per-repository lock).
- Destructive tools (`delete_file`, `create_repo`, force push) require a user confirmation through the `ConfirmationGate`.
- The engine is bounded: max iterations, max output tokens, per-tool timeout. A runaway model cannot loop forever.

---

### Task 1: AI provider layer

**Files:**
- Create module `core/ai` (`core/ai/build.gradle.kts`).
- Create: `core/ai/src/main/java/com/voidapp/core/ai/AiProvider.kt` — `AiProvider`, `ChatRequest`, `ChatMessage`, `ToolDefinition`, `ToolCall`, `ChatChunk`, `Model`, `Tier`.
- Create: `core/ai/src/main/java/com/voidapp/core/ai/openai/OpenAiCompatibleProvider.kt` — the one adapter every provider uses.
- Create: `core/ai/src/main/java/com/voidapp/core/ai/openai/OpenAiConfig.kt` — per-provider base URL, auth header, model prefixes.
- Create: `core/ai/src/main/java/com/voidapp/core/ai/openai/ProviderCatalog.kt` — the four configured providers.
- Test: `core/ai/src/test/java/com/voidapp/core/ai/openai/OpenAiCompatibleProviderTest.kt`.

**Interfaces:**
- Consumes: `HttpClient` (`core:common`), `SecretStore` (`core:security`).
- Produces:
  ```kotlin
  enum class Tier { FAST, STANDARD, STRONG }
  data class Model(val id: String, val providerId: String, val displayName: String,
                   val tier: Tier, val contextWindow: Int, val supportsTools: Boolean, val isFree: Boolean)
  data class ChatMessage(val role: Role, val content: String, val toolCalls: List<ToolCall> = emptyList(),
                         val toolCallId: String? = null)
  data class ToolCall(val id: String, val name: String, val arguments: JsonObject)
  data class ToolDefinition(val name: String, val description: String, val parameters: JsonObject)
  data class ChatRequest(val messages: List<ChatMessage>, val tools: List<ToolDefinition> = emptyList(),
                         val temperature: Double = 0.2, val maxTokens: Int = 4096, val model: String)
  sealed interface ChatChunk {
      data class Delta(val text: String) : ChatChunk
      data class ToolCallDelta(val call: ToolCall) : ChatChunk
      data class Finished(val reason: String) : ChatChunk
  }
  interface AiProvider {
      val id: String
      suspend fun chat(request: ChatRequest, apiKey: String): Flow<ChatChunk>
  }
  ```

**Notes / decisions:**
- Ktor 2.3 has no SSE plugin: the adapter reads `bodyAsChannel()` and parses `data: {...}` frames itself, stopping at `[DONE]`. Tool-call deltas are accumulated per index and emitted as one `ToolCall` when the stream ends.
- The adapter sends `tools` only when the request has any, so providers without tool support still work.
- `ProviderCatalog` holds: OpenRouter (`https://openrouter.ai/api/v1`), NVIDIA NIM (`https://integrate.api.nvidia.com/v1`), Kira AI and Nara Router (**base URLs to be confirmed with the user — Task 1 Step 5**).
- The adapter never sees a key it did not ask for: the caller passes the key in, the adapter never reads storage.

- [ ] **Step 1: Write the failing test**

  `OpenAiCompatibleProviderTest` with Ktor's `MockEngine`:
  - a non-streaming reply becomes one `Delta` chunk and a `Finished` chunk;
  - a streamed reply (`data: {...}` frames) becomes several `Delta` chunks in order;
  - tool calls in the stream are accumulated into a single `ToolCall` with parsed JSON arguments;
  - the `Authorization` header carries the key and the body carries the model and the messages;
  - `[DONE]` ends the flow.

- [ ] **Step 2: Run the test to verify it fails**

  Run: `./gradlew :core:ai:testDebugUnitTest`
  Expected: FAIL — module does not exist yet.

- [ ] **Step 3: Implement the module and the adapter**

  Create `core/ai`, register it in `settings.gradle.kts`, add `core:common`, `core:security`, `ktor-client-core`, `ktor-client-mock` (test only), `kotlinx-serialization-json`.

- [ ] **Step 4: Run the tests**

  Run: `./gradlew :core:ai:testDebugUnitTest`
  Expected: PASS.

- [ ] **Step 5: Confirm the provider configuration with the user**

  Ask for the exact base URL of Kira AI and Nara Router, and for one free model id per provider. Record the answers in `ProviderCatalog`.

- [ ] **Step 6: Commit**

  ```bash
  git add core/ai settings.gradle.kts gradle/libs.versions.toml
  git commit -m "feat(ai): OpenAI-compatible provider adapter"
  ```

---

### Task 2: Model catalog and smart router

**Files:**
- Create: `core/ai/src/main/java/com/voidapp/core/ai/models/ModelCatalog.kt` — the built-in model list per provider (free models flagged).
- Create: `core/ai/src/main/java/com/voidapp/core/ai/router/TaskClassifier.kt` — labels a task `edit` · `refactor` · `explain` · `scaffold` · `review`.
- Create: `core/ai/src/main/java/com/voidapp/core/ai/router/SmartRouter.kt` — picks a provider + model, falls back on failure.
- Test: `core/ai/src/test/java/com/voidapp/core/ai/router/SmartRouterTest.kt`.

**Interfaces:**
- Consumes: `AiProvider` (Task 1), `SecretStore`, `SettingsStore` (the user's default model).
- Produces:
  ```kotlin
  enum class TaskKind { EDIT, REFACTOR, EXPLAIN, SCAFFOLD, REVIEW }
  data class Route(val providerId: String, val model: Model, val fallback: Boolean = false)
  class SmartRouter {
      fun classify(task: String): TaskKind
      suspend fun pick(task: String, availableProviders: List<AiProvider>): Route
      suspend fun next(route: Route, failed: List<String>): Route?
  }
  ```

**Rules:**
- The user's default model wins when it is available; the router only upgrades for `REFACTOR`/`SCAFFOLD`.
- Free models are preferred when the task is `EDIT`/`EXPLAIN` and the tier suffices.
- A provider without a stored key is never selected.
- `next()` walks to the next candidate after a provider failure, so one dead provider cannot end a run.

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run it, verify it fails**
- [ ] **Step 3: Implement the catalog, the classifier and the router**
- [ ] **Step 4: Run `./gradlew :core:ai:testDebugUnitTest` — PASS**
- [ ] **Step 5: Commit** `feat(ai): model catalog and smart router`

---

### Task 3: Tool framework

**Files:**
- Create module `core/agent` (`core/agent/build.gradle.kts`).
- Create: `core/agent/src/main/java/com/voidapp/core/agent/Tool.kt` — `Tool`, `ToolContext`, `ToolResult`.
- Create: `core/agent/src/main/java/com/voidapp/core/agent/ToolRegistry.kt`.
- Create: `core/agent/src/main/java/com/voidapp/core/agent/ConfirmationGate.kt`.
- Test: `core/agent/src/test/java/com/voidapp/core/agent/ToolRegistryTest.kt`.

**Interfaces:**
- Consumes: `Workspace`, `WorkspaceManager`, `GitClient`, `SecretStore`, `GitHubApi`.
- Produces:
  ```kotlin
  interface Tool {
      val name: String
      val description: String
      val parameters: JsonObject          // JSON Schema
      val requiresConfirmation: Boolean get() = false
      suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult
  }
  sealed interface ToolResult {
      data class Success(val output: String) : ToolResult
      data class Error(val message: String) : ToolResult
  }
  data class ToolContext(val workspace: Workspace, val gate: ConfirmationGate, val token: String?)
  interface ConfirmationGate { suspend fun confirm(request: ConfirmationRequest): Boolean }
  ```

**Notes:**
- Tool errors are returned, never thrown: the model sees the message and can correct itself.
- `ToolRegistry` maps names to tools and rejects unknown names with a readable error.
- `parameters` is a JSON Schema object built with `kotlinx.serialization`; the model receives it verbatim.

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run it, verify it fails**
- [ ] **Step 3: Implement the framework**
- [ ] **Step 4: Run `./gradlew :core:agent:testDebugUnitTest` — PASS**
- [ ] **Step 5: Commit** `feat(agent): tool contract, registry and confirmation gate`

---

### Task 4: File tools

**Files:**
- Create: `core/agent/src/main/java/com/voidapp/core/agent/tools/FileTools.kt` — `list_files`, `read_file`, `write_file`, `edit_file`, `search_files`, `create_file`, `delete_file`.
- Test: `core/agent/src/test/java/com/voidapp/core/agent/tools/FileToolsTest.kt`.

**Interfaces:**
- Consumes: `WorkspaceManager` (Plan A).
- Produces: one `Tool` per row of the spec's table §6.2 for files.

**Rules:**
- `edit_file` fails when `old_text` is absent and fails when it matches more than one place ("ambiguous"), never guesses.
- `search_files` supports a glob and a regex, returns file + line + match.
- `delete_file` sets `requiresConfirmation = true` and refuses to delete outside the workspace (the manager already enforces this).
- `read_file` returns numbered lines and a truncation notice past the line cap.

- [ ] **Step 1: Write the failing test** — one test per tool, plus the ambiguity and confirmation cases.
- [ ] **Step 2: Run it, verify it fails**
- [ ] **Step 3: Implement the tools**
- [ ] **Step 4: Run `./gradlew :core:agent:testDebugUnitTest` — PASS**
- [ ] **Step 5: Commit** `feat(agent): file tools`

---

### Task 5: Git tools

**Files:**
- Create: `core/agent/src/main/java/com/voidapp/core/agent/tools/GitTools.kt` — `git_status`, `git_diff`, `git_log`, `git_commit`, `git_push`, `git_branch`, `git_checkout`.
- Test: `core/agent/src/test/java/com/voidapp/core/agent/tools/GitToolsTest.kt`.

**Interfaces:**
- Consumes: `GitClient` (Plan A), `DiffEngine` (Plan A), `WorkspaceManager`.
- Produces: one `Tool` per git row of the spec's table §6.2.

**Rules:**
- `git_diff` returns a unified diff; `git_commit` stages everything and reports the hash; `git_push` needs the GitHub token, which the tool reads from the `ToolContext` — the token never appears in the output.
- A rejected push is reported as an error the model can react to, not as a silent success.

- [ ] **Step 1: Write the failing test** — against a local `file://` remote, exactly like Plan A's git tests.
- [ ] **Step 2: Run it, verify it fails**
- [ ] **Step 3: Implement the tools**
- [ ] **Step 4: Run `./gradlew :core:agent:testDebugUnitTest` — PASS**
- [ ] **Step 5: Commit** `feat(agent): git tools`

---

### Task 6: GitHub tools

**Files:**
- Modify: `core/common/src/main/java/com/voidapp/core/common/github/GitHubApi.kt` — add `createRepo` and `repoInfo`.
- Create: `core/agent/src/main/java/com/voidapp/core/agent/tools/RepoTools.kt` — `list_repos`, `create_repo`, `repo_info`.
- Test: `core/agent/src/test/java/com/voidapp/core/agent/tools/RepoToolsTest.kt`.

**Interfaces:**
- Consumes: `GitHubApi` (Task 6 of Plan A), `ReposRepository`.
- Produces: `createRepo` (confirmation required), `repoInfo`.

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run it, verify it fails**
- [ ] **Step 3: Implement the API methods and the tools**
- [ ] **Step 4: Run `./gradlew :core:agent:testDebugUnitTest :core:common:testDebugUnitTest` — PASS**
- [ ] **Step 5: Commit** `feat(agent): repository tools`

---

### Task 7: Agent engine

**Files:**
- Create: `core/agent/src/main/java/com/voidapp/core/agent/AgentEngine.kt` — the loop.
- Create: `core/agent/src/main/java/com/voidapp/core/agent/AgentEvent.kt`.
- Create: `core/agent/src/main/java/com/voidapp/core/agent/context/ContextManager.kt`.
- Create: `core/agent/src/main/java/com/voidapp/core/agent/planner/Planner.kt`.
- Test: `core/agent/src/test/java/com/voidapp/core/agent/AgentEngineTest.kt`.

**Interfaces:**
- Consumes: `AiProvider` + `SmartRouter` (Tasks 1-2), `ToolRegistry` (Task 3), all tools.
- Produces:
  ```kotlin
  sealed interface AgentEvent {
      data class Thinking(val model: String) : AgentEvent
      data class Token(val text: String) : AgentEvent
      data class ToolCallStarted(val call: ToolCall) : AgentEvent
      data class ToolCallFinished(val call: ToolCall, val result: ToolResult) : AgentEvent
      data class PlanUpdated(val plan: List<String>) : AgentEvent
      data class Finished(val summary: String) : AgentEvent
      data class Failed(val message: String) : AgentEvent
  }
  class AgentEngine {
      fun run(task: String, ctx: ToolContext): Flow<AgentEvent>
  }
  ```

**Rules (guardrails, spec §6.5):**
- Max 25 iterations per run, max output tokens per run, 60 s timeout per tool call.
- The loop stops when the model answers without tool calls.
- A provider failure falls back to the next route (Task 2) once; a second failure ends the run with `Failed`.
- The context keeps the system prompt, the plan, the last N turns and the current tool results; file reads are truncated.

- [ ] **Step 1: Write the failing test** — a scripted fake provider that asks for `write_file`, then answers; assert the events, the file on disk, and that the iteration limit stops a looping model.
- [ ] **Step 2: Run it, verify it fails**
- [ ] **Step 3: Implement the engine, the context manager and the planner**
- [ ] **Step 4: Run `./gradlew :core:agent:testDebugUnitTest` — PASS**
- [ ] **Step 5: Commit** `feat(agent): agent engine, context manager and planner`

---

### Task 8: Chat session storage

**Files:**
- Modify: `core/database/src/main/java/com/voidapp/core/database/VoidDatabase.kt` — add `chat_sessions` and `chat_messages` tables.
- Create: `core/database/src/main/java/com/voidapp/core/database/ChatDao.kt`.
- Create: `feature/chat/src/main/java/com/voidapp/feature/chat/ChatRepository.kt`.
- Test: `core/database/src/test/java/com/voidapp/core/database/ChatDaoTest.kt`.

**Interfaces:**
- Consumes: Room (Plan A).
- Produces: `ChatSession(id, repoFullName, title, createdAt)`, `ChatMessage(id, sessionId, role, content, toolJson, createdAt)`, `ChatRepository`.

**Rules:**
- Messages are stored so a conversation survives the process; the agent context is rebuilt from them.
- Deleting a session deletes its messages.

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run it, verify it fails**
- [ ] **Step 3: Implement the tables, the DAO and the repository**
- [ ] **Step 4: Run `./gradlew :core:database:testDebugUnitTest` — PASS**
- [ ] **Step 5: Commit** `feat(chat): conversation storage`

---

### Task 9: Chat UI

**Files:**
- Create module `feature/chat` (`feature/chat/build.gradle.kts`).
- Create: `feature/chat/src/main/java/com/voidapp/feature/chat/ChatViewModel.kt`.
- Create: `feature/chat/src/main/java/com/voidapp/feature/chat/ChatScreen.kt`.
- Create: `feature/chat/src/main/java/com/voidapp/feature/chat/ToolCallCard.kt` — tool-call card.
- Create: `feature/chat/src/main/java/com/voidapp/feature/chat/DiffCard.kt` — diff card.
- Modify: `feature/workspace/.../WorkspaceScreen.kt` — split view: file tree pane + chat pane.
- Modify: `app/src/main/java/com/voidapp/app/navigation/VoidNavHost.kt` — the workspace route carries the chat.

**Interfaces:**
- Consumes: `AgentEngine` (Task 7), `ChatRepository` (Task 8).
- Produces: a streaming chat with tool-call cards and diff cards; the composer sends the message and renders events as they arrive.

**Rules:**
- The design language stays: flat surfaces, indigo primary, amber secondary, coral only for destructive actions.
- A pending confirmation renders as a card with Accept / Reject; the engine waits.
- Long output is not truncated in the UI (the model may need it); only the *context* is bounded.

- [ ] **Step 1: Write the failing test** — a ViewModel test with a scripted engine: the events reach the state in order, and a confirmation card blocks until answered.
- [ ] **Step 2: Run it, verify it fails**
- [ ] **Step 3: Implement the ViewModel and the screens**
- [ ] **Step 4: Run `./gradlew :feature:chat:testDebugUnitTest :app:assembleDebug` — PASS**
- [ ] **Step 5: Commit** `feat(chat): streaming chat, tool cards and diff cards`

---

### Task 10: Agent end-to-end test

**Files:**
- Create: `app/src/androidTest/java/com/voidapp/app/AgentIntegrationTest.kt`.

**Interfaces:**
- Consumes: everything above.
- Produces: one instrumented test that runs the engine against a scripted provider over a local `file://` remote and asserts the file was edited, committed and pushed.

**Notes:**
- Uses the `HiltTestRunner` from Plan A Task 10, so `@HiltAndroidTest` works.
- Needs a device or emulator. If none is attached, the test is compiled and the run is documented as pending — the same rule Plan A used.

- [ ] **Step 1: Write the test**
- [ ] **Step 2: Run `./gradlew :app:connectedDebugAndroidTest`**
- [ ] **Step 3: Fix failures and re-run**
- [ ] **Step 4: Commit** `test: agent end-to-end run against a local remote`

---

## Self-review

**Spec coverage:** §6.1 loop → Task 7. §6.2 tools → Tasks 3-6. §6.3 context → Task 7. §6.4 planner → Task 7. §6.5 guardrails → Task 7. §8.1-8.2 providers → Task 1. §8.3 router → Task 2. §8.4 key storage → Plan A Task 9. §10.4 screens 5-6 → Task 9. §11 secrets → Task 1 (keys never logged) and the tools. §13 testing → every task plus Task 10.

**Open items that need the user:** the base URLs and model ids of Kira AI and Nara Router (Task 1 Step 5); the provider keys themselves (Plan A Task 9 already stores them).

**Not in this plan:** SQLCipher wiring, biometric lock, `FLAG_SECURE` per screen, the release pipeline and the certificate pins. Those are Plan C.
