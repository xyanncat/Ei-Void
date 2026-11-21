package com.eivoid.feature.repos

import com.eivoid.core.ai.plan.Plan
import com.eivoid.core.ai.plan.PlanProvider
import com.eivoid.core.common.github.GitHubApi
import com.eivoid.core.common.github.Repo
import com.eivoid.core.database.RepoDao
import com.eivoid.core.database.RepoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReposRepositoryTest {

    private val plan = Plan.FREE

    @Test
    fun `refresh maps API repos and stores them`() = runTest {
        val api = FakeGitHubApi(
            listOf(Repo("1", "owner", "repo", "owner/repo", false, "main", null, 0L)),
        )
        val repository = ReposRepository(api, FakeDao(), planProvider())

        val result = repository.refreshRepos()

        assertEquals(listOf("owner/repo"), result.map { it.fullName })
    }

    @Test
    fun `refresh writes to the dao`() = runTest {
        val dao = FakeDao()
        val api = FakeGitHubApi(
            listOf(
                Repo("1", "owner", "alpha", "owner/alpha", false, "main", null, 0L),
                Repo("2", "owner", "beta", "owner/beta", true, "dev", null, 0L),
            ),
        )
        val repository = ReposRepository(api, dao, planProvider())

        repository.refreshRepos()

        assertEquals(2, dao.observeAll().first().size)
    }

    @Test
    fun `search filters by name`() = runTest {
        val dao = FakeDao(
            listOf(
                Repo("1", "owner", "alpha", "owner/alpha", false, "main", null, 0L),
                Repo("2", "owner", "beta", "owner/beta", false, "main", null, 0L),
            ),
        )
        val repository = ReposRepository(FakeGitHubApi(), dao, planProvider())

        val result = repository.search("alp").first()

        assertEquals(listOf("alpha"), result.map { it.name })
    }

    @Test
    fun `refresh stops after a short page`() = runTest {
        val api = FakeGitHubApi(listOf(Repo("1", "owner", "repo", "owner/repo", false, "main", null, 0L)))
        val repository = ReposRepository(api, FakeDao(), planProvider())

        repository.refreshRepos()

        assertEquals(1, api.calls)
    }

    @Test
    fun `observeRepos is empty before a refresh`() = runTest {
        val repository = ReposRepository(FakeGitHubApi(), FakeDao(), planProvider())

        assertTrue(repository.observeRepos().first().isEmpty())
    }

    @Test
    fun `refresh drops repositories the account no longer has`() = runTest {
        val dao = FakeDao()
        val first = ReposRepository(
            FakeGitHubApi(listOf(Repo("1", "owner", "alpha", "owner/alpha", false, "main", null, 0L))),
            dao,
            planProvider(),
        )
        first.refreshRepos()

        val second = ReposRepository(
            FakeGitHubApi(listOf(Repo("2", "owner", "beta", "owner/beta", false, "main", null, 0L))),
            dao,
            planProvider(),
        )
        second.refreshRepos()

        assertEquals(listOf("owner/beta"), dao.observeAll().first().map { it.fullName })
    }

    @Test
    fun `an empty account clears the cache`() = runTest {
        val dao = FakeDao()
        ReposRepository(
            FakeGitHubApi(listOf(Repo("1", "owner", "alpha", "owner/alpha", false, "main", null, 0L))),
            dao,
            planProvider(),
        ).refreshRepos()

        ReposRepository(FakeGitHubApi(), dao, planProvider()).refreshRepos()

        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun `linking marks the repository as a project`() = runTest {
        val dao = FakeDao()
        val repository = ReposRepository(
            FakeGitHubApi(listOf(Repo("1", "owner", "alpha", "owner/alpha", false, "main", null, 0L))),
            dao,
            planProvider(),
        )
        repository.refreshRepos()

        val result = repository.link("1")

        assertEquals(LinkResult.Linked, result)
        assertEquals(listOf("owner/alpha"), repository.observeLinked().first().map { it.fullName })
    }

    @Test
    fun `the free plan stops at seven projects`() = runTest {
        val dao = FakeDao()
        val repository = ReposRepository(FakeGitHubApi(), dao, planProvider(Plan.FREE))
        repeat(7) { index -> dao.insertAll(listOf(entity(index.toString(), linked = true))) }

        val result = repository.link("8")

        assertTrue(result is LinkResult.LimitReached)
        assertEquals(Plan.FREE, (result as LinkResult.LimitReached).plan)
    }

    @Test
    fun `refresh keeps linked projects linked`() = runTest {
        val dao = FakeDao()
        val api = FakeGitHubApi(
            listOf(Repo("1", "owner", "alpha", "owner/alpha", false, "main", null, 0L)),
        )
        val repository = ReposRepository(api, dao, planProvider())
        repository.refreshRepos()
        repository.link("1")

        repository.refreshRepos()

        assertEquals(listOf("owner/alpha"), repository.observeLinked().first().map { it.fullName })
    }

    @Test
    fun `unlinking frees a slot`() = runTest {
        val dao = FakeDao()
        val repository = ReposRepository(FakeGitHubApi(), dao, planProvider(Plan.FREE))
        repeat(7) { index -> dao.insertAll(listOf(entity(index.toString(), linked = true))) }

        repository.unlink("0")

        assertEquals(LinkResult.Linked, repository.link("8"))
    }

    private fun entity(id: String, linked: Boolean = false) = RepoEntity(
        id = id,
        owner = "owner",
        name = "repo$id",
        fullName = "owner/repo$id",
        isPrivate = false,
        defaultBranch = "main",
        description = null,
        updatedAt = 0L,
        isLinked = linked,
    )

    private fun planProvider(current: Plan = plan) = object : PlanProvider {
        override fun current(): Plan = current
    }

    private class FakeGitHubApi(
        private val repos: List<Repo> = emptyList(),
    ) : GitHubApi {
        var calls: Int = 0
            private set

        override suspend fun listRepos(page: Int): List<Repo> {
            calls++
            return if (page == 1) repos else emptyList()
        }

        override suspend fun createRepo(
            name: String,
            description: String?,
            isPrivate: Boolean,
        ): Repo = Repo(
            id = "new",
            owner = "owner",
            name = name,
            fullName = "owner/$name",
            isPrivate = isPrivate,
            defaultBranch = "main",
            description = description,
            updatedAt = 0L,
        )

        override suspend fun repoInfo(fullName: String): Repo =
            repos.first { it.fullName == fullName }
    }

    private class FakeDao(initial: List<Repo> = emptyList()) : RepoDao {
        private val state = MutableStateFlow(initial.map { it.toEntity() })

        override suspend fun insertAll(repos: List<RepoEntity>) {
            // Upsert, like the real @Insert(REPLACE) query.
            val merged = state.value.associateBy { it.id }.toMutableMap()
            repos.forEach { merged[it.id] = it }
            state.value = merged.values.toList()
        }

        override fun observeAll(): Flow<List<RepoEntity>> = state.map { it }

        override fun search(query: String): Flow<List<RepoEntity>> = state.map { repos ->
            repos.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.fullName.contains(query, ignoreCase = true)
            }
        }

        override suspend fun deleteAll() {
            state.value = emptyList()
        }

        override suspend fun deleteNotIn(ids: List<String>) {
            state.value = state.value.filter { it.id in ids }
        }

        override fun observeLinked(): Flow<List<RepoEntity>> =
            state.map { all -> all.filter { it.isLinked } }

        override suspend fun linkedCount(): Int = state.value.count { it.isLinked }

        override suspend fun linkedIds(): List<String> =
            state.value.filter { it.isLinked }.map { it.id }

        override suspend fun setLinked(id: String, linked: Boolean) {
            state.value = state.value.map { entity ->
                if (entity.id == id) entity.copy(isLinked = linked) else entity
            }
        }
    }
}
