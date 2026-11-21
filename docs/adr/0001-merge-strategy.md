# ADR 0001 — Merge Ei Snore into Void as one product

- **Status:** Accepted
- **Date:** 2026-11-21
- **Deciders:** Ei Void product owner

## Context

Two working Android products exist:

- **Ei Snore** — reminders, exact alarms, Clerk identity, Node/Express backend,
  Discord.js DM delivery, email delivery, a small website.
- **Void** — a modular Kotlin/Compose coding agent, Clerk identity plus a GitHub
  token gateway, JGit, a Discord bot, and an Express/EJS website.

The product owner wants one app, one bot, one website and a new repository.

## Decision

1. Use Void's modular Android architecture as the base and port Ei Snore's
   reminder domain into new `core:reminders` and `feature:reminders` modules.
2. Use one Clerk application "Ei Void" for both pillars.
3. Use one Discord application (the existing Void application, renamed) for
   linking, usage and reminders.
4. Use one Postgres database with unified tables.
5. Use two Node processes: `services/web` (site + API) and `services/bot`
   (gateway + scheduler). They share `packages/*`.
6. Ship a greenfield applicationId `com.eivoid.app`; do not migrate the old
   installs or hosted data.

## Consequences

- The Android package rename is mechanical but touches every module.
- The old Ei Snore and Void deployments remain separate until Ei Void passes
  device QA.
- The reminder UI must be rewritten in Void's design system rather than copied
  wholesale.
- The Cloudflare auth Worker is retired in favor of `services/web`.
- The bot's scheduler can use the database directly, removing the web service
  from the delivery path.
- A single Clerk application requires a new native registration and GitHub
  custom OAuth credentials.
