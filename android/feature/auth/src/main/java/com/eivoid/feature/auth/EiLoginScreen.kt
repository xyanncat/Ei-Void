package com.eivoid.feature.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clerk.ui.auth.AuthMode
import com.clerk.ui.auth.AuthView
import com.eivoid.designsystem.component.EiCard
import com.eivoid.designsystem.component.EiDimens
import com.eivoid.designsystem.component.EiDivider
import com.eivoid.designsystem.component.EiNotice
import com.eivoid.designsystem.component.EiPrimaryButton
import com.eivoid.designsystem.component.EiScreen
import com.eivoid.designsystem.component.EiSecondaryButton
import com.eivoid.designsystem.component.EiSectionLabel
import com.eivoid.designsystem.theme.VoidColors

/**
 * The Ei Void entry screen.
 *
 * Pure OLED black, a single white line-art mark, one blue action. The Clerk
 * [AuthView] still owns the actual sign-in flow and slides over this screen
 * as an opaque overlay; everything here is the landing surface around it.
 */
@Composable
fun EiLoginScreen(
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
        EiScreen {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = EiDimens.screenPadding, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 440.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(20.dp))
                    EiMark(size = 108.dp)

                    Spacer(Modifier.height(26.dp))
                    Text(
                        text = "Ei Void",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-2).sp,
                        ),
                        color = VoidColors.oledTextPrimary,
                        maxLines = 1,
                    )

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Your assistant, your reminders, your code.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = VoidColors.oledTextSecondary,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(32.dp))
                    FeatureList()

                    Spacer(Modifier.height(28.dp))

                    if (!viewModel.clerkConfigured) {
                        SetupCard()
                    } else {
                        SignInCard(
                            busy = state.busy,
                            onContinue = { authMode = AuthMode.SignInOrUp },
                            onCreateAccount = { authMode = AuthMode.SignUp },
                        )
                    }

                    if (state.status.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        EiNotice(
                            message = state.status,
                            danger = state.needsGithub,
                        )
                    }

                    if (state.needsGithub) {
                        Spacer(Modifier.height(12.dp))
                        GithubCard(
                            accountPortalUrl = viewModel.accountPortalUrl,
                            busy = state.busy,
                            onOpenPortal = { url ->
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                )
                            },
                            onRetry = viewModel::retryGithub,
                        )
                    }

                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = "Ei Void sign-in is powered by Clerk. Discord is connected " +
                            "later in Customize for Ei Snore notifications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VoidColors.oledTextSecondary,
                        textAlign = TextAlign.Center,
                    )

                    if (showDebugSignIn) {
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(
                                    enabled = !state.busy,
                                    onClickLabel = "Continue in debug mode",
                                    onClick = viewModel::debugSignIn,
                                )
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Continue in debug mode",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = VoidColors.oledAccentBlue,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        val mode = authMode
        if (mode != null) {
            // AuthView's own theme background is transparent, so the overlay
            // paints the same OLED black canvas as the screen beneath it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VoidColors.oledBlack),
            ) {
                AuthView(
                    modifier = Modifier.fillMaxSize(),
                    clerkTheme = voidClerkTheme(),
                    logo = { EiMark(size = 56.dp) },
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

/** The primary surface: one Clerk account, then the app. */
@Composable
private fun SignInCard(
    busy: Boolean,
    onContinue: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    EiCard {
        EiSectionLabel(text = "Sign in")
        Spacer(Modifier.height(10.dp))
        Text(
            text = "One account for every way in.",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = VoidColors.oledTextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Continue with Discord, Google, email or GitHub. GitHub is linked once " +
                "so Ei Void can clone and push.",
            style = MaterialTheme.typography.bodyMedium,
            color = VoidColors.oledTextSecondary,
        )
        Spacer(Modifier.height(18.dp))
        EiPrimaryButton(
            text = "Continue with Clerk",
            onClick = onContinue,
            enabled = !busy,
            loading = busy,
        )
        Spacer(Modifier.height(10.dp))
        EiSecondaryButton(
            text = "Create an account",
            onClick = onCreateAccount,
            enabled = !busy,
        )
    }
}

/** Shown when local.properties has no Clerk key yet. */
@Composable
private fun SetupCard() {
    EiCard {
        EiSectionLabel(text = "Setup")
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Clerk is not configured",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = VoidColors.oledTextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Add clerk.publishableKey and ei-void.apiUrl to local.properties, " +
                "then rebuild.",
            style = MaterialTheme.typography.bodyMedium,
            color = VoidColors.oledTextSecondary,
        )
    }
}

/** Signed in to Clerk, but the account has no GitHub link yet. */
@Composable
private fun GithubCard(
    accountPortalUrl: String?,
    busy: Boolean,
    onOpenPortal: (String) -> Unit,
    onRetry: () -> Unit,
) {
    EiCard {
        EiSectionLabel(text = "GitHub")
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Connect GitHub",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = VoidColors.oledTextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Your Clerk account is not linked to GitHub yet. Connect it once so " +
                "Ei Void can clone and push.",
            style = MaterialTheme.typography.bodyMedium,
            color = VoidColors.oledTextSecondary,
        )
        Spacer(Modifier.height(18.dp))
        if (accountPortalUrl != null) {
            EiPrimaryButton(
                text = "Open Clerk account",
                onClick = { onOpenPortal(accountPortalUrl) },
                enabled = !busy,
            )
            Spacer(Modifier.height(10.dp))
        }
        EiSecondaryButton(
            text = "I connected GitHub — retry",
            onClick = onRetry,
            enabled = !busy,
        )
        if (accountPortalUrl == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Open your Clerk account in a browser and connect GitHub.",
                style = MaterialTheme.typography.bodySmall,
                color = VoidColors.oledTextSecondary,
            )
        }
    }
}

