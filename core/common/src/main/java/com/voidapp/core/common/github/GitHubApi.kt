package com.voidapp.core.common.github

import com.voidapp.core.security.SecretStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A repository as Void sees it. */
data class Repo(
    val id: String,
    val owner: String,
    val name: String,
    val fullName: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val description: String?,
    val updatedAt: Long,
)

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

        val response: List<RepoDto> = client.get("$BASE_URL/user/repos") {
            bearerAuth(token)
            parameter("per_page", PAGE_SIZE)
            parameter("page", page)
            parameter("sort", "updated")
            parameter("affiliation", "owner,collaborator,organization_member")
        }.body()

        return response.map { it.toRepo() }
    }

    override suspend fun createRepo(
        name: String,
        description: String?,
        isPrivate: Boolean,
    ): Repo {
        val token = secretStore.getToken()
            ?: error("Not signed in to GitHub")

        val response: RepoDto = client.post("$BASE_URL/user/repos") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("name", name)
                    put("private", isPrivate)
                    description?.let { put("description", it) }
                },
            )
        }.body()

        return response.toRepo()
    }

    override suspend fun repoInfo(fullName: String): Repo {
        val token = secretStore.getToken()
            ?: error("Not signed in to GitHub")

        val response: RepoDto = client.get("$BASE_URL/repos/$fullName") {
            bearerAuth(token)
        }.body()

        return response.toRepo()
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
