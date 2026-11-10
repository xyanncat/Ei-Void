package com.voidapp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Key entry per provider. Existing keys are never shown back to the user:
 * the field is always empty and a saved key is indicated instead.
 */
@Composable
fun ProviderKeysSection(viewModel: SettingsViewModel) {
    Column(
        modifier = Modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsViewModel.PROVIDERS.forEach { provider ->
            ProviderKeyRow(provider = provider, viewModel = viewModel)
        }
    }
}

@Composable
private fun ProviderKeyRow(provider: ProviderInfo, viewModel: SettingsViewModel) {
    var draft by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(viewModel.hasProviderKey(provider.id)) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = provider.displayName, style = MaterialTheme.typography.titleLarge)
            if (saved) {
                Text(
                    text = "Key saved",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Text(
            text = provider.note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text(if (saved) "Replace saved key" else "Paste API key") },
            singleLine = true,
            visualTransformation = if (revealed) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                TextButton(onClick = { revealed = !revealed }) {
                    Text(if (revealed) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    viewModel.saveProviderKey(provider.id, draft)
                    draft = ""
                    revealed = false
                    saved = true
                },
                enabled = draft.isNotBlank(),
            ) {
                Text("Save")
            }
            TextButton(
                onClick = {
                    viewModel.clearProviderKey(provider.id)
                    draft = ""
                    saved = false
                },
                enabled = saved,
            ) {
                Text("Clear")
            }
        }
    }
}
