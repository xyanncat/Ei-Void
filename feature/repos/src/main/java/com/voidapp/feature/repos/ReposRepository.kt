package com.voidapp.feature.repos

import com.voidapp.core.common.github.GitHubApi
import com.voidapp.core.common.github.Repo
import com.voidapp.core.database.RepoDao
import com.voidapp.core.database.RepoEntity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ReposRepository @Inject constructor(
    private val api: GitHubApi,
    private val dao: RepoDao,
) {

    /**
     * Pages through every repository and mirrors it into Room. Returns the
     * full list so callers can show a result without waiting for the write.
     */
    suspend fun refreshRepos(): List<Repo> = withContext(Dispatchers.IO) {
        val fetched = mutableListOf<Repo>()
        var page = 1
        while (page <= MAX_PAGES) {
            val batch = api.listRepos(page)
            fetched += batch
            if (batch.size < PAGE_SIZE) break
            page++
        }
        dao.insertAll(fetched.map { it.toEntity() })
        fetched
    }

    fun observeRepos(): Flow<List<Repo>> =
        dao.observeAll().map { entities -> entities.map { it.toRepo() } }

    fun search(query: String): Flow<List<Repo>> =
        dao.search(query).map { entities -> entities.map { it.toRepo() } }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 100
    }
}

internal fun Repo.toEntity() = RepoEntity(
    id = id,
    owner = owner,
    name = name,
    fullName = fullName,
    isPrivate = isPrivate,
    defaultBranch = defaultBranch,
    description = description,
    updatedAt = updatedAt,
)

internal fun RepoEntity.toRepo() = Repo(
    id = id,
    owner = owner,
    name = name,
    fullName = fullName,
    isPrivate = isPrivate,
    defaultBranch = defaultBranch,
    description = description,
    updatedAt = updatedAt,
)
