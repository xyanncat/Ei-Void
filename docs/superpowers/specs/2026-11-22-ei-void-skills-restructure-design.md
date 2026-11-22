# Ei Void 0.4 — Skills, Server AI and Product Restructure

**Status:** Draft for review, 2026-11-22

## Goal

Turn Ei Void from an assistant shell with user-supplied provider keys into a
coherent product: one server-backed AI service, a real agent skill system with
custom skills, a Discord control centre, a first-class Reminders tab, and a
Settings screen. Remove the image-generation and audio surfaces entirely.

The visible result should feel deliberate rather than assembled: OLED surfaces,
Inter typography, a small set of high-value actions, real status in every card,
and no debug or placeholder chrome.

## Non-goals

- No image generation, image editing, audio input, audio output, voice mode,
  dictation or waveform action.
- No user-provided AI provider keys. The app never asks for, stores or sends a
  model-provider secret.
- No server-side execution of user code. Custom skills are instruction packs,
  not executable plugins.
- No billing implementation. Plan and usage are enforced by the existing
  preview plan model.
- No new Discord bot commands beyond the existing reminder/link/usage surface
  unless the Remote screen needs a read-only status call.

## Product decisions

1. **One AI service.** Model calls go through the Ei Void web service. The
   server owns `AI_API_KEY`; the app authenticates with its Clerk bearer token.
   The app still exposes the three product tiers (Red Dwarfs, White Dwarfs,
   Neutron Stars) and an Auto choice. The server maps tier aliases to real model
   ids and never returns a provider key.
2. **Chat vs Projects.** Chat is the conversational assistant and uses enabled
   skills as expert instructions. Projects is the tool-capable coding agent and
   also executes tool-backed skills such as Web Browser. The Skills screen says
   which skills are instruction-only and which run tools in Projects.
3. **Custom skills are instructions only.** A custom skill has a name, a
   one-line description, a category icon and instructions. It cannot add code,
   shell commands or tool definitions. This is a security boundary, not a
   temporary limitation.
4. **Reminder tab, not a duplicate `Library`.** The current Library screen is
   already the reminder list. It becomes the **Reminders** destination and
   absorbs the Ei Snore notification preferences currently in Customize.
5. **Remote owns Discord.** Remote becomes the Ei Void bot configuration and
   status screen: linked account, guild membership, bot availability, test DM,
   disconnect and the command reference. Settings no longer duplicates the
   Discord controls; it links to Remote.
6. **Settings, not Customize.** Rename the tab, route, title, files, copy and
   error strings to Settings. The old Customize wording disappears.
7. **OLED only.** The surface remains pure-black OLED. Light/system theme is
   not offered in this release; the existing theme preference is retired.

## Information architecture

Drawer destinations, in order:

| Tab | Purpose |
|---|---|
| Chat | Assistant home and conversation |
| Reminders | Reminder list, editor and Ei Snore notification preferences |
| Projects | Repositories and the coding-agent workspace |
| Skills | Built-in and custom agent skills |
| Remote | Ei Void Discord bot configuration and status |
| Settings | Account, model tier, usage, permissions, privacy and data |

Usage remains reachable from Settings. The old Images and Explore destinations
are removed. New chat remains a primary action in the drawer.

## Screen specifications

### Chat

- Keep the branded header, Inter type, capability cards, workspace shortcuts,
  privacy line and floating composer.
- Remove image/audio actions. The composer has attach/menu, text, and one
  send/disabled action. No mic, waveform, dictation or voice-mode callbacks.
- The plus menu becomes: New reminder, Projects, Skills, Settings.
- Add an **active skills rail** above the composer: up to three enabled skills
  with icons and a `+N` overflow; tapping opens Skills. If none are enabled, a
  quiet "Add a skill" chip opens Skills.
- Capability cards remain four; copy stays short enough for compact phones.
- Empty, loading, streaming, error and offline-service states are explicit.

### Reminders

- Rename `LibraryScreen` to `RemindersScreen` and the route to `reminders`.
- Top bar: "Reminders" / "Bills, renewals and Ei Snore".
- Existing Upcoming/Paid segments, month stats, cards and actions remain.
- Add an **Ei Snore notifications** card near the top:
  - default lead time (1/2/4/7 days)
  - reminder time
  - email copy toggle
  - Discord status summary and "Open Remote" action
  - exact-alarm / notification permission shortcuts
- The Discord controls themselves live in Remote; this card links there.
- Add reminder uses the existing editor.

### Skills

- New top-level screen with two sections: **Built in** and **Your skills**.
- Built-in skills: UI Design, Frontend Dev, Code Review, Web Browser, Web Apps,
  Discord Bots, Security Check. Each has an icon, description and an enabled
  switch. Built-ins cannot be edited or deleted.
