package com.sunvader.komgasun.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunvader.komgasun.KomgasunApp
import com.sunvader.komgasun.data.model.ServerConfig
import com.sunvader.komgasun.data.model.ServerType
import com.sunvader.komgasun.data.repository.KomgaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val serverUrl: String = "",
    val localUrl: String = "",
    val remoteUrl: String = "",
    val activeServerType: ServerType = ServerType.LOCAL,
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
            val local = repository.preferencesManager.localUrlFlow.firstOrNull() ?: ""
            val remote = repository.preferencesManager.remoteUrlFlow.firstOrNull() ?: ""
            val activeType = repository.preferencesManager.activeServerTypeFlow.firstOrNull() ?: ServerType.LOCAL
            val savedUsername = repository.preferencesManager.savedUsernameFlow.firstOrNull() ?: ""
            val defaultLocal = local.ifBlank { "http://192.168.1.100:25600" }
            val currentUrl = if (activeType == ServerType.REMOTE) remote else defaultLocal

            _uiState.update {
                it.copy(
                    serverUrl = currentUrl,
                    localUrl = defaultLocal,
                    remoteUrl = remote,
                    activeServerType = activeType,
                    username = savedUsername
                )
            }
        }
    }

    fun onServerTypeSelect(type: ServerType) {
        _uiState.update { current ->
            val updatedLocal = if (current.activeServerType == ServerType.LOCAL) current.serverUrl else current.localUrl
            val updatedRemote = if (current.activeServerType == ServerType.REMOTE) current.serverUrl else current.remoteUrl
            val newActiveUrl = if (type == ServerType.REMOTE) updatedRemote else updatedLocal
            current.copy(
                activeServerType = type,
                serverUrl = newActiveUrl,
                localUrl = updatedLocal,
                remoteUrl = updatedRemote,
                errorMessage = null
            )
        }
    }

    fun onServerUrlChange(url: String) {
        _uiState.update { current ->
            val updatedLocal = if (current.activeServerType == ServerType.LOCAL) url else current.localUrl
            val updatedRemote = if (current.activeServerType == ServerType.REMOTE) url else current.remoteUrl
            current.copy(
                serverUrl = url,
                localUrl = updatedLocal,
                remoteUrl = updatedRemote,
                errorMessage = null
            )
        }
    }

    fun onUsernameChange(username: String) {
        _uiState.update { it.copy(username = username, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        var cleanUrl = state.serverUrl.trim().removeSuffix("/")
        val username = state.username.trim()
        val password = state.password

        if (cleanUrl.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Introduce la URL del servidor") }
            return
        }
        if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
            cleanUrl = "http://$cleanUrl"
        }
        if (username.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Introduce el nombre de usuario") }
            return
        }

        val otherUrl = if (state.activeServerType == ServerType.LOCAL) state.remoteUrl else state.localUrl

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val config = ServerConfig(
                serverUrl = cleanUrl,
                username = username,
                password = password
            )
            val result = repository.login(config, state.activeServerType, otherUrl)
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
