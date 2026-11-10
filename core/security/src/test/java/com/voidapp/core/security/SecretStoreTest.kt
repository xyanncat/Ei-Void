package com.voidapp.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Contract tests for the storage keys and clearing behaviour. The
 * encryption-at-rest property needs a real AndroidKeyStore, so it is
 * covered by SecretStoreEncryptionTest (instrumented).
 */
@RunWith(RobolectricTestRunner::class)
class SecretStoreTest {

    private lateinit var store: SecretStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = SecretStore(context.getSharedPreferences("secret_store_test", Context.MODE_PRIVATE))
        store.clearAll()
    }

    @Test
    fun `save and read token`() {
        store.saveToken("gho_secret123")
        assertEquals("gho_secret123", store.getToken())
    }

    @Test
    fun `clearAll removes token and provider keys`() {
        store.saveToken("gho_secret123")
        store.saveProviderKey("openrouter", "sk-or-abc")
        store.clearAll()
        assertNull(store.getToken())
        assertNull(store.getProviderKey("openrouter"))
    }

    @Test
    fun `provider keys are stored per provider`() {
        store.saveProviderKey("openrouter", "sk-or-abc")
        store.saveProviderKey("nvidia", "nvapi-xyz")
        assertEquals("sk-or-abc", store.getProviderKey("openrouter"))
        assertEquals("nvapi-xyz", store.getProviderKey("nvidia"))

        store.clearProviderKey("openrouter")
        assertNull(store.getProviderKey("openrouter"))
        assertEquals("nvapi-xyz", store.getProviderKey("nvidia"))
    }

    @Test
    fun `clearToken leaves provider keys intact`() {
        store.saveToken("gho_secret123")
        store.saveProviderKey("openrouter", "sk-or-abc")
        store.clearToken()
        assertNull(store.getToken())
        assertEquals("sk-or-abc", store.getProviderKey("openrouter"))
    }
}
