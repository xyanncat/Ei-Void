package com.voidapp.core.common.network

import java.util.Base64
import okhttp3.ConnectionSpec
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinConfigTest {

    // A syntactically valid pin: sha256/ + base64 of 32 zero bytes.
    private val zeroPin: String =
        "sha256/" + Base64.getEncoder().encodeToString(ByteArray(32))

    @Test
    fun `pins are applied to the client`() {
        val pins = listOf(PinConfig("api.github.com", listOf(zeroPin)))

        val client = NetworkModule.provideOkHttpClient(pins)

        assertNotNull(client.certificatePinner)
        assertEquals(1, client.certificatePinner.pins.size)
    }

    @Test
    fun `no pins leaves the platform trust store in charge`() {
        val client = NetworkModule.provideOkHttpClient(emptyList())

        assertTrue(client.certificatePinner.pins.isEmpty())
    }

    @Test
    fun `cleartext is not an allowed connection spec`() {
        val client = NetworkModule.provideOkHttpClient(emptyList())

        assertTrue(client.connectionSpecs.isNotEmpty())
        assertFalse(client.connectionSpecs.contains(ConnectionSpec.CLEARTEXT))
    }

    @Test
    fun `an http url is not reachable through the pinned client`() {
        val client = NetworkModule.provideOkHttpClient(
            listOf(PinConfig("api.github.com", listOf(zeroPin))),
        )

        val cleartextRequest = Request.Builder().url("http://api.github.com/user").build()

        // Building the request is fine; the connection specs are what forbid cleartext.
        assertEquals("http", cleartextRequest.url.scheme)
        assertFalse(client.connectionSpecs.contains(ConnectionSpec.CLEARTEXT))
    }
}
