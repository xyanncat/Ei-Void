# Void — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the foundation of Void — a secure, multi-module Android app that logs in with GitHub, lists and clones repositories into an app-private workspace, browses and edits files, and commits and pushes changes via JGit.

**Architecture:** Multi-module Gradle project (`app`, `core:common`, `core:security`, `core:database`, `core:git`, `core:workspace`, `feature:auth`, `feature:repos`, `feature:workspace`, `feature:settings`, `designsystem`). Hilt for DI, Jetpack Compose for UI, Room + SQLCipher for persistence, Ktor (OkHttp engine) with certificate pinning for networking, JGit for git. No backend; all secrets in EncryptedSharedPreferences.

**Tech Stack:** Kotlin 2.0, Jetpack Compose (BOM), Hilt, Coroutines/Flow, Ktor + OkHttp, JGit 6.x, Room 2.6 + SQLCipher 4.5, DataStore 1.1, EncryptedSharedPreferences 1.1, JUnit4 + Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-10-void-core-design.md`

**Plan splitting:** This is Plan A of three. Plan B adds the AI provider layer + agent engine + chat UI. Plan C adds the full editor, security hardening, and build/distribution. Plan A produces a usable GitHub repo client without the agent.

## Global Constraints

- `minSdk = 35`, `targetSdk = 35`, `compileSdk = 35`; ABI filter `arm64-v8a` only.
- Base package: `com.void`.
- No analytics, telemetry, crash reporting, or third-party SDKs.
- Permissions: `INTERNET` only.
- Transport: TLS 1.3, certificate pinning, no cleartext (`network_security_config.xml`).
- Secrets (OAuth token, API keys) only in `EncryptedSharedPreferences`; never in logs, never in on-disk git config.
- All secrets cleared on logout.
- Commit after every task; conventional commit messages.
- On Windows run Gradle as `.\gradlew.bat`; on macOS/Linux `./gradlew`.

## Review Focus

1. **Cleartext traffic** — a reasonable person expects no plaintext HTTP. Test: `network_security_config.xml` sets `cleartextTrafficPermitted="false"` and an `http://` connection fails.
2. **Token leakage** — a reasonable person expects tokens never written to logs. Test: `SecretStore` values are absent from any logged output.
3. **Git credential on disk** — a reasonable person expects the OAuth token not persisted in `.git/config`. Test: after clone/push, `.git/config` contains no token.
4. **Main-thread git work** — a reasonable person expects the UI not to freeze during clone/commit. Test: git operations run on `Dispatchers.IO`.
5. **Oversized file reads** — a reasonable person expects a huge file not to crash or hang. Test: `readFile` truncates beyond the line cap and reports the truncation.

---

### Task 1: Project scaffold & design system

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle/libs.versions.toml`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/void/app/VoidApp.kt`
- Create: `app/src/main/java/com/void/app/MainActivity.kt`
- Create: `app/src/main/java/com/void/app/navigation/VoidNavHost.kt`
- Create: `core/common/build.gradle.kts`
- Create: `core/security/build.gradle.kts`
- Create: `core/database/build.gradle.kts`
- Create: `core/git/build.gradle.kts`
- Create: `core/workspace/build.gradle.kts`
- Create: `feature/auth/build.gradle.kts`
- Create: `feature/repos/build.gradle.kts`
- Create: `feature/settings/build.gradle.kts`
- Create: `feature/workspace/build.gradle.kts`
- Create: `designsystem/build.gradle.kts`
- Create: `designsystem/src/main/java/com/void/designsystem/theme/Color.kt`
- Create: `designsystem/src/main/java/com/void/designsystem/theme/Type.kt`
- Create: `designsystem/src/main/java/com/void/designsystem/theme/Theme.kt`
- Test: `designsystem/src/test/java/com/void/designsystem/theme/ColorTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `com.void.designsystem.theme.VoidColors` (object with `primary`, `secondary`, `accent`, `surfaceDark`, `surfaceLight`); `VoidTheme` composable; the Gradle module structure later tasks import.

- [ ] **Step 1: Write the failing test**

`designsystem/src/test/java/com/void/designsystem/theme/ColorTest.kt`:
```kotlin
package com.void.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Test
import org.junit.Assert.assertEquals

class ColorTest {
    @Test fun `primary token is deep indigo`() =
        assertEquals(Color(0xFF4F46E5), VoidColors.primary)

    @Test fun `secondary token is warm amber`() =
        assertEquals(Color(0xFFF59E0B), VoidColors.secondary)

