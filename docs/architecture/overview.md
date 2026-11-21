# Ei Void architecture

```mermaid
flowchart LR
  subgraph Phone["Android app — com.eivoid.app"]
    UI["Compose UI<br/>Home · Reminders · Code · Settings"]
    Reminders["core:reminders<br/>Room · AlarmManager · notifications"]
    Agent["core:agent<br/>tools · planner · router"]
    Git["core:git<br/>JGit clone/commit/push"]
    Secrets["core:security<br/>Keystore secrets"]
    DB[("Room / SQLCipher")]
    UI --> Reminders
    UI --> Agent
    Agent --> Git
    Agent --> DB
    Reminders --> DB
    Reminders --> Secrets
  end

  Clerk["Clerk<br/>Google · Discord · GitHub"]
  Web["services/web<br/>Express · EJS · Clerk · API"]
  Bot["services/bot<br/>Discord.js · scheduler"]
  DBPostgres[("PostgreSQL<br/>accounts · reminders · links · usage")]
  Discord["Discord gateway"]
  GitHub["GitHub"]
  Mail["Gmail API / Brevo / SendGrid / SMTP"]

  UI -- session JWT --> Clerk
  UI -- sync + account + token --> Web
  UI -- provider calls --> Agent
  Git -- HTTPS --> GitHub
  Web -- verify JWT / provider token --> Clerk
  Web -- SQL --> DBPostgres
  Bot -- SQL --> DBPostgres
  Bot -- gateway + DMs --> Discord
  Bot -- HTTPS --> Mail
  Web -- HTTPS --> Mail
```

## Runtime responsibilities

| Component | Owns | Never holds |
|---|---|---|
| Android app | Reminder truth, alarms, agent loop, local secrets | Discord bot token, Clerk secret, mail credentials |
| `services/web` | Public pages, Clerk sessions, app API, GitHub token exchange, APK updates | Repository contents, prompts, AI keys |
| `services/bot` | Discord gateway, slash commands, reminder scheduler, DM/fallback delivery | Clerk session tokens, AI keys |
| Postgres | Account, reminder, link, device and usage state | Secrets, prompts, repository contents |

## Reminder delivery flow

```mermaid
sequenceDiagram
  participant App as Android app
  participant API as services/web
  participant DB as Postgres
  participant Bot as services/bot
  participant Discord
  participant Mail
  App->>App: save reminder in Room + schedule alarm
  App->>API: POST /api/v1/reminders/sync (Clerk JWT)
  API->>DB: upsert account + replace reminders
  loop every 60s
    Bot->>DB: dueReminders(now)
    Bot->>DB: claim discord/email
    Bot->>Discord: DM or fallback mention
    Bot->>Mail: email to Clerk address
    Bot->>DB: mark delivered per channel
  end
  App-->>App: local exact alarm fires even offline
```

## Security boundaries

- The Android app talks to Clerk directly and to the web API with a Clerk JWT.
- The web service verifies every JWT before reading or writing user data.
- The bot maps a Discord snowflake to an account; it never trusts a Clerk ID
  supplied by a caller.
- The bot token is only in the bot worker; the Clerk secret and mail credentials
  are only in web/bot environments.
- Device keys are hashed at rest and only carry usage counters.
- No service logs message content, email addresses or tokens.
