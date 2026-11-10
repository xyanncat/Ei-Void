package com.voidapp.app.agent

import com.voidapp.core.agent.ProviderResolver
import com.voidapp.core.agent.ProviderTarget
import com.voidapp.core.ai.models.ModelCatalog
import com.voidapp.core.ai.openai.OpenAiCompatibleProvider
import com.voidapp.core.ai.openai.ProviderCatalog
import com.voidapp.core.security.SecretStore
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns the stored provider keys into the models the engine may use.
 *
 * The list is the curated catalog: every entry whose provider has a key
 * becomes a candidate, and the router picks the tier from there. A provider
 * without a key contributes nothing.
 */
@Singleton
class SecretProviderResolver @Inject constructor(
    private val client: HttpClient,
    private val secretStore: SecretStore,
) : ProviderResolver {

    override suspend fun targets(): List<ProviderTarget> = withContext(Dispatchers.IO) {
        val configs = ProviderCatalog.all.associateBy { it.providerId }

        ModelCatalog.all
            .sortedBy { it.rank }
            .mapNotNull { entry ->
                val config = configs[entry.providerId] ?: return@mapNotNull null
                val key = secretStore.getProviderKey(entry.providerId)
                    ?: return@mapNotNull null
                if (key.isBlank()) return@mapNotNull null

                ProviderTarget(
                    provider = OpenAiCompatibleProvider(client, config),
                    model = ModelCatalog.toModel(entry),
                )
            }
    }

    override suspend fun keyFor(providerId: String): String? = withContext(Dispatchers.IO) {
        secretStore.getProviderKey(providerId)
    }
}
