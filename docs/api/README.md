# Ei Void API

Base URL: `https://<web-host>/api/v1`

## Authentication

| Caller | Credential |
|---|---|
| Android app | `Authorization: Bearer <Clerk session JWT>` |
| Device usage upload | `Authorization: Bearer eivoid_<device key>` |
| Bot service (optional) | `Authorization: Bearer <BOT_API_TOKEN>` |

Clerk JWTs are verified by signature, issuer and expiry. The subject must start
with `user_`.

## Endpoints

### `POST /reminders/sync`

```json
{
  "profile": {
    "timezone": "Asia/Riyadh",
    "notifyHour": 9,
    "notifyMinute": 0,
    "name": "Jane Doe",
    "email": "jane@example.com",
    "emailEnabled": true
  },
  "reminders": [
    {
      "localId": 1,
      "title": "Netflix renewal",
      "merchant": "Netflix",
      "bank": "",
      "amountMinor": 64900,
      "currency": "INR",
      "dueAt": 1760000000000,
      "notifyAt": 1759657200000,
      "repeatRule": "MONTHLY",
      "category": "SUBSCRIPTION",
      "notes": "",
      "leadDays": 4,
      "isPaid": false,
      "isActive": true,
      "paidAt": null
    }
  ]
}
```

Response: `{ "ok": true, "synced": 1, "discordLinked": false }`.

The server replaces only the authenticated user's reminders. It preserves the
Discord/email delivery markers when `dueAt` is unchanged.

### `GET /account`

```json
{
  "email": "jane@example.com",
  "displayName": "Jane Doe",
  "discord": { "id": "123456789012345678", "username": "jane" },
  "plan": "Free",
  "usage": {
    "plan": "Free",
    "tokensUsed": 0,
    "tokensLimit": 30000000,
    "periodEnd": null,
    "createdAt": "2026-11-21T09:00:00.000Z"
  }
}
```

### `DELETE /account`

Deletes the account and all rows that cascade from it.

### `POST /discord/link`

```json
{ "accessToken": "discord-oauth-access-token" }
```

Verifies the token with Discord, requires membership in `DISCORD_GUILD_ID`, and
stores the Discord ID. The token is not persisted.

### `DELETE /discord/link`

Clears the link.

### `POST /test/email`

Sends a test message to the authenticated Clerk account's primary email. The
request body is ignored; no caller-supplied recipient is accepted.

### `POST /test/discord`

Sends a test DM to the linked Discord user. Returns `409 discord_not_linked`
when absent.

### `GET /github/token`

Returns `{ token, scopes, expiresAt }` for the signed-in Clerk user's GitHub
provider token. Requires the GitHub social connection with custom credentials.

### `POST /devices`

Issues a device key. The plaintext key is returned once, prefixed `eivoid_`.

### `DELETE /devices/:id`

Revokes a device key owned by the caller.

### `POST /usage`

Device-key authenticated. Accepts `{ plan, tokensUsed, tokensLimit, periodEnd }`.

### `GET /app/latest`

Returns `{ version, apkUrl, notes, publishedAt }` from the latest GitHub
Release.

### `GET /app/download`

Streams the APK asset.

## Error shape

```json
{ "error": "invalid_clerk_token" }
```

| Status | Meaning |
|---|---|
| 400 | Invalid payload |
| 401 | Missing/invalid credential |
| 403 | Valid credential without permission |
| 404 | Not found or not linked |
| 409 | State conflict (e.g. Discord not linked) |
| 429 | Rate limited; honor `Retry-After` |
| 503 | A required server dependency is not configured |
