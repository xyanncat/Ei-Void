package com.eivoid.feature.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Discord OAuth2 with PKCE, used only to link a Discord account for Ei Snore
 * reminder notifications.
 *
 * The app never uses this as its login. The Clerk session remains the app's
 * identity; this activity returns a short-lived Discord access token that the
 * backend verifies and exchanges for a stored Discord user id.
 *
 * The redirect URI must be registered in the Discord Developer Portal as
 * `${EI_VOID_API_URL}/discord-callback`, and the application must have the
 * Public Client setting enabled. No client secret lives in the APK.
 */
class DiscordAuthActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var handled = false
    private lateinit var state: String
    private lateinit var codeVerifier: String

    private val clientId: String get() = BuildConfig.DISCORD_CLIENT_ID.trim()
    private val redirectUri: String
        get() = BuildConfig.EI_VOID_API_URL.trimEnd('/') + REDIRECT_PATH

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (clientId.isBlank()) {
            finishWithError(
                "Discord is not configured in this build. Add discord.clientId to local.properties.",
            )
            return
        }
        if (!BuildConfig.EI_VOID_API_URL.startsWith("https://")) {
            finishWithError(
                "The Ei Void API URL is not configured. Add ei-void.apiUrl to local.properties.",
            )
            return
        }

        state = randomUrlSafe(32)
        codeVerifier = randomUrlSafe(64)
        val challenge = sha256Base64Url(codeVerifier)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val progress = ProgressBar(this).apply { isIndeterminate = true }
        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean = handleRedirect(request?.url?.toString().orEmpty())

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: android.graphics.Bitmap?,
                ) {
                    if (handleRedirect(url.orEmpty())) return
                    progress.visibility = android.view.View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progress.visibility = android.view.View.GONE
                }
            }
        }
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            progress,
            FrameLayout.LayoutParams(96, 96).apply { gravity = Gravity.CENTER },
        )
        setContentView(root)

        webView.loadUrl(authorizationUrl(challenge))
    }

    private fun authorizationUrl(challenge: String): String {
        fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        return "https://discord.com/oauth2/authorize" +
            "?response_type=code" +
            "&client_id=${enc(clientId)}" +
            "&redirect_uri=${enc(redirectUri)}" +
            "&scope=identify%20guilds" +
            "&state=${enc(state)}" +
            "&code_challenge=${enc(challenge)}" +
            "&code_challenge_method=S256" +
            "&prompt=consent"
    }

    private fun handleRedirect(url: String): Boolean {
        if (handled || !url.startsWith(redirectUri)) return false
        handled = true

        val uri = Uri.parse(url)
        val error = uri.getQueryParameter("error")
        if (error != null) {
            finishWithError("Discord authorization was cancelled ($error).")
            return true
        }
        if (uri.getQueryParameter("state") != state) {
            finishWithError("Discord returned a mismatched state. Try again.")
            return true
        }
        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            finishWithError("Discord did not return an authorization code.")
            return true
        }

        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { exchangeCode(code) } }
                .onSuccess { identity ->
                    setResult(
                        Activity.RESULT_OK,
                        Intent()
                            .putExtra(EXTRA_ACCESS_TOKEN, identity.accessToken)
                            .putExtra(EXTRA_USER_ID, identity.id)
                            .putExtra(EXTRA_USERNAME, identity.username),
                    )
                    finish()
                }
                .onFailure { error ->
                    finishWithError(error.message ?: "Discord linking failed.")
                }
        }
        return true
    }

    private fun exchangeCode(code: String): DiscordIdentity {
        val body = listOf(
            "client_id" to clientId,
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to redirectUri,
            "code_verifier" to codeVerifier,
        ).joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }

        val tokenConnection =
            (URL("https://discord.com/api/v10/oauth2/token").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
        try {
            tokenConnection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val codeStatus = tokenConnection.responseCode
            val text = (if (codeStatus in 200..299) tokenConnection.inputStream else tokenConnection.errorStream)
                ?.bufferedReader()?.use { it.readText() }
                .orEmpty()
            if (codeStatus !in 200..299) {
                throw IllegalStateException("Discord token request failed (HTTP $codeStatus).")
            }
            val tokenJson = JSONObject(text)
            val token = tokenJson.optString("access_token")
            if (token.isBlank()) throw IllegalStateException("Discord returned no access token.")

            val userConnection =
                (URL("https://discord.com/api/v10/users/@me").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    setRequestProperty("Authorization", "Bearer $token")
                }
            try {
                val userStatus = userConnection.responseCode
                val userText = (if (userStatus in 200..299) userConnection.inputStream else userConnection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }
                    .orEmpty()
                if (userStatus !in 200..299) {
                    throw IllegalStateException("Discord user request failed (HTTP $userStatus).")
                }
                val user = JSONObject(userText)
                val id = user.optString("id")
                if (id.isBlank()) throw IllegalStateException("Discord returned no user id.")
                val username = if (user.isNull("global_name")) {
                    user.optString("username")
                } else {
                    user.optString("global_name").ifBlank { user.optString("username") }
                }
                return DiscordIdentity(
                    id = id,
                    username = username.ifBlank { "Discord user" },
                    accessToken = token,
                )
            } finally {
                userConnection.disconnect()
            }
        } finally {
            tokenConnection.disconnect()
        }
    }

    private fun finishWithError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(EXTRA_ERROR, message),
        )
        finish()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    private fun randomUrlSafe(bytes: Int): String {
        val data = ByteArray(bytes)
        SecureRandom().nextBytes(data)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }

    private fun sha256Base64Url(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private data class DiscordIdentity(
        val id: String,
        val username: String,
        val accessToken: String,
    )

    companion object {
        const val EXTRA_ACCESS_TOKEN = "discord_access_token"
        const val EXTRA_USER_ID = "discord_user_id"
        const val EXTRA_USERNAME = "discord_username"
        const val EXTRA_ERROR = "discord_error"
        private const val REDIRECT_PATH = "/discord-callback"
    }
}
