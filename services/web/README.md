# Ei Void website and API

The product site, dashboard and app API for Ei Void: reminders and an on-device
coding agent in one product. It is a Node 22 / Express service with
server-rendered EJS, a small design system and no client framework. Everything is
same-origin except the Clerk sign-in widget, which is loaded only when Clerk is
configured.

The site uses the **chalk-on-ink** identity: near-black ink, chalk text, an
italic display serif and a warm halo accent, with a paper light theme. Dark is
the default; the toggle remembers the choice in `localStorage`.

## Pages

| Path | What it is |
|---|---|
| `/` | Landing page for both pillars: reminders and the coding agent |
| `/reminders`, `/agent` | Product detail for each half of the app |
| `/features`, `/pricing` | Feature detail and preview plans |
| `/bot` | Discord companion, commands and linking model |
| `/download` | Signed-APK / build-from-source page |
| `/docs` … | Guides, API contract and security model |
| `/security`, `/privacy`, `/terms` | Threat model and legal pages |
| `/changelog` | Release notes |
| `/sign-in` | Clerk sign-in page (requires a Clerk publishable key) |
| `/dashboard` | Authenticated account, reminder summary, Discord, plan, usage and device keys |
| `/link?code=…` | Authenticated single-use Discord confirmation page |
| `/healthz` | Container probe; `{ ok, version }` |
| `/api/v1/health` | Boolean service health |

## Run locally

```bash
cd services/web
cp .env.example .env
# fill DATABASE_URL, CLERK_* and one mail provider
npm install
npm run dev               # http://localhost:3000
```

A Postgres database is required because the service uses the shared
`@eivoid/db` store and runs migrations at startup:

```bash
export DATABASE_URL=postgres://user:password@localhost:5432/eivoid
npm run dev
```

Without Clerk keys the marketing pages still render, but `/sign-in`, `/dashboard`,
`/link` and every authenticated API request fail closed. Without a mail provider
the test-email endpoint returns 503.

```bash
npm run typecheck
npm test
```

The production image is the repository-root `Dockerfile.web` and runs
`tsx services/web/src/server.ts` as the unprivileged `node` user:

```bash
docker build -f Dockerfile.web -t eivoid-web .
docker run --rm -p 3000:3000 -e DATABASE_URL=… -e SESSION_SECRET=… eivoid-web
```

## Environment

| Variable | Required | Purpose |
|---|---|---|
| `DATABASE_URL` | yes | Postgres connection string; migrations run at startup. |
| `SESSION_SECRET` | production | Signs the form and CSRF cookies. ≥ 32 chars. |
| `CLERK_SECRET_KEY` | for auth | Verifies bearer tokens and calls the Clerk Backend API. Server-only. |
| `CLERK_PUBLISHABLE_KEY` | for sign-in | Public key; encodes the frontend API host used by `/sign-in`. |
| `CLERK_ISSUER` | optional | Overrides the issuer derived from the publishable key. |
| `PUBLIC_BASE_URL` | optional | Canonical URL. Defaults to `RENDER_EXTERNAL_URL`, then the local port. |
| `BOT_API_TOKEN` | optional | Shared first-party service secret. |
| `DISCORD_APPLICATION_ID` | optional | Public snowflake for the invite button. |
| `DISCORD_GUILD_ID` | recommended | The single home server; the invite and membership check use it. |
| `DISCORD_BOT_TOKEN` | for test DMs | Used only by `POST /api/v1/test/discord`. Never sent to a browser. |
| `GITHUB_TOKEN` | for updates | Reads and streams GitHub Releases in `/api/v1/app/*`. |
| `GITHUB_REPO` | optional | `owner/repo` used for links and releases. |
| `MAIL_PROVIDER`, `MAIL_FROM`, provider credentials | for email | Test email and reminder delivery. |
| `DOWNLOAD_URL` | optional | HTTPS URL of the signed APK. |
| `RELEASES_URL` | optional | Releases page; defaults to the GitHub repo. |
| `FRONTEND_SIGN_IN_URL` | optional | Defaults to `${PUBLIC_BASE_URL}/sign-in`. |
| `CONTACT_EMAIL` | optional | Shown on the privacy page. |

Generate secrets with `openssl rand -hex 32`. Never put the Discord bot token or
the Clerk secret key in a browser, a log or this repository.

## API

Every app endpoint uses `Authorization: Bearer <Clerk session JWT>` unless noted.
The contract is documented in `docs/api/README.md` and implemented in
`src/routes/api.ts`.

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/reminders/sync` | Replace the caller's reminder snapshot |
| `GET /api/v1/account` | Email, display name, Discord link, plan and usage |
| `DELETE /api/v1/account` | Delete the caller's server data |
| `POST/DELETE /api/v1/discord/link` | Link or unlink a Discord account |
| `POST /api/v1/test/email` | Send to the caller's Clerk email only |
| `POST /api/v1/test/discord` | Send a test DM to the linked Discord user |
| `GET /api/v1/github/token` | Return the caller's GitHub provider token |
| `POST /api/v1/devices`, `DELETE /api/v1/devices/:id` | Issue/revoke a device key |
| `POST /api/v1/usage` | Device-key-authenticated usage snapshot |
| `GET /api/v1/app/latest`, `GET /api/v1/app/download` | APK update metadata and proxy |

## Deploy on Render

The repository-root `render.yaml` is the Blueprint:

- `eivoid-web` — this service, Docker (`Dockerfile.web`), health check `/healthz`.
- `eivoid-bot` — the Discord worker (`Dockerfile.bot`), starter plan.
- `eivoid-db` — Postgres 16, wired into both services as `DATABASE_URL`.

Set the `sync: false` values in the Render dashboard. `SESSION_SECRET`,
`BOT_API_TOKEN` and `DATABASE_URL` are generated/wired by the Blueprint.

The bot uses the shared database directly; it does not need a web API token for
reminder delivery. `BOT_API_TOKEN` remains available for first-party callers.

## Security

- A strict nonce-based CSP; no third-party scripts except the Clerk frontend.
- Helmet headers, HSTS in production, rate limits on forms and every mutation.
- Clerk JWTs are verified for signature, issuer and expiry; per-user ownership
  is enforced in `Store`.
- Email recipients come from the Clerk account, never from a request body.
- Device keys are stored as SHA-256 hashes; only counters and the plan are
  accepted.
- The website never receives repository content, prompts or AI provider keys.

## Layout

```text
services/web/
├── src/            Express app, config, Clerk auth, content, routes
├── views/          EJS layout, pages, docs and partials
├── public/         site.css, site.js, brand art, screenshots, manifest
├── content/        Markdown docs rendered by the docs routes
└── test/           route/config tests (vitest + supertest)
```