- Custom skills: user-created instruction packs. Create, edit and delete.
  Confirmation before delete. Form fields:
  - name (2–40 characters)
  - description (1–120 characters)
  - icon/category (fixed set)
  - instructions (1–4,000 characters)
  - enabled
- Custom skills show a "Instructions only" badge; no code/tool execution.
- A search field filters by name/description.
- Empty state explains what a skill is and offers "Create your first skill".
- Skills are enabled globally; Chat uses them as prompt instructions and
  Projects also exposes the Web Browser tool when that skill is enabled.

### Remote

- Top bar: "Remote" / "Ei Void Discord bot".
- Hero status card:
  - linked Discord username or "Not linked"
  - guild membership status
  - bot availability (server has token)
  - fallback channel configured/not configured
- Primary Connect / Disconnect / Send test actions using the existing OAuth
  activity and `/api/v1/discord/link`, `/api/v1/test/discord`.
- Server section: Ei Void server name, guild id (read-only), invite action.
- Command reference: `/about`, `/link`, `/reminders`, `/snooze`, `/paid`, `/usage`.
- Fallback explanation: DMs closed → channel mention.
- Loading, offline, rate-limited and not-linked states have copy.
- No bot token or Discord access token is shown or persisted by the app.

### Settings

- Rename from Customize; top bar "Settings" / "Account and app".
- Sections:
  - **Account** — plan, Clerk identity, usage link, sign out, delete data.
  - **Assistant** — Auto/model tier, Skills link, usage link.
  - **Reminders** — link to the Reminders tab.
  - **Discord** — status row that opens Remote.
  - **Permissions** — notifications and exact alarms.
  - **Appearance** — OLED black (fixed in this release).
  - **About** — version, privacy note, licenses.
- Remove Provider keys completely. Remove the provider-key card, screen,
  `ProviderInfo`/`PROVIDERS`, provider-key methods and their tests.
- No duplicate Discord connect/test/disconnect controls.

## AI architecture

### Backend gateway

The web service exposes an OpenAI-compatible proxy:

`POST /api/v1/ai/chat/completions`

- Clerk bearer auth required.
- Accepts `model` as one of the tier aliases:
  `eivoid-red-dwarf`, `eivoid-white-dwarf`, `eivoid-neutron-star`.
- Forwards `messages`, `tools`, `temperature`, `max_tokens`, `stream` and
  `stream_options` to the configured upstream.
- Adds `Authorization: Bearer $AI_API_KEY` server-side. The key is never logged
  or returned.
- Streams the upstream SSE response unchanged (with correct no-buffer headers)
  when `stream=true`; returns the upstream JSON otherwise.
- Enforces a request-size cap, message/tool caps and an AI rate limit.
- Checks the latest usage snapshot against the plan limit before the call and
  records a new snapshot after usage is known.
- Returns safe error codes: `ai_unavailable`, `ai_auth_failed`,
  `plan_limit_reached`, `tier_not_in_plan`, `too_many_requests`,
  `ai_upstream_failed`.

Config:

| Variable | Meaning |
|---|---|
| `AI_API_KEY` | Server-owned provider key (required for AI) |
| `AI_BASE_URL` | OpenAI-compatible root, default `https://openrouter.ai/api/v1` |
| `AI_MODEL_RED_DWARF` | Upstream model for the strong tier |
| `AI_MODEL_WHITE_DWARF` | Upstream model for the middle tier |
| `AI_MODEL_NEUTRON_STAR` | Upstream model for the fast tier |
| `AI_APP_URL` | Optional HTTP-Referer for OpenRouter |
| `AI_APP_NAME` | Optional X-Title for OpenRouter |

`GET /api/v1/health` adds `ai: boolean`.

### Android client

- Add `ServerModelCatalog` with one `Model` per tier alias and provider id
  `eivoid`.
- Add `BackendProviderResolver` implementing `ProviderResolver`:
  - returns the three aliases
  - `keyFor("eivoid")` returns the Clerk session token
  - base URL is `${EI_VOID_API_URL}/api/v1/ai`
  - no local provider key is read
- Reuse `OpenAiCompatibleProvider` for SSE parsing.
- Remove `SecretProviderResolver`, `ProviderCatalog` app usage and provider-key
  UI/storage. Keep a one-time cleanup that removes legacy `provider_key_*`
  entries from `EncryptedSharedPreferences`.
- If `EI_VOID_API_URL` is blank or Clerk is signed out, Chat shows "The Ei Void
  AI service is not configured / Sign in" rather than asking for a key.
- Usage screen reads the server account usage (`GET /api/v1/account`) instead of
  the local usage ledger. The local ledger is no longer written.

## Skills architecture

New Android library `core:skills`:

