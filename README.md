<div align="center">

# Ei Void

**Reminders and an on-device coding agent, in one Android app.**

</div>

Ei Void merges two products that were built separately:

- **Reminders** — local-first bill, renewal and one-time payment tracking with exact Android alarms, Mark Paid / Snooze actions, optional email and Discord delivery.
- **Agent** — a native Android AI coding agent that clones a GitHub repository, edits it through guarded tools, and commits/pushes the result.

One app, one Clerk account, one Discord bot, one website, one Postgres database.

## Repository

```text
android/                 Kotlin + Compose application (com.eivoid.app)
  app/                   shell, Hilt, navigation, manifest
  core/                  common, security, database, git, workspace, ai, agent, reminders
  feature/               auth, repos, chat, workspace, reminders, settings
  designsystem/          theme and components
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

# Android: unit tests + debug APK
cd android
./gradlew :app:assembleDebug :core:reminders:testDebugUnitTest :feature:reminders:testDebugUnitTest
```

## Status

- [x] New repo skeleton and Android package rebrand
- [x] Android debug APK builds
- [x] Reminder domain, alarms, notifications and UI (39 core + 20 feature tests)
- [x] Shared backend packages (99 tests)
- [x] Unified Discord bot (62 tests)
- [x] Unified website, dashboard, link flow and app API (41 tests)
- [x] Emulator walkthrough: add → alarm → notification → Mark paid → Snooze
- [x] Redmi 10 Prime walkthrough: installed, added a reminder, exact alarm fired, notification posted
- [ ] Usage sync from the app to the dashboard/bot (API and device keys are ready)
- [ ] Real Clerk/Discord/Postgres credentials and a Render deployment
- [ ] Signed release APK

Note: MIUI 14 still rejects `adb install` with `INSTALL_FAILED_USER_RESTRICTED` on
this Redmi even with the two developer toggles on. The working install path was
the phone's Chrome/package installer over the LAN; see `docs/qa/README.md`.

See `docs/superpowers/plans/2026-11-21-ei-void-unification.md` for the full task list and `docs/qa/README.md` for the device checklist.
