# Ei Void — Unified Product Design

**Date:** 2026-11-21
**Status:** Approved for implementation
**Scope:** Merge Ei Snore (reminders) and Void (on-device coding agent) into one Android application, one Discord bot, one website, and one new repository.

---

## 1. Purpose

Ei Void is one Android product with two pillars:

1. **Reminders** — local-first bill, renewal and one-time payment tracking with exact Android alarms, Mark Paid / Snooze actions, optional email delivery, and optional Discord delivery.
2. **Agent** — a native Android AI coding agent that clones a GitHub repository, edits it through guarded tools, and commits/pushes the result.

The pillars share one Clerk identity, one design system, one local database, one settings area, one Discord link, one website, one dashboard, one bot and one Postgres database.

### Success criteria

- A fresh install signs in with Clerk, adds a reminder, and receives an exact local notification while offline.
- The reminder syncs to the hosted API and `/reminders` in Discord lists it.
- The same account links GitHub, clones a repository, runs the agent, and pushes a change.
- One website serves marketing, docs, dashboard, Discord linking, and APK updates.
- One Discord bot serves `/link`, `/usage`, `/reminders`, `/snooze`, `/paid` and `/about`.
- No secret from either source archive is present in the new repository history.
- CI builds the APK and typechecks/tests the Node workspaces.

### Non-goals for v1

- Play Store distribution; the APK remains a GitHub Releases sideload.
- Billing integration; plan names and token budgets stay as product metadata.
- Migrating existing Ei Snore or Void installs/data; this is greenfield.
- iOS or Kotlin Multiplatform.
- Local shell execution in the agent.
- Multi-tenant Discord bot; it remains locked to one home guild.

## 2. Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Product name | **Ei Void** | Combines both identities; matches the author's existing `Ei-Void` references |
| Repo | `ei-void` | Clean, hyphenated GitHub name |
| Android applicationId | `com.eivoid.app` | Greenfield; no inherited Clerk package registration |
| Kotlin namespace | `com.eivoid` | Safe (not a Java keyword), consistent with the app ID |
| Merge base | Void | Modern modular Kotlin/Hilt/Compose architecture and working agent core |
| App shape | One merged APK | Shared identity, database, settings and navigation |
| Continuity | Greenfield | New package; old apps and databases remain separate |
| Clerk | New "Ei Void" application | One identity for app, website and bot linking; Google, Discord and GitHub connections |
| GitHub provider token | Unified web service endpoint | Folds the Cloudflare Worker into the same Node deployment |
| Discord application | Reuse the existing Void bot application, rename it | It is already tied to the home guild and invite; the Ei Snore bot is retired |
| Backend topology | `services/web` + `services/bot` + one Postgres | Bot/scheduler must stay always-on; web can sleep |
| Bot data access | Shared `@eivoid/db` package | Removes the HTTP hop and keeps the scheduler independent of web uptime |
| Native SDK floor | `minSdk 26` preferred, fall back to 33 if agent QA fails | Keeps Android 8+ reminder reach |
| Local database | Room + SQLCipher | Reminders and AI credentials justify encryption at rest |

## 3. Architecture

```text
                         ┌────────────────────────────┐
                         │       Clerk (Ei Void)      │
                         │ Google · Discord · GitHub  │
                         └────────────┬───────────────┘
                                      │ session JWT
        ┌─────────────────────────────┼─────────────────────────────┐
        │                             │                             │
┌───────▼────────┐          ┌─────────▼─────────┐          ┌────────▼────────┐
│  Android app   │          │   services/web    │          │   services/bot  │
│  com.eivoid.app│  HTTPS   │ Express + EJS     │  shared  │ Discord.js 14   │
│                │─────────▶│ pages + /api/v1   │◀────────▶│ commands        │
│ Room + alarms  │          │ Clerk + dashboard │  @eivoid │ scheduler       │
│ JGit + agent   │          │ GitHub token      │   /db    │ DM + fallback   │
└───────┬────────┘          │ APK updates       │          └────────┬────────┘
        │                   └─────────┬─────────┘                   │
        │                             │                             │
        │                   ┌─────────▼─────────┐                   │
        └────── JGit ───────▶│    Postgres       │◀──────────────────┘
             GitHub         │ accounts          │
                            │ reminders         │
                            │ link_codes        │
                            │ device_keys       │
                            │ usage_snapshots   │
                            │ waitlist          │
                            └───────────────────┘
```