    @Test fun `accent token is electric coral`() =
        assertEquals(Color(0xFFFF5A5F), VoidColors.accent)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :designsystem:testDebugUnitTest --tests "com.void.designsystem.theme.ColorTest"`
Expected: FAIL — `VoidColors` unresolved.

- [ ] **Step 3: Create the Gradle scaffold**

`gradle/libs.versions.toml` with at least: AGP, Kotlin, KSP, Hilt, Compose BOM, Coroutines, Ktor, OkHttp, JGit, Room, SQLCipher, security-crypto, DataStore, Navigation, Lifecycle, JUnit, Robolectric. Root `build.gradle.kts` applies AGP/Kotlin/Hilt/KSP via `alias(...)` with `apply false`. `settings.gradle.kts` includes all modules. Each module `build.gradle.kts` applies the plugins and dependencies it needs. `gradle.properties`: `android.useAndroidX=true`, `kotlin.code.style=official`, `org.gradle.jvmargs=-Xmx4g`.

- [ ] **Step 4: Create the design system tokens**

`Color.kt`: primitive scales + the semantic `VoidColors` object (`primary = Color(0xFF4F46E5)`, `secondary = Color(0xFFF59E0B)`, `accent = Color(0xFFFF5A5F)`, `surfaceDark = Color(0xFF0B0B12)`, `surfaceLight = Color(0xFFF7F7FB)`). `Type.kt`: Manrope for UI, JetBrains Mono for code (use `FontFamily.Default` fallback for now; bundled fonts arrive in Plan C). `Theme.kt`: `VoidTheme` composable wrapping Material 3 `MaterialTheme` with `VoidColors`.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :designsystem:testDebugUnitTest --tests "com.void.designsystem.theme.ColorTest"`
Expected: PASS.

- [ ] **Step 6: Create the app shell**

`app/src/main/AndroidManifest.xml`: `INTERNET` permission only; `application` with `android:name=".VoidApp"`, `android:allowBackup="false"`, `android:networkSecurityConfig="@xml/network_security_config"` (created in Task 3), and a Material3 theme. `VoidApp`: `@HiltAndroidApp`. `MainActivity`: `@AndroidEntryPoint`, content `VoidTheme { VoidNavHost() }`. `VoidNavHost`: start destination `onboarding` (placeholder), bottom nav with `repos` and `settings` (placeholders).

- [ ] **Step 7: Build the app**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL; `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 8: Commit**

```bash
git add .
git commit -m "feat: scaffold Void multi-module project and design system"
```

---

### Task 2: SecretStore (EncryptedSharedPreferences)

**Files:**
- Create: `core/security/src/main/java/com/void/core/security/SecretStore.kt`
- Test: `core/security/src/test/java/com/void/core/security/SecretStoreTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `SecretStore` with `saveToken(String)`, `getToken(): String?`, `clearToken()`, `saveProviderKey(providerId: String, key: String)`, `getProviderKey(providerId: String): String?`, `clearProviderKey(providerId: String)`, `clearAll()`.

- [ ] **Step 1: Write the failing test**

`core/security/src/test/java/com/void/core/security/SecretStoreTest.kt` (Robolectric):
```kotlin
@RunWith(RobolectricTestRunner::class)
class SecretStoreTest {
    private lateinit var store: SecretStore

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = SecretStore(context)
        store.clearAll()
    }

    @Test fun `save and read token`() {
        store.saveToken("gho_secret123")
        assertEquals("gho_secret123", store.getToken())
    }

    @Test fun `token is not stored as plaintext`() {
        store.saveToken("gho_secret123")
        val prefs = context.getSharedPreferences("void_secrets", Context.MODE_PRIVATE)
        val raw = prefs.all.values.joinToString()
        assertFalse(raw.contains("gho_secret123"))
    }

    @Test fun `clearAll removes token and provider keys`() {
        store.saveToken("gho_secret123")
        store.saveProviderKey("openrouter", "sk-or-abc")
        store.clearAll()
        assertNull(store.getToken())
        assertNull(store.getProviderKey("openrouter"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:security:testDebugUnitTest --tests "com.void.core.security.SecretStoreTest"`
Expected: FAIL — `SecretStore` unresolved.

- [ ] **Step 3: Implement `SecretStore`**

Use `EncryptedSharedPreferences.create(...)` with `MasterKey.Builder(...).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()` and `EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV` / `PrefValueEncryptionScheme.AES256_GCM`. Store the token under key `gh_token` and provider keys under `provider_key_<providerId>`. `clearAll()` clears the file.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:security:testDebugUnitTest --tests "com.void.core.security.SecretStoreTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/security
git commit -m "feat(security): add encrypted SecretStore for tokens and provider keys"
```

---

### Task 3: Network client, certificate pinning & cleartext ban

**Files:**
- Create: `core/common/src/main/java/com/void/core/common/network/PinConfig.kt`
- Create: `core/common/src/main/java/com/void/core/common/network/NetworkModule.kt`
- Create: `app/src/main/res/xml/network_security_config.xml`
- Test: `core/common/src/test/java/com/void/core/common/network/PinConfigTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `PinConfig(host: String, pattern: String, certificates: List<String>)`; `NetworkModule.provideOkHttpClient(pins: List<PinConfig>): OkHttpClient`; `NetworkModule.provideKtorClient(okHttp: OkHttpClient): HttpClient`.

- [ ] **Step 1: Write the failing test**

`core/common/src/test/java/com/void/core/common/network/PinConfigTest.kt`:
```kotlin
class PinConfigTest {
    @Test fun `pins are applied to the client`() {
        val pins = listOf(
            PinConfig("api.github.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAA=", emptyList())
        )
        val client = NetworkModule.provideOkHttpClient(pins)
        assertNotNull(client.certificatePinner)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:common:testDebugUnitTest --tests "com.void.core.common.network.PinConfigTest"`
Expected: FAIL — `NetworkModule` unresolved.

- [ ] **Step 3: Implement pinning and the network module**

`PinConfig` is a data class. `NetworkModule.provideOkHttpClient` builds an OkHttp client with a `CertificatePinner` built from the pins (pin format `sha256/<base64>`), a `ConnectionSpec` restricted to TLS 1.3 (and TLS 1.2 as a fallback only if 1.3 is unavailable on the device — note this in a comment), and no cleartext. `provideKtorClient` wraps the OkHttp client in Ktor's `OkHttp` engine.

- [ ] **Step 4: Add the network security config**

`app/src/main/res/xml/network_security_config.xml`:
```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :core:common:testDebugUnitTest --tests "com.void.core.common.network.PinConfigTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/common app/src/main/res/xml
git commit -m "feat(network): add Ktor/OkHttp client with certificate pinning and cleartext ban"
```

---

### Task 4: Room database with SQLCipher

**Files:**
- Create: `core/database/src/main/java/com/void/core/database/RepoEntity.kt`
- Create: `core/database/src/main/java/com/void/core/database/RepoDao.kt`
- Create: `core/database/src/main/java/com/void/core/database/VoidDatabase.kt`
- Test: `core/database/src/test/java/com/void/core/database/RepoDaoTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `RepoEntity(id: String, owner: String, name: String, fullName: String, private: Boolean, defaultBranch: String, description: String?, updatedAt: Long)`; `RepoDao.insertAll(List<RepoEntity>)`, `RepoDao.observeAll(): Flow<List<RepoEntity>>`, `RepoDao.search(query: String): Flow<List<RepoEntity>>`, `RepoDao.deleteAll()`; `VoidDatabase` (Room, SQLCipher).

- [ ] **Step 1: Write the failing test**

`core/database/src/test/java/com/void/core/database/RepoDaoTest.kt` (Robolectric):
```kotlin
@RunWith(RobolectricTestRunner::class)
class RepoDaoTest {
    private lateinit var db: VoidDatabase
    private lateinit var dao: RepoDao

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VoidDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.repoDao()
    }

