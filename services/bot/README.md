# Ei Void Discord bot

The always-on Discord worker for Ei Void. It serves one server, answers
commands in ephemeral replies, and runs the reminder scheduler every 60 seconds
against the shared PostgreSQL database.

> **The bot token is a server credential.** It must never be committed, put in
> the Android app or `BuildConfig`, pasted into an issue, or shipped in an APK.
> If a token has ever been posted in a chat, commit or screenshot, reset it in
> the Discord Developer Portal before deploying. The Android app talks to Clerk;
> the bot token is not a Clerk OAuth secret and cannot be used for app sign-in.

## Commands

| Command | Behaviour |
|---|---|
| `/usage` | Shows the caller's plan and latest token snapshot, or the plan's advertised limit with a "not synced" note. |
| `/link` | Creates a single-use, 10-minute code and returns `PUBLIC_BASE_URL/link?code=...`. |
| `/reminders` | Lists the caller's next five unpaid reminders with title, amount and due date. |
| `/snooze local_id [days]` | Moves one of the caller's reminders forward by 1-30 days. |
| `/paid local_id` | Marks one of the caller's reminders as paid. |
| `/about` | One-line description of Ei Void and the public website link. |

Every reply is ephemeral. Commands are guild-scoped to `DISCORD_GUILD_ID`; a
DM or another server is refused, and the worker leaves any foreign guild it is
invited to. No privileged intents are requested: only `GatewayIntentBits.Guilds`.

## Architecture

The worker reuses the shared workspace packages:

- `@eivoid/config` validates the environment.
- `@eivoid/db` provides the PostgreSQL pool, migrations and `Store`.
- `@eivoid/discord-core` provides the guild lock, DM delivery with a channel
  fallback, and reminder message formatting.
- `@eivoid/mailer` sends reminder email through Gmail, Brevo, SendGrid or SMTP.
- `@eivoid/contracts` supplies `planLimit` and reminder types.

At startup the worker runs the idempotent migrations, starts the `/health`
listener on `BOT_HEALTH_PORT`, logs in with the bot token, and starts the
scheduler on `ClientReady`. `SIGTERM` and `SIGINT` stop the scheduler, close the
health server, destroy the gateway client and drain the pool.

The scheduler claims each delivery channel in the database with a five-minute
lease before sending, and marks a channel notified only after a successful
send. That makes restarts and multiple workers idempotent. Permanent email
failures (`invalid_email`, `email_not_configured`) are marked terminal so they
cannot occupy the batch forever; transient failures release the claim for a
later tick.

## Configure

```bash
cd services/bot
cp .env.example .env      # .env is gitignored
# fill DISCORD_BOT_TOKEN and DATABASE_URL
npm run register          # once, and after changing commands
npm run dev               # local development
```

Required: `DISCORD_BOT_TOKEN`, `DISCORD_GUILD_ID`, `DATABASE_URL`.
Optional: `DISCORD_APPLICATION_ID` (otherwise derived from the token),
`DISCORD_FALLBACK_CHANNEL_ID`, `BOT_HEALTH_PORT` (default `8081`),
`PUBLIC_BASE_URL` or `RENDER_EXTERNAL_URL` (for `/link`), and the provider
mail variables from the website's `.env.example`.

Without a public base URL, `/link` replies that linking is not configured
rather than inventing a URL. Without a mail provider, the scheduler delivers
Discord reminders only.

In the Discord Developer Portal:

- **Installation** -> enable **Guild Install** only, never User Install.
- **Bot** -> turn **Public Bot** off, so only the owner can generate an invite.
- Do not enable privileged intents.

Invite URL (the guild is preselected and the picker is hidden):

```
https://discord.com/api/oauth2/authorize?client_id=YOUR_APPLICATION_ID&permissions=18432&scope=bot%20applications.commands&guild_id=848841415940898827&disable_guild_select=true
```

## Health

`GET /health` returns:

```json
{
  "ok": true,
  "bot": true,
  "guilds": 1,
  "inLockedGuild": true,
  "database": true,
  "mail": true,
  "scheduler": true,
  "guildLock": "848841415940898827"
}
```

`database` is a live `SELECT 1` probe. The endpoint returns `503` when `ok` is
false. It never includes tokens, email addresses or message contents.

## Tests

```bash
npm run typecheck -w @eivoid/bot
npm test -w @eivoid/bot
```

The tests use a fake `Store`, Discord client and mailer; they do not connect to
Discord or PostgreSQL. They cover the config guard, command definitions,
account linking and ownership checks, scheduler duplicate prevention and
restart safety, health payloads, and message formatting.