### Trust boundaries

- The Android app holds only public configuration and the user's own tokens (Clerk session, GitHub provider token, AI provider keys) in Keystore-backed storage.
- The web service holds the Clerk secret, Discord bot token usage for verification only where needed, mail credentials, and the database URL.
- The bot worker holds the Discord bot token, database URL, and mail credentials if it runs the scheduler.
- No service receives repository content, prompts, chat messages, or AI provider keys.
- The bot never receives a Clerk session token.

## 4. Repository layout

```text
ei-void/
├── android/                       Gradle root
│   ├── app/                       com.eivoid.app
│   ├── core/
│   │   ├── common/                network, config, auth, GitHub API
│   │   ├── security/              EncryptedSecretStore
│   │   ├── database/              one Room DB
│   │   ├── git/ workspace/
│   │   ├── ai/ agent/
│   │   └── reminders/             reminder domain, alarms, sync
│   ├── feature/
│   │   ├── auth/ repos/ chat/ workspace/ settings/
│   │   └── reminders/
│   └── designsystem/
├── services/
│   ├── web/                       Express + EJS + Clerk + API
│   └── bot/                       Discord.js worker + scheduler
├── packages/
│   ├── contracts/                 zod schemas and shared API types
│   ├── config/                    env parsing and validation
│   ├── db/                        pg pool, migrations, Store
│   ├── mailer/                    Gmail API, Brevo, SendGrid, SMTP
│   └── discord-core/              guild guard, OAuth verify, DM/fallback
├── docs/
├── .github/workflows/
├── render.yaml
├── package.json                   npm workspaces
└── README.md
```

### Node workspace conventions

- Node 22, ESM TypeScript, source-only package entrypoints (`src/index.ts`) executed with `tsx`.
- Every package owns one responsibility and exposes a small index.
- Shared behavior lives in packages; services compose them.
- Database access is only through `@eivoid/db`.
- Contracts are validated with zod at every HTTP and bot boundary.

## 5. Android application

### 5.1 Modules

| Module | Responsibility |
|---|---|
| `:app` | Hilt entry points, `EiVoidApp`, root Clerk gate, navigation, DI modules, manifest, resources |
| `:core:common` | `AuthConfiguration`, Ktor client, TLS/pinning, GitHub API, shared time types |
| `:core:security` | `SecretStore` / `EncryptedSecretStore` |
| `:core:database` | Room database with repos, chats, usage, reminders |
| `:core:git`, `:core:workspace` | JGit clone/commit/push, workspace files, diffs |
| `:core:ai`, `:core:agent` | provider adapters, router, tool loop |
| `:core:reminders` | reminder models, repository, alarm scheduler, notifications, backend sync, device key |
| `:feature:auth` | Clerk native sign-in and GitHub token exchange |
| `:feature:repos`, `:feature:workspace`, `:feature:chat` | agent UI |
| `:feature:reminders` | reminder home card, list, edit, detail, permission guidance |
| `:feature:settings` | account, theme, AI providers, usage, notifications, Discord, delete data |
| `:designsystem` | theme tokens, background, components, logo |

### 5.2 Identity and sign-in

- `EiVoidApp.onCreate()` initializes Clerk when `BuildConfig.CLERK_PUBLISHABLE_KEY` starts with `pk_`.
- `EiVoidRoot` observes `Clerk.isInitialized` and `Clerk.userFlow`.
  - not initialized → loading
  - signed out → Clerk `AuthView()` over the branded backdrop
  - signed in → main navigation
- The first time the Code pillar needs GitHub, the app fetches the Clerk session token and calls `GET /api/v1/github/token` on the web service. The response is `{ token, scopes, expiresAt }`.
- If the user has not linked GitHub to Clerk, the app shows "Connect GitHub" and opens the Clerk Account Portal.
- The GitHub token is stored in `EncryptedSecretStore` and never logged.

### 5.3 Navigation

Bottom destinations:

1. **Home** — greeting, next reminder, reminder summary, update card, agent/usage shortcut.
2. **Reminders** — active/upcoming/paid list, search/filter, add FAB.
3. **Code** — repository list and clone; workspace/chat pushed full-screen.
4. **Settings** — account, notifications, Discord, AI providers, theme, usage, legal, sign out, delete data.

`ReminderEdit`, `Usage`, `Workspace`, and `Onboarding` are pushed full-screen routes. Repos remains the root of the authenticated back stack.

### 5.4 Reminder domain