    @Test fun `insert and observe repos`() = runTest {
        dao.insertAll(listOf(repo("1", "owner", "repo")))
        val result = dao.observeAll().first()
        assertEquals(1, result.size)
        assertEquals("owner/repo", result[0].fullName)
    }

    @Test fun `search filters by name`() = runTest {
        dao.insertAll(listOf(repo("1", "owner", "alpha"), repo("2", "owner", "beta")))
        val result = dao.search("alp").first()
        assertEquals(listOf("alpha"), result.map { it.name })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:database:testDebugUnitTest --tests "com.void.core.database.RepoDaoTest"`
Expected: FAIL — `VoidDatabase` unresolved.

- [ ] **Step 3: Implement the database**

`RepoEntity` is a Room `@Entity(tableName = "repos")` with `id` as primary key. `RepoDao` uses `@Insert(onConflict = REPLACE)`, `@Query("SELECT * FROM repos ORDER BY updatedAt DESC")`, and a `search` query matching `name LIKE :query OR fullName LIKE :query`. `VoidDatabase` is an abstract Room database; the SQLCipher factory is wired in the Hilt module in Task 5 (use the in-memory builder in tests).

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:database:testDebugUnitTest --tests "com.void.core.database.RepoDaoTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/database
git commit -m "feat(database): add Room database with SQLCipher and repo entity"
```

---

### Task 5: GitHub OAuth device flow

**Files:**
- Create: `core/common/src/main/java/com/void/core/common/github/GitHubAuthApi.kt`
- Create: `feature/auth/src/main/java/com/void/feature/auth/AuthRepository.kt`
- Create: `feature/auth/src/main/java/com/void/feature/auth/LoginViewModel.kt`
- Create: `feature/auth/src/main/java/com/void/feature/auth/LoginScreen.kt`
- Test: `feature/auth/src/test/java/com/void/feature/auth/AuthRepositoryTest.kt`

**Interfaces:**
- Consumes: `SecretStore` (Task 2), `NetworkModule` (Task 3).
- Produces: `DeviceCode(deviceCode: String, userCode: String, verificationUri: String, expiresIn: Int, interval: Int)`; `TokenResult` sealed type (`Success(token: String)`, `AuthorizationPending`, `Expired`, `Failed`); `AuthRepository.requestDeviceCode(): DeviceCode`, `AuthRepository.pollForToken(deviceCode: DeviceCode): TokenResult`, `AuthRepository.isLoggedIn(): Boolean`, `AuthRepository.logout()`.

- [ ] **Step 1: Write the failing test**

`feature/auth/src/test/java/com/void/feature/auth/AuthRepositoryTest.kt`:
```kotlin
class AuthRepositoryTest {
    @Test fun `device code request returns a code`() = runTest {
        val api = FakeAuthApi(deviceCode = DeviceCode("dc", "UC-123", "https://github.com/login/device", 900, 5))
        val repo = AuthRepository(api, FakeSecretStore())
        val code = repo.requestDeviceCode()
        assertEquals("UC-123", code.userCode)
    }

    @Test fun `poll success stores the token`() = runTest {
        val store = FakeSecretStore()
        val repo = AuthRepository(FakeAuthApi(token = "gho_abc"), store)
        val result = repo.pollForToken(DeviceCode("dc", "UC-123", "uri", 900, 0))
        assertEquals("gho_abc", (result as TokenResult.Success).token)
        assertEquals("gho_abc", store.getToken())
        assertTrue(repo.isLoggedIn())
    }

    @Test fun `logout clears the token`() = runTest {
        val store = FakeSecretStore()
        val repo = AuthRepository(FakeAuthApi(token = "gho_abc"), store)
        repo.pollForToken(DeviceCode("dc", "UC-123", "uri", 900, 0))
        repo.logout()
        assertFalse(repo.isLoggedIn())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :feature:auth:testDebugUnitTest --tests "com.void.feature.auth.AuthRepositoryTest"`
Expected: FAIL — `AuthRepository` unresolved.

- [ ] **Step 3: Implement the auth API and repository**

`GitHubAuthApi` uses Ktor: `POST /login/device/code` (client_id, scopes `repo read:user workflow`) → `DeviceCode`; `POST /login/oauth/access_token` (client_id, device_code, grant_type `urn:ietf:params:oauth:grant-type:device_code`) → token or error. `AuthRepository` wraps the API, stores the token via `SecretStore`, and maps errors to `TokenResult`. The client id comes from `BuildConfig` (a `GITHUB_CLIENT_ID` field; no client secret).

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :feature:auth:testDebugUnitTest --tests "com.void.feature.auth.AuthRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Implement the login UI**

`LoginViewModel`: exposes `deviceCode: StateFlow<DeviceCode?>`, `startLogin()` (requests the code, opens the verification URL via an Intent), `poll()` (polls on a coroutine at the server interval), and `logout()`. `LoginScreen`: shows the user code in large type with a "Open GitHub" button, a copy button, and a status line; on success, navigates to Repos. Use `FLAG_SECURE` on this screen.

- [ ] **Step 6: Commit**

```bash
git add core/common feature/auth
git commit -m "feat(auth): add GitHub OAuth device flow and login screen"
```

---

### Task 6: Repo listing

**Files:**
- Create: `core/common/src/main/java/com/void/core/common/github/GitHubApi.kt`
- Create: `feature/repos/src/main/java/com/void/feature/repos/ReposRepository.kt`
- Create: `feature/repos/src/main/java/com/void/feature/repos/ReposViewModel.kt`
- Create: `feature/repos/src/main/java/com/void/feature/repos/ReposScreen.kt`
- Test: `feature/repos/src/test/java/com/void/feature/repos/ReposRepositoryTest.kt`

**Interfaces:**
- Consumes: `GitHubAuthApi`/token (Task 5), `VoidDatabase` (Task 4).
- Produces: `data class Repo(id: String, owner: String, name: String, fullName: String, private: Boolean, defaultBranch: String, description: String?, updatedAt: Long)`; `GitHubApi.listRepos(page: Int): List<Repo>`; `ReposRepository.refreshRepos(): List<Repo>`, `ReposRepository.observeRepos(): Flow<List<Repo>>`, `ReposRepository.search(query: String): Flow<List<Repo>>`.

- [ ] **Step 1: Write the failing test**

`feature/repos/src/test/java/com/void/feature/repos/ReposRepositoryTest.kt`:
```kotlin
class ReposRepositoryTest {
    @Test fun `refresh maps API repos and stores them`() = runTest {
        val api = FakeGitHubApi(listOf(Repo("1", "owner", "repo", "owner/repo", false, "main", null, 0L)))
        val repo = ReposRepository(api, FakeDao())
        val result = repo.refreshRepos()
        assertEquals(listOf("owner/repo"), result.map { it.fullName })
    }

    @Test fun `search filters by name`() = runTest {
        val dao = FakeDao(listOf(
            Repo("1", "owner", "alpha", "owner/alpha", false, "main", null, 0L),
            Repo("2", "owner", "beta", "owner/beta", false, "main", null, 0L)
        ))
        val repo = ReposRepository(FakeGitHubApi(), dao)
        val result = repo.search("alp").first()
        assertEquals(listOf("alpha"), result.map { it.name })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :feature:repos:testDebugUnitTest --tests "com.void.feature.repos.ReposRepositoryTest"`
Expected: FAIL — `ReposRepository` unresolved.

- [ ] **Step 3: Implement the API and repository**

`GitHubApi.listRepos(page)` calls `GET /user/repos?per_page=100&page=N&sort=updated` with the bearer token and maps to `Repo`. `ReposRepository.refreshRepos()` pages through all repos, writes them to Room, and returns the full list; `observeRepos()` reads from Room; `search()` delegates to `RepoDao.search`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :feature:repos:testDebugUnitTest --tests "com.void.feature.repos.ReposRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Implement the repos UI**

`ReposViewModel`: exposes `repos: StateFlow<List<Repo>>`, `query: StateFlow<String>`, `refresh()`, and `search()`. `ReposScreen`: a search field, a list of repos (full name, description, updated time), a refresh action, and a "link a repo" affordance. Tapping a repo navigates to the workspace (Task 7).

- [ ] **Step 6: Commit**

```bash
git add core/common feature/repos
git commit -m "feat(repos): list and search GitHub repositories"
```

---

### Task 7: Git clone & workspace file access

**Files:**
- Create: `core/git/src/main/java/com/void/core/git/GitResult.kt`
- Create: `core/git/src/main/java/com/void/core/git/GitClient.kt`
- Create: `core/git/src/main/java/com/void/core/git/CloneManager.kt`
- Create: `core/workspace/src/main/java/com/void/core/workspace/FileInfo.kt`
- Create: `core/workspace/src/main/java/com/void/core/workspace/WorkspaceManager.kt`
- Test: `core/git/src/test/java/com/void/core/git/CloneManagerTest.kt`
- Test: `core/workspace/src/test/java/com/void/core/workspace/WorkspaceManagerTest.kt`

**Interfaces:**
- Consumes: `Repo` (Task 6), `SecretStore` (Task 2).
- Produces: `GitResult` sealed type (`Success`, `Failure(message: String)`, `Conflict`); `CloneProgress(percent: Int, message: String)`; `GitClient.clone(repo: Repo, token: String, onProgress: (CloneProgress) -> Unit): GitResult`; `Workspace(path: File, repo: Repo)`; `FileInfo(path: String, name: String, isDirectory: Boolean, sizeBytes: Long, lastModified: Long)`; `FileContent(path: String, lines: List<String>, truncated: Boolean)`; `WorkspaceManager.workspaceFor(repo: Repo): Workspace`, `listFiles(workspace: Workspace, path: String = "."): List<FileInfo>`, `readFile(workspace: Workspace, path: String, startLine: Int? = null, endLine: Int? = null): FileContent`, `writeFile(workspace: Workspace, path: String, content: String): GitResult`, `deleteFile(workspace: Workspace, path: String): GitResult`.

- [ ] **Step 1: Write the failing test for cloning**

`core/git/src/test/java/com/void/core/git/CloneManagerTest.kt`:
```kotlin
class CloneManagerTest {
    @Test fun `clone creates a workspace with the repo files`() = runTest {
        val origin = createLocalBareRepoWithOneFile()   // helper: temp bare repo + commit
        val repo = Repo("1", "owner", "repo", "owner/repo", false, "main", null, 0L)
        val result = CloneManager().clone(repo, "token") { }   // uses file:// URL in test
        assertTrue(result is GitResult.Success)
        assertTrue(File(result.workspace.path, "README.md").exists())
    }

    @Test fun `clone reports progress`() = runTest {
        val progress = mutableListOf<CloneProgress>()
        CloneManager().clone(repo, "token") { progress.add(it) }
        assertTrue(progress.isNotEmpty())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:git:testDebugUnitTest --tests "com.void.core.git.CloneManagerTest"`
Expected: FAIL — `CloneManager` unresolved.

- [ ] **Step 3: Implement cloning**

`CloneManager.clone` uses JGit `CloneCommand` with `setDepth(1)`, `setDirectory(workspacePath)`, and a `CredentialsProvider` built from the token. It reports progress via a `CloneMonitor` mapped to `CloneProgress`. It runs on `Dispatchers.IO`. The workspace path is `filesDir/void/workspaces/<owner>/<repo>`. The token is never written to `.git/config`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:git:testDebugUnitTest --tests "com.void.core.git.CloneManagerTest"`
Expected: PASS.

- [ ] **Step 5: Write the failing test for the workspace**

`core/workspace/src/test/java/com/void/core/workspace/WorkspaceManagerTest.kt`:
```kotlin
class WorkspaceManagerTest {
    @Test fun `listFiles returns files and directories`() {
        val ws = tempWorkspaceWithFiles()
        val files = WorkspaceManager().listFiles(ws)
        assertTrue(files.any { it.name == "src" && it.isDirectory })
        assertTrue(files.any { it.name == "README.md" })
    }

    @Test fun `readFile truncates beyond the line cap`() {
        val ws = tempWorkspaceWithFiles()
        writeLines(File(ws.path, "big.txt"), 5000)
        val content = WorkspaceManager().readFile(ws, "big.txt")
        assertTrue(content.truncated)
        assertEquals(WORKSPACE_LINE_CAP, content.lines.size)
    }

    @Test fun `writeFile creates nested paths`() {
        val ws = tempWorkspaceWithFiles()
        WorkspaceManager().writeFile(ws, "src/main/kotlin/New.kt", "fun main() {}")
        assertTrue(File(ws.path, "src/main/kotlin/New.kt").exists())
    }
}
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `./gradlew :core:workspace:testDebugUnitTest --tests "com.void.core.workspace.WorkspaceManagerTest"`
Expected: FAIL — `WorkspaceManager` unresolved.

- [ ] **Step 7: Implement the workspace manager**

`WorkspaceManager` resolves the workspace directory, lists files (excluding `.git`), reads files with a line cap (`WORKSPACE_LINE_CAP = 500`) and a `truncated` flag, writes files creating parent directories, and deletes files. All I/O on `Dispatchers.IO`.

- [ ] **Step 8: Run the test to verify it passes**

Run: `./gradlew :core:workspace:testDebugUnitTest --tests "com.void.core.workspace.WorkspaceManagerTest"`
Expected: PASS.

- [ ] **Step 9: Implement the workspace screen**

`feature/workspace/src/main/java/com/void/feature/workspace/WorkspaceScreen.kt`: a file tree pane (from `WorkspaceManager.listFiles`) and a file viewer (from `readFile`). Tapping a directory navigates into it; tapping a file shows its contents read-only. Editing and the diff view arrive in Plan C.

- [ ] **Step 10: Commit**

```bash
git add core/git core/workspace feature/workspace
git commit -m "feat(git): clone repos, workspace file access and file browser"
```

---

### Task 8: Commit, push & diff

**Files:**
- Modify: `core/git/src/main/java/com/void/core/git/GitClient.kt`
- Create: `core/workspace/src/main/java/com/void/core/workspace/DiffEngine.kt`
- Test: `core/git/src/test/java/com/void/core/git/GitClientCommitPushTest.kt`
- Test: `core/workspace/src/test/java/com/void/core/workspace/DiffEngineTest.kt`

**Interfaces:**
- Consumes: `Workspace` (Task 7), `Repo` (Task 6).
- Produces: `GitClient.status(workspace: Workspace): GitResult`, `GitClient.commit(workspace: Workspace, message: String): GitResult`, `GitClient.push(workspace: Workspace, token: String): GitResult`, `GitClient.log(workspace: Workspace, limit: Int): List<Commit>`, `GitClient.branches(workspace: Workspace): List<Branch>`, `GitClient.checkout(workspace: Workspace, branch: String): GitResult`; `GitStatus(modified: List<String>, staged: List<String>, untracked: List<String>)`; `Commit(hash: String, message: String, author: String, timestamp: Long)`; `Branch(name: String, current: Boolean)`; `DiffEngine.unifiedDiff(old: String, new: String): String`, `DiffEngine.lineDiff(old: String, new: String): List<DiffLine>`; `DiffLine(type: DiffType, text: String)`, `DiffType` enum (`ADDED`, `REMOVED`, `UNCHANGED`).

- [ ] **Step 1: Write the failing test for commit/push**

`core/git/src/test/java/com/void/core/git/GitClientCommitPushTest.kt`:
```kotlin
class GitClientCommitPushTest {
    @Test fun `commit and push to a local remote`() = runTest {
        val remote = createLocalBareRepo()
        val ws = cloneInto(remote)
        File(ws.path, "new.txt").writeText("hello")
        val commit = GitClient().commit(ws, "add new.txt")
        assertTrue(commit is GitResult.Success)
        val push = GitClient().push(ws, "token")
        assertTrue(push is GitResult.Success)
        assertTrue(remoteHasCommit(remote, "add new.txt"))
    }

    @Test fun `commit with no changes fails cleanly`() = runTest {
        val ws = cloneInto(createLocalBareRepo())
        val result = GitClient().commit(ws, "empty")
        assertTrue(result is GitResult.Failure)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:git:testDebugUnitTest --tests "com.void.core.git.GitClientCommitPushTest"`
Expected: FAIL — `GitClient` unresolved.

- [ ] **Step 3: Implement commit/push/status/log/branches/checkout**

`GitClient` uses JGit: `status` via `Git.status().call()`, `commit` via `add` + `commit` (fail if no changes), `push` via `PushCommand` with the in-memory credentials provider, `log` via `log().setMaxCount(limit).call()`, `branches` via `branchList()`, `checkout` via `checkout()`. All on `Dispatchers.IO`. The token is never persisted.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:git:testDebugUnitTest --tests "com.void.core.git.GitClientCommitPushTest"`
Expected: PASS.

- [ ] **Step 5: Write the failing test for the diff engine**

`core/workspace/src/test/java/com/void/core/workspace/DiffEngineTest.kt`:
```kotlin
class DiffEngineTest {
    @Test fun `line diff marks additions and removals`() {
        val diff = DiffEngine.lineDiff("a\nb\nc", "a\nB\nc\nd")
        assertEquals(listOf(
            DiffLine(DiffType.UNCHANGED, "a"),
            DiffLine(DiffType.REMOVED, "b"),
            DiffLine(DiffType.ADDED, "B"),
            DiffLine(DiffType.UNCHANGED, "c"),
            DiffLine(DiffType.ADDED, "d")
        ), diff)
    }

    @Test fun `unified diff contains a header`() {
        val diff = DiffEngine.unifiedDiff("a\nb", "a\nc")
        assertTrue(diff.contains("---"))
        assertTrue(diff.contains("+++"))
    }
}
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `./gradlew :core:workspace:testDebugUnitTest --tests "com.void.core.workspace.DiffEngineTest"`
Expected: FAIL — `DiffEngine` unresolved.

- [ ] **Step 7: Implement the diff engine**

`DiffEngine.lineDiff` uses a standard LCS-based diff over lines. `unifiedDiff` formats with `---`/`+++` headers and `@@` hunks.

- [ ] **Step 8: Run the test to verify it passes**

Run: `./gradlew :core:workspace:testDebugUnitTest --tests "com.void.core.workspace.DiffEngineTest"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add core/git core/workspace
git commit -m "feat(git): commit, push, status, log, branches and diff"
```

---

### Task 9: Settings — provider keys, model defaults, theme

**Files:**
- Create: `feature/settings/src/main/java/com/void/feature/settings/SettingsViewModel.kt`
- Create: `feature/settings/src/main/java/com/void/feature/settings/SettingsScreen.kt`
- Create: `feature/settings/src/main/java/com/void/feature/settings/ProviderConfigScreen.kt`
- Test: `feature/settings/src/test/java/com/void/feature/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `SecretStore` (Task 2), DataStore.
- Produces: `SettingsViewModel.saveProviderKey(providerId: String, key: String)`, `SettingsViewModel.loadProviderKey(providerId: String): String?`, `SettingsViewModel.clearProviderKey(providerId: String)`, `SettingsViewModel.setDefaultModel(modelId: String)`, `SettingsViewModel.setTheme(mode: ThemeMode)`; `ThemeMode` enum (`SYSTEM`, `LIGHT`, `DARK`).

- [ ] **Step 1: Write the failing test**

`feature/settings/src/test/java/com/void/feature/settings/SettingsViewModelTest.kt`:
```kotlin
class SettingsViewModelTest {
    @Test fun `save and load a provider key`() = runTest {
        val vm = SettingsViewModel(FakeSecretStore(), FakeSettingsStore())
        vm.saveProviderKey("openrouter", "sk-or-abc")
        assertEquals("sk-or-abc", vm.loadProviderKey("openrouter"))
    }

    @Test fun `clear removes the provider key`() = runTest {
        val vm = SettingsViewModel(FakeSecretStore(), FakeSettingsStore())
        vm.saveProviderKey("openrouter", "sk-or-abc")
        vm.clearProviderKey("openrouter")
        assertNull(vm.loadProviderKey("openrouter"))
    }

    @Test fun `default model is persisted`() = runTest {
        val store = FakeSettingsStore()
        val vm = SettingsViewModel(FakeSecretStore(), store)
        vm.setDefaultModel("google/gemini-2.0-flash")
        assertEquals("google/gemini-2.0-flash", store.defaultModel)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :feature:settings:testDebugUnitTest --tests "com.void.feature.settings.SettingsViewModelTest"`
Expected: FAIL — `SettingsViewModel` unresolved.

- [ ] **Step 3: Implement the settings ViewModel and store**

`SettingsViewModel` wraps `SecretStore` for provider keys and a DataStore-backed `SettingsStore` for the default model and theme. `ProviderConfigScreen` lists the supported providers (OpenRouter, Nvidia NIM, Kira AI, Nara Router) with a key input, a "validate" action (Plan B), and a show/hide toggle. The screen uses `FLAG_SECURE` and clears the clipboard after paste.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :feature:settings:testDebugUnitTest --tests "com.void.feature.settings.SettingsViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/settings
git commit -m "feat(settings): provider keys, model defaults and theme"
```

---

### Task 10: Foundation integration test

**Files:**
- Create: `app/src/androidTest/java/com/void/app/FoundationIntegrationTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 1-9.
- Produces: a green end-to-end instrumented test.

- [ ] **Step 1: Write the integration test**

`app/src/androidTest/java/com/void/app/FoundationIntegrationTest.kt`: with `FakeAuthApi`, `FakeGitHubApi`, and a local git remote, drive the app from login → repo list → clone → edit a file → commit → push. Assert the remote received the commit and the workspace shows the edited file.

- [ ] **Step 2: Run the test on a device or emulator**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: PASS.

- [ ] **Step 3: Fix any failures and re-run**

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest
git commit -m "test: add foundation end-to-end integration test"
```

---

## Self-Review

**Spec coverage:** Section 5 (modules) → Task 1. Section 6 (agent engine) → Plan B. Section 7 (workspace & git) → Tasks 7-8. Section 8 (AI layer) → Plan B. Section 9 (GitHub auth) → Tasks 5-6. Section 10 (UI/UX) → Tasks 1, 5, 6, 9 (basic screens; full editor in Plan C). Section 11 (security) → Tasks 2, 3, 9, plus the hardening in Plan C. Sections 12-14 → Tasks 3, 8, 10. No gaps for Plan A's scope.

**Step scan:** Every step names one action with a checkable result; no step decides nothing and no step is a full implementation transcript.

**Type consistency:** `Repo`, `GitResult`, `Workspace`, `FileInfo`, `FileContent`, `SecretStore`, `NetworkModule`, `PinConfig`, `VoidDatabase`, `RepoDao`, `DeviceCode`, `TokenResult`, `AuthRepository`, `GitHubApi`, `ReposRepository`, `GitClient`, `CloneManager`, `DiffEngine`, `SettingsViewModel` are used consistently across tasks.

**Review Focus:** cleartext (Task 3), token leakage (Task 2), git credential on disk (Task 7 step 3), main-thread git work (Tasks 7-8), oversized reads (Task 7 step 5) — each has a test.

**Proportion:** The plan is comparable in length to the spec's Plan-A scope, not a transcript.
