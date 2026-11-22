# Ei Void QA

## Android device matrix

| Device | API | Status |
|---|---|---|
| Android emulator `void35` | 35 | Discord wiring build installed; assistant and Discord config path walked through |
| Redmi 10 Prime (`005302500508`) | 33 | v0.3.2 production UI preview installed; card layout, landscape and restored reminders verified |

## First UI pass (2026-11-22)

The old bottom-tab shell is gone. The app now opens on the AI assistant and
uses a single slide-out drawer. The Production UI pass below supersedes the
assistant home and drawer details in this first-pass walkthrough.

Verified on the emulator:

- **Assistant home**: pure black canvas, staggered menu button, incognito chat
  button, “Put Ei Void to work” headline, two horizontally scrolling chip rows,
  “Pick a task”, and the floating ask bar with plus/mic/blue voice action.
- **Drawer**: charcoal sheet with Ei Void title, search action, Chat, Images,
  Library, Customize, Projects, Remote and Explore rows. It slides over a dimmed
  backdrop.
- **Library**: Upcoming/Paid segmented filter, month stats, OLED reminder cards
  with due-date pills and quiet actions, and one sticky Add reminder button.
- **Editor**: title, merchant, amount + INR/USD, due date, reminder time, repeat
  and category pills, lead-days stepper, notes, validation, and a sticky Save
  reminder button.
- **Projects**: search, workspace stats, repository cards with branch /
  visibility / linked pills, empty and error states.
- **Customize**: EI VOID, EI SNORE and APP sections; model tiers, plan/usage,
  provider keys, notification lead/time, email, Discord status, permissions,
  appearance, sign out and delete data.
- **Usage**: plan hero, monthly token progress, per-tier allowances and reset
  date, labelled as a local ledger.
- **Chat/workspace shell**: Files/Chat pill switch and the agent chat reskinned
  to the same OLED components.
- **Reminder flow**: added “New UI Test / Test / ₹10”; the row persisted, the
  exact alarm fired, and Mark paid moved it to 0 upcoming · 1 paid.
- **Release artifact**: `EiVoid-v0.2.0-debug.apk` downloaded from the GitHub
  release, verified as `com.eivoid.app` 0.2.0 / arm64 / Android 13+, installed
  on the emulator and launched (`release-v0.2.0-emulator.png`).

Verified on the Redmi:

- The redesigned APK updated through the phone's package installer and passed
  Xiaomi's security scan (`Passed security tests · No risks detected`).
- Home rendered the new assistant surface in both orientations.
- The previous reminder flow still works on the device (exact alarm and
  notification were verified in the prior build; the reminder database survived
  the update).
- The Discord wiring build installed with `adb install -r` and the assistant
  home was checked in landscape: the header and incognito action respect the
  side navigation bar, both chip rows fit, and “Pick a task” stays above the
  slim ask bar.
- Customize → Ei Snore → Discord notifications → Connect shows the explicit
  “Discord is not configured in this build” notice on the Redmi instead of
  failing silently.
- The published `EiVoid-v0.2.0-debug.apk` was installed through the package
  installer after Play Protect scanned it as safe. The reminder database and
  DataStore settings were backed up with `run-as`, the old debug install was
  replaced, and the data was restored; Library still shows the prior paid
  reminder. Keystore-backed provider keys were not restored.

Evidence screenshots: `v2-home2.png`, `new-drawer.png`, `v2-library-saved.png`,
`v2-editor-filled.png`, `v2-projects.png`, `v2-customize.png`, `v2-usage.png`,
`redmi-v2-home.png`, `redmi-notif.png`, `redmi-discord-2-landscape.png`,
`redmi-discord-5-snore.png`, `redmi-discord-6-result.png`,
`release-v0.2.0-emulator.png`, `redmi-release-v0.2.0.png`.

## Production UI pass (2026-11-22)

The assistant is no longer a debug shell with oversized copy and emoji chips.

- **No debug ribbon.** The yellow Clerk-bypass banner is gone from the product
  surface; local previews are identified inside Customize, not above every
  screen. System bars are black with light icons on the OLED canvas.
- **Inter typography.** Bundled Inter Regular/Medium/SemiBold/Bold gives the
  app one a product-owned type scale instead of OEM Roboto.
- **Assistant home.** A branded header (menu, cat mark, wordmark, incognito),
  a short greeting, four equal-height capability cards with vector icons and
  descriptions, a workspace card, a privacy line and a floating composer.
- **Drawer.** Brand header, account card (local workspace shows “On-device
  profile”), a primary New chat action, grouped Workspace/Preferences sections,
  selected-state treatment, and a scrollable short-viewport layout.
- **Composer.** Plus menu with line icons, Inter input text, dictation, and a
  blue voice/send action that switches to an up arrow when there is text.
