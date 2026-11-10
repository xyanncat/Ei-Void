package com.voidapp.app.agent

import com.voidapp.core.agent.ProviderResolver
import com.voidapp.core.agent.ProviderTarget
import com.voidapp.core.ai.Model
import com.voidapp.core.ai.models.ModelCatalog
import com.voidapp.core.ai.openai.OpenAiCompatibleProvider
import com.voidapp.core.ai.openai.ProviderCatalog
import com.voidapp.core.security.SecretStore
import com.voidapp.feature.settings.SettingsStore
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns the stored provider keys into the providers the engine may use.
 *
 * The model for each provider comes from the Settings default model:
 *
 * - `providerId:modelId` selects that provider and model;
 * - a bare model id is used on the provider whose default model it is;
 * - otherwise the provider's built-in default applies.
 */
@Singleton
class SecretProviderResolver @Inject constructor(
    private val client: HttpClient,
    private val secretStore: SecretStore,
    private val settingsStore: SettingsStore,
) : ProviderResolver {

    override suspend fun targets(): List<ProviderTarget> = withContext(Dispatchers.IO) {
        val preference = settingsStore.defaultModel.value?.trim().orEmpty()
        val preferredProvider = preference.substringBefore(':', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() && ProviderCatalog.byId(it) != null }
        val preferredModel = if (preferredProvider != null) {
            preference.substringAfter(':').trim()
        } else {
            preference
        }

        ProviderCatalog.all.mapNotNull { config ->
            val key = secretStore.getProviderKey(config.providerId)
                ?: return@mapNotNull null
            if (key.isBlank()) return@mapNotNull null

            val modelId = when {
                preferredProvider == config.providerId && preferredModel.isNotBlank() ->
                    preferredModel
                preferredProvider == null && preferredModel == config.defaultModelId ->
                    preferredModel
                else -> config.defaultModelId
            } ?: return@mapNotNull null

            val model: Model = ModelCatalog.infer(modelId, config.providerId)
            ProviderTarget(OpenAiCompatibleProvider(client, config), model)
        }
    }

    override suspend fun keyFor(providerId: String): String? = withContext(Dispatchers.IO) {
        secretStore.getProviderKey(providerId)
    }
}
