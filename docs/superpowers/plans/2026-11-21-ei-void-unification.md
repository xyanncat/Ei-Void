# Ei Void Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge Ei Snore (reminders) and Void (on-device coding agent) into one Android app, one Discord bot, one website and one new repository.

**Architecture:** Void's modular Kotlin/Hilt/Compose codebase is the Android base. Ei Snore's reminder domain is ported into new `core:reminders` and `feature:reminders` modules. The Node side becomes an npm workspace with shared `packages/` and two services: a Clerk-authenticated Express website/API and an always-on Discord.js bot that owns reminder scheduling.

**Tech Stack:** Kotlin 2.4.21, Compose BOM 2026.06, Hilt 2.60.1, Room, JGit, Ktor, Clerk Android 1.5; Node 22, TypeScript, Express, EJS, Discord.js 14, PostgreSQL, zod, Vitest; GitHub Actions; Render.

**Spec:** `docs/superpowers/specs/2026-11-21-ei-void-unification-design.md`

## Global Constraints

- Product name is **Ei Void**; applicationId and namespace are `com.eivoid.app`; Kotlin base package is `com.eivoid`.
- Greenfield release: no existing Ei Snore or Void data migration; new Clerk application; new Postgres schema.
- One Android APK, one Discord bot application, one website deployment, one Postgres database.
- `minSdk 26` preferred, `targetSdk 35`, `compileSdk 37`, JDK 21.
- Room is local-first; the network never blocks a reminder save.
- Default reminder lead is **4 days**.
- All secrets stay server-side: Discord bot token, Clerk secret, mail credentials, database URL.
- No `.env.local`, `keystore.properties`, `release-keystore.jks`, `local.properties`, `.git`, build outputs or `node_modules` from the source archives may be committed.
- Node packages are ESM TypeScript with source-only entrypoints (`src/index.ts`) and run with `tsx`.
- Every HTTP and bot boundary validates with zod; every SQL query is parameterized.
- Bot replies are ephemeral and restricted to the single home guild.

## Review Focus

These are the failures most likely to bite a user; each has a test in the owning task.

1. A reminder due at time T is delivered twice or not at all after a bot restart (scheduler claim).
2. A reminder saved offline never syncs after connectivity returns, or one user's sync deletes another user's rows (sync isolation).
3. A notification fires with the wrong local time after a timezone change or DST boundary (alarm scheduling).
4. A Clerk user without a linked Discord account can still trigger a DM or read another user's usage (authorization).
5. Sign-out leaves cloned repositories, AI keys, or reminders on the device (data lifecycle).

---

## Phase 0: Repository bootstrap and secret quarantine

### Task 0.1: Bootstrap the monorepo

**Files:**
- Create: `README.md`, `.gitignore`, `LICENSE`, `package.json`, `tsconfig.base.json`, `tsconfig.json`
- Create: `docs/superpowers/specs/2026-11-21-ei-void-unification-design.md`
- Create: `docs/superpowers/plans/2026-11-21-ei-void-unification.md`

- [x] Copy Void's Android modules into `android/` and rename `com.voidapp` to `com.eivoid`.
- [x] Set `applicationId = "com.eivoid.app"`, namespace `com.eivoid.app`, root project `Ei Void`, app label `Ei Void`.
- [x] Copy Void's `website` and `discord-bot` into `services/web` and `services/bot` as starting points.
- [x] Add npm workspaces and shared TypeScript config.
- [x] Add a `.gitignore` that excludes secrets, build outputs, archives and dependency folders.
- [x] Commit the bootstrap.

**Verification:** `./gradlew :app:assembleDebug` succeeds in `android/`; `git status` shows no secret files.

### Task 0.2: Secret quarantine

**Files:**
- Create: `docs/security/secret-rotation.md`

- [ ] List every credential present in the source archives: Discord bot token(s), Clerk secret key(s), Gmail client/refresh token, Brevo/SendGrid keys, GitHub token, release keystore.
- [ ] Rotate each one before the first deployment.
- [ ] Confirm no source archive path is tracked by git.
- [ ] Add gitleaks/secret scanning to CI.

**Verification:** `git log --all --name-only | rg '(\.env|keystore|local\.properties)'` returns nothing.

---

## Phase 1: Android identity, rebrand and shell

### Task 1.1: Rebrand the Android base

**Files:**
- Modify: `android/settings.gradle.kts`, `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/res/values/strings.xml`
- Rename classes where user-facing: `VoidApp` → `EiVoidApp`, `VoidRoot` → `EiVoidRoot`, `VoidNavHost` → `EiVoidNavHost`.

