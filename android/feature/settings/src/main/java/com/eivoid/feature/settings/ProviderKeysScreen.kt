package com.eivoid.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eivoid.designsystem.component.EiCard
import com.eivoid.designsystem.component.EiNotice
import com.eivoid.designsystem.component.EiPrimaryButton
import com.eivoid.designsystem.component.EiScreen
import com.eivoid.designsystem.component.EiSecondaryButton
import com.eivoid.designsystem.component.EiSectionLabel
import com.eivoid.designsystem.component.EiStatusPill
import com.eivoid.designsystem.component.EiTextField
import com.eivoid.designsystem.component.EiTopBar
import com.eivoid.designsystem.theme.VoidColors

/**
 * Provider API keys, entered one provider at a time.
 *
 * A saved key is never read back into the field. The card only reports that a
 * key exists, and the password field starts empty so the user can replace it.
 */
@Composable
fun ProviderKeysScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    EiScreen {
        EiTopBar(
            title = "Provider keys",
            subtitle = "Stored on this device",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                EiNotice(
                    message = "Keys stay on this phone and are sent only to the " +
                        "provider you call. A saved key is never shown again.",
                )
            }

            item { EiSectionLabel("PROVIDERS") }

            items(
                items = SettingsViewModel.PROVIDERS,
                key = { it.id },
            ) { provider ->
                ProviderKeyCard(
                    provider = provider,
                    viewModel = viewModel,
                )
            }

            item {
                Text(
                    text = "Clearing a key revokes it from this device. You can paste " +
                        "a new key at any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoidColors.oledTextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ProviderKeyCard(
    provider: ProviderInfo,
    viewModel: SettingsViewModel,
) {
    // The draft is deliberately not saveable: key material must not be written
    // to the saved instance state when the card leaves composition.
    var draft by remember(provider.id) { mutableStateOf("") }
    var saved by remember(provider.id) {
        mutableStateOf(viewModel.hasProviderKey(provider.id))
    }

    EiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VoidColors.oledTextPrimary,
                )
                Text(
                    text = provider.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = VoidColors.oledTextSecondary,
                )
            }
            Spacer(Modifier.width(10.dp))
            EiStatusPill(
                text = if (saved) "Key saved" else "Not set",
                color = if (saved) VoidColors.oledSuccess else VoidColors.oledTextSecondary,
            )
        }

        Spacer(Modifier.height(16.dp))

        EiTextField(
            value = draft,
            onValueChange = { draft = it },
            label = "API key",
            placeholder = if (saved) "Paste a replacement key" else "Paste your key",
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
            ),
            visualTransformation = PasswordVisualTransformation(),
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EiPrimaryButton(
                text = if (saved) "Replace" else "Save",
                onClick = {
                    viewModel.saveProviderKey(provider.id, draft)
                    draft = ""
                    saved = viewModel.hasProviderKey(provider.id)
                },
                modifier = Modifier.weight(1f),
                enabled = draft.isNotBlank(),
            )
            EiSecondaryButton(
                text = "Clear",
                onClick = {
                    viewModel.clearProviderKey(provider.id)
                    draft = ""
                    saved = false
                },
                modifier = Modifier.weight(1f),
                enabled = saved,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = if (saved) {
                "A key is saved on this device. It is never displayed back to you."
            } else {
                "No key saved for this provider."
            },
            style = MaterialTheme.typography.bodySmall,
            color = VoidColors.oledTextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
