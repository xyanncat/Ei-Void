# Void — Core Architecture Design

**Date:** 2026-11-10
**Status:** Design approved — pending spec review
**Scope:** Sub-project 1 — Core Agent + Workspace + GitHub
**Platform:** Android 15+ (ARM64) · Kotlin + Jetpack Compose

---

## 1. Overview

Void is a mobile AI agent that brings the Codex / Claude Code workflow to Android. A user logs in with GitHub, picks a repository, and chats with an agent that reads, edits, and organizes their code, then commits and pushes changes back to GitHub. Everything runs on-device; GitHub is the only remote.

The agent is a local **tool-use loop**: the model chooses tools, the app executes them against a sandboxed workspace and a real git repository, and the results feed back into the model. There is no shell and no backend.

## 2. Goals

- Full AI coding-agent workflow on a phone: chat, file read/write, git commit/push
- Per-user AI provider connections (OpenRouter, Nvidia NIM, Kira AI, Nara Router) with a smart router
- GitHub OAuth login and repo selection
- Real git via JGit: shallow clones, app-private workspace
- Illustrative + minimal UI, dark-first, 2 complementary colors + 1 accent
- APK distributed via GitHub Releases

## 3. Non-goals (v1)

- No local shell / command execution
- No cloud sandbox or backend services
- No on-device model inference
- No Play Store distribution
- No iOS (Kotlin Multiplatform deferred)

## 4. Constraints

| Constraint | Value |
|---|---|
| minSdk / targetSdk / compileSdk | 35 / 35 / 35 |
| ABI | arm64-v8a only |
| Storage | app-private, no permissions |
| AI | user-provided API keys only |
| Offline | workspace browsable, agent unavailable |

## 5. Module structure

Base package: `com.void`

```
void/
  app/                              com.void.app
    MainActivity, VoidApp (Hilt), NavHost, onboarding gate
  core/
    agent/                          com.void.core.agent
      AgentEngine, AgentSink, ToolRegistry, Tool, ToolResult
      tools/                        ListFiles, ReadFile, WriteFile, EditFile,
                                    SearchFiles, CreateFile, DeleteFile,
                                    GitStatus, GitDiff, GitLog, GitCommit,
                                    GitPush, GitBranch, GitCheckout,
                                    ListRepos, CreateRepo, RepoInfo
      planner/                      Planner, Plan, Step
      context/                      ContextManager, MessageHistory, TokenBudget
    ai/                             com.void.core.ai
      AiProvider, ChatRequest, ChatChunk, Model, Tier
      openai/                       OpenAiCompatibleAdapter, OpenAiConfig
      router/                       SmartRouter, TaskClassifier
      models/                       ModelCatalog
    git/                            com.void.core.git
      GitClient, CloneManager, GitAuth, GitResult, GitConflict
    workspace/                      com.void.core.workspace
      WorkspaceManager, FileTree, FileInfo, DiffEngine, WorkspaceStorage
    security/                       com.void.core.security
      SecretStore (EncryptedSharedPreferences), TokenStore, KeyStoreFactory
    common/                         com.void.core.common
      Result types, Dispatchers, Logger, extensions
  feature/
    auth/                           com.void.feature.auth
      LoginScreen, LoginViewModel, AuthRepository
    repos/                          com.void.feature.repos
      ReposScreen, ReposViewModel, RepoPicker, CloneProgress
    chat/                           com.void.feature.chat
      ChatScreen, ChatViewModel, MessageList, ToolCallCard, DiffCard
    editor/                         com.void.feature.editor
      EditorScreen, EditorViewModel, CodeView, DiffView, FileTreePane
    settings/                       com.void.feature.settings
      SettingsScreen, SettingsViewModel, ProviderConfigScreen, ModelDefaults
  designsystem/                     com.void.designsystem
    theme/ (Color, Type, Shape, Theme), tokens/, components/, illustration/
```

**Key libraries:** Compose BOM + Material 3 · Coroutines/Flow · Ktor Client (SSE) · JGit · Hilt · Room · DataStore · kotlinx.serialization · Coil.

## 6. Agent engine

### 6.1 Loop

