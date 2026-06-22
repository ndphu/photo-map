package com.photomap.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.photomap.app.data.repository.AuthRepository
import com.photomap.app.data.repository.BackendServerManager
import com.photomap.app.data.preferences.BackendUrlConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val switchingBackend: Boolean = false,
    val backendError: String? = null,
)

class AuthViewModel(
    private val repository: AuthRepository,
    private val backendServerManager: BackendServerManager,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()
    val backendConfiguration: StateFlow<BackendUrlConfiguration> =
        backendServerManager.configuration

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        submit(onSuccess) { repository.login(email, password) }
    }

    fun register(email: String, password: String, displayName: String, onSuccess: () -> Unit) {
        submit(onSuccess) { repository.register(email, password, displayName) }
    }

    fun switchBackend(
        useCustomUrl: Boolean,
        customBaseUrl: String,
        onSuccess: (Boolean) -> Unit,
    ) {
        if (_state.value.switchingBackend) return
        viewModelScope.launch {
            _state.value = _state.value.copy(switchingBackend = true, backendError = null)
            try {
                val changed = backendServerManager.switchServer(useCustomUrl, customBaseUrl)
                _state.value = _state.value.copy(switchingBackend = false, backendError = null)
                onSuccess(changed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    switchingBackend = false,
                    backendError = error.message ?: "Cannot change backend server",
                )
            }
        }
    }

    fun clearBackendError() {
        _state.value = _state.value.copy(backendError = null)
    }

    private fun submit(onSuccess: () -> Unit, action: suspend () -> Unit) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = AuthUiState(loading = true)
            runCatching { action() }
                .onSuccess {
                    _state.value = AuthUiState()
                    onSuccess()
                }
                .onFailure {
                    _state.value = AuthUiState(error = it.message ?: "Request failed")
                }
        }
    }
}

class AuthViewModelFactory(
    private val repository: AuthRepository,
    private val backendServerManager: BackendServerManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AuthViewModel(repository, backendServerManager) as T
}