`ReminderEntity` fields: id, title, merchant, bank, amountMinor, currency, dueAt, repeatRule, category, notes, leadDays, leadDaysCustomized, isPaid, isActive, createdAt, paidAt, notifiedForDueAt.

Behavior ported from Ei Snore:

- Repeat rules `NONE`, `WEEKLY`, `MONTHLY`, `QUARTERLY`, `YEARLY`; `advance` uses local time.
- Default lead is 4 days in the new product (Ei Snore's UI default was 3; the spec's 4-day buffer wins).
- `markPaid` stops a one-time reminder; a repeating reminder creates the next future occurrence.
- `snooze` moves `dueAt` forward by at least one day and resets the local notified marker.
- `AlarmScheduler` uses `setExactAndAllowWhileIdle` when permitted, otherwise `setAndAllowWhileIdle`.
- `ReminderAlarmReceiver` posts the notification and only records `notifiedForDueAt` when the notification actually posted.
- `ReminderActionReceiver` handles Mark Paid and Snooze from the notification.
- `BootReceiver` reschedules on boot, package replace, time change and timezone change.
- Notification channel `reminders`, high importance, private visibility, content intent deep-links to the reminder.
- Exact-alarm and notification permission guidance is shown in Settings; denial falls back gracefully.

### 5.5 Sync

- Room is the source of truth. The app never blocks a save on the network.
- After each mutation, and on sign-in/app start, a coalesced background sync uploads the full reminder snapshot.
- `BackendSyncClient` uses the Ktor client, the Ei Void API base URL, and `Clerk.auth.getToken()`.
- A failed sync is retried with bounded backoff; a later successful sync is idempotent.
- The API stores reminders per Clerk user and replaces only that user's set.
- Discord and email delivery state is tracked server-side, not in the app.

### 5.6 Design

- Keep Void's chalk-on-ink dark-first system and paper light theme.
- Add one reminder accent (amber/lime) plus success/warning tokens; do not reintroduce Ei Snore's four-color neon palette.
- Use Void's `VoidBackground`, cards, typography (Manrope UI / JetBrains Mono code).
- Provide a `ReminderGlassCard` variant only if it stays legible against the same tokens.

### 5.7 Security

- `minSdk 26`, `targetSdk 35`, `compileSdk 37`, `arm64-v8a` first; add other ABIs only if size allows.
- `allowBackup=false`.
- TLS 1.2/1.3 only, cleartext banned, certificate pins for GitHub, Clerk, Ei Void API and AI providers at release.
- `EncryptedSharedPreferences` for GitHub token, AI provider keys, device key and any Discord link token.
- SQLCipher-backed Room in release builds; plain in-memory Room in JVM tests.
- `FLAG_SECURE` in release; optional biometric lock later.
- Sign-out wipes secrets, Room data, cloned workspaces and cached repos.
- Delete account also calls `DELETE /api/v1/account` and wipes local data.

## 6. Web service

### 6.1 Pages

| Path | Purpose |
|---|---|
| `/` | Two-pillar landing |
| `/reminders` | Reminder product page |
| `/agent` | Agent product page |
| `/features`, `/pricing` | Capability and plan detail |
| `/bot` | Discord companion and commands |
| `/download` | Signed APK / build from source |
| `/docs` + `/docs/:slug` | Agent, reminders, bot, API, security, self-hosting |
| `/security`, `/privacy`, `/terms` | Trust and legal |
| `/changelog` | Release notes |
| `/dashboard` | Clerk-authenticated account, reminders, Discord, usage, device keys, delete data |
| `/link?code=…` | Clerk-authenticated Discord link confirmation |
| `/thanks` | Waitlist confirmation |
| `/healthz` | `{ ok, store, version }` |
| `/sitemap.xml`, `/robots.txt` | SEO |

### 6.2 App API

All app endpoints use `Authorization: Bearer <Clerk session JWT>` unless noted.

