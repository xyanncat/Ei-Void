package com.voidapp.feature.auth

import com.voidapp.core.common.github.DeviceCode
import com.voidapp.core.common.github.GitHubAuthApi
import com.voidapp.core.common.github.TokenResult
import com.voidapp.core.security.SecretStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {

    @Test
    fun `device code request returns a code`() = runTest {
        val api = FakeAuthApi(
            deviceCode = DeviceCode(
                "dc",
                "UC-123",
                "https://github.com/login/device",
                900,
                5,
            ),
        )
        val repository = AuthRepository(api, FakeSecretStore())

        val code = repository.requestDeviceCode()

        assertEquals("UC-123", code.userCode)
    }

    @Test
    fun `poll success stores the token`() = runTest {
        val store = FakeSecretStore()
        val repository = AuthRepository(FakeAuthApi(token = "gho_abc"), store)

        val result = repository.pollForToken(DeviceCode("dc", "UC-123", "uri", 900, 0))

        assertEquals("gho_abc", (result as TokenResult.Success).token)
        assertEquals("gho_abc", store.getToken())
        assertTrue(repository.isLoggedIn())
    }

    @Test
    fun `pending does not store a token`() = runTest {
        val store = FakeSecretStore()
        val repository = AuthRepository(
            FakeAuthApi(result = TokenResult.AuthorizationPending),
            store,
        )

        val result = repository.pollForToken(DeviceCode("dc", "UC-123", "uri", 900, 0))

        assertEquals(TokenResult.AuthorizationPending, result)
        assertNull(store.getToken())
        assertFalse(repository.isLoggedIn())
    }

    @Test
    fun `logout clears the token`() = runTest {
        val store = FakeSecretStore()
        val repository = AuthRepository(FakeAuthApi(token = "gho_abc"), store)
        repository.pollForToken(DeviceCode("dc", "UC-123", "uri", 900, 0))

        repository.logout()

        assertFalse(repository.isLoggedIn())
        assertNull(store.getToken())
    }

    private class FakeAuthApi(
        private val deviceCode: DeviceCode = DeviceCode("dc", "UC", "uri", 900, 5),
        private val token: String? = null,
        private val result: TokenResult = TokenResult.AuthorizationPending,
    ) : GitHubAuthApi {
        override suspend fun requestDeviceCode(): DeviceCode = deviceCode

        override suspend fun pollAccessToken(deviceCode: String): TokenResult =
            token?.let { TokenResult.Success(it) } ?: result
    }

    private class FakeSecretStore : SecretStore {
        private var token: String? = null
        private val providerKeys = mutableMapOf<String, String>()

        override fun saveToken(token: String) {
            this.token = token
        }

        override fun getToken(): String? = token

        override fun clearToken() {
            token = null
        }

        override fun saveProviderKey(providerId: String, key: String) {
            providerKeys[providerId] = key
        }

        override fun getProviderKey(providerId: String): String? = providerKeys[providerId]

        override fun clearProviderKey(providerId: String) {
            providerKeys.remove(providerId)
        }

        override fun clearAll() {
            token = null
            providerKeys.clear()
        }
    }
}
