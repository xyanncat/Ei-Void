package com.eivoid.feature.auth

import com.eivoid.core.security.SecretStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {

    @Test
    fun `there is no session without a token`() {
        val repository = AuthRepository(FakeSecretStore())

        assertFalse(repository.isLoggedIn())
    }

    @Test
    fun `debug sign in stores a local token`() {
        val store = FakeSecretStore()
        val repository = AuthRepository(store)

        repository.debugSignIn()

        assertTrue(repository.isLoggedIn())
        assertTrue(!store.getToken().isNullOrBlank())
    }

    @Test
    fun `logout clears the token`() {
        val store = FakeSecretStore()
        val repository = AuthRepository(store)
        repository.debugSignIn()

        repository.logout()

        assertFalse(repository.isLoggedIn())
        assertNull(store.getToken())
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
