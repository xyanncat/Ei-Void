package com.eivoid.feature.repos

import com.eivoid.core.ai.plan.Plan
import com.eivoid.core.ai.plan.PlanProvider
import com.eivoid.core.common.github.GitHubApi
import com.eivoid.core.common.github.Repo
import com.eivoid.core.database.RepoDao
import com.eivoid.core.database.RepoEntity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The result of linking a project. */
sealed interface LinkResult {
    data object Linked : LinkResult
    data class LimitReached(val plan: Plan) : LinkResult
}

class ReposRepository @Inject constructor(
    private val api: GitHubApi,
    private val dao: RepoDao,
    private val planProvider: PlanProvider,
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
        // The API has no notion of a linked project, so read the local flags
        // before the REPLACE insert and carry them over; otherwise every
        // refresh would silently unlink everything.
        val linked = dao.linkedIds().toSet()
        dao.insertAll(fetched.map { repo -> repo.copy(isLinked = repo.id in linked).toEntity() })
        // Repositories that disappeared upstream must not stay in the cache:
        // their names can be private.
        if (fetched.isEmpty()) {
            dao.deleteAll()
        } else {
            dao.deleteNotIn(fetched.map { it.id })
        }
        fetched
    }

    fun observeRepos(): Flow<List<Repo>> =
        dao.observeAll().map { entities -> entities.map { it.toRepo() } }

    fun observeLinked(): Flow<List<Repo>> =
        dao.observeAll().map { entities ->
            entities.filter { it.isLinked }.map { it.toRepo() }
        }

    fun search(query: String): Flow<List<Repo>> =
        dao.search(query).map { entities -> entities.map { it.toRepo() } }

    /** Adds a repository to the user's projects, if the plan has room. */
    suspend fun link(id: String): LinkResult = withContext(Dispatchers.IO) {
        val plan = planProvider.current()
        val linked = dao.linkedCount()
        if (linked >= plan.maxProjects) {
            return@withContext LinkResult.LimitReached(plan)
        }
        dao.setLinked(id, true)
        LinkResult.Linked
    }

    /** Removes a repository from the user's projects. */
    suspend fun unlink(id: String) = withContext(Dispatchers.IO) {
        dao.setLinked(id, false)
    }

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
    isLinked = isLinked,
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
    isLinked = isLinked,
)