| Method and path | Behavior |
|---|---|
| `POST /api/v1/reminders/sync` | Validate profile + reminders, upsert account, replace the user's reminders, return `{ synced, discordLinked }` |
| `GET /api/v1/account` | Return email, display name, Discord link, plan and latest usage |
| `DELETE /api/v1/account` | Delete the account and cascade data |
| `POST /api/v1/discord/link` | Verify a Discord OAuth access token, require home-guild membership, store the link |
| `DELETE /api/v1/discord/link` | Clear the link |
| `POST /api/v1/test/email` | Send to the Clerk primary email only |
| `POST /api/v1/test/discord` | Send a test DM to the linked Discord user |
| `GET /api/v1/github/token` | Verify Clerk session; return the GitHub provider token from Clerk's Backend API |
| `POST /api/v1/devices` | Issue a one-time device key (returned once) and store its SHA-256 hash |
| `DELETE /api/v1/devices/:id` | Revoke a device key |
| `POST /api/v1/usage` | Device-key bearer; accept plan + counters |
| `GET /api/v1/app/latest` | Latest APK version and notes from GitHub Releases |
| `GET /api/v1/app/download` | Stream the latest APK asset |
| `GET /api/v1/health` | Public health and configuration booleans, never values |

### 6.3 Bot API

If the bot uses the web service for link-code creation, use `Authorization: Bearer <BOT_API_TOKEN>` on `/v1/discord/link` and `/v1/discord/usage`. In the default design the bot uses `@eivoid/db` directly and only the website uses the shared packages.

### 6.4 Website auth

- `@clerk/express` middleware with the Ei Void publishable/secret keys.
- `/dashboard` and `/link` require a Clerk session; unauthenticated visitors get Clerk's sign-in flow or a sign-in link.
- Browser forms keep the double-submit CSRF token and signed cookie only for form integrity, not identity.
- The old Discord-cookie identity is removed.

### 6.5 Website security

- Keep the nonce-based CSP, Helmet, HSTS in production, strict referrer policy, and no third-party scripts/fonts.
- Rate-limit waitlist, link, device and usage endpoints.
- Parameterized SQL only; zod validation at every boundary.
- No secret values in health or logs.
- `/dashboard`, `/link`, `/thanks`, and `/v1` are `noindex`.

## 7. Bot worker

### 7.1 Commands

| Command | Behavior |
|---|---|
| `/link` | Create a 10-minute single-use code; reply with the HTTPS website link |
| `/usage` | Look up the caller's linked account and latest usage snapshot |
| `/reminders` | List the caller's next 5 active reminders (title, amount, due date) |
| `/snooze <reminder> [days]` | Snooze one of the caller's reminders by 1–30 days |
| `/paid <reminder>` | Mark one of the caller's reminders paid |
| `/about` | One-line description of Ei Void |

All replies are ephemeral. Commands are guild-scoped to `DISCORD_GUILD_ID`. Refuse DMs and foreign guilds. Leave foreign guilds on startup and on `guildCreate`.

### 7.2 Scheduler

- Runs every 60 seconds in the bot worker.
- Selects at most 25 active, unpaid reminders with `notify_at <= now` that are not fully delivered.
- Claims each channel atomically before sending:
  - `discord_claimed_at` for Discord
  - `email_claimed_at` for email
- Marks `discord_notified_due_at = due_at` and `email_notified_due_at = due_at` independently.
- On transient failure, releases the claim with a retry-after timestamp; on permanent failure (invalid email, missing transport, blocked DM with no fallback), marks terminal.
- Repeats every minute; a reminder due at time T is never sent twice for the same `due_at`.
- The bot checks guild membership before a DM and falls back to a channel mention when DMs are closed.
- The scheduler is single-process safe via `FOR UPDATE SKIP LOCKED` / conditional updates.

### 7.3 Runtime

- `/health` reports `{ ok, bot, guilds, inLockedGuild, database, mail, scheduler }` booleans, never secrets.
- Graceful shutdown on SIGTERM/SIGINT.
- No privileged intents; only `Guilds`.
- One bot process per token.

## 8. Data model

See `packages/db/src/migrations`. The canonical tables:

- `accounts`
- `reminders`
- `link_codes`
- `device_keys`
- `usage_snapshots`
- `waitlist`
- `schema_migrations`

All FKs cascade from `accounts.clerk_user_id`. Discord IDs are unique when present. Device key hashes are unique. Reminder uniqueness is `(clerk_user_id, local_id)`.

## 9. Migration mapping