```
user message
  → Planner (complex tasks only): split into steps
  → loop (max N iterations):
      model = SmartRouter.pick(task, context)
      response = model.chat(messages, tools)
      if no tool calls → final answer, stop
      execute tool calls against workspace/git
      append results to context
  → if workspace dirty → commit + push (with confirmation)
```

The loop is implemented in `AgentEngine.run(task, workspace, session)` as a suspend function emitting `AgentEvent`s through `AgentSink`:

```kotlin
sealed interface AgentEvent {
    data class Thinking(val model: String) : AgentEvent
    data class Token(val text: String) : AgentEvent          // streaming
    data class ToolCallStarted(val call: ToolCall) : AgentEvent
    data class ToolCallFinished(val call: ToolCall, val result: ToolResult) : AgentEvent
    data class PlanUpdated(val plan: Plan) : AgentEvent
    data class Finished(val summary: String) : AgentEvent
    data class Failed(val error: AgentError) : AgentEvent
}
```

### 6.2 Tools

Each tool implements:

```kotlin
interface Tool {
    val name: String
    val description: String
    val parameters: JsonSchema
    suspend fun execute(arguments: JsonObject, ctx: ToolContext): ToolResult
}
```

`ToolContext` carries the `Workspace`, `GitClient`, `Session`, and a `ConfirmationGate`.

| Tool | Parameters | Returns |
|---|---|---|
| `list_files` | `path` | `List<FileInfo>` |
| `read_file` | `path`, `start_line?`, `end_line?` | lines with numbers; truncated if oversized |
| `write_file` | `path`, `content` | bytes written; creates dirs |
| `edit_file` | `path`, `old_text`, `new_text` | edit applied; fails if not found / ambiguous |
| `search_files` | `query`, `glob?`, `regex?` | file + line matches |
| `create_file` | `path`, `content` | created path |
| `delete_file` | `path` | deleted; **requires confirmation** |
| `git_status` | — | modified / staged / untracked |
| `git_diff` | `path?` | unified diff |
| `git_log` | `limit?` | recent commits |
| `git_commit` | `message` | commit hash; stages all changes |
| `git_push` | — | push result |
| `git_branch` | — | branches + current |
| `git_checkout` | `branch` | checkout result |
| `list_repos` | `query?` | user repos |
| `create_repo` | `name`, `description?`, `private` | created repo; **requires confirmation** |
| `repo_info` | `name` | metadata |

Tool errors are returned as `ToolResult.Error` and fed back to the model — the agent can self-correct.

### 6.3 Context management

`ContextManager` builds the message list for each model call:

1. System prompt (agent role, workspace path, git state, tool rules)
2. Plan (if any)
3. Conversation history (last N turns)
4. Tool results from the current loop

Rules:
- Truncate file reads to a max line count; include a truncation notice.
- When over the model's token budget, summarize the oldest turns into a rolling summary.
- Per-model context window from `Model.contextWindow`; leave headroom for output.

### 6.4 Planner

`Planner` runs only when `TaskClassifier` marks a task as complex (multi-file, refactor, scaffold). It produces a `Plan` (list of `Step`s) that is shown in the UI and used to keep the loop on track. Simple tasks skip planning.

### 6.5 Guardrails

- Max iterations per run (default 25)
- Max total output tokens per run
- Destructive operations (`delete_file`, `create_repo`, force push) require explicit user confirmation via `ConfirmationGate`
- Secrets (API keys, tokens) never logged or included in telemetry
- Tool execution timeout per call

## 7. Workspace & git

### 7.1 Storage layout

```
filesDir/void/
  workspaces/<owner>/<repo>/     cloned repository (real .git)
  sessions/<sessionId>.json      conversation history backup
  database/void.db               Room
```

All app-private. No storage permissions.

### 7.2 Clone strategy

- Shallow clone (`--depth 1`) by default for speed.
- Full clone available as an option in Settings.
- Progress reported through `CloneManager` as a `Flow<CloneProgress>`.
- A repo is cloned on first selection; subsequent opens reuse the local copy.

### 7.3 Git operations

`GitClient` wraps JGit:

