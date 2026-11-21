<div align="center">

# Ei Void

**An OLED AI assistant that also keeps your bills and your code in reach.**

</div>

Ei Void is one Android app built around a quiet, black-canvas assistant:

- **Assistant** — an AI-first home with a slide-out drawer, prompt chips, a floating ask bar and a conversation view. It uses the user's own AI provider keys.
- **Library** — local-first bill, renewal and one-time payment reminders with exact Android alarms, Mark Paid / Snooze, optional email and Discord delivery.
- **Projects** — a native Android AI coding agent that clones a GitHub repository, edits it through guarded tools, and commits/pushes the result.
- **Customize** — one place for Ei Void identity, model tiers, provider keys, and the Ei Snore Discord notification connection.

One app, one Clerk account, one Discord bot, one website, one Postgres database.

## Repository

```text
android/                 Kotlin + Compose application (com.eivoid.app)
  app/                   shell, Hilt, drawer navigation, manifest
  core/                  common, security, database, git, workspace, ai, agent, reminders
  feature/               assistant, auth, repos, chat, workspace, reminders, settings
  designsystem/          OLED tokens and the Ei component kit
services/
  web/                   Express + EJS site, dashboard, app API
  bot/                   Discord.js worker and reminder scheduler
packages/
  contracts/             zod schemas and API types
  config/                environment validation
  db/                    PostgreSQL migrations and Store
  mailer/                Gmail API, Brevo, SendGrid, SMTP
  discord-core/          guild guard, OAuth verification, DM/fallback
docs/
  superpowers/           design spec and implementation plan
  security/              threat model and secret rotation
  runbooks/              deploy and rollback
```

## Design

The UI is deliberately one system, not a restyled template:

- Pure black OLED canvas, charcoal surfaces, 1px hairline borders, white type,
  muted gray secondary text and a single electric-blue action accent.
- One shared component kit (`designsystem/component/EiComponents.kt`):
  `EiScreen`, `EiTopBar`, `EiCard`, `EiListRow`, `EiPill`, `EiSegmented`,
  `EiTextField`, `EiPrimaryButton`, `EiNotice`, `EiEmptyState`, `EiStat`.
- A slide-out drawer replaces the old bottom navigation. Chat, Images, Library,
  Customize, Projects, Remote and Explore are drawer destinations.
- Screens are designed from scratch: assistant home, library, reminder editor,
  projects, customize, usage, remote, explore, login, and the files/chat
  workspace shell.

## Build the app

Requirements: JDK 21, Android SDK with platform 37, Android SDK build-tools 37.

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd android
.\gradlew.bat :app:assembleDebug
```

The APK lands in `android/app/build/outputs/apk/debug/app-debug.apk`.

### Configuration

Create `android/local.properties` (gitignored):

```properties
sdk.dir=C\:\\Users\\you\\Android\\Sdk
clerk.publishableKey=pk_test_...
ei-void.apiUrl=https://your-web-service.onrender.com
clerk.accountPortalUrl=https://your-clerk-instance.accounts.dev/user
```

Release signing comes from `keystore.properties` or the `RELEASE_*` environment variables; never commit a keystore.

## Run the services

Requirements: Node 22+, PostgreSQL 16.

```bash
cp .env.example .env
npm install
npm run dev:web
npm run dev:bot
```

Both services share `DATABASE_URL`; the web service runs the idempotent migrations at boot if configured, and the bot worker runs the reminder scheduler every 60 seconds.

## Deploy

`render.yaml` is a Render Blueprint with:

- `eivoid-web` — Docker web service, health check `/healthz`
- `eivoid-bot` — Docker worker, always-on scheduler
- `eivoid-db` — PostgreSQL 16

Set the `sync: false` values in the Render dashboard. The only mandatory secret for the bot is `DISCORD_BOT_TOKEN`; the web service needs the Clerk keys, database URL and mail provider credentials.

## Security

- No secret from the source archives is committed. Rotate every credential that was present there before deploying; see `docs/security/secret-rotation.md`.
- The app stores tokens and keys in Keystore-backed storage; release builds are TLS-only with certificate pinning.
- The web service verifies Clerk JWTs; the bot never receives a Clerk session.
- The bot is locked to one Discord guild and never uses privileged intents.
- Repository content, prompts and AI provider keys never reach the server.

## Verify

```bash
# Node: 202 tests across the five packages, web and bot
npm run typecheck
npm test

# Android: full unit suite, lint, and the debug APK
cd android
./gradlew testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Status

- [x] New repo skeleton and Android package rebrand
- [x] Android debug APK builds; full unit suite and lint green
- [x] OLED design system and every top-level screen rebuilt from scratch
- [x] Assistant home, drawer, library, editor, projects, customize, usage, login
- [x] Reminder domain, alarms, notifications and the redesigned library
- [x] Shared backend packages (99 tests)
- [x] Unified Discord bot (62 tests)
- [x] Unified website, dashboard, link flow and app API (41 tests)
- [x] Emulator walkthrough: add → alarm → notification → Mark paid; new UI screens
- [x] Redmi 10 Prime: final redesigned APK installed, security scan passed, launched
- [ ] App-to-dashboard usage sync (API and device keys are ready)
- [ ] Real Clerk/Discord/Postgres credentials and a Render deployment
- [ ] Discord OAuth link in Customize (the section and API are ready; app hop is a stub)
- [ ] Signed release APK

Note: MIUI 14 still rejects `adb install` with `INSTALL_FAILED_USER_RESTRICTED` on
this Redmi even with the two developer toggles on. The working install path is
the phone's Files/package installer; the final build passed Xiaomi's security
scan. See `docs/qa/README.md`.

See `docs/superpowers/plans/2026-11-21-ei-void-unification.md` for the full task list and `docs/qa/README.md` for the device checklist.
