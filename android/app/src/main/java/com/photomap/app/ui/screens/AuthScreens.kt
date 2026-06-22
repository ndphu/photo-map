package com.photomap.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.photomap.app.ui.AuthUiState
import com.photomap.app.data.preferences.BackendUrlConfiguration

@Composable
fun LoginScreen(
    state: AuthUiState,
    backendConfiguration: BackendUrlConfiguration,
    onLogin: (String, String) -> Unit,
    onRegister: () -> Unit,
    onConfigureBackend: (Boolean, String) -> Unit,
    onClearBackendError: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showBackendDialog by remember { mutableStateOf(false) }

    AuthForm(
        title = "Private gallery",
        subtitle = "Sign in to your encrypted cloud library",
        state = state,
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onLogin(email, password) },
            enabled = !state.loading &&
                !state.switchingBackend &&
                email.isNotBlank() &&
                password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Sign in")
            }
        }
        TextButton(onClick = onRegister, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Create account")
        }
        TextButton(
            onClick = {
                onClearBackendError()
                showBackendDialog = true
            },
            enabled = !state.loading && !state.switchingBackend,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Server")
        }
    }

    if (showBackendDialog) {
        BackendServerDialog(
            configuration = backendConfiguration,
            saving = state.switchingBackend,
            externalError = state.backendError,
            onDismiss = { showBackendDialog = false },
            onSave = { useCustomUrl, customBaseUrl ->
                showBackendDialog = false
                onConfigureBackend(useCustomUrl, customBaseUrl)
            },
        )
    }
}

@Composable
fun RegisterScreen(
    state: AuthUiState,
    backendConfiguration: BackendUrlConfiguration,
    onRegister: (String, String, String) -> Unit,
    onLogin: () -> Unit,
    onConfigureBackend: (Boolean, String) -> Unit,
    onClearBackendError: () -> Unit,
) {
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showBackendDialog by remember { mutableStateOf(false) }

    AuthForm(
        title = "Create account",
        subtitle = "Your media stays in your private R2 bucket",
        state = state,
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onRegister(email, password, displayName) },
            enabled = !state.loading &&
                !state.switchingBackend &&
                displayName.isNotBlank() &&
                email.isNotBlank() &&
                password.length >= 8,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.loading) "Creating..." else "Create account")
        }
        TextButton(onClick = onLogin, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Back to sign in")
        }
        TextButton(
            onClick = {
                onClearBackendError()
                showBackendDialog = true
            },
            enabled = !state.loading && !state.switchingBackend,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Server")
        }
    }

    if (showBackendDialog) {
        BackendServerDialog(
            configuration = backendConfiguration,
            saving = state.switchingBackend,
            externalError = state.backendError,
            onDismiss = { showBackendDialog = false },
            onSave = { useCustomUrl, customBaseUrl ->
                showBackendDialog = false
                onConfigureBackend(useCustomUrl, customBaseUrl)
            },
        )
    }
}

@Composable
private fun AuthForm(
    title: String,
    subtitle: String,
    state: AuthUiState,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        state.backendError?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
