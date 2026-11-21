package com.eivoid.feature.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clerk.ui.auth.AuthMode
import com.clerk.ui.auth.AuthView
import com.eivoid.designsystem.component.VoidBackground
import com.eivoid.designsystem.component.VoidCard
import com.eivoid.designsystem.component.VoidFeaturePill
import com.eivoid.designsystem.component.VoidLogo
import com.eivoid.designsystem.component.VoidPrimaryButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    showDebugSignIn: Boolean = false,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var authMode by rememberSaveable { mutableStateOf<AuthMode?>(null) }

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoggedIn()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VoidLogo(size = 136.dp)
            Spacer(Modifier.height(20.dp))

            Text(
                text = "VOID",
                style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 12.sp),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "A coding agent that lives on your phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            VoidCard(modifier = Modifier.fillMaxWidth()) {
                if (!viewModel.clerkConfigured) {
                    Text(
                        text = "Clerk is not configured",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Add clerk.publishableKey and void.backendUrl to local.properties, " +
                            "then rebuild.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "One account for every way in.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Continue with Discord, Google, email or GitHub. Sign in or create " +
                            "a single Clerk account; GitHub is linked once to clone and push.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    VoidPrimaryButton(
                        text = "Continue with Clerk",
                        onClick = { authMode = AuthMode.SignInOrUp },
                        enabled = !state.busy,
                    )
                    TextButton(
                        onClick = { authMode = AuthMode.SignUp },
                        enabled = !state.busy,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("Create an account")
                    }

                    if (state.busy) {
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (state.status.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = state.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (state.needsGithub) {
                Spacer(Modifier.height(16.dp))
                VoidCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Connect GitHub",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Your Clerk account is not linked to GitHub yet. Connect it once " +
                            "so Void can clone and push.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))

                    val portal = viewModel.accountPortalUrl
                    if (portal != null) {
                        VoidPrimaryButton(
                            text = "Open Clerk account",
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(portal)),
                                )
                            },
                        )
                    } else {
                        Text(
                            text = "Open your Clerk account in a browser and connect GitHub.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = viewModel::retryGithub,
                        enabled = !state.busy,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("I connected GitHub — retry")
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoidFeaturePill("Clone")
                VoidFeaturePill("Edit")
                VoidFeaturePill("Push")
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Private by default. Keys stay on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (showDebugSignIn) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = viewModel::debugSignIn) {
                    Text("Continue in debug mode")
                }
            }
        }

        val mode = authMode
        if (mode != null) {
            // The AuthView theme is transparent so the cosmic canvas continues
            // behind it. This wrapper is opaque, so the landing screen behind
            // it cannot bleed through.
            VoidBackground(
                darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                modifier = Modifier.fillMaxSize(),
            ) {
                AuthView(
                    modifier = Modifier.fillMaxSize(),
                    clerkTheme = voidClerkTheme(),
                    logo = { VoidLogo(size = 64.dp) },
                    persistIdentifiers = false,
                    isDismissible = true,
                    onDismiss = { authMode = null },
                    onAuthComplete = {
                        authMode = null
                        viewModel.onClerkAuthComplete()
                    },
                    mode = mode,
                )
            }
        }
    }
}
