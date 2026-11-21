package com.eivoid.core.common.github

import com.eivoid.core.security.SecretStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One repository as Void sees it. */
data class Repo(
    val id: String,
    val owner: String,
    val name: String,
    val fullName: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val description: String?,
    val updatedAt: Long,
    /** True when the user added it as one of their projects. */
    val isLinked: Boolean = false,
)

/** A GitHub request failed for a reason the user can act on. */
class GitHubApiException(message: String) : Exception(message)

interface GitHubApi {
    /** One page of the authenticated user's repositories, newest update first. */
    suspend fun listRepos(page: Int): List<Repo>

    /** Creates a repository for the authenticated user. */
    suspend fun createRepo(
        name: String,
        description: String?,
        isPrivate: Boolean,
    ): Repo

    /** Metadata of one repository, by `owner/name`. */
    suspend fun repoInfo(fullName: String): Repo
}

class KtorGitHubApi(
    private val client: HttpClient,
    private val secretStore: SecretStore,
) : GitHubApi {

    override suspend fun listRepos(page: Int): List<Repo> {
        val token = secretStore.getToken()
            ?: error("Not signed in to GitHub")

        val response = client.get("$BASE_URL/user/repos") {
            bearerAuth(token)
            parameter("per_page", PAGE_SIZE)
            parameter("page", page)
            parameter("sort", "updated")
            parameter("affiliation", "owner,collaborator,organization_member")
        }
        response.requireSuccess()

        return response.body<List<RepoDto>>().map { it.toRepo() }
    }

    override suspend fun createRepo(
        name: String,
        description: String?,
        isPrivate: Boolean,
    ): Repo {
        val token = secretStore.getToken()
            ?: error("Not signed in to GitHub")

        val response = client.post("$BASE_URL/user/repos") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("name", name)
                    put("private", isPrivate)
                    description?.let { put("description", it) }
                },
            )
        }
        response.requireSuccess()

        return response.body<RepoDto>().toRepo()
    }

    override suspend fun repoInfo(fullName: String): Repo {
        val token = secretStore.getToken()
            ?: error("Not signed in to GitHub")

        val response = client.get("$BASE_URL/repos/$fullName") {
            bearerAuth(token)
        }
        response.requireSuccess()

        return response.body<RepoDto>().toRepo()
    }

    /**
     * Rejects a failed request before the body is decoded. GitHub's error
     * bodies are machine JSON, not user-facing copy: mapping the status here
     * keeps deserializer text off the screen.
     */
    private fun HttpResponse.requireSuccess() {
        if (status.isSuccess()) return
        throw GitHubApiException(
            when (status.value) {
                401 -> "GitHub rejected the saved sign-in. Sign out and sign in again."
                403 -> "GitHub refused the request. The rate limit may be exhausted."
                404 -> "GitHub could not find that repository."
                in 500..599 -> "GitHub is unavailable right now. Try again in a moment."
                else -> "GitHub returned an unexpected status (${status.value})."
            },
        )
    }

    private companion object {
        const val BASE_URL = "https://api.github.com"
        const val PAGE_SIZE = 100
    }
}

@Serializable
private data class RepoDto(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("private") val isPrivate: Boolean = false,
    @SerialName("default_branch") val defaultBranch: String = "main",
    val description: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val owner: OwnerDto,
)

@Serializable
private data class OwnerDto(val login: String)

private fun RepoDto.toRepo() = Repo(
    id = id.toString(),
    owner = owner.login,
    name = name,
    fullName = fullName,
    isPrivate = isPrivate,
    defaultBranch = defaultBranch,
    description = description,
    updatedAt = updatedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
)