- [ ] Set the app label and theme names to Ei Void.
- [ ] Replace user-facing "Void" copy in the existing screens with "Ei Void" while keeping module names stable.
- [ ] Add a single `AppConfig` object for API base URL, Clerk key, Discord client ID and app version.
- [ ] Run `:app:assembleDebug`.

**Verification:** APK installs and opens; app name is Ei Void.

### Task 1.2: Unified Clerk gate and navigation shell

**Files:**
- Modify: `android/app/src/main/java/com/eivoid/app/ui/EiVoidRoot.kt` (formerly `VoidRoot.kt`)
- Modify: `android/app/src/main/java/com/eivoid/app/navigation/EiVoidNavHost.kt`
- Modify: `android/app/src/main/java/com/eivoid/app/di/AppModule.kt`
- Create: `android/feature/reminders/` module shell and `android/core/reminders/` module shell

- [ ] Root observes `Clerk.isInitialized` and `Clerk.userFlow`; signed-out shows `AuthView()`, signed-in shows the app.
- [ ] Bottom destinations become Home, Reminders, Code, Settings.
- [ ] Home is the reminders home; Code is the existing repo list; Settings is the existing settings screen.
- [ ] Repos remains the authenticated root for back-stack invariants.
- [ ] Add placeholder Reminders screens so the graph compiles before the domain is ported.
- [ ] Run `:app:assembleDebug`.

**Verification:** Sign-in screen renders; debug build can enter the shell; all four tabs navigate.

### Task 1.3: Fold the GitHub token gateway into the API

**Files:**
- Modify: `android/core/common/src/main/java/com/eivoid/core/common/auth/AuthConfiguration.kt`
- Modify: `android/feature/auth/src/main/java/com/eivoid/feature/auth/ClerkTokenClient.kt`
- Modify: `android/app/build.gradle.kts`
- Create: `services/web/src/routes/github.ts`

- [ ] Rename `VOID_BACKEND_URL` to `EI_VOID_API_URL` in build config and code.
- [ ] Point the token client at `GET /api/v1/github/token`.
- [ ] Keep the existing 401/404/409 error mapping.
- [ ] Add a web endpoint test for missing/invalid session, missing GitHub link and success.

**Verification:** Contract test passes; app compiles with empty config.

---

## Phase 2: Reminder domain

### Task 2.1: Room persistence

**Files:**
- Create: `android/core/database/src/main/java/com/eivoid/core/database/ReminderEntity.kt`
- Create: `android/core/database/src/main/java/com/eivoid/core/database/ReminderDao.kt`
- Modify: `android/core/database/src/main/java/com/eivoid/core/database/EiVoidDatabase.kt` (formerly `VoidDatabase.kt`)
- Test: `android/core/database/src/test/java/com/eivoid/core/database/ReminderDaoTest.kt`

- [ ] Define `ReminderEntity` with the fields in the spec, enums stored as strings.
- [ ] Add `ReminderDao`: observeAll, observeActive, get, getActive, getAll, insert, update, deleteById, deleteAll, applyDefaultLeadDays, markNotified.
- [ ] Add the reminder entity/dao to the database and bump the schema (greenfield: version 1 with all entities).
- [ ] Test insert/update/observe/mark-paid/paidAt.

**Verification:** `:core:database:testDebugUnitTest` passes.

### Task 2.2: Reminder repository and repeat rules

**Files:**
- Create: `android/core/reminders/src/main/java/com/eivoid/core/reminders/Reminder.kt`
- Create: `android/core/reminders/src/main/java/com/eivoid/core/reminders/ReminderRepository.kt`
- Create: `android/core/reminders/src/main/java/com/eivoid/core/reminders/ReminderSettings.kt`
- Test: `android/core/reminders/src/test/java/com/eivoid/core/reminders/ReminderRepositoryTest.kt`

- [ ] Port repeat rules and `advanceUntilFuture`.
- [ ] Implement save (insert/update + schedule + background sync), markPaid (one-time stops, repeating advances), snooze (>= 1 day, resets notified), delete, deleteAll, rescheduleAll, applyDefaultLeadDays.
- [ ] Test each behavior with a fake DAO/scheduler/sync.
- [ ] Test that a repeating reminder creates exactly one future occurrence.

**Verification:** `:core:reminders:testDebugUnitTest` passes.

### Task 2.3: Alarms and notifications