/** Three quiet product lines, separated by hairlines. */
@Composable
private fun FeatureList() {
    Column(modifier = Modifier.fillMaxWidth()) {
        FeatureRow("AI assistant")
        EiDivider(modifier = Modifier.padding(start = 30.dp))
        FeatureRow("Bill and renewal reminders")
        EiDivider(modifier = Modifier.padding(start = 30.dp))
        FeatureRow("GitHub projects")
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val stroke = 1.8.dp.toPx()
            val check = Path().apply {
                moveTo(size.width * 0.14f, size.height * 0.54f)
                lineTo(size.width * 0.42f, size.height * 0.82f)
                lineTo(size.width * 0.88f, size.height * 0.24f)
            }
            drawPath(
                path = check,
                color = VoidColors.oledAccentBlue,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = VoidColors.oledTextPrimary,
        )
    }
}

/**
 * A minimal cat-in-a-ring mark drawn as white line art. It replaces the
 * illustrated logo on this screen so the landing page stays pure black.
 */
@Composable
private fun EiMark(
    modifier: Modifier = Modifier,
    size: Dp = 108.dp,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Ei Void mark" },
    ) {
        val stroke = 1.6.dp.toPx()
        val radius = this.size.minDimension / 2f
        val ring = radius * 0.72f
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f

        drawCircle(
            color = Color.White,
            radius = ring,
            center = Offset(cx, cy),
            style = Stroke(width = stroke),
        )

        val ears = Path().apply {
            moveTo(cx - ring * 0.56f, cy - ring * 0.44f)
            lineTo(cx - ring * 0.66f, cy - ring * 0.98f)
            lineTo(cx - ring * 0.18f, cy - ring * 0.78f)
            moveTo(cx + ring * 0.56f, cy - ring * 0.44f)
            lineTo(cx + ring * 0.66f, cy - ring * 0.98f)
            lineTo(cx + ring * 0.18f, cy - ring * 0.78f)
        }
        drawPath(
            path = ears,
            color = Color.White,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        val eyeRadius = stroke * 1.35f
        drawCircle(
            color = Color.White,
            radius = eyeRadius,
            center = Offset(cx - ring * 0.26f, cy - ring * 0.04f),
        )
        drawCircle(
            color = Color.White,
            radius = eyeRadius,
            center = Offset(cx + ring * 0.26f, cy - ring * 0.04f),
        )

        // A short nose stroke, the only vertical detail inside the ring.
        drawLine(
            color = Color.White.copy(alpha = 0.86f),
            start = Offset(cx, cy + ring * 0.10f),
            end = Offset(cx, cy + ring * 0.22f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
