# Rollback Ei Void

## Web / bot

1. Render Dashboard → the service → **Deploys**.
2. Select the last known-good deploy → **Redeploy**.
3. Do the same for the other service if the release changed both.
4. Check `/healthz` and the bot `/health`.

Migrations are additive. A rollback of the application code does not need a
database rollback unless a migration is known to be destructive (none are in
v1).

## Android

1. Keep the previous signed APK attached to its GitHub Release.
2. Repoint `DOWNLOAD_URL` or the `/api/v1/app/latest` result to the previous
   release if the new build is broken.
3. If the APK is already installed, users can sideload the previous release;
   the package name and signing key must match.

## Credentials

If the incident involved a leaked token:

1. Rotate the credential first.
2. Redeploy with the new value.
3. Do not restore a deployment that contains the old value.
4. Add the incident and rotation to `docs/security/secret-rotation.md`.

## Database

- The bot scheduler claims deliveries before sending. If it is stopped mid-send,
  a claim may remain until its retry window expires; the next run will retry.
- To pause delivery without stopping the bot, unset `DISCORD_BOT_TOKEN` or
  `MAIL_PROVIDER` in the worker environment; the scheduler will skip the
  channel and keep the app/local alarms working.
