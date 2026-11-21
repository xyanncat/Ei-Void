# Deploy Ei Void on Render

## 1. Prerequisites

- A Render account with the GitHub App connected to the `ei-void` repository.
- A Clerk application "Ei Void" with Google, Discord and GitHub connections.
- A Discord application "Ei Void" (reuse the Void application, rename it) with
  Guild Install only and a freshly reset bot token.
- A mail sender: Gmail API OAuth credentials, Brevo, SendGrid or SMTP.
- A GitHub token with read access to `xyanncat/Ei-Void` releases.
- A new Android keystore and its passwords stored in CI secrets or a vault.

## 2. Create the Blueprint

1. Render Dashboard → **New → Blueprint**.
2. Select the repository. Render reads `render.yaml`.
3. Create the `eivoid-web`, `eivoid-bot` and `eivoid-db` resources.

## 3. Fill secrets

### eivoid-web

| Variable | Value |
|---|---|
| `CLERK_PUBLISHABLE_KEY` | Ei Void publishable key |
| `CLERK_SECRET_KEY` | Ei Void secret key |
| `CLERK_ISSUER` | Clerk frontend API URL |
| `DISCORD_APPLICATION_ID` | Ei Void Discord application ID |
| `PUBLIC_BASE_URL` | Custom domain, or leave empty to use `RENDER_EXTERNAL_URL` |
| `GITHUB_TOKEN` | Read-only release token |
| `CONTACT_EMAIL` | Public contact address |
| `MAIL_*` | One configured mail provider |

### eivoid-bot

| Variable | Value |
|---|---|
| `DISCORD_BOT_TOKEN` | Fresh Ei Void bot token |
| `DISCORD_APPLICATION_ID` | Ei Void Discord application ID |
| `DISCORD_FALLBACK_CHANNEL_ID` | Optional notification channel |
| `MAIL_*` | Same provider configuration as web |

`SESSION_SECRET`, `BOT_API_TOKEN` and `DATABASE_URL` are generated/wired by
the Blueprint.

## 4. Verify

- `GET https://<web-host>/healthz` returns `{ ok: true, store: "postgres", version }`.
- `GET https://<web-host>/api/v1/health` returns configuration booleans; no secret values.
- The bot logs "Ei Void Discord bot is online" and reports the home guild.
- `GET https://<bot-host>/health` returns `{ ok: true, database: true, inLockedGuild: true }` if the worker health port is exposed through a private service.
- Run `/about` in the home guild; it replies ephemerally.
- Run `/link`; the returned URL opens the Ei Void site and requires Clerk sign-in.

## 5. Register commands

From a trusted machine:

```bash
cd services/bot
DISCORD_BOT_TOKEN=... DISCORD_GUILD_ID=848841415940898827 npm run register
```

Commands are guild-scoped; never register globally.

## 6. First account smoke test

1. Sign in to the app with Clerk Google.
2. Add a reminder due tomorrow.
3. Confirm the local notification fires.
4. Open Settings → sync; then `/reminders` in Discord.
5. Link GitHub, clone a small repository, run a prompt, review the diff, push.
6. Close Discord DMs and confirm the fallback channel mention arrives.

## 7. Rollback

See `docs/runbooks/rollback.md`.
