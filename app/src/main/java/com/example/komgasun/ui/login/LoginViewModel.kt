package com.example.komgasun.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.komgasun.KomgasunApp
import com.example.komgasun.data.model.ServerConfig
import com.example.komgasun.data.repository.KomgaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class LoginViewModel(
    private val repository: KomgaRepository = KomgasunApp.instance.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedUrl = repository.preferencesManager.savedUrlFlow.firstOrNull() ?: ""
            val savedUsername = repository.preferencesManager.savedUsernameFlow.firstOrNull() ?: ""
            _uiState.update {
                it.copy(
                    serverUrl = savedUrl.ifBlank { "http://192.168.1.100:25600" },
                    username = savedUsername
                )
            }
        }
    }

    fun onServerUrlChange(url: String) {
        _uiState.update { it.copy(serverUrl = url, errorMessage = null) }
    }

    fun onUsernameChange(username: String) {
        _uiState.update { it.copy(username = username, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        val cleanUrl = state.serverUrl.trim()
        val username = state.username.trim()
        val password = state.password

        if (cleanUrl.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Introduce la URL del servidor") }
            return
        }
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            _uiState.update { it.copy(errorMessage = "La URL debe comenzar con http:// o https://") }
            return
        }
        if (username.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Introduce el nombre de usuario") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val config = ServerConfig(
                serverUrl = cleanUrl,
                username = username,
                password = password
            )
            val result = repository.login(config)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                onSuccess()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Error de autenticación"
                    )
                }
            }
        }
    }
}
