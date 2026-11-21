export interface Feature {
  index: string;
  title: string;
  body: string;
}

export interface Tier {
  name: string;
  label: string;
  body: string;
  multiplier: string;
}

export interface Plan {
  name: string;
  price: string;
  cadence: string;
  tagline: string;
  features: string[];
  featured?: boolean;
}

export interface Faq {
  q: string;
  a: string;
}

export interface Step {
  number: string;
  title: string;
  body: string;
}

export interface Doc {
  slug: string;
  title: string;
  description: string;
  body: string;
}

export interface ChangelogEntry {
  version: string;
  date: string;
  title: string;
  items: string[];
}

export const features: Feature[] = [
  {
    index: '01',
    title: 'Real git, not a mock',
    body:
      'JGit runs on the phone. Clone, status, diff, branches, commit and push — the actual repository, with actual history.',
  },
  {
    index: '02',
    title: 'Guarded tools',
    body:
      'The agent reads, searches, writes and edits through a typed tool registry. Destructive work asks for your tap first.',
  },
  {
    index: '03',
    title: 'A tier, not a model menu',
    body:
      'You pick Red Dwarfs, White Dwarfs or Neutron Stars. The router chooses a model; a failed model is skipped inside the same tier.',
  },
  {
    index: '04',
    title: 'Your phone is the machine',
    body:
      'No laptop, no remote IDE, no server between you and the repo. A workspace on disk, Room for history, Keystore for keys.',
  },
  {
    index: '05',
    title: 'One identity',
    body:
      'Clerk is the single door: Discord, Google, email and GitHub. The GitHub token is exchanged server-side, never typed into a file.',
  },
  {
    index: '06',
    title: 'A companion in Discord',
    body:
      'Link your Discord account from the bot, then ask for usage or pop open your dashboard without opening the app.',
  },
];

export const tiers: Tier[] = [
  {
    name: 'Red Dwarfs',
    label: 'deep work',
    body: 'Scaffolding, migrations and broad changes. The strongest reasoning, the longest leash.',
    multiplier: '1×',
  },
  {
    name: 'White Dwarfs',
    label: 'steady hands',
    body: 'Reviews, refactors and multi-file edits. Balanced cost, deliberate tool use.',
    multiplier: '5×',
  },
  {
    name: 'Neutron Stars',
    label: 'quick orbit',
    body: 'Explanations and tiny edits. Small, fast models for the question between sips of coffee.',
    multiplier: '10×',
  },
];

export const plans: Plan[] = [
  {
    name: 'Free',
    price: '₹0',
    cadence: 'forever',
    tagline: 'A real workspace in your pocket.',
    features: [
      '7 projects',
      '30M tokens / month',
      'Neutron Stars',
      'Clerk sign-in',
      'Discord linking',
    ],
  },
  {
    name: 'Pro',
    price: '₹200',
    cadence: 'per month',
    tagline: 'For the after-hours build.',
    features: [
      '15 projects',
      '80M tokens / month',
      'Neutron + White Dwarfs',
      'Usage dashboard',
      'Priority support',
    ],
    featured: true,
  },
  {
    name: 'Max',
    price: '₹500',
    cadence: 'per month',
    tagline: 'Let the cat stay up all night.',
    features: [
      '30 projects',
      '200M tokens / month',
      'Every tier',
      'Usage dashboard',
      'Early features',
    ],
  },
];

export const faqs: Faq[] = [
  {
    q: 'Is Ei Void a web IDE?',
    a: 'No. It is a native Android app with a git workspace on the phone. There is no browser tab holding your repository.',
  },
  {
    q: 'Where do my keys live?',
    a: 'Provider keys and the GitHub token are encrypted with the Android Keystore. They are not in a plain file, and the Discord bot never receives them.',
  },
  {
    q: 'Does my code leave the phone?',
    a: 'Only the context the agent sends to your chosen AI provider, and the git operations you approve. Ei Void has no analytics, no telemetry and no intermediary server. The bot receives reminder and usage counters, not repository content.',
  },
  {
    q: 'Why Clerk?',
    a: 'One account for Discord, Google, email and GitHub. Clerk keeps the session and the GitHub connection; the app exchanges the Clerk session for the GitHub token it needs to clone and push.',
  },
  {
    q: 'Is the Discord bot verified?',
    a: 'The bot is built and deployable, but Discord verification is a Developer Portal review, not a code flag. This site does not claim the badge until Discord grants it.',
  },
  {
    q: 'Can the bot DM me if my DMs are closed?',
    a: 'No. Discord does not let an app bypass your DM privacy, and OAuth scopes authorize an install rather than a closed inbox. Ei Void never needs a DM: every command reply is ephemeral inside its home server, so closing DMs does not hide /link or /usage.',
  },
  {
    q: 'Can I use my own models?',
    a: 'The tier router picks providers and models; provider keys are yours. A custom model picker is on the roadmap, behind the same guarded tool layer.',
  },
  {
    q: 'Is this production ready?',
    a: 'It is a developer preview. The Android app builds and runs; the GitHub bridge and the usage sync are the remaining hosted pieces. Treat the plans as a preview, not a bill.',
  },
  {
    q: 'What phones are supported?',
    a: 'Android 13 (API 33) and newer, arm64. The current preview is tested on a Redmi 21061119BI running Android 13.',
  },
];

