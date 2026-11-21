# Ei Void QA

## Android device matrix

| Device | API | Status |
|---|---|---|
| Android emulator `void35` | 35 | Final merged APK installed and walked through |
| Redmi 10 Prime (`005302500508`) | 33 | Installed through the phone's package installer and walked through |

MIUI 14 still rejects `adb install` with `INSTALL_FAILED_USER_RESTRICTED` even
with **Install via USB** and **USB debugging (Security settings)** enabled. The
working install path used here was:

1. Serve `app-debug.apk` over the LAN.
2. Open the URL in Chrome on the phone and allow Chrome to install unknown apps.
3. Accept Play Protect's scan prompt.
4. Install from the package installer.

The next ADB install may work after MIUI's install session is refreshed, but the
phone installer is the reliable path on this device.

Verified on the emulator on 2026-11-21:

- Home opens first with the four-tab shell (Home, Reminders, Code, Settings).
- Added “Internet / ISP / ₹500”; the row persisted in Room and rendered on Home.
- The exact alarm fired and posted the `reminders` channel notification with
  Mark paid and Snooze actions.
- Mark paid moved the row to Paid and stopped its alarm.
- Snooze moved the due date forward one day and reset the notified marker.
- Settings shows the reminder notification, email and permission sections with
  Ei Void branding.
- No fatal exception in logcat; the process stayed alive.

Verified on the Redmi 10 Prime on 2026-11-22:

- Installed and launched; Home opened first with the four-tab shell.
- Notification and exact-alarm permissions were granted.
- Added “Redmi Test / Phone / ₹1”; the row persisted and showed on Home.
- The exact alarm fired (`com.eivoid.core.reminders.action.REMINDER`).
- The notification posted on the `reminders` channel with **MARK PAID** and
  **SNOOZE 1 DAY**.
- No crash; the process stayed alive.

Evidence screenshots: `redmi-home.png`, `redmi-reminders.png`,
`redmi-edit.png`, `redmi-notif.png`, plus the emulator set
`eivoid-home-final.png`, `eivoid-reminders.png`, `eivoid-notif-expanded.png`,
`eivoid-settings-final.png`.

## Reminder checks

- [x] Add a one-time reminder due in two minutes; notification fires.
- [x] Mark Paid moves the row to Paid and stops its alarm (emulator).
- [x] Snooze 1 day moves the due date and resets the delivered marker (emulator).
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
