package com.voidapp.core.common.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkModule {

    /**
     * Builds the single HTTP client the app uses.
     *
     * - [ConnectionSpec.MODERN_TLS] accepts TLS 1.3 and TLS 1.2 only, with
     *   modern cipher suites. TLS 1.2 stays as the fallback for devices whose
     *   provider cannot negotiate 1.3. Cleartext is never in this spec.
     * - Certificate pinning is applied when [pins] is non-empty. An empty
     *   list means "use the platform trust store" and is only acceptable
     *   before release pins are configured.
     */
    fun provideOkHttpClient(pins: List<PinConfig>): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)

        if (pins.isNotEmpty()) {
            val pinner = CertificatePinner.Builder()
            pins.forEach { config ->
                config.pins.forEach { pin ->
                    pinner.add(config.host, pin)
                }
            }
            builder.certificatePinner(pinner.build())
        }

        return builder.build()
    }

    fun provideKtorClient(okHttp: OkHttpClient): HttpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttp
        }
        expectSuccess = false
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }
}