- **Chat.** Refined user bubbles, assistant mark, streaming dots, and an
  actionable provider error banner.
- **Icons.** Material outlined icons for every structural action; no emoji
  used as navigation or task artwork.

Verified on the emulator: portrait home, landscape home (four compact cards),
drawer in both orientations including scrolling to Usage, composer typing and
send, provider error state, and navigation to Library and Customize. Evidence:
`redesign-home-final.png`, `redesign-drawer-1.png`, `redesign-chat-1.png`,
`redesign-final-home-landscape.png`, `redesign-drawer-landscape-2.png`.

Verified on the Redmi 10 Prime with the published `v0.3.2` APK:

- Play Protect scanned the APK as “This app looks safe”.
- The home renders the branded header, four equal-height capability cards with
  untruncated copy, the workspace card, privacy line and composer.
- Library still shows 0 upcoming · 1 paid after the install, so the reminder
  database and DataStore settings survived the backup/install/restore path.
- Evidence: `redmi-v0.3.2-home.png`, `redmi-v0.3.2-library.png`.

## Installing on this Redmi

With **Install via USB** and **USB debugging (Security settings)** enabled,
`adb install -r` succeeded for the Discord build on 2026-11-22. If MIUI still
rejects it with `INSTALL_FAILED_USER_RESTRICTED`, use the package installer:

1. `adb push app-debug.apk /sdcard/Download/EiVoid.apk`.
2. Open the Files app → Download → the APK.
3. Choose **Package installer** → **Update**.
4. Let Play Protect/Xiaomi Security scan it; the build passed with no risks.

Do not disable Play Protect or package verification to force an install.

## Discord notification connection

Customize now launches a real Discord OAuth2 PKCE flow:

- `DiscordAuthActivity` opens Discord with scopes `identify guilds` and a
  state/PKCE challenge, then returns the access token to Settings.
- `SettingsViewModel` sends the token to `POST /api/v1/discord/link` with the
  Clerk session; the backend verifies it, checks the home guild, and stores the
  Discord id.
- The linked username is shown in Customize; Disconnect calls
  `DELETE /api/v1/discord/link`; Send test calls `POST /api/v1/test/discord`.
- The access token is never persisted, and the bot token never reaches the app.

Local verification is limited because `android/local.properties` has no
`discord.clientId` or `ei-void.apiUrl`. With those empty, Connect returns a clear
configuration message instead of pretending to link — verified on the emulator
(`discord-connect-6-result.png`) and the Redmi (`redmi-discord-6-result.png`),
2026-11-22. Unit tests cover the client request/response and error mapping; the
full OAuth round-trip needs a registered Discord application and a deployed Ei
Void web service.

- [ ] Connect a Discord account in the home guild.
- [ ] Reject an account that is not in the home guild.
- [ ] Disconnect removes the link.
- [ ] Send test delivers a DM when DMs are open.
- [ ] Closed DMs fall back to the notification channel mention.

## Reminder checks

- [x] Add a one-time reminder; notification fires.
- [x] Mark Paid moves the row to Paid and stops its alarm.
- [x] Snooze shifts the due date and resets the delivered marker.
- [ ] Add a repeating reminder; Mark Paid creates the next occurrence.
- [ ] Deny POST_NOTIFICATIONS; app explains how to enable it and does not record delivery.
- [ ] Revoke exact-alarm permission; scheduling falls back to inexact.
- [ ] Reboot the device; active alarms are rescheduled.
- [ ] Change timezone; alarms are recomputed.
- [ ] Force-stop the app; an alarm still fires.
- [ ] Tap Mark Paid and Snooze from the notification.
- [ ] Airplane mode: reminder still saves and fires; sync retries later.

## Agent checks

- [ ] Clerk sign-in completes.
- [ ] GitHub link + token exchange succeeds.
- [ ] Clone a small repository.
- [ ] Prompt the agent; streaming and tool cards render.
- [ ] Review the diff and push.
- [ ] Sign out wipes token, repos, chats and workspaces.

## Website checks

- [ ] `/`, `/reminders`, `/agent`, `/pricing`, `/bot`, `/docs`, `/privacy` render.
- [ ] `/dashboard` and `/link` require sign-in.
- [ ] `/link` code is single-use and expires.
- [ ] Device key is shown once.
- [ ] APK download metadata endpoint responds.

## Bot checks

- [ ] `/about` replies ephemerally.
- [ ] `/link` returns an HTTPS URL.
- [ ] `/reminders` lists only the caller's reminders.
- [ ] `/snooze` and `/paid` modify only the caller's reminder.
- [ ] A foreign guild or DM is refused.
- [ ] Scheduler sends once when DMs are available.
- [ ] DMs closed: fallback channel mention arrives.
- [ ] Bot restart does not duplicate a delivery.

## Screenshots

Screenshots taken during the build are in this directory. They are development
artifacts, not release assets.
