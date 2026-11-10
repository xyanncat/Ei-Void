package com.voidapp.feature.repos

import com.voidapp.core.common.github.GitHubApi
import com.voidapp.core.common.github.Repo
import com.voidapp.core.database.RepoDao
import com.voidapp.core.database.RepoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReposRepositoryTest {

    @Test
    fun `refresh maps API repos and stores them`() = runTest {
        val api = FakeGitHubApi(
            listOf(Repo("1", "owner", "repo", "owner/repo", false, "main", null, 0L)),
        )
        val repository = ReposRepository(api, FakeDao())

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
        val repository = ReposRepository(api, dao)

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
        val repository = ReposRepository(FakeGitHubApi(), dao)

        val result = repository.search("alp").first()

        assertEquals(listOf("alpha"), result.map { it.name })
    }

    @Test
    fun `refresh stops after a short page`() = runTest {
        val api = FakeGitHubApi(listOf(Repo("1", "owner", "repo", "owner/repo", false, "main", null, 0L)))
        val repository = ReposRepository(api, FakeDao())

        repository.refreshRepos()

        assertEquals(1, api.calls)
    }

    @Test
    fun `observeRepos is empty before a refresh`() = runTest {
        val repository = ReposRepository(FakeGitHubApi(), FakeDao())

        assertTrue(repository.observeRepos().first().isEmpty())
    }

    @Test
    fun `refresh drops repositories the account no longer has`() = runTest {
        val dao = FakeDao()
        val first = ReposRepository(
            FakeGitHubApi(listOf(Repo("1", "owner", "alpha", "owner/alpha", false, "main", null, 0L))),
            dao,
        )
        first.refreshRepos()

        val second = ReposRepository(
            FakeGitHubApi(listOf(Repo("2", "owner", "beta", "owner/beta", false, "main", null, 0L))),
            dao,
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
        ).refreshRepos()

        ReposRepository(FakeGitHubApi(), dao).refreshRepos()

        assertTrue(dao.observeAll().first().isEmpty())
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
    }
}
