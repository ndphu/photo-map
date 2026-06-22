package com.photomap.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photomap.app.data.preferences.BackendUrlConfiguration
import com.photomap.app.data.preferences.normalizeBackendBaseUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackendServerDialog(
    configuration: BackendUrlConfiguration,
    saving: Boolean,
    externalError: String?,
    onDismiss: () -> Unit,
    onSave: (Boolean, String) -> Unit,
) {
    var useCustomUrl by remember(configuration) {
        mutableStateOf(configuration.useCustomUrl)
    }
    var customBaseUrl by remember(configuration) {
        mutableStateOf(configuration.customBaseUrl)
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Backend server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    BackendMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = useCustomUrl == (mode == BackendMode.CUSTOM),
                            onClick = {
                                useCustomUrl = mode == BackendMode.CUSTOM
                                validationError = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = BackendMode.entries.size,
                            ),
                            enabled = !saving,
                        ) {
                            Text(mode.label)
                        }
                    }
                }
                if (useCustomUrl) {
                    OutlinedTextField(
                        value = customBaseUrl,
                        onValueChange = {
                            customBaseUrl = it
                            validationError = null
                        },
                        label = { Text("Base URL") },
                        placeholder = { Text("https://api.example.com/") },
                        singleLine = true,
                        enabled = !saving,
                        isError = validationError != null || externalError != null,
                        supportingText = {
                            (validationError ?: externalError)?.let { Text(it) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = configuration.defaultBaseUrl,
                        onValueChange = {},
                        label = { Text("Build default") },
                        singleLine = true,
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    externalError?.let { Text(it) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalizedUrl = if (useCustomUrl) {
                        try {
                            normalizeBackendBaseUrl(customBaseUrl)
                        } catch (error: IllegalArgumentException) {
                            validationError = error.message
                            return@Button
                        }
                    } else {
                        customBaseUrl
                    }
                    onSave(useCustomUrl, normalizedUrl)
                },
                enabled = !saving,
            ) {
                Text(if (saving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text("Cancel")
            }
        },
    )
}

private enum class BackendMode(val label: String) {
    DEFAULT("Default"),
    CUSTOM("Custom"),
}
