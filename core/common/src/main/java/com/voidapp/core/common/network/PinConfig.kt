package com.voidapp.core.common.network

/**
 * A host and the SHA-256 pins its certificate chain must match.
 *
 * Pin format follows OkHttp: `sha256/<base64 of the 32-byte SubjectPublicKeyInfo digest>`.
 */
data class PinConfig(
    val host: String,
    val pins: List<String>,
)