**Files:**
- Create: `android/core/reminders/src/main/java/com/eivoid/core/reminders/AlarmScheduler.kt`
- Create: `android/core/reminders/src/main/java/com/eivoid/core/reminders/NotificationHelper.kt`
- Create: `android/core/reminders/src/main/java/com/eivoid/core/reminders/ReminderAlarmReceiver.kt`
- Create: `android/core/reminders/src/main/java/com/eivoid/core/reminders/ReminderActionReceiver.kt`
- Create: `android/core/reminders/src/main/java/com/eivoid/core/reminders/BootReceiver.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Test: `android/core/reminders/src/test/java/com/eivoid/core/reminders/AlarmSchedulerTest.kt`

- [ ] Port exact/inexact scheduling with `RTC_WAKEUP`.
- [ ] Compute `notifyAt` from due date, lead days and the user's notify time in the system zone.
- [ ] Post the reminder notification with Mark Paid and Snooze actions.
- [ ] Record `notifiedForDueAt` only when the notification posts.
- [ ] Reschedule on boot, package replace, time change and timezone change.
- [ ] Add permissions and receivers to the manifest.
- [ ] Robolectric-test trigger time, cancellation and reschedule.

**Verification:** Robolectric tests pass; a debug build installs and schedules an alarm.

### Task 2.4: Backend sync client

**Files:**
- Create: `android/core/reminders/src/main/java/com/eivoid/core/reminders/ReminderSyncClient.kt`
- Test: `android/core/reminders/src/test/java/com/eivoid/core/reminders/ReminderSyncClientTest.kt`

- [ ] Build the sync JSON with profile (timezone, notify time, name, email, emailEnabled) and reminders.
- [ ] Use `Clerk.auth.getToken()` and the shared Ktor client.
- [ ] Coalesce requests; never block a local save.
- [ ] Map backend errors to user-facing messages.
- [ ] Test request shape, auth header, 401, 409, and success with MockEngine.

**Verification:** `:core:reminders:testDebugUnitTest` passes.

---

## Phase 3: Reminder UI and settings

### Task 3.1: Reminders screen and editor

**Files:**
- Create: `android/feature/reminders/src/main/java/com/eivoid/feature/reminders/RemindersScreen.kt`
- Create: `android/feature/reminders/src/main/java/com/eivoid/feature/reminders/EditReminderScreen.kt`
- Create: `android/feature/reminders/src/main/java/com/eivoid/feature/reminders/RemindersViewModel.kt`
- Create: `android/feature/reminders/src/main/java/com/eivoid/feature/reminders/ReminderFormatting.kt`
- Test: `android/feature/reminders/src/test/java/com/eivoid/feature/reminders/RemindersViewModelTest.kt`

- [ ] Home card: next reminder, month totals, paid/upcoming counts.
- [ ] Reminders list: active/upcoming/paid, amount, due date, category.
- [ ] Editor: title, merchant, bank, amount, currency, due date/time, repeat, category, notes, lead days.
- [ ] Permission guidance for notifications and exact alarms.
- [ ] Format money/date with the same rules as Ei Snore.
- [ ] ViewModel test for save, mark paid, snooze, delete.

**Verification:** UI tests/navigation manual; ViewModel tests pass.

### Task 3.2: Settings integration

**Files:**
- Modify: `android/feature/settings/src/main/java/com/eivoid/feature/settings/SettingsScreen.kt`
- Modify: `android/feature/settings/src/main/java/com/eivoid/feature/settings/SettingsStore.kt`
- Modify: `android/feature/settings/src/main/java/com/eivoid/feature/settings/SessionCleaner.kt`
- Test: `android/feature/settings/src/test/java/com/eivoid/feature/settings/SessionCleanerTest.kt`

- [ ] Identity card: Clerk name/email.
- [ ] Notifications card: default lead, notify time, email toggle, permission status.
- [ ] Discord card: connect/disconnect/test; show linked username.
- [ ] AI provider cards remain.
- [ ] Sign out clears secrets, Room data, workspaces, cached repos and cancels alarms.
- [ ] Delete account calls the API and then wipes locally.

**Verification:** SessionCleaner test passes; manual sign-out leaves no data.

---

## Phase 4: Backend foundation

### Task 4.1: Shared contracts, config and database

**Files:**
- Create: `packages/contracts/src/index.ts`
- Create: `packages/config/src/index.ts`
- Create: `packages/db/src/index.ts`, `packages/db/src/migrations.ts`, `packages/db/src/migrate.ts`
- Test: `packages/contracts/test/contracts.test.ts`, `packages/db/test/store.test.ts`

- [ ] Implement the schemas/types and normalization from the spec.
- [ ] Implement env parsing with production validation and Clerk issuer derivation.
- [ ] Implement the unified migrations and `Store`.
- [ ] Test pure normalization and (when `TEST_DATABASE_URL` is set) transactional sync isolation.

**Verification:** `npx tsc --noEmit -p tsconfig.json`; `npm test` in the packages.

### Task 4.2: Mailer and Discord core

**Files:**
- Create: `packages/mailer/src/index.ts`
- Create: `packages/discord-core/src/index.ts`
- Test: `packages/mailer/test/mailer.test.ts`, `packages/discord-core/test/discord-core.test.ts`

- [ ] Port Gmail API/Brevo/SendGrid/SMTP with structured results.
- [ ] Port Discord OAuth verification, guild guard, invite URL, DM/fallback formatting.
- [ ] Test provider detection, request shapes, status mapping and fallback selection.

**Verification:** Package tests pass.

---

## Phase 5: Unified Discord bot

### Task 5.1: Bot commands and ownership

**Files:**
- Modify: `services/bot/src/commands.ts`, `services/bot/src/index.ts`, `services/bot/src/register.ts`
- Create: `services/bot/src/reminderCommands.ts`
- Test: `services/bot/test/commands.test.ts`

- [ ] Keep `/link`, `/usage`, `/about`; add `/reminders`, `/snooze`, `/paid`.
- [ ] Resolve the caller's account by Discord ID; reject unlinked users with a clear message.
- [ ] Every reply is ephemeral; foreign guilds/DMs are refused.
- [ ] Test command data, ownership checks and formatting.

**Verification:** `npm test -w @eivoid/bot` passes; manual command test in the home guild.

### Task 5.2: Reminder scheduler

**Files:**
- Create: `services/bot/src/scheduler.ts`
- Create: `services/bot/src/delivery.ts`
- Test: `services/bot/test/scheduler.test.ts`

- [ ] Every 60 seconds, claim due reminders per channel.
- [ ] Send Discord DM or fallback mention; send email through `@eivoid/mailer`.
- [ ] Mark each channel delivered independently; retry transient failures; terminal-mark permanent ones.
- [ ] Test duplicate prevention, restart safety, one channel failing without blocking the other.

**Verification:** Scheduler tests pass; logs show one delivery per reminder.

### Task 5.3: Bot health and graceful shutdown

**Files:**
- Modify: `services/bot/src/index.ts`
- Create: `services/bot/src/health.ts`
- Test: `services/bot/test/health.test.ts`

- [ ] Expose `/health` on `BOT_HEALTH_PORT` with booleans only.
- [ ] Handle SIGTERM/SIGINT: stop scheduler, destroy client, close pool.
- [ ] Test the health payload and shutdown ordering.

**Verification:** Local `/health` returns ok; container exits cleanly.

---

## Phase 6: Unified website and app API

### Task 6.1: Clerk website auth and page skeleton

**Files:**
- Modify: `services/web/src/app.ts`, `services/web/src/config.ts`, `services/web/src/routes/pages.ts`
- Create: `services/web/src/auth/clerk.ts`
- Modify: `services/web/views/` branding and navigation
- Test: `services/web/test/pages.test.ts`

- [ ] Replace the Discord-cookie identity with `@clerk/express`.
- [ ] Protect `/dashboard` and `/link`.
- [ ] Rebrand pages to Ei Void and add `/reminders` and `/agent`.
- [ ] Keep CSP, CSRF and rate limits.
- [ ] Test public pages, auth redirects and dashboard rendering.

**Verification:** `npm test -w @eivoid/web` passes.

### Task 6.2: App API

**Files:**
- Create: `services/web/src/routes/api.ts`
- Create: `services/web/src/auth/appAuth.ts`
- Create: `services/web/src/middleware/errors.ts`
- Test: `services/web/test/api.test.ts`

- [ ] Implement every endpoint in the spec's app API table.
- [ ] Verify Clerk JWTs, enforce per-user ownership, and never accept a recipient from the body.
- [ ] Implement GitHub provider-token exchange.
- [ ] Implement device key issue/revoke and usage ingestion.
- [ ] Add rate limits and request size caps.
- [ ] Test 401/403/404/409/429 and success paths.

**Verification:** API tests pass; manual curl with a real Clerk token returns expected data.

### Task 6.3: Dashboard, link flow and docs

**Files:**
- Modify: `services/web/src/routes/pages.ts`
- Modify: `services/web/views/dashboard.ejs`, `services/web/views/link.ejs`
- Modify: `services/web/src/content.ts`
- Test: `services/web/test/link.test.ts`

- [ ] Dashboard shows account, reminders summary, Discord link, plan/usage, device keys and delete data.
- [ ] `/link` confirms a bot-created code for the signed-in Clerk user.
- [ ] Docs cover agent, reminders, bot, API, security and self-hosting.
- [ ] Update privacy/terms for both pillars.
- [ ] Test link code expiry, single use and cross-user protection.

**Verification:** Playwright/manual walkthrough succeeds.

### Task 6.4: APK downloads and health

**Files:**
- Create: `services/web/src/routes/app.ts`
- Modify: `services/web/src/app.ts`
- Test: `services/web/test/app.test.ts`

- [ ] `GET /api/v1/app/latest` reads GitHub Releases with a cache.
- [ ] `GET /api/v1/app/download` streams the APK asset.
- [ ] Health returns configuration booleans without values.
- [ ] Test missing token, missing asset and successful proxying with mocked fetch.

**Verification:** Endpoint tests pass.

---

## Phase 7: Usage sync and dashboard integration

### Task 7.1: Device keys and usage upload

**Files:**
- Modify: `android/core/reminders/src/main/java/com/eivoid/core/reminders/` (or create `core:usage`)
- Create: `android/core/usage/src/main/java/com/eivoid/core/usage/UsageSyncClient.kt`
- Modify: `android/feature/settings/src/main/java/com/eivoid/feature/settings/UsageViewModel.kt`
- Test: `android/core/usage/src/test/java/com/eivoid/core/usage/UsageSyncClientTest.kt`

- [ ] Issue a device key after sign-in (opt-in) and store it encrypted.
- [ ] Upload plan + counters; handle 401/429/5xx.
- [ ] Show last-sync state in the app.
- [ ] Test request shape and retry behavior.

**Verification:** `/usage` in Discord shows the app's counters after a sync.

### Task 7.2: Dashboard usage

**Files:**
- Modify: `services/web/views/dashboard.ejs`, `services/web/src/routes/pages.ts`
- Test: `services/web/test/dashboard.test.ts`

- [ ] Show plan, used/limit, percent and last sync.
- [ ] Show "linked, not synced" when no snapshot exists.
- [ ] Test unsynced and synced states.

**Verification:** Dashboard tests pass.

---

## Phase 8: Hardening, CI and release

### Task 8.1: Security hardening

**Files:**
- Modify: `android/core/security/`, `android/core/common/network/`, `android/app/src/main/`
- Modify: `services/web/src/app.ts`, `services/bot/src/index.ts`
- Create: `docs/security/threat-model.md`

- [ ] Enable SQLCipher in release Room.
- [ ] Fill certificate pins for GitHub, Clerk, Ei Void API and AI providers.
- [ ] Add gitleaks, CodeQL and Dependabot.
- [ ] Add request IDs and structured logging with content redaction.
- [ ] Add rate limits to every mutation and linking endpoint.

**Verification:** Security checklist passes; no secret appears in logs.

### Task 8.2: CI

**Files:**
- Create: `.github/workflows/android.yml`, `.github/workflows/node.yml`, `.github/workflows/security.yml`, `.github/workflows/release.yml`
- Test: CI run on a pull request.

- [ ] Android job: JDK 21, SDK 37, unit tests, lint, assembleDebug.
- [ ] Node job: npm ci, typecheck, tests, builds.
- [ ] Security job: gitleaks, CodeQL, npm audit.
- [ ] Release job: tag → signed APK → GitHub Release.

**Verification:** All workflows green; release attaches an APK.

### Task 8.3: Render deployment

**Files:**
- Create: `render.yaml`
- Create: `services/web/Dockerfile`, `services/bot/Dockerfile`
- Create: `docs/runbooks/deploy.md`, `docs/runbooks/rollback.md`

- [ ] Blueprint with web, bot, Postgres.
- [ ] Env wiring: database URL, Clerk keys, Discord token, mail provider, updater.
- [ ] Health checks and auto-deploy.
- [ ] Document secret setup and rollback.

**Verification:** Blueprint deploys; `/healthz` and bot `/health` are green.

### Task 8.4: Device QA and release candidate

- [ ] Fresh install → Clerk sign-in → add reminder → exact alarm → mark paid → snooze.
- [ ] Reboot/timezone change reschedules.
- [ ] Sync → `/reminders` in Discord → `/snooze` → `/paid`.
- [ ] Link GitHub → clone → agent edit → commit/push.
- [ ] DMs closed → fallback mention.
- [ ] Sign out wipes data; delete account removes server data.
- [ ] Build signed APK, publish release, verify in-app update.

**Verification:** QA checklist complete; release tagged.

---

## Execution notes

- Phases 4–6 can run in parallel with Phase 2–3 once the Android base builds.
- The bot must be deployable before the website dashboard is finished so `/link` can be tested.
- Never mark a task complete without running its verification command.
- Keep the spec and this plan in sync; record any deliberate deviation in `docs/adr/`.
