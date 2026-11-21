# Ei Void QA

## Android device matrix

| Device | API | Status |
|---|---|---|
| Android emulator `void35` | 35 | Base shell installed; reminder build pending |
| Redmi 10 Prime | 33+ | USB ADB interface detected; waiting for USB-debugging authorization |

## Reminder checks

- [ ] Add a one-time reminder due in two minutes; notification fires.
- [ ] Add a repeating reminder; Mark Paid creates the next occurrence.
- [ ] Snooze 1 day moves the due date and re-arms the alarm.
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
