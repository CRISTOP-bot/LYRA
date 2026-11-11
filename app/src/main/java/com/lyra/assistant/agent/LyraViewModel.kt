package com.lyra.assistant.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lyra.assistant.data.ChatMessage
import com.lyra.assistant.data.ConversationStore
import com.lyra.assistant.data.ProviderSettings
import com.lyra.assistant.security.SecretVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LyraViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ConversationStore(app)
    private val vault = SecretVault()
    private val provider = OpenAiCompatibleProvider()
    val messages = store.messages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderSettings("https://api.openai.com/v1", "gpt-4o-mini"))
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _status = MutableStateFlow("En reposo")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _hasKey = MutableStateFlow(false)
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    init { viewModelScope.launch { store.encryptedSecret.collect { _hasKey.value = !it.isNullOrBlank() } } }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || _busy.value) return
        viewModelScope.launch {
            _error.value = null
            val user = ChatMessage(role = "user", text = clean)
            store.append(user)
            _busy.value = true
            _status.value = "Procesando"
            try {
                val encrypted = store.encryptedSecret.first()
                val secret = encrypted?.let(vault::decrypt).orEmpty()
                val history = store.messages.first().takeLast(24).map { it.role to it.text }
                _status.value = "Conectando con el modelo"
                val answer = withContext(Dispatchers.IO) { provider.reply(settings.value.baseUrl, settings.value.model, secret, history) }
                store.append(ChatMessage(role = "assistant", text = answer))
                _status.value = "Respuesta lista"
            } catch (e: Exception) {
                val message = e.message ?: "No se pudo completar la solicitud."
                _error.value = message
                store.append(ChatMessage(role = "assistant", text = "No pude responder: $message"))
                _status.value = "Error"
            } finally { _busy.value = false }
        }
    }

    fun saveSettings(baseUrl: String, model: String, apiKey: String) {
        viewModelScope.launch {
            runCatching {
                require(baseUrl.startsWith("https://")) { "La dirección del proveedor debe usar HTTPS." }
                require(model.isNotBlank()) { "Escribe el identificador del modelo." }
                store.saveProvider(baseUrl, model)
                if (apiKey.isNotBlank()) store.saveSecret(vault.encrypt(apiKey.trim()))
                _error.value = null
            }.onFailure { _error.value = it.message }
        }
    }

    fun clearConversation() { viewModelScope.launch { store.clearConversation() } }
    fun setStatus(value: String) { _status.value = value }
}