```kotlin
interface GitClient {
    suspend fun clone(repo: Repo, progress: CloneProgress.() -> Unit): Result<Workspace>
    suspend fun pull(workspace: Workspace): Result<Unit>
    suspend fun status(workspace: Workspace): Result<GitStatus>
    suspend fun diff(workspace: Workspace, path: String?): Result<String>
    suspend fun log(workspace: Workspace, limit: Int): Result<List<Commit>>
    suspend fun commit(workspace: Workspace, message: String): Result<String>
    suspend fun push(workspace: Workspace): Result<Unit>
    suspend fun branches(workspace: Workspace): Result<List<Branch>>
    suspend fun checkout(workspace: Workspace, branch: String): Result<Unit>
}
```

Auth: OAuth token as HTTPS credential (`UsernamePasswordCredentialsProvider("x-access-token", token)`).

### 7.4 Concurrency & locking

- All JGit calls run on `Dispatchers.IO` via a **per-repo single-thread executor** to avoid `.git/index.lock` contention.
- If a lock is detected, surface a clear error instead of failing silently.
- Merge conflicts are detected and surfaced in the UI; the agent can attempt a resolution or the user can intervene.

### 7.5 Diffing

- `DiffEngine` produces unified diffs via JGit.
- A line-level diff (additions/deletions) is computed for the diff view using a standard diff algorithm.
- `DiffCard` in the chat renders per-file diffs with syntax-highlighted additions/removals.

## 8. AI provider layer

### 8.1 Provider interface

```kotlin
interface AiProvider {
    val id: String
    suspend fun validate(key: String): Boolean
    fun listModels(): List<Model>
    fun chat(request: ChatRequest): Flow<ChatChunk>
}
```

`ChatRequest` carries messages, tool definitions, temperature, and max tokens. `ChatChunk` is either a token delta, a tool call, or a finish reason.

### 8.2 Adapters

One `OpenAiCompatibleAdapter` covers **OpenRouter, Nvidia NIM, Kira AI, Nara Router** — all expose OpenAI-compatible `/v1/chat/completions`. Per-provider config supplies base URL, auth header, and model mapping. A custom adapter is written only if a provider deviates.

Free models: OpenRouter `:free` suffixed models and the NIM free endpoint are marked `isFree = true` and preferred by the router when quality suffices.

### 8.3 Smart router

`SmartRouter.pick(task, context)`:

1. `TaskClassifier` labels the task: `edit` · `refactor` · `explain` · `scaffold` · `review`
2. Map label → tier: `fast` / `standard` / `strong`
3. Pick the best available model in that tier from the user's configured providers
4. Fall back to the next provider on failure

The user sets a default model in Settings; the router may upgrade for complex tasks.

### 8.4 Key storage

API keys are stored in `EncryptedSharedPreferences` (AES-256, Android Keystore). Keys are never written to logs, crashes, or analytics. Each provider has its own key entry.

## 9. GitHub auth

### 9.1 Device flow

1. App requests a device code from `POST /login/device/code` (client_id, scopes)
2. UI shows the user code + opens the verification URL
3. App polls `POST /login/oauth/access_token` at the server-provided interval
4. On success, store the access token

Scopes: `repo`, `read:user`, `workflow`.

### 9.2 Token storage

The OAuth token is stored encrypted in `EncryptedSharedPreferences` and reused for both GitHub REST calls and git HTTPS operations.

### 9.3 Repo listing

`GitHubApi.listRepos(query: String?)` calls `/user/repos` with pagination (100/page), sorted by `updated`. Search is client-side over the loaded set. Results are cached in Room.

## 10. UI/UX

### 10.1 Design language

**Illustrative Design & Minimalism.** Flat vector illustrations for empty states, onboarding, and first-run moments. Minimal chrome, generous whitespace, rounded shapes, no decorative noise. One illustration per empty state; never clip-art, always on-brand line/solid vector art.

### 10.2 Color

Two complementary colors plus one attention accent. Dark-first.