export const steps: Step[] = [
  {
    number: '01',
    title: 'Sign in once',
    body: 'Clerk opens the door with Discord, Google, email or GitHub. Connect GitHub so Ei Void can fetch a token.',
  },
  {
    number: '02',
    title: 'Clone a repo',
    body: 'Pick a repository and a branch. The workspace is created on the phone, with the files you already know.',
  },
  {
    number: '03',
    title: 'Ask for a change',
    body: 'The agent reads, searches and edits through guarded tools. You see the tool calls, not a wall of magic.',
  },
  {
    number: '04',
    title: 'Review and push',
    body: 'Inspect the diff, run what you can, then approve the commit. Push confirmation is a deliberate second tap.',
  },
];

export const changelog: ChangelogEntry[] = [
  {
    version: '0.2.0',
    date: '21 Nov 2026',
    title: 'Cat in the void',
    items: [
      'The cat-and-halo artwork is the app mark, adaptive icon, splash and chat empty state.',
      'Chalk-on-ink design system with a paper light scheme and accessible tier markers.',
      'Clerk-only sign-in with Discord, Google, email and GitHub.',
      'Security pass: workspace boundary, push confirmation and sign-out cleanup.',
      'Discord bot scaffold with a server-side token guard; product site with docs, dashboard and the companion API.',
      'The companion is locked to its home server: guild-scoped commands, ephemeral replies and no DM dependency.',
      'Known: the GitHub bridge and usage sync are not deployed yet.',
    ],
  },
  {
    version: '0.1.0',
    date: '10 Nov 2026',
    title: 'First light',
    items: [
      'Native Android agent shell with Room, Keystore, Ktor and JGit.',
      'Tool registry, tier router and usage ledger.',
      'Repo list, workspace, chat and settings.',
    ],
  },
];

const doc = (slug: string, title: string, description: string, body: string): Doc => ({
  slug,
  title,
  description,
  body: body.trim(),
});

