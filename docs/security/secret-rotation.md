# Secret rotation before the first Ei Void deployment

The source archives contained local configuration and signing material. Treat
every credential that was present there as exposed. Do not copy any of it into
this repository, and rotate it before deploying Ei Void.

## Credentials to rotate

| Credential | Where it was | New home |
|---|---|---|
| Discord bot token for the Ei Snore application | `Ei Snore/backend/.env.local` and deployment env | Retire the Ei Snore bot application |
| Discord bot token for the Void application | Render worker env | Rename the application to Ei Void, reset the token, put it only in Render |
| Clerk secret key | Render/dashboard env | New Ei Void Clerk application; Render only |
| Clerk publishable key | app config | Public; safe to embed, but use the new application |
| Gmail OAuth client secret and refresh token | `Ei Snore/backend/.env.local` and Render env | New Google Cloud OAuth client; Render only |
| Brevo API key | Render env | New key; Render only |
| SendGrid API key | Render env | New key; Render only |
| SMTP password | local/Render env | New app password; Render only |
| GitHub token used by the APK updater | Render env | New read-only token; Render only |
| Android release keystore and passwords | `Ei Snore/release-keystore.jks`, `keystore.properties` | New keystore generated for `com.eivoid.app`; CI secrets/external vault only |

## Repository rules

- `.env`, `.env.*`, `keystore.properties`, `*.jks`, `*.keystore` and
  `local.properties` are gitignored.
- `.env.example` contains names only, never values.
- gitleaks runs on every push and pull request.
- If a secret ever reaches a commit, rotate it first, then rewrite history;
  deleting the commit is not enough.

## Clerk application checklist

1. Create a new Clerk application named **Ei Void**.
2. Enable the Native API and register the Android package `com.eivoid.app`
   with the debug and release SHA-1 values.
3. Enable Google, Discord and GitHub social connections.
4. For GitHub, use custom OAuth credentials with scopes `repo` and
   `read:user`; Clerk only returns provider tokens for custom credentials.
5. Put the publishable key in the Android build and the website; put the
   secret key only in Render.
6. Run the GitHub provider-token endpoint against a real session before
   shipping the APK.

## Discord application checklist

1. Reuse the existing Void bot application and rename it **Ei Void**.
2. Enable Guild Install only; disable User Install and Public Bot.
3. Keep privileged intents off; the bot requests only `Guilds`.
4. Reset the bot token and copy it only into the Render worker environment.
5. Invite the bot to the home guild with `bot applications.commands` and the
   permissions the reminder fallback needs.
6. Register the slash commands once from a trusted machine.

## Rollback

- Rotate first, then redeploy. A rollback that restores an exposed token is
  not a rollback.
- The previous Ei Snore and Void deployments remain separate until the new
  app, bot and site pass device QA.