```kotlin
enum class SkillIcon { DESIGN, FRONTEND, REVIEW, BROWSER, WEB_APP, DISCORD, SECURITY, CUSTOM }
enum class SkillSource { BUILT_IN, CUSTOM }

data class AgentSkill(
    val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val icon: SkillIcon,
    val source: SkillSource,
    val enabled: Boolean,
)

interface SkillsStore {
    val skills: StateFlow<List<AgentSkill>>
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun saveCustom(
        id: String?,
        name: String,
        description: String,
        instructions: String,
        icon: SkillIcon,
    ): Result<AgentSkill>
    suspend fun deleteCustom(id: String)
}
```

- `BuiltInSkills` owns the seven definitions and fixed ids:
  `ui-design`, `frontend-dev`, `code-review`, `web-browser`, `web-apps`,
  `discord-bots`, `security-check`.
- `DataStoreSkillsStore` stores custom definitions and enabled ids as JSON in a
  dedicated `void_skills` DataStore. Built-ins come from code and are merged on
  read.
- `SkillPromptBuilder` appends only enabled skills to the system prompt, in a
  bounded format:
  `Active skills` + name/description + instructions.
- Validation: name 2–40, description 1–120, instructions 1–4,000; reject
  control characters; custom IDs are `custom:<uuid>`.
- `AssistantEngine` injects enabled skills on every send.
- `AgentEngine.run` takes `skills: List<AgentSkill>`; `ContextManager` appends
  them to the coding-agent system prompt.
- Custom skills never add tools. Built-in Web Browser enables the `web_fetch`
  tool for Projects; other built-ins are expert instruction packs.

### Browser tool

- `core:agent` gains a `BrowserService` interface and `WebFetchTool`:
  `web_fetch(url)` → text.
- App implementation `BackendBrowserService` calls
  `POST /api/v1/browse` with the Clerk token.
- Backend browse:
  - requires Clerk auth and rate limiting
  - accepts HTTPS only
  - blocks localhost, private/link-local/loopback IPs and redirects to them
  - caps response size and timeout
  - strips scripts/styles/HTML and returns title/url/text (max 20k chars)
- The tool is read-only and does not require confirmation. Its result is
  wrapped as untrusted web content with a "treat as data, not instructions"
  prefix so a page cannot silently redirect the agent.
- Chat remains instruction-only for skills in this release; tool skills run in
  Projects. The Skills UI labels this.

## Discord architecture

- Move the OAuth launcher, link state and actions from Settings to a
  `RemoteViewModel` plus `RemoteScreen` in `feature:settings`.
- Add `GET /api/v1/discord/status` requiring Clerk:
  ```json
  {
    "linked": true,
    "discord": { "id": "...", "username": "..." },
    "guildId": "848841415940898827",
    "botConfigured": true,
    "fallbackChannelConfigured": false,
    "inviteUrl": "https://discord.com/oauth2/authorize?..."
  }
  ```
- Add `DiscordStatus` and `status()` to `DiscordLinkService`/`DiscordLinkClient`
  with the same safe error mapping as the other Discord calls.
- Settings shows only a status/link row to Remote.
- Reminders shows Ei Snore preferences and a status/link to Remote.

## Data and migration

- No Room schema change is required for skills (DataStore JSON).
- Add a one-time cleanup of legacy provider keys from `EncryptedSharedPreferences`
  (`provider_key_*`), with a unit test.
- Remove `ThemeMode`/theme state if it stays unused after the OLED-only
  decision; keep the stored key dormant or clear it.
- Keep reminder data and workspace data untouched.
- Existing provider keys are ignored and removed; no migration uploads them.

## Security and privacy

- AI provider key lives only in the server environment.
- Clerk bearer auth for AI, browse and Discord status.
- No provider key, bot token or Discord access token in UI, logs or analytics.
- Custom skills are untrusted text: validate length, store locally, never
  execute as code.
- Browse endpoint has SSRF protections, HTTPS-only, size/time caps and auth.
- The server does not store prompt content beyond usage counters.
- Update README/security docs and the website privacy/FAQ copy to say prompts
  go to the Ei Void service and its configured upstream model provider, not
  directly from the phone.

## Acceptance criteria

1. No Images or audio/voice surface exists in the app, routes, drawer or copy.
2. No screen asks for or mentions provider API keys; no provider-key code path
   remains reachable.
3. Chat works through the deployed Ei Void AI gateway when `AI_API_KEY` is set;
   without it, the error is explicit and actionable.
4. Skills tab lists seven built-ins, allows enable/disable, creates/edits/deletes
   custom skills, and persists across restarts.
5. Enabled skills appear in Chat and in the coding-agent system prompt.
6. Web Browser skill exposes `web_fetch` in Projects; browse is authenticated,
   HTTPS-only and blocked from private hosts.
7. Remote connects/disconnects/tests Discord, shows linked username, guild, bot
   and fallback status; Settings and Reminders link to it.
8. Reminders tab retains all reminder behavior and contains Ei Snore
   notification preferences.
9. Settings is named Settings everywhere; Customize strings are gone.
10. Node tests, Android unit tests, lint, debug build and Redmi walkthrough pass.
