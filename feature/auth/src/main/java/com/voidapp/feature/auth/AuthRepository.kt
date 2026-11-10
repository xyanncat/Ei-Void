package com.voidapp.feature.auth

import com.voidapp.core.common.github.DeviceCode
import com.voidapp.core.common.github.GitHubAuthApi
import com.voidapp.core.common.github.TokenResult
import com.voidapp.core.security.SecretStore
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository @Inject constructor(
    private val api: GitHubAuthApi,
    private val secretStore: SecretStore,
) {

    suspend fun requestDeviceCode(): DeviceCode = withContext(Dispatchers.IO) {
        api.requestDeviceCode()
    }

    /**
     * Polls once. The token is persisted only on success; failures leave any
     * existing token untouched.
     */
    suspend fun pollForToken(deviceCode: DeviceCode): TokenResult = withContext(Dispatchers.IO) {
        val result = api.pollAccessToken(deviceCode.deviceCode)
        if (result is TokenResult.Success) {
            secretStore.saveToken(result.token)
        }
        result
    }

    fun isLoggedIn(): Boolean = !secretStore.getToken().isNullOrBlank()

    fun logout() {
        // Spec: every secret is cleared on logout.
        secretStore.clearAll()
    }
}