| Role | Color | Hex |
|---|---|---|
| Primary | Deep Indigo | `#4F46E5` |
| Secondary (complementary) | Warm Amber | `#F59E0B` |
| Accent (mismatch — grabs attention) | Electric Coral | `#FF5A5F` |
| Surface (dark) | Near-black | `#0B0B12` |
| Surface (light) | Off-white | `#F7F7FB` |

Tokens are defined in `designsystem/theme/Color.kt` as primitive scales and mapped to Material 3 semantic roles in `Theme.kt`.

### 10.3 Typography

- UI: **Manrope**
- Code: **JetBrains Mono**

### 10.4 Screens

1. **Onboarding** — 3 illustrated pages (welcome, connect GitHub, pick a repo)
2. **Login** — GitHub device-flow code entry
3. **Repos** — list, search, "link a repo", clone progress
4. **Workspace** — file tree pane + chat pane
5. **Chat** — conversation, streaming tokens, tool-call cards, diff cards
6. **Editor** — code view, diff view
7. **Settings** — providers, API keys, model defaults, theme, account

### 10.5 Navigation

Single-activity, `Navigation Compose`. Bottom nav for the three top-level destinations (Repos, Workspace, Settings); onboarding/login shown as a gated start destination.

## 11. Persistence & security

| Data | Store |
|---|---|
| Repos, sessions, messages | Room |
| Settings (defaults, theme) | DataStore |
| OAuth token, API keys | EncryptedSharedPreferences |
| Workspace files | App-private filesystem |
| Conversation backup | JSON per session |

Security rules:
- No keys/tokens in logs, crash reports, or analytics
- Tokens never leaves the device except to their provider/GitHub
- Screens containing keys are excluded from screenshots/recents (`FLAG_SECURE` on the provider config screen)

## 12. Error handling & offline

| Failure | Behavior |
|---|---|
| Network unavailable | Exponential backoff + retry button; workspace still browsable; agent disabled with a banner |
| Git lock detected | Clear error + "retry" action |
| Merge conflict | Surface conflict in UI; agent may resolve or user intervenes |
| Auth expired | Prompt re-login |
| Agent loop exceeded | Stop, report partial progress, allow continuation |
| Provider failure | Router falls back to next configured provider |

## 13. Testing

- **Unit:** agent loop (fake provider), tool registry, router, context manager, workspace ops, git wrapper (against a local test repo)
- **UI:** Compose tests for onboarding, repos, chat rendering
- **Integration:** clone + push against a throwaway GitHub repo; OAuth device flow (manual)

Test doubles: `FakeAiProvider`, `FakeGitClient`, in-memory workspace.

## 14. Build & distribution

- Gradle with a version catalog; `arm64-v8a` ABI filter
- `minSdk 35`, `targetSdk 35`, `compileSdk 35`
- GitHub Actions workflow: on tag → build release APK → attach to GitHub Release
- Users download the APK from the Releases page and sideload

## 15. Milestones

1. **Scaffold** — Gradle modules, theme tokens, navigation shell, Hilt
2. **Security + settings** — EncryptedSharedPreferences, DataStore, provider config UI
3. **GitHub auth** — device flow, token storage, repo listing
4. **Workspace + git** — clone, file tree, read/write, diff, commit, push
5. **AI provider layer** — adapters, smart router, streaming
6. **Agent engine** — tool-use loop, tools, context, planner, guardrails
7. **Chat UI** — conversation, streaming, tool-call cards, diff cards
8. **Editor** — code view, diff view
9. **Integration + testing** — end-to-end run against a throwaway repo
10. **Build + distribution** — GitHub Actions, signed APK, Release

## 16. Risks & open questions

| Risk | Mitigation |
|---|---|
| JGit performance on large repos | Shallow clone default; progress UI; warn on very large repos |
| Context window overflow | Token budget + summarization in `ContextManager` |
| Provider API drift | Adapter interface isolates changes; version-pinned models |
| OAuth app review / rate limits | Device flow is standard; handle polling errors gracefully |
| Agent loops away on bad tasks | Max iterations, planner, user-visible plan, stop button |

Open questions (to resolve during planning):
- Exact model catalog per provider at build time
- Whether to support sparse checkout for monorepos
- Editor library choice (CodeEditor vs. custom Compose editor)