| Source | Destination | Action |
|---|---|---|
| Void `app`, `core/*`, `feature/*`, `designsystem` | `android/` | Copied, package-renamed to `com.eivoid`, rebranded |
| Void `website` | `services/web` | Copied, content rebuilt for two pillars, Clerk + reminder APIs added |
| Void `discord-bot` | `services/bot` | Copied, command set extended, scheduler embedded |
| Void `server` (Cloudflare Worker) | `services/web/src/auth/github.ts` | Folded into the web service |
| Ei Snore `ReminderEntity`, `Daos`, `ReminderRepository`, `Models` | `android/core/reminders`, `core/database` | Ported to Hilt/Kotlin, default lead 4 |
| Ei Snore `AlarmScheduler`, `NotificationHelper`, receivers | `android/core/reminders` | Ported and hardened |
| Ei Snore `BackendSyncClient` | `android/core/reminders` | Rewritten with Ktor + Clerk token |
| Ei Snore `MainViewModel`, screens | `android/feature/reminders` | Rewritten with Hilt, Void design system, four-tab shell |
| Ei Snore `server.js` schema/scheduler/mail/DM | `packages/db`, `packages/mailer`, `packages/discord-core`, `services/bot` | Refactored |
| Ei Snore static site | `services/web/views` | Content merged; retired as separate site |

### Never copied

`.git`, `.gradle`, `build`, `node_modules`, `local.properties`, `.env.local`, `keystore.properties`, `release-keystore.jks`, APKs, logs, extracted archives.

## 10. Security and privacy

- **Secret quarantine:** the supplied archives contain local env files, signing properties and a release keystore. No value from them may enter the repo. Rotate every credential that was present before deployment.
- **Clerk:** secret only on the server; publishable key in the app/website; JWT verified by signature, issuer and expiry.
- **GitHub:** provider token returned only to an authenticated Clerk session; never stored server-side or logged.
- **Discord:** bot token only in the bot worker; OAuth access tokens used once for linking and not persisted; single-guild lock; no privileged intents.
- **Email:** recipient is always the Clerk primary email; no open relay; provider credentials server-side only.
- **Database:** least-privilege user, TLS, indexes on delivery queries, no content logging.
- **Website:** CSP, CSRF, rate limits, input validation, no third-party trackers.
- **App:** Keystore-backed secrets, SQLCipher, no telemetry, no analytics, no prompt/reminder logging.
- **Data lifecycle:** sign-out wipes local data; delete-account removes server data; device keys are revocable.

## 11. Testing

- **Android unit:** contract/validation, repeat rules, repository actions, alarm scheduler (Robolectric), sync client (MockEngine), ViewModels, Room DAO/migration.
- **Android instrumented:** encrypted store, alarm notification, notification actions, boot reschedule, Clerk session (manual).
- **Node:** contracts validation, config validation, Store SQL against ephemeral Postgres, mailer request shapes, Discord OAuth status mapping, scheduler claim/retry logic, bot command ownership checks, web route auth.
- **Website:** supertest pages/API, CSP/CSRF, Clerk auth boundaries, link flow, XSS escaping, rate limits, sitemap/robots.
- **Manual:** real device sign-in, exact alarm on API 33+, DST/timezone change, reboot, DMs closed fallback, GitHub clone/push, Render deployment.

## 12. Operations

- **Render Blueprint:** `eivoid-web` (Docker web), `eivoid-bot` (worker), `eivoid-db` (Postgres 16).
- **Health:** `/healthz` on web, `/health` on bot.
- **Logs:** structured, request IDs, no secrets/content.
- **CI:** Android build/test/lint, Node typecheck/test, security scan, release APK on tag.
- **Rollback:** redeploy the previous image; the database migrations are additive.
- **Costs:** bot worker and Postgres are the always-on costs; web can be free/starter.

## 13. Risks and open issues

| Risk | Mitigation |
|---|---|
| Package rename breaks imports/resources | Mechanical rename + full build/test before feature work |
| Clerk native Google/GitHub setup | New application checklist; SHA-1 registration; custom GitHub credentials |
| JGit/agent on API 26 | Test matrix; fall back to minSdk 33 if needed |
| SQLCipher migration/size | Greenfield schema; measure APK; keep plain Room in tests |
| Exact alarms blocked by OEM | Permission guidance, inexact fallback, test on Redmi |
| Render free web sleeps | Bot worker owns the scheduler; web health check and keep-warm optional |
| Discord DM privacy | Fallback channel mention; never claim a bypass |
| Secrets in source archives | Rotate before deploy; gitleaks in CI |
| Two design languages | One token set; reminder accent only |
| Billing undefined | Keep plans metadata only; no payment code in v1 |

## 14. Approval

Design approved by the product owner on 2026-11-21 with the instruction to begin implementation. The five recommended defaults in §2 are adopted unless corrected during implementation.
