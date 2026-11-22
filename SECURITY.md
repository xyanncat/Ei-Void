# Security Policy

Ei Void is a developer preview. Security fixes land on `main` and in the next
tagged release.

## Supported versions

| Version | Supported |
| ------- | --------- |
| 0.2.x   | Yes       |
| < 0.2   | No        |

## Reporting a vulnerability

Report privately through GitHub's **Security → Advisories → Report a
vulnerability** flow on this repository. Please do not open a public issue for
a suspected vulnerability.

Include the version or commit, the affected component (Android app, web
service, bot, shared package), reproduction steps, and the impact you expect.
We aim to acknowledge reports within three business days and will coordinate a
fix and disclosure window with you.

Never include live tokens, passwords, keystores or personal data in a report.
Redact them and describe where they were found instead.

## Scope

In scope:

- The Android app in `android/` (Clerk sign-in, provider keys, repository
  workspaces, reminder alarms and notifications, Discord linking).
- The web service and API in `services/web/` (Clerk JWT verification, Discord
  OAuth verification, device keys, reminder sync).
- The Discord bot in `services/bot/` (guild lock, link codes, reminder
  scheduling, DM and channel delivery).
- Shared packages in `packages/`, the Docker images, and the GitHub Actions
  workflows.

Out of scope: findings that require a rooted device, physical access to an
unlocked phone, or a compromised Clerk, Discord, GitHub, Render or AI-provider
account. Denial-of-service reports against the free-tier hosting are also out
of scope.

## Secrets

The repository must never contain provider keys, Discord bot tokens, Clerk
secret keys, database URLs or signing keystores. If one is exposed, rotate it
before removing it from history; see
[`docs/security/secret-rotation.md`](docs/security/secret-rotation.md).
