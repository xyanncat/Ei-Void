# Ei Void 0.4 — Skills, Server AI and Product Restructure

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace user-supplied AI keys with an Ei Void server AI gateway, add built-in/custom agent skills, turn Remote into the Discord bot control centre, rename Customize to Settings, make Reminders a first-class tab, and remove all image and audio surfaces.

**Architecture:** The Android app keeps its local-first reminders, workspaces and git tools, but every model call goes through an authenticated `/api/v1/ai/chat/completions` proxy that owns the upstream provider key. A new `core:skills` library stores built-in and custom instruction packs in DataStore; the assistant and coding agent inject enabled skills into their system prompts, and Projects gains a `web_fetch` tool backed by a guarded server browse endpoint. Navigation is reduced to Chat, Reminders, Projects, Skills, Remote and Settings.

**Tech Stack:** Kotlin 2.4.21, Jetpack Compose Material 3, Hilt, Ktor, DataStore, Room, Node 22, TypeScript, Express, zod, Vitest, PostgreSQL.

**Spec:** `docs/superpowers/specs/2026-11-22-ei-void-skills-restructure-design.md`

## Global Constraints

- The UI is OLED-only. No light/system theme option, no debug ribbon, no emoji used as structural icons.
- Typography is Inter; new surfaces use the existing `designsystem` tokens and `EiComponents`.
- No image generation, image editing, audio input, audio output, voice mode, dictation or waveform action may remain in code, routes, drawer or copy.
- No user-provided AI provider keys. The app must never ask for, store or send a provider secret. `AI_API_KEY` exists only in the web service environment.
- Custom skills are text instructions only. They cannot add code, shell commands, tools or URLs that execute.
- Every new server route requires a Clerk bearer token. Browse is HTTPS-only and blocks loopback, private, link-local and redirect-to-private targets.
- Existing reminder data, workspace data and Clerk sessions must survive the upgrade. Legacy `provider_key_*` entries must be deleted.
- App version is `0.4.0`, `versionCode = 6`; minSdk 33, targetSdk 35, compileSdk 37.
- Verification after every functional task: `npm run typecheck`, `npm test`, `cd android && ./gradlew testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Every task ends with a commit using the repository attribution line.

## Review Focus

- **AI service not configured / Clerk signed out / upstream 401 or 429:** the user must get a precise retry or sign-in instruction, never a provider-key prompt or an indefinite spinner. Pinned by Task 5 and Task 6 tests.
- **A custom skill with blank, oversized or control-character content:** the editor must reject it and never corrupt the stored skill list or the model prompt. Pinned by Task 7 and Task 8 tests.
- **Browse target is localhost, a private address, a non-HTTPS URL or redirects to one:** the request must fail with a safe message and no internal network access. Pinned by Task 3 tests.
- **Discord not linked, left the guild, bot token absent or DMs closed:** Remote must show the real state and the test action must explain the fallback. Pinned by Task 4 and Task 13 tests.
- **Existing 0.3.x user upgrades with provider keys and reminder data:** legacy keys must be removed, reminders must remain, and no removed route/tab may crash the back stack. Pinned by Task 6 and Task 14 tests.

---

## File Map

**Create**

- `packages/contracts/src/ai.ts` — tier aliases, chat request/response schema, Discord status schema.
- `services/web/src/routes/ai.ts` — authenticated OpenAI-compatible proxy.
- `services/web/src/routes/browse.ts` — guarded web fetch.
- `services/web/test/ai.test.ts`, `services/web/test/browse.test.ts`.
- `android/core/skills/` — new library: `AgentSkill.kt`, `BuiltInSkills.kt`, `SkillValidation.kt`, `SkillPromptBuilder.kt`, `SkillsStore.kt`, `DataStoreSkillsStore.kt`, tests.
- `android/feature/skills/` — new feature: `SkillsScreen.kt`, `SkillEditorScreen.kt`, `SkillsViewModel.kt`, tests.
- `android/core/ai/.../models/ServerModelCatalog.kt` — three server aliases.
- `android/app/.../agent/BackendProviderResolver.kt` — Clerk-authenticated provider.
- `android/app/.../agent/BackendBrowserService.kt` — `/browse` client.
- `android/core/agent/.../tools/WebFetchTool.kt`, `BrowserService.kt`.
- `android/feature/settings/.../RemoteScreen.kt`, `RemoteViewModel.kt`.
- `android/feature/settings/.../SettingsScreen.kt` (renamed from Customize).
- `android/feature/reminders/.../RemindersScreen.kt` (renamed from Library).
- `android/designsystem/.../Motion.kt`.
- `docs/skills/README.md`, `docs/releases/v0.4.0.md`.

**Modify**

- `packages/config/src/index.ts` + tests — `AI_*` config.
- `services/web/src/app.ts`, `routes/api.ts`, `ports.ts` as needed.
- `services/web/.env.example`, `render.yaml`, `README.md`, API/security/architecture docs.
- `android/core/ai/.../openai/OpenAiCompatibleProvider.kt` only if the proxy needs header changes.
- `android/app/src/main/java/com/eivoid/app/di/AgentModule.kt`, `AppModule.kt`.
- `android/core/security/.../SecretStore.kt`, `EncryptedSecretStore.kt`.
- `android/feature/assistant/.../AssistantEngine.kt`, `AssistantViewModel.kt`, `AssistantHomeScreen.kt`, `AssistantDrawer.kt`, `AssistantModels.kt`.
- `android/feature/settings/.../CustomizeScreen.kt` → delete/replace, `SettingsViewModel.kt`, `SettingsStore.kt`, `UsageOverviewScreen.kt`, `UsageViewModel.kt`, `ProviderKeysScreen.kt` → delete.
- `android/feature/reminders/.../LibraryScreen.kt` → `RemindersScreen.kt`.
- `android/core/reminders/.../DiscordLinkClient.kt`, tests; `DiscordIdentity`/status models.
- `android/core/agent/.../AgentEngine.kt`, `context/ContextManager.kt`, `ToolRegistry.kt` if skill filtering is needed.
- `android/app/.../navigation/VoidNavHost.kt`.
- `android/settings.gradle.kts`, `android/app/build.gradle.kts`, `android/build.gradle.kts`.
- `README.md`, `docs/qa/README.md`, `docs/api/README.md`, `docs/security/*`, `docs/architecture/overview.md`, `docs/runbooks/deploy.md`.

**Delete**

- `android/feature/settings/.../ProviderKeysScreen.kt`.
- `android/app/.../agent/SecretProviderResolver.kt`.
- `android/feature/assistant/.../AssistantPlaceholderScreens.kt` (Images/Explore/old Remote).
- Images/Explore routes, destinations and drawer rows.
- All mic/waveform/dictation/voice-mode code and copy.
- Provider-key strings, tests and docs.
- `ThemeMode`/`SettingsStore.theme` if no longer referenced after the OLED-only change.

---

### Task 1: AI contracts and server configuration

**Files:**
- Create: `packages/contracts/src/ai.ts`
- Modify: `packages/contracts/src/index.ts`
- Modify: `packages/config/src/index.ts`
- Test: `packages/contracts/test/contracts.test.ts`, `packages/config/test/config.test.ts`

**Interfaces:**
- Consumes: existing zod and config test helpers.
- Produces:
  - `AI_TIER_ALIASES = ['eivoid-red-dwarf','eivoid-white-dwarf','eivoid-neutron-star'] as const`
  - `aiChatRequestSchema`, type `AiChatRequest`
  - `browseRequestSchema` (`{ url: string }`)
  - `planAllowsTier(plan: string | null | undefined, tier: 'red' | 'white' | 'neutron'): boolean`
  - `discordStatusSchema`
  - `Config.ai: { apiKey: string | null; baseUrl: string; models: { redDwarf: string; whiteDwarf: string; neutronStar: string }; appUrl: string; appName: string }`

- [ ] **Step 1: Write the failing contract tests**

```ts
it('accepts every tier alias and rejects an unknown alias', () => {
  for (const model of AI_TIER_ALIASES) {
    expect(aiChatRequestSchema.safeParse({
      model, messages: [{ role: 'user', content: 'hello' }],
    }).success).toBe(true);
  }
  expect(aiChatRequestSchema.safeParse({
    model: 'gpt-4', messages: [{ role: 'user', content: 'hello' }],
  }).success).toBe(false);
});

it('rejects more than 64 messages or 32 tools', () => {
  const messages = Array.from({ length: 65 }, () => ({ role: 'user', content: 'x' }));
  const tools = Array.from({ length: 33 }, (_, i) => ({
    type: 'function', function: { name: `t${i}`, description: '', parameters: {} },
  }));
  expect(aiChatRequestSchema.safeParse({ model: 'eivoid-red-dwarf', messages }).success).toBe(false);
  expect(aiChatRequestSchema.safeParse({
    model: 'eivoid-red-dwarf', messages: [{ role: 'user', content: 'x' }], tools,
  }).success).toBe(false);
});

it('maps plan names to the tiers they may use', () => {
  expect(planAllowsTier('Free', 'red')).toBe(false);
  expect(planAllowsTier('Free', 'neutron')).toBe(true);
  expect(planAllowsTier('Pro', 'white')).toBe(true);
  expect(planAllowsTier('Max', 'red')).toBe(true);
});
```

- [ ] **Step 2: Run the failing tests**

Run: `npm test --workspace @eivoid/contracts`
Expected: FAIL because `aiChatRequestSchema`/`AI_TIER_ALIASES` are not exported.

- [ ] **Step 3: Implement `packages/contracts/src/ai.ts`**

Use `z.object` with `model: z.enum(AI_TIER_ALIASES)`, `messages: z.array(z.object({ role: z.enum(['system','user','assistant','tool']), content: z.string().max(200_000), tool_call_id: z.string().optional(), tool_calls: z.array(toolCallSchema).max(32).optional() })).min(1).max(64)`, `tools: z.array(toolDefinitionSchema).max(32).optional()`, `stream: z.boolean().optional()`, `temperature`, `top_p`, `max_tokens`, `stream_options`. Export a Discord status schema matching the spec. Re-export from `index.ts`.

- [ ] **Step 4: Write the failing config tests**

```ts
it('maps AI env names and defaults the upstream base url', () => {
  const config = loadConfig({
    NODE_ENV: 'test',
    PUBLIC_BASE_URL: 'http://localhost:3000',
    SESSION_SECRET: 'test-session-secret-that-is-long-enough',
    AI_API_KEY: 'sk-test',
    AI_MODEL_RED_DWARF: 'strong/model',
    AI_MODEL_WHITE_DWARF: 'middle/model',
    AI_MODEL_NEUTRON_STAR: 'fast/model',
  });
  expect(config.ai.apiKey).toBe('sk-test');
  expect(config.ai.baseUrl).toBe('https://openrouter.ai/api/v1');
  expect(config.ai.models.neutronStar).toBe('fast/model');
});

it('leaves AI unconfigured when AI_API_KEY is blank', () => {
  const config = makeTestConfig();
  expect(config.ai.apiKey).toBeNull();
});
```

- [ ] **Step 5: Implement config**

Add `AI_API_KEY`, `AI_BASE_URL`, `AI_MODEL_RED_DWARF`, `AI_MODEL_WHITE_DWARF`, `AI_MODEL_NEUTRON_STAR`, `AI_APP_URL`, `AI_APP_NAME` to `loadConfig` and `makeTestConfig` merging. Do not throw when the key is absent; `apiKey` is `null`. Default models may be blank strings until configured.

- [ ] **Step 6: Run the tests and commit**

Run: `npm run typecheck && npm test --workspace @eivoid/contracts --workspace @eivoid/config`
Expected: PASS.
Commit: `feat(ai): add server AI contracts and configuration`.

---

### Task 2: Server AI gateway

**Files:**
- Create: `services/web/src/routes/ai.ts`
- Modify: `services/web/src/app.ts`, `services/web/src/routes/api.ts` (health `ai`)
- Test: `services/web/test/ai.test.ts`, `services/web/test/app.test.ts`

**Interfaces:**
- Consumes: `Config.ai`, `WebStore.latestUsage`, `WebStore.addUsageSnapshot`, `requireUser`.
- Produces: `createAiRouter({ config, store, auth, fetchImpl }): Router` mounted at `/api/v1/ai`.

- [ ] **Step 1: Write the failing gateway tests**

Use the existing `harness` pattern with `fetchImpl` capturing the upstream request.

```ts
it('streams upstream deltas with the server key and mapped model', async () => {
  const upstream = vi.fn(async (url, init) => {
    expect(url).toBe('https://openrouter.ai/api/v1/chat/completions');
    expect(init.headers.Authorization).toBe('Bearer ai-key');
    expect(JSON.parse(init.body).model).toBe('strong/model');
    return new Response(
      'data: {"choices":[{"delta":{"content":"Hi"}}]}\n\n' +
      'data: {"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":1}}\n\n' +
      'data: [DONE]\n\n',
      { headers: { 'Content-Type': 'text/event-stream' } },
    );
  });
  const res = await request(h.app).post('/api/v1/ai/chat/completions')
    .set('Authorization', token('user_1'))
    .send({ model: 'eivoid-red-dwarf', messages: [{ role: 'user', content: 'hello' }] });
  expect(res.status).toBe(200);
  expect(res.text).toContain('"content":"Hi"');
  expect(upstream).toHaveBeenCalledOnce();
});

it('rejects a plan over its token limit before calling upstream', async () => {
  store.addUsageSnapshot({ clerkUserId: 'user_1', plan: 'Free', tokensUsed: 30_000_000, tokensLimit: 30_000_000, periodEnd: null });
  const res = await request(h.app).post('/api/v1/ai/chat/completions')
    .set('Authorization', token('user_1'))
    .send({ model: 'eivoid-neutron-star', messages: [{ role: 'user', content: 'hello' }] });
  expect(res.status).toBe(402);
  expect(res.body.error).toBe('plan_limit_reached');
});

it('never exposes the upstream key in an error', async () => {
  fetchImpl = vi.fn(async () => new Response('Incorrect API key sk-secret', { status: 401 }));
  const res = await request(h.app).post('/api/v1/ai/chat/completions')
    .set('Authorization', token('user_1'))
    .send({ model: 'eivoid-white-dwarf', messages: [{ role: 'user', content: 'x' }] });
  expect(res.status).toBe(502);
  expect(JSON.stringify(res.body)).not.toContain('sk-secret');
});
```

Also test: no Clerk token → 401; `AI_API_KEY` empty → 503 `ai_unavailable`; invalid body → 400; usage snapshot recorded with summed tokens; health `ai` boolean.

- [ ] **Step 2: Run the failing tests**

Run: `npm test --workspace @eivoid/web -- test/ai.test.ts`
Expected: FAIL with route 404.

- [ ] **Step 3: Implement the route**

Implement `createAiRouter`. `POST /chat/completions`:
1. Validate `aiChatRequestSchema`.
2. Reject when `config.ai.apiKey` is null.
3. Read the account and `latestUsage`; plan is `latestUsage?.plan ?? account?.plan ?? 'Free'`; if `tokensUsed >= tokensLimit`, return 402.
4. Map the alias to its tier and check `planAllowsTier(plan, tier)`; return 403 `tier_not_in_plan` for a tier outside the plan.
5. Map the alias to `config.ai.models`; return 503 if the target model is blank.
6. Build an upstream body with the mapped model, messages/tools/stream fields, and `stream_options.include_usage = true` when streaming.
7. `fetch` `${baseUrl}/chat/completions` with `Authorization: Bearer ${apiKey}` and optional OpenRouter headers.
8. For SSE: set `Content-Type: text/event-stream`, `Cache-Control: no-cache`, `X-Accel-Buffering: no`; flush headers; pipe `response.body` to `res`; buffer/parse the final `usage` event.
9. After the stream, insert a usage snapshot using `latest.tokensUsed + usageTokens` (or the local estimate when usage is missing), plan and `planLimit(plan)`.
10. For non-streaming, forward JSON and record usage from `body.usage`.
11. Map auth/rate/upstream failures to safe codes; never include upstream body or key.

Mount `app.use('/api/v1/ai', createAiRouter(...))` after the API router. Add `ai: Boolean(config.ai.apiKey)` to `/api/v1/health`.

- [ ] **Step 4: Run the tests and commit**

Run: `npm run typecheck && npm test --workspace @eivoid/web`
Expected: PASS.
Commit: `feat(ai): add authenticated server AI gateway`.

---

### Task 3: Guarded web browse endpoint

**Files:**
- Create: `services/web/src/routes/browse.ts`
- Modify: `services/web/src/app.ts`, `services/web/src/routes/api.ts` (health)
- Test: `services/web/test/browse.test.ts`

**Interfaces:**
- Produces: `createBrowseRouter({ auth, fetchImpl?, lookupImpl? }): Router` mounted at `/api/v1/browse`; `POST /` body `{ url: string }` → `{ url, title, text }`.

- [ ] **Step 1: Write the failing tests**

```ts
it('rejects non-https and private targets', async () => {
  for (const url of ['http://example.com', 'https://localhost/', 'https://127.0.0.1/', 'https://10.0.0.5/']) {
    const res = await request(h.app).post('/api/v1/browse')
      .set('Authorization', token('user_1')).send({ url });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe('url_not_allowed');
  }
});

it('blocks a hostname that resolves to a private address', async () => {
  const app = harness({ lookupImpl: async () => [{ address: '192.168.1.4', family: 4 }] }).app;
  const res = await request(app).post('/api/v1/browse')
    .set('Authorization', token('user_1')).send({ url: 'https://evil.example' });
  expect(res.status).toBe(400);
});

it('strips scripts and truncates text', async () => {
  fetchImpl = vi.fn(async () => new Response(
    '<html><head><title>Doc</title><script>bad()</script></head><body><h1>Hi</h1><p>Useful</p></body></html>',
    { headers: { 'Content-Type': 'text/html' } },
  ));
  const res = await request(h.app).post('/api/v1/browse')
    .set('Authorization', token('user_1')).send({ url: 'https://example.com/doc' });
  expect(res.body).toEqual({ url: 'https://example.com/doc', title: 'Doc', text: 'Hi Useful' });
});
```

Also test: auth required; redirect to private blocked; response over 1 MiB rejected; 3s timeout; plain text returned.

- [ ] **Step 2: Run the failing tests**

Expected: 404 route.

- [ ] **Step 3: Implement**

Use `browseRequestSchema` from Task 1. Validate HTTPS. Resolve hostname with injected `lookupImpl` (`dns.promises.lookup` by default) and reject any private/link-local/loopback IP. Fetch with `redirect: 'manual'`, timeout 10s, max 1 MiB; follow at most 3 redirects but re-validate each. Strip `<script>`, `<style>`, tags; decode basic entities; normalize whitespace; cap text at 20,000 chars. Return safe errors. Mount `/api/v1/browse`.

- [ ] **Step 4: Run tests and commit**

Run: `npm run typecheck && npm test --workspace @eivoid/web`
Commit: `feat(web): add guarded browse endpoint`.

---

### Task 4: Discord status endpoint

**Files:**
- Modify: `services/web/src/routes/api.ts`, `packages/contracts/src/ai.ts` (Discord status schema), `services/web/src/app.ts` (health already has discord)
- Test: `services/web/test/app.test.ts` or a new `services/web/test/discord-status.test.ts`

**Interfaces:**
- Produces: `GET /api/v1/discord/status` →
  `{ linked, discord: {id, username} | null, guildId, botConfigured, fallbackChannelConfigured, inviteUrl }`.

- [ ] **Step 1: Write the failing test**

```ts
it('returns the linked Discord state and never the bot token', async () => {
  store.linkDiscord('user_1', { id: '123', username: 'cat' });
  const res = await request(h.app).get('/api/v1/discord/status')
    .set('Authorization', token('user_1'));
  expect(res.status).toBe(200);
  expect(res.body).toMatchObject({
    linked: true,
    discord: { id: '123', username: 'cat' },
    guildId: GUILD,
    botConfigured: true,
    fallbackChannelConfigured: false,
  });
  expect(JSON.stringify(res.body)).not.toContain('bot-token');
});
```

- [ ] **Step 2: Implement**

Add the route after `/account` with `clerkUser`. Build the invite URL only from the public application id, guild id and configured permissions; never include the token. Return `botConfigured: Boolean(config.discord.token)`, `fallbackChannelConfigured: Boolean(config.discord.fallbackChannelId)`.

- [ ] **Step 3: Run tests and commit**

Run: `npm run typecheck && npm test --workspace @eivoid/web`
Commit: `feat(discord): expose safe bot status`.

---

### Task 5: Android server provider

**Files:**
- Create: `android/core/ai/src/main/java/com/eivoid/core/ai/models/ServerModelCatalog.kt`
- Create: `android/core/ai/src/main/java/com/eivoid/core/ai/ServerAiMessages.kt`
- Create: `android/app/src/main/java/com/eivoid/app/agent/BackendProviderResolver.kt`
- Modify: `android/app/src/main/java/com/eivoid/app/di/AgentModule.kt`, `core/ai/.../AiProvider.kt`, `openai/OpenAiCompatibleProvider.kt`
- Test: `android/core/ai/src/test/.../ServerModelCatalogTest.kt`, `ServerAiMessagesTest.kt`, `android/app/src/test/.../BackendProviderResolverTest.kt`, an SSE MockEngine test.

**Interfaces:**
- Produces:
  - `object ServerModelCatalog { const val PROVIDER_ID = "eivoid"; val all: List<Model> }`
  - `fun serverOpenAiConfig(apiUrl: String): OpenAiConfig`
  - `class BackendProviderResolver(client, apiUrl, tokenProvider: suspend () -> String? = { ReminderSyncClient.clerkToken() }) : ProviderResolver`

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun `the server catalog exposes one alias per tier`() {
  assertEquals(listOf(Tier.RED_DWARF, Tier.WHITE_DWARF, Tier.NEUTRON_STAR), ServerModelCatalog.all.map { it.tier })
  assertEquals(setOf("eivoid"), ServerModelCatalog.all.map { it.providerId }.toSet())
}

@Test fun `the resolver returns Clerk tokens and the three aliases`() = runTest {
  val resolver = BackendProviderResolver(client = HttpClient(MockEngine { respond("") }), apiUrl = "https://api.test", tokenProvider = { "clerk-token" })
  assertEquals(3, resolver.targets().size)
  assertEquals("clerk-token", resolver.keyFor("eivoid"))
}

@Test fun `a blank api url produces no targets`() = runTest {
  val resolver = BackendProviderResolver(HttpClient(MockEngine { respond("") }), apiUrl = "", tokenProvider = { "clerk-token" })
  assertTrue(resolver.targets().isEmpty())
}
```

Add a MockEngine test that the provider posts to `https://api.test/api/v1/ai/chat/completions` with `Authorization: Bearer clerk-token` and parses an SSE delta. Add tests for the safe error mapper:

```kotlin
@Test fun `server errors become actionable messages`() {
  assertEquals("Your AI allowance is used up for this month.", ServerAiMessages.forStatus(402, "plan_limit_reached"))
  assertEquals("The Ei Void AI service is not configured yet.", ServerAiMessages.forStatus(503, "ai_unavailable"))
  assertEquals("That model tier is not included in your plan.", ServerAiMessages.forStatus(403, "tier_not_in_plan"))
  assertEquals("Too many requests. Try again in a moment.", ServerAiMessages.forStatus(429, null))
  assertEquals("Your session expired. Sign in again.", ServerAiMessages.forStatus(401, null))
}
```

- [ ] **Step 2: Implement**

`ServerModelCatalog.all` uses model ids `eivoid-red-dwarf`, `eivoid-white-dwarf`, `eivoid-neutron-star`, context window 32,000, provider id `eivoid`, display name from `Tier`.
`serverOpenAiConfig` strips a trailing slash and appends `/api/v1/ai`.
Extend `AiProviderException` with optional `statusCode`/`errorCode` (or add an `AiProviderHttpException` subtype) and parse the server's `{ error }` JSON in `OpenAiCompatibleProvider` on non-2xx. `ServerAiMessages` maps the status/code to the four user-facing messages above.
Bind `BackendProviderResolver` in `AgentModule`; leave `SecretProviderResolver` binding until Task 6 so the app compiles, then switch. Pass `BuildConfig.EI_VOID_API_URL` into the resolver.

- [ ] **Step 3: Run tests and commit**

Run: `cd android && ./gradlew :core:ai:testDebugUnitTest :app:testDebugUnitTest`
Commit: `feat(ai): add Android server model gateway`.

---

### Task 6: Remove provider keys and local usage metering

**Files:**
- Delete: `android/feature/settings/.../ProviderKeysScreen.kt`
- Delete: `android/app/.../agent/SecretProviderResolver.kt`
- Modify: `android/app/.../di/AgentModule.kt`
- Modify: `android/core/security/.../SecretStore.kt`, `EncryptedSecretStore.kt`
- Modify: `android/feature/settings/.../SettingsViewModel.kt`, `CustomizeScreen.kt`, tests
- Modify: `android/feature/assistant/.../AssistantEngine.kt`
- Test: `android/core/security/src/test/...` (or existing tests), `feature/settings`/`feature/assistant` tests.

**Interfaces:**
- Consumes: `BackendProviderResolver`.
- Produces: `SecretStore.clearLegacyProviderKeys()`; no provider-key methods; `AssistantEngine` service/sign-in messages.

- [ ] **Step 1: Write failing tests**

- `SecretStore` fake/prefs test: `clearLegacyProviderKeys` removes `provider_key_openrouter` but keeps `gh_token`.
- `AssistantEngineTest`: with no resolver targets, the failure message is `The Ei Void AI service is unavailable. Try again.`; with no Clerk token, `Sign in to Ei Void to use the assistant.`; no message contains "provider key".
- Remove provider-key tests from `SettingsViewModelTest`; add a compile-time absence assertion (the class no longer has the methods).

- [ ] **Step 2: Implement cleanup**

Add `clearLegacyProviderKeys()` to `SecretStore` and `EncryptedSecretStore`; remove `saveProviderKey`/`getProviderKey`/`clearProviderKey`. Keep `clearAll` clearing token and legacy keys. Create `app/.../security/LegacyCredentialCleanup.kt` with `@Inject constructor(secretStore: SecretStore)` and `fun run()`; call it from `MainActivity.onCreate` (MainActivity is already `@AndroidEntryPoint`, so inject the cleanup with `@Inject`). Test that it removes only legacy provider keys and leaves `gh_token`.

Delete `ProviderKeysScreen`, `ProviderInfo`, `SettingsViewModel.PROVIDERS` and provider-key methods; remove the Provider keys card from Customize/Settings (the screen will be renamed in Task 12). Switch `AgentModule` to `BackendProviderResolver`. Remove `RoomUsageMeter` binding from `AgentEngine` (leave the DAO/table for a later cleanup; stop writing). Update `AssistantEngine` error constants and tests; when a call fails with a server status/code, use `ServerAiMessages` before the generic `shortError`.

- [ ] **Step 3: Run tests and commit**

Run: `cd android && ./gradlew testDebugUnitTest :app:lintDebug :app:assembleDebug`
Commit: `refactor(ai): remove user provider keys and local metering`.

---

### Task 7: Core skills library

**Files:**
- Create: `android/core/skills/build.gradle.kts`, `src/main/AndroidManifest.xml`
- Create: `AgentSkill.kt`, `BuiltInSkills.kt`, `SkillValidation.kt`, `SkillPromptBuilder.kt`, `SkillsStore.kt`, `DataStoreSkillsStore.kt`
- Modify: `android/settings.gradle.kts`, `android/app/build.gradle.kts`, `android/app/.../di/AppModule.kt`
- Test: `android/core/skills/src/test/...`

**Interfaces:**
- Produces: types and store per the spec; `SkillPromptBuilder.build(base: String, skills: List<AgentSkill>): String`.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun `the seven built-ins have unique ids`() {
  assertEquals(7, BuiltInSkills.all.size)
  assertEquals(7, BuiltInSkills.all.map { it.id }.toSet().size)
  assertTrue(BuiltInSkills.all.any { it.id == "web-browser" })
}

@Test fun `validation rejects blank, oversized and control-character content`() {
  assertTrue(SkillValidation.validate("", "desc", "body").isFailure)
  assertTrue(SkillValidation.validate("ok", "desc", "x".repeat(4001)).isFailure)
  assertTrue(SkillValidation.validate("ok", "desc", "bad\u0000body").isFailure)
}

@Test fun `the prompt builder includes enabled skills only`() {
  val prompt = SkillPromptBuilder.build("BASE", listOf(skill("a", enabled = true), skill("b", enabled = false)))
  assertTrue(prompt.contains("BASE"))
  assertTrue(prompt.contains("a"))
  assertFalse(prompt.contains("b"))
}
```

Store tests use a temp `PreferenceDataStoreFactory`:
`toggles a built-in and persists across a new instance`; `saves edits and deletes a custom skill`; `custom skill ids are prefixed custom:`.

- [ ] **Step 2: Implement**

Add the module to `settings.gradle.kts`; add dependencies for `datastore-preferences`, `kotlinx-serialization-json`, serialization plugin, Hilt, test JUnit/coroutines. `DataStoreSkillsStore` uses `stringPreferencesKey("skills_json")` and a serialized `SkillsPreferences(customSkills, enabledBuiltIns, enabledCustom)`. Merge built-ins on every read. Provide the singleton in `AppModule` with an application `CoroutineScope`.

- [ ] **Step 3: Run tests and commit**

Run: `cd android && ./gradlew :core:skills:testDebugUnitTest`
Commit: `feat(skills): add core skill catalog and store`.

---

### Task 8: Skills screen and editor

**Files:**
- Create: `android/feature/skills/build.gradle.kts`, `src/main/AndroidManifest.xml`, `SkillsScreen.kt`, `SkillEditorScreen.kt`, `SkillsViewModel.kt`
- Modify: `android/settings.gradle.kts`, `android/app/build.gradle.kts`
- Test: `android/feature/skills/src/test/.../SkillsViewModelTest.kt`

**Interfaces:**
- Consumes: `SkillsStore`, `AgentSkill`, `BuiltInSkills`.
- Produces: `SkillsScreen(onOpenDrawer, onCreate, onEdit)`, `SkillEditorScreen(onBack, skillId)`; ViewModel state `skills`, `filter`, `editor`.

- [ ] **Step 1: Write failing ViewModel tests**

- `toggling a built-in updates the store and state`
- `saving an invalid custom skill sets validation errors and does not call saveCustom`
- `deleting a custom skill removes it after confirmation`
- `saving a valid custom skill closes the editor and refreshes the list`

- [ ] **Step 2: Build the UI**

Use `EiScreen`, `EiTopBar` ("Skills" / "Expert modes for the agent"), search field, Built in and Your skills sections, toggle switches, custom skill cards, create button, and empty state. Editor: name/description/instructions, fixed icon picker, enabled switch, save/delete, validation messages. No code execution claims. Show "Instructions only" for custom skills and "Runs in Projects" for Web Browser.

- [ ] **Step 3: Add module to app and run tests**

Add `implementation(project(":feature:skills"))` and run `:feature:skills:testDebugUnitTest`.

- [ ] **Step 4: Commit**

`feat(skills): add skills management screen`.

---

### Task 9: Inject skills into Chat and Projects

**Files:**
- Modify: `android/feature/assistant/.../AssistantEngine.kt`, `AssistantViewModel.kt`, `AssistantHomeScreen.kt`
- Modify: `android/core/agent/.../AgentEngine.kt`, `context/ContextManager.kt`
- Modify: `android/feature/chat/...` (the workspace chat ViewModel that calls `AgentEngine.run`)
- Test: `AssistantEngineTest`, `AgentEngineTest`, `ContextManagerTest`

**Interfaces:**
- Consumes: `SkillsStore.skills`.
- Produces:
  - `AssistantEngine` constructor gains `skillsStore: SkillsStore`
  - `AgentEngine.run(task, ctx, history, skills: List<AgentSkill> = emptyList())`
  - `ContextManager.systemPrompt(task, workspace, plan, skills)`

- [ ] **Step 1: Write failing tests**

- `assistant system prompt contains enabled skill instructions`
- `agent system prompt contains enabled skill instructions and no disabled ones`
- `assistant view model exposes enabled skills for the rail`

Use fake `SkillsStore` implementations with one enabled/one disabled skill; capture the `ChatRequest` sent by a fake provider.

- [ ] **Step 2: Implement**

`AssistantEngine.buildMessages` prepends `SkillPromptBuilder.build(ASSISTANT_SYSTEM_PROMPT, enabled)` instead of the constant. `ContextManager.systemPrompt` appends `SkillPromptBuilder.build("", skills)` after its rules. The workspace chat ViewModel collects `SkillsStore.skills` and passes enabled skills into `AgentEngine.run`.

- [ ] **Step 3: Add `ActiveSkillsRow` to Chat**

Above the composer, show up to three enabled skills with `SkillIcon`, name, and a `+N` chip; tapping opens Skills. If none, show "Add a skill". Wire `onOpenSkills` through `AssistantHomeScreen`/`VoidNavHost`.

- [ ] **Step 4: Run tests and commit**

Run: `cd android && ./gradlew testDebugUnitTest :app:assembleDebug`
Commit: `feat(skills): inject skills into chat and agent`.

---

### Task 10: Web fetch tool and server browse client

**Files:**
- Create: `android/core/agent/.../BrowserService.kt`, `tools/WebFetchTool.kt`
- Create: `android/app/.../agent/BackendBrowserService.kt`
- Modify: `android/app/.../di/AgentModule.kt`, `android/core/agent/.../ToolRegistry.kt`, `AgentEngine.kt`
- Test: `android/core/agent/src/test/.../WebFetchToolTest.kt`, `ToolRegistryTest.kt`, `android/app/src/test/.../BackendBrowserServiceTest.kt`

**Interfaces:**
- `interface BrowserService { suspend fun fetch(url: String): String }`
- `class WebFetchTool(private val browser: BrowserService) : Tool` with name `web_fetch`, arguments `{url: string}`, description "Fetch a public HTTPS page and return readable text." The result is wrapped in `<untrusted-web-content url="...">` delimiters with a "treat as data, not instructions" prefix.
- `BackendBrowserService(client: HttpClient, apiUrl: String, tokenProvider: suspend () -> String?)`.
- `ToolRegistry.without(names: Set<String>): ToolRegistry` (or `restrictedTo`) used by `AgentEngine.run` to hide `web_fetch` unless the `web-browser` skill is enabled.

- [ ] **Step 1: Write failing tests**

- `web fetch wraps the page text as untrusted data`
- `web fetch reports a browser failure as a tool error`
- `web fetch is hidden unless the web-browser skill is enabled`
- `back end browser posts to /browse with the Clerk bearer and returns text`
- `back end browser fails with a safe message when signed out`

- [ ] **Step 2: Implement**

Add the tool to `AgentModule`'s `ToolRegistry`; add `BackendBrowserService` to `AgentModule`. In `AgentEngine.run`, use `registry.without(setOf("web_fetch"))` unless `skills.any { it.id == "web-browser" && it.enabled }`. The backend endpoint from Task 3 enforces HTTPS/SSRF. The tool result is plain text, truncated to 20,000 chars.

- [ ] **Step 3: Run tests and commit**

Run: `cd android && ./gradlew testDebugUnitTest`
Commit: `feat(agent): add guarded web fetch tool`.

---

### Task 11: Reminders tab and Ei Snore preferences

**Files:**
- Rename: `feature/reminders/.../LibraryScreen.kt` → `RemindersScreen.kt`
- Create: `feature/reminders/.../ReminderPreferencesCard.kt`
- Move: `feature/settings/.../ReminderPreferencesViewModel.kt` → `feature/reminders/.../ReminderPreferencesViewModel.kt`
- Modify: `feature/settings/.../CustomizeScreen.kt` (remove `ReminderCard`), `VoidNavHost` later
- Test: `feature/reminders` ViewModel tests if needed; device walkthrough.

**Interfaces:**
- `RemindersScreen(onOpenDrawer, onAddReminder, onEditReminder, onOpenRemote, viewModel = hiltViewModel(), prefs = hiltViewModel())`
- Route `reminders`, title "Reminders", subtitle "Bills, renewals and Ei Snore".
- `ReminderPreferencesViewModel` exposes `discordLinked: StateFlow<Boolean>` by calling `DiscordLinkService.account()`; the card shows linked/not linked and links to Remote. It does not duplicate Connect/Disconnect/Test.

- [ ] **Step 1: Rename and restyle**

Rename the composable/file/route/filter, change title, keep existing reminder behavior. Add the Ei Snore card: lead-day segmented choices, time picker, email toggle, Discord status/link to Remote, permission rows. Reuse `ReminderPreferencesViewModel`; do not duplicate connect/test controls.

- [ ] **Step 2: Update the home workspace link and Settings references**

Point "Reminders" to the renamed screen. Remove the reminder preference card from Settings; leave a link to Reminders.

- [ ] **Step 3: Verify and commit**

Run `./gradlew :feature:reminders:testDebugUnitTest :app:assembleDebug`; walk through add/edit/mark paid, lead time, email toggle. Commit `feat(reminders): make reminders a first-class tab`.

---

### Task 12: Settings rename and cleanup

**Files:**
- Rename: `feature/settings/.../CustomizeScreen.kt` → `SettingsScreen.kt`
- Modify: `SettingsViewModel.kt`, `SettingsStore.kt`, `UsageOverviewScreen.kt`, `UsageViewModel.kt`
- Delete: `ProviderKeysScreen.kt` if not already deleted
- Test: existing settings tests updated; grep assertions.

**Interfaces:**
- `SettingsScreen(onOpenDrawer, onLoggedOut, onOpenUsage, onOpenRemote, onOpenReminders, onOpenSkills)`.
- No `ProviderInfo`, no provider-key methods, no Discord connect/test.

- [ ] **Step 1: Rename every surface**

Rename function, file, route (`settings`), drawer label, title, comments and copy. Replace "Customize" with "Settings" in error banners, links and docs. Remove the provider-key card/screen and provider tests. Remove the Discord card and launcher (moved to Remote). Remove the ReminderCard (moved to Reminders). Add link rows for Reminders, Remote, Skills, Usage.

- [ ] **Step 2: Remove dead theme state**

If `ThemeMode`/`SettingsStore.theme` has no remaining consumer, remove them and their tests; the app is OLED-only. Keep `preferredTier` and `plan`.

- [ ] **Step 3: Verify and commit**

Run `grep -R "Customize" android/feature android/app` and `./gradlew testDebugUnitTest :app:lintDebug :app:assembleDebug`.
Commit `refactor(settings): rename Customize to Settings and remove dead sections`.

---

### Task 13: Remote Discord control centre

**Files:**
- Create: `feature/settings/.../RemoteScreen.kt`, `RemoteViewModel.kt`
- Modify: `core/reminders/.../DiscordLinkClient.kt`, `DiscordLinkService` and tests
- Modify: `feature/settings/.../SettingsScreen.kt` (Remote link), `feature/reminders/.../RemindersScreen.kt` (Remote link)
- Delete: old `RemoteScreen` from `AssistantPlaceholderScreens.kt`
- Test: `DiscordLinkClientTest`, `RemoteViewModelTest`

**Interfaces:**
- `data class DiscordStatus(val linked, val discord: DiscordIdentity?, val guildId, val botConfigured, val fallbackChannelConfigured, val inviteUrl)`.
- `DiscordLinkService.status(): Result<DiscordStatus>`.
- `RemoteViewModel` exposes status, busy, message; `connect(accessToken)`, `disconnect()`, `test()`, `refresh()`.

- [ ] **Step 1: Write failing tests**

- `status parses linked, guild, bot and invite without a token`
- `status maps 401 to a sign-in message`
- `remote view model shows linked username and test success`
- `remote view model explains DMs closed on a test failure`

- [ ] **Step 2: Implement the client and ViewModel**

Add `GET /api/v1/discord/status` parsing with the same safe error map. Move Discord logic out of `SettingsViewModel` into `RemoteViewModel`. Reuse `DiscordAuthActivity` and the existing launcher in Remote.

- [ ] **Step 3: Build the screen**

`EiScreen` + `EiTopBar("Remote", "Ei Void Discord bot")`; hero status card with linked username, guild, bot, fallback; Connect/Disconnect/Send test; command reference; fallback explanation; invite action; states. No token display.

- [ ] **Step 4: Run tests and commit**

Run `./gradlew testDebugUnitTest :app:lintDebug :app:assembleDebug`; install and verify not-linked/config path on emulator/Redmi.
Commit `feat(remote): add Discord bot control centre`.

---

### Task 14: Navigation restructure and image/audio removal

**Files:**
- Modify: `AssistantDrawer.kt`, `AssistantHomeScreen.kt`, `AssistantModels.kt`, `VoidNavHost.kt`
- Delete: `AssistantPlaceholderScreens.kt`, image/explore references
- Modify: `AssistantEngine.kt` / `VoidNavHost` audio callbacks
- Test: compile, navigation walkthrough, `grep` for removed symbols.

**Interfaces:**
- `AssistantDestination { HOME, REMINDERS, PROJECTS, SKILLS, REMOTE, SETTINGS }` (plus internal route constants; no IMAGES/EXPLORE).
- Routes: `assistant`, `reminders`, `projects`, `skills`, `remote`, `settings`, `usage`, editors/workspaces.
- Composer: no `onDictate`/`onVoiceMode`; plus menu New reminder/Projects/Skills/Settings.

- [ ] **Step 1: Remove audio and image code**

Delete `Mic`, `GraphicEq`, mic/waveform buttons, `voiceUnavailable`, `onDictate`/`onVoiceMode`, Images/Explore screens/routes/destinations and all copy. Remove `ImagesScreen`/`ExploreScreen` and `AssistantPlaceholderScreens.kt`. `AssistantModels` keeps only task prompts.

- [ ] **Step 2: Rewire navigation**

Drawer sections: Workspace = Chat, Reminders, Projects, Skills; System = Remote, Settings. Update `drawerDestinationFor`/`routeFor`, top-level routes, `VoidNavHost` composables, `AssistantDrawer`, `AssistantHomeScreen` callbacks. Remove the legacy `AssistantShell` branches for Images/Explore or delete the unused shell. Settings/Reminders links call the new routes. Keep Usage nested under Settings.

- [ ] **Step 3: Verify no dead references**

Run `grep -R "IMAGES\|EXPLORE\|Library\|Customize\|onDictate\|onVoiceMode\|Mic\|GraphicEq" android/app android/feature android/core` and `./gradlew testDebugUnitTest :app:lintDebug :app:assembleDebug`. Remaining historical docs are allowed; no app code hits.

- [ ] **Step 4: Device walkthrough and commit**

Open every drawer tab, back, new chat, deep link to reminder editor, rotate landscape. Commit `feat(nav): restructure tabs and remove image/audio surfaces`.

---

### Task 15: Advanced UI polish and motion

**Files:**
- Create: `designsystem/.../Motion.kt`
- Modify: `AssistantHomeScreen.kt`, `SkillsScreen.kt`, `RemoteScreen.kt`, `RemindersScreen.kt`, `SettingsScreen.kt`, `AssistantDrawer.kt`
- Test: compile; device screenshots in portrait/landscape.

**Interfaces:**
- `object EiMotion { const val Short=120; const val Medium=220; const val Long=320; val Standard = CubicBezierEasing(0.2f,0f,0f,1f) }`
- `fun Modifier.pressScale(interactionSource: MutableInteractionSource, scale: Float = 0.97f): Modifier`

- [ ] **Step 1: Implement motion tokens and press feedback**

Apply pressScale to task cards, skill cards and primary actions; do not shift layout bounds. Use `animateColorAsState` for drawer selection and `animateContentSize` for expanding cards.

- [ ] **Step 2: Add the skill rail and nav transitions**

Render up to three enabled skills above the composer; use `AnimatedVisibility` for the rail and `AnimatedContent`/NavHost fade-slide transitions for top-level destinations. Respect reduced motion (`LocalMotionDurationScale`).

- [ ] **Step 3: Polish the new screens**

Ensure equal-height cards, safe areas, 4/8dp rhythm, 48dp touch targets, no overflow at large font scale, and no debug/placeholder copy. Add skeleton/loading states only where data is async.

- [ ] **Step 4: Device verification and commit**

Capture portrait/landscape screenshots for Chat, Reminders, Skills, Remote, Settings. Commit `feat(ui): add motion and product polish`.

---

### Task 16: Server-backed usage screen

**Files:**
- Modify: `core/reminders/.../DiscordLinkClient.kt`, `feature/settings/.../UsageViewModel.kt`, `UsageOverviewScreen.kt`, `SettingsScreen.kt`
- Test: `UsageViewModelTest`, `DiscordLinkClientTest`

**Interfaces:**
- Extend `EiVoidAccount` with `usage: UsageSnapshot?`, `planLimit: Int` and `plan: String`.
- `UsageViewModel` consumes `DiscordLinkService.account()` (or a renamed account client) and exposes server usage.
- `AccountPlanSync` (or `SettingsViewModel.refreshAccount`) writes `SettingsStore.setPlan(Plan.fromName(account.plan))` after a successful account read.

- [ ] **Step 1: Write failing tests**

- `usage view model maps server usage and plan limit`
- `usage view model shows sign-in state when Clerk is absent`
- `usage view model shows an unavailable state when the service fails`
- `a successful account refresh updates the local plan`

- [ ] **Step 2: Implement**

Parse the `/account` `usage`/`planLimit`/`plan` fields in `EiVoidAccount`. Replace local `UsageDao` reads. The screen copy says "Server usage" and explains the monthly reset. Keep a local empty state for signed-out previews. Stop writing local usage entries (already removed from the agent in Task 6). Sync the server plan into `SettingsStore` so the tier router and Settings match the account.

- [ ] **Step 3: Run tests and commit**

Commit `feat(usage): show server AI usage`.

---

### Task 17: Docs, configuration, release and device verification

**Files:**
- Modify: `README.md`, `docs/architecture/overview.md`, `docs/api/README.md`, `docs/security/*`, `docs/runbooks/deploy.md`, `docs/qa/README.md`, `render.yaml`, `.env.example`, `services/web/.env.example`, `services/web/src/content.ts`, `services/web/views/*` where copy mentions provider keys, images or audio
- Create: `docs/skills/README.md`, `docs/releases/v0.4.0.md`
- Modify version: `android/app/build.gradle.kts` `versionCode = 6`, `versionName = "0.4.0"`

- [ ] **Step 1: Document**

Remove all provider-key, image, audio and Customize references. This includes
the website copy in `services/web/src/content.ts`: the old "no intermediary
server" and "provider keys are yours" answers are no longer true. Add:
- architecture diagram/flow: app → Clerk-authenticated Ei Void AI → upstream model.
- AI env table.
- skills authoring guide with built-in IDs and custom skill format/limits.
- Remote Discord setup and command reference.
- Reminders/Ei Snore flow.
- security/SSRF/secret notes; prompts go through the Ei Void service.
- release notes and checksum process.

- [ ] **Step 2: Run the full verification**

```bash
npm run typecheck
npm test
cd android
./gradlew testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Expected: Node and Android suites green, lint clean, APK built.

- [ ] **Step 3: Deploy prerequisites**

Add `AI_API_KEY`, `AI_BASE_URL`, the three `AI_MODEL_*` values to Render `sync:false`; leave Clerk/Discord/GitHub/mail unchanged. Document that the app cannot use AI until the web service is deployed with the key.

- [ ] **Step 4: Device verification**

Install the 0.4.0 debug/release preview on the emulator and Redmi. Walk through:
- Chat with the server AI configured (or explicit unavailable state).
- Skills create/toggle/edit/delete and restart persistence.
- Reminders add/edit/mark paid; Ei Snore preferences.
- Remote not-linked, connect error, linked/test if credentials exist.
- Settings links, Drawer, landscape, back stack, no debug ribbon.
- Existing reminders preserved.

- [ ] **Step 5: Tag and release**

Bump, commit, push, tag `v0.4.0`, publish the prerelease asset, rename it to `EiVoid-v0.4.0.apk` if the workflow still emits `-debug`, and record the checksum.

---

## Self-Review

**Spec coverage**

- Server AI gateway / no user keys → Tasks 1, 2, 5, 6, 16.
- Skills built-in/custom/prompt/tool → Tasks 7, 8, 9, 10.
- Remote Discord → Tasks 4, 13.
- Reminders tab/Ei Snore → Task 11.
- Settings rename/removal of provider keys → Tasks 6, 12.
- Image/audio removal and IA → Task 14.
- Advanced UI → Task 15.
- Docs/release/device → Task 17.

**Type consistency**

- `AI_TIER_ALIASES` matches `ServerModelCatalog` ids.
- `DiscordStatus` field names match the backend response and `RemoteViewModel`.
- `AgentSkill`/`SkillsStore` signatures are used consistently by Tasks 8–10.
- `createAiRouter`/`createBrowseRouter` signatures match app wiring.
- Routes `reminders`, `skills`, `remote`, `settings` match all callbacks.

**Open decisions to confirm with the product owner**

1. The current Library is already the reminder list, so this plan renames it to Reminders rather than adding a second reminder tab.
2. Tool-backed Web Browser runs in Projects in this release; Chat uses skills as instructions only.
3. AI requires the web service and `AI_API_KEY`; there is no local model-key fallback.
