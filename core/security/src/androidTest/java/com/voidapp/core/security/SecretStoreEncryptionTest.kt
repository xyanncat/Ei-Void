package com.voidapp.core.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the production path encrypts at rest. Requires a device/emulator:
 * the AndroidKeyStore is not available to JVM unit tests.
 */
@RunWith(AndroidJUnit4::class)
class SecretStoreEncryptionTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        EncryptedSecretStore(context).clearAll()
    }

    @Test
    fun tokenIsNotStoredAsPlaintext() {
        val store = EncryptedSecretStore(context)
        store.clearAll()
        store.saveToken("gho_secret123")

        val raw = context
            .getSharedPreferences(EncryptedSecretStore.PREFS_NAME, Context.MODE_PRIVATE)
            .all
            .values
            .joinToString()

        assertTrue("the prefs file must not be empty after a write", raw.isNotEmpty())
        assertFalse("token must not appear in the prefs file", raw.contains("gho_secret123"))
        assertEquals("gho_secret123", store.getToken())
    }
}