export const docs: Doc[] = [
  doc(
    'getting-started',
    'Getting started',
    'Build Ei Void, sign in, and open a repository without a laptop.',
    `
## What you need

- JDK 21 and the Android SDK with platform 37.
- An Android 13+ phone with arm64.
- A Clerk publishable key. The Android app reads it from \`local.properties\`, not from source.
- A GitHub account to connect through Clerk.

## Build the app

\`\`\`bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
\`\`\`

The release build is R8-minified and signed with the project key. The current
preview is on the \`feat/clerk-auth\` branch.

## Configure Clerk

Add the public key and the account portal URL to \`local.properties\`:

\`\`\`properties
clerk.publishableKey=pk_test_...
clerk.accountPortalUrl=https://your-instance.accounts.dev/user
\`\`\`

The secret key is never in the app. It belongs in the Cloudflare Worker that
turns a Clerk session into a GitHub token.

## First run

Open Ei Void, choose **Continue with Clerk**, and finish sign-in. If you signed
up with something other than GitHub, open your Clerk account and connect GitHub
once. Ei Void needs that token for clone and push; it stores it in the Android
Keystore.

## The honest blocker

The GitHub bridge is a separate Worker in \`server/\`. Until it is deployed and
\`void.backendUrl\` is set, sign-in works but clone and push cannot complete.
That is the first hosted piece on the roadmap, not a hidden setting.
`,
  ),
  doc(
    'github-bridge',
    'GitHub bridge',
    'The small Worker that exchanges a Clerk session for a GitHub token.',
    `
The app never sees a Clerk secret and never asks for a GitHub token by hand.
It sends its Clerk session to the bridge; the bridge verifies it and asks the
Clerk Backend API for the user's linked GitHub OAuth token.

## Deploy

\`\`\`bash
cd server
npm install
npx wrangler login
npx wrangler secret put CLERK_SECRET_KEY
npx wrangler deploy
\`\`\`

Add the deployed URL to \`local.properties\`:

\`\`\`properties
void.backendUrl=https://void-auth-gateway.example.workers.dev
\`\`\`

## Why it exists

Putting the Clerk secret in the APK would hand every user the keys to the
instance. The Worker keeps the secret at the edge and returns only the token the
authenticated user already connected.

## The contract

\`GET /github-token\` with \`Authorization: Bearer <Clerk session JWT>\` returns
either a short-lived GitHub OAuth token or a typed error the app can explain.
A 404 means GitHub is not connected yet; the app sends the user to the account
portal.
`,
  ),
  doc(
    'discord-bot',
    'Discord companion',
    'Link Discord, check usage, and keep the bot token server-side.',
    `
The bot is a long-running Node service in \`discord-bot/\`. It has three commands:

| Command | What it does |
|---|---|
| \`/link\` | Returns a one-time URL where a Discord user confirms the link. |
| \`/usage\` | Reads the latest usage snapshot for the linked account. |
| \`/about\` | One line about Ei Void and the account portal. |

## One server, no DMs

The bot is locked to a single guild, \`848841415940898827\`. Commands are
registered there only, interactions from another guild or a DM are refused, and
a foreign invite is left automatically. The public invite preselects the server
and hides the picker.

The bot never sends DMs. Discord does not allow an app to bypass a user's DM
privacy settings; the \`bot\` and \`applications.commands\` scopes authorize an
install, not a closed inbox. Every reply is ephemeral in the home server, which
already reaches the user who ran the command whether or not DMs are open.

In the Developer Portal, set **Installation → Guild Install** only and turn
**Public Bot** off. Together with the locked invite and the runtime guild check,
that leaves no install path to another server.

## Token rules

The bot token is a server credential. It must never be committed, put in the
Android app or exposed to browser JavaScript. The bot derives its application
id from the token's first segment, so a pasted client id from another app cannot
break command registration.

## Render

The repository includes a Render blueprint with a web service, a Postgres
database and the bot as a background worker. Set \`DISCORD_BOT_TOKEN\` as a
secret; the worker gets the website URL and service token from the web service.

## Usage sync

The bot can only show usage that the website has. The app's usage ledger is
still local, so until an app build syncs a snapshot the bot says: linked, but
not synced yet. The dashboard issues a one-time device key for that sync; the
API is documented under **API**.
`,
  ),
  doc(
    'self-hosting',
    'Self-hosting',
    'Run the website and the bot on Render, or in any container host.',
    `
## Render blueprint

\`render.yaml\` at the repository root defines:

- \`void-web\` — the Node/Express site and API, built from \`website/Dockerfile\`.
- \`void-bot\` — the Discord worker, built from \`discord-bot/Dockerfile\`.
- \`void-db\` — a Postgres database connected to the web service.

Create a Blueprint from the repository in Render, then set the values the
blueprint cannot know:

| Variable | Service | Secret |
|---|---|---|
| \`DISCORD_BOT_TOKEN\` | \`void-bot\` | yes |
| \`DISCORD_GUILD_ID\` | \`void-web\`, \`void-bot\` | no — the blueprint sets the home server |
| \`PUBLIC_BASE_URL\` | \`void-web\` | no |
| \`DOWNLOAD_URL\` | \`void-web\` | no |

\`BOT_API_TOKEN\`, \`SESSION_SECRET\` and the database URL are generated or wired
by the blueprint. The bot's \`VOID_API_TOKEN\` is copied from the website's
\`BOT_API_TOKEN\`; it is not the Discord token.

## Local preview

\`\`\`bash
cd website
npm install
cp .env.example .env
npm run dev
\`\`\`

Without \`DATABASE_URL\` the site uses an in-memory store. Links and waitlist
entries vanish on restart; production should always have Postgres.

## Docker

Both services have production Dockerfiles. Run them with the environment
variables set; never bake an \`.env\` file into an image.
`,
  ),
  doc(
    'api',
    'API',
    'Clerk-authenticated endpoints for the app, device usage sync and releases.',
    `
All paths are relative to the website's base URL and live under \`/api/v1\`.
Clerk bearer tokens come from the Android app; device keys come from the
dashboard. Secrets never appear in a response or a log.

## Clerk bearer

\`\`\`http
Authorization: Bearer <Clerk session JWT>
\`\`\`

The service verifies the token with the Clerk secret key. A missing or invalid
token is a 401 with \`invalid_clerk_token\`.

| Method | Path | Purpose |
|---|---|---|
| POST | \`/reminders/sync\` | Upsert profile and replace the reminder snapshot |
| GET | \`/account\` | Account, latest usage and plan limit |
| DELETE | \`/account\` | Delete the account and cascaded rows |
| POST | \`/discord/link\` | Verify a Discord OAuth token and link the id |
| DELETE | \`/discord/link\` | Unlink Discord |
| POST | \`/test/email\` | Send a test email to the synced account address |
| POST | \`/test/discord\` | Send a test DM through the bot REST API |
| GET | \`/github/token\` | Exchange the Clerk user for the GitHub OAuth token |
| POST | \`/devices\` | Issue a one-time device key |
| DELETE | \`/devices/:id\` | Revoke one of your device keys |

### Sync reminders

\`\`\`http
POST /api/v1/reminders/sync
Content-Type: application/json

{
  "profile": {
    "timezone": "Asia/Kolkata",
    "notifyHour": 9,
    "notifyMinute": 0,
    "name": "Cat",
    "email": "cat@example.com",
    "emailEnabled": true
  },
  "reminders": [
    {
      "localId": 1,
      "title": "Electricity bill",
      "amountMinor": 142000,
      "currency": "INR",
      "dueAt": 1790000000000,
      "notifyAt": 1789913600000,
      "repeatRule": "MONTHLY",
      "category": "BILL"
    }
  ]
}
\`\`\`

The response is \`{ "ok": true, "synced": 1, "discordLinked": false }\`. The
snapshot replaces the previous one; a reminder absent from the list is deleted.

## Device usage sync

The dashboard issues a key beginning \`eivoid_\` and shows it once. It is stored
as a SHA-256 digest.

\`\`\`http
POST /api/v1/usage
Authorization: Bearer eivoid_...
Content-Type: application/json

{
  "plan": "Free",
  "tokensUsed": 1200000,
  "tokensLimit": 30000000,
  "periodEnd": "2026-12-01"
}
\`\`\`

A valid snapshot returns \`202 { "ok": true }\`. Only counters and the plan are
stored. Do not upload repository contents, prompts, chat messages or keys.

## GitHub token

\`\`\`http
GET /api/v1/github/token
Authorization: Bearer <Clerk session JWT>
\`\`\`

Returns \`{ "token": "...", "scopes": [...], "expiresAt": 1790000000 }\`. A 404
means GitHub is not connected; a 409 means Clerk is refreshing the token and
carries \`Retry-After: 2\`; a 502 is an upstream failure with no credential
detail.

## Releases and health

\`GET /app/latest\` returns the current GitHub release metadata and an
\`apkUrl\`. \`GET /app/download\` streams the APK. Both are mirrored under
\`/api/v1/app\` and cached for 60 seconds.

\`GET /api/v1/health\` returns booleans only: \`{ "ok": true, "database": true,
"clerk": true, "mail": true, "discord": true, "updates": true }\`.
\`GET /healthz\` is the small container probe.
`,
  ),
  doc(
    'security',
    'Security model',
    'What lives where, and what the website never receives.',
    `
## The phone

- Provider keys and the GitHub token are encrypted with the Android Keystore.
- The workspace is scoped to the app's private directory; git tools refuse
  paths outside it.
- Deleting files and pushing need an explicit confirmation.
- Sign-out clears the Clerk session, the secret store, the workspace, chats and
  the usage ledger.

## The website

- Session cookies are signed, HttpOnly, SameSite=Lax and Secure in production.
- State-changing browser forms use a double-submit CSRF token.
- The bot API uses a separate service token and constant-time comparison.
- The database stores Discord ids, usernames, plans, usage counters and
  waitlist emails. It never stores repository content or provider keys.
- The bot token is not a website credential and is never rendered.

## The bot

The token lives in the worker's environment. Link codes are 192 bits of
randomness, single-use, and expire after ten minutes. Bot replies are
ephemeral. The bot requests only the Guilds intent.

## Reporting

Use GitHub Security Advisories on the repository. Please do not open a public
issue for a vulnerability. The preview has no bug bounty.
`,
  ),
  doc(
    'android',
    'The Android app',
    'Modules, storage and the shape of the agent.',
    `
Ei Void is a single-activity Compose app with Hilt. The modules are small on
purpose:

- \`core:agent\` — the loop, the tool registry and guarded file/git tools.
- \`core:ai\` — providers, tiers and token accounting.
- \`core:git\`, \`core:workspace\`, \`core:security\`, \`core:database\`.
- \`feature:auth\`, \`feature:repos\`, \`feature:chat\`, \`feature:workspace\`,
  \`feature:settings\`.
- \`designsystem\` — the chalk-on-ink tokens and components.

Chat and workspace are a split screen on a phone; the floating navigation is
intentionally small. The app is dark-first and has a paper light scheme.

## Storage

Room holds repos, chats and the usage ledger. DataStore holds non-secret
settings. The Keystore-backed store holds the keys that matter. The workspace
is ordinary files under the app's private directory, which keeps JGit simple
and the boundary auditable.
`,
  ),
];
