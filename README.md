<div align="center">

<img src="docs/assets/hero.svg" alt="Void — a coding agent that lives on your phone" width="100%">

<br/>

[![Android](https://img.shields.io/badge/Android-15%2B-4F46E5?style=flat-square&labelColor=0B0B12)](https://developer.android.com/about/versions/15)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-F59E0B?style=flat-square&labelColor=0B0B12)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-Material%203-FF5A5F?style=flat-square&labelColor=0B0B12)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-4F46E5?style=flat-square&labelColor=0B0B12)](LICENSE)

</div>

**Void** is a native Android AI coding agent. It clones one of your GitHub
repositories onto the phone, lets a model read and change the files through a
set of guarded tools, and commits and pushes the result. There is no server in
the middle: the only network peers are GitHub and the AI provider you choose.

<br/>

## What it does

| | |
|---|---|
| **Talks to your repository** | Real git through JGit — clone, status, diff, commit, push, branches. Not a mock. |
| **Edits like an agent** | Read, write, create, edit and search files, with the model choosing the tools. |
| **Asks before it destroys** | Deleting files and creating repositories wait for your tap. |
| **Hides the plumbing** | You pick a *tier*, never a provider's model name. |
| **Keeps everything on the phone** | Keys in the Android Keystore, TLS only, no analytics, no telemetry. |

<br/>

## Three tiers

The router reads the task and picks a tier. You can override it in Settings.

| Tier | When | Character |
|---|---|---|
| **Red Dwarfs** | scaffolding, broad changes | strongest reasoning |
| **White Dwarfs** | reviews, refactors | balanced |
| **Neutron Stars** | explanations, small edits | smallest and fastest |

A model that fails or does not answer is skipped for the next one **in the same
tier** before the router ever moves down a tier. Every model call has a 45 s
budget; every tool call has its own.

<br/>

## Plans

| | Free | Pro | Max |
|---|---|---|---|
| **Price** | ₹0 | ₹200 / month | ₹500 / month |
| **Projects** | 7 | 15 | 30 |
| **Tokens / month** | 30M | 80M | 200M |
| **Neutron Stars** | ✓ | ✓ | ✓ |
| **White Dwarfs** | — | 5× | 10× |
| **Red Dwarfs** | — | 2× | 5× |

The tier multipliers decide how much of the monthly token budget each tier may
use. Usage is counted per tier and shown on the app's usage page.

<br/>

## Status

Honest, as of the last commit:

| | |
|---|---|
| ✅ Built and unit-tested | 150 tests across all modules; both APKs assemble |
| ✅ Working | login, repo list, clone, file browser, git tools, agent loop, chat, settings |
| ⚠️ Unverified on a device | no phone or emulator was attached during development, so the three instrumented tests have only been compiled ([#2](https://github.com/xyanncat/Ei-Void/issues/2)) |
| ⚠️ Not configured yet | the OAuth client id ([#1](https://github.com/xyanncat/Ei-Void/issues/1)) and a decision on billing ([#14](https://github.com/xyanncat/Ei-Void/issues/14)) |

<br/>

## Build it

You need **JDK 21** and the **Android SDK with platform 35**.

```bash
# Linux / macOS
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug
```

```powershell
# Windows
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

### Sign in

Login uses GitHub's OAuth **device flow**, which needs an OAuth App with
Device Flow enabled and **no client secret**. Put its client id in
`local.properties` (gitignored — never commit it):

```properties
github.clientId=Ov23li...
```

Then build again. Without it, the login screen cannot complete.

<br/>

## Security

- **No backend.** Void has no service; the app talks to GitHub and the model
  provider directly.
- **Keys stay on the device.** Provider keys and the GitHub token live in
  `EncryptedSharedPreferences` (AES-256, Android Keystore), are read per
  request, and are never logged, never put in a URL, never committed.
- **Transport.** TLS 1.2/1.3 only, cleartext banned at the manifest level,
  certificate pinning wired and waiting for release pins.
- **Sign out wipes.** Token, provider keys, cached repositories and cloned
  workspaces are all deleted.
- **No telemetry.** No analytics, no crash reporting, no third-party SDKs.
  `INTERNET` is the only permission.

<br/>

## Roadmap

Everything below is tracked as an issue.

**Blocked on you**
- [#1](https://github.com/xyanncat/Ei-Void/issues/1) — the OAuth client id, so login can finish
- [#2](https://github.com/xyanncat/Ei-Void/issues/2) — run it on a device
- [#14](https://github.com/xyanncat/Ei-Void/issues/14) — decide how subscriptions are billed

**Build and release**
- [#3](https://github.com/xyanncat/Ei-Void/issues/3) launcher icon and splash ·
  [#4](https://github.com/xyanncat/Ei-Void/issues/4) signed release build ·
  [#20](https://github.com/xyanncat/Ei-Void/issues/20) CI

**Security**
- [#11](https://github.com/xyanncat/Ei-Void/issues/11) SQLCipher ·
  [#12](https://github.com/xyanncat/Ei-Void/issues/12) biometric lock ·
  [#13](https://github.com/xyanncat/Ei-Void/issues/13) certificate pins

**Accuracy**
- [#5](https://github.com/xyanncat/Ei-Void/issues/5) real token counting ·
  [#6](https://github.com/xyanncat/Ei-Void/issues/6) context windows ·
  [#7](https://github.com/xyanncat/Ei-Void/issues/7) project limits ·
  [#10](https://github.com/xyanncat/Ei-Void/issues/10) prune usage rows

**Product**
- [#8](https://github.com/xyanncat/Ei-Void/issues/8) chat history ·
  [#9](https://github.com/xyanncat/Ei-Void/issues/9) partial output ·
  [#15](https://github.com/xyanncat/Ei-Void/issues/15) the editor ·
  [#16](https://github.com/xyanncat/Ei-Void/issues/16) project management ·
  [#17](https://github.com/xyanncat/Ei-Void/issues/17) onboarding illustrations ·
  [#18](https://github.com/xyanncat/Ei-Void/issues/18) offline and retries

**Docs**
- [#19](https://github.com/xyanncat/Ei-Void/issues/19) refresh the specification and the plans

<br/>

## License

[MIT](LICENSE) © 2026 xyanncat

<div align="center"><sub>Built for a phone, not a terminal.</sub></div>
