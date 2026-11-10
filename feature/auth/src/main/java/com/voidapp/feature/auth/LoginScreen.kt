package com.voidapp.feature.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoggedIn()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Ei | Void",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "An AI coding agent that works on your repositories, from your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(36.dp))

        val deviceCode = state.deviceCode
        if (deviceCode == null) {
            Button(
                onClick = viewModel::startLogin,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign in with GitHub")
            }
        } else {
            Text(
                text = "YOUR CODE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = deviceCode.userCode,
                style = MaterialTheme.typography.displayLarge,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(deviceCode.verificationUri)),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open GitHub")
            }
            TextButton(
                onClick = { clipboard.setText(AnnotatedString(deviceCode.userCode)) },
            ) {
                Text("Copy code")
            }
        }

        Spacer(Modifier.height(24.dp))

        if (state.busy) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = state.status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
